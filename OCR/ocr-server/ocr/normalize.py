"""OCR 6단계: 항목명 정규화 (동의어 사전 + RapidFuzz).

짝짓기 결과(항목명, 금액)의 항목명을 표준 필드(base_salary 등)로 바꾼다.
원본 항목명은 덮어쓰지 않고 그대로 보관한다. 금액은 보지 않고 항목명만으로 판단한다.

매칭 순서
  1. exact       공백만 지우고 사전에 있는지
  2. (차단)       정산·환급·소급 등이 들어 있으면 여기서 멈추고 unmapped로 보낸다
                 ("건강보험정산"은 글자는 비슷하지만 다른 항목이다)
  3. normalized  괄호 부분(일할 등)·기호·끝의 '료'를 정리한 뒤 사전에 있는지
  4. fuzzy       RapidFuzz 편집 거리로 OCR 오타 정리
                 - 2글자 이하: 쓰지 않음  - 3~5글자: 1글자 차이까지  - 6글자 이상: 2글자 차이까지
                 - 같은 거리의 후보가 서로 다른 필드면 애매하므로 매칭하지 않음
                 - fuzzy로 맞춘 결과는 항상 needs_review
  5. 위에서 다 안 되면 unmapped_items로 보낸다

같은 필드에 항목이 여러 개 오면(연장근로수당 + 휴일근로수당 → overtime_pay) 금액을 합산하고 원본 목록을 남긴다.
합계 필드(지급합계 등)는 합산하지 않는다. 서로 다른 값이 두 번 나오면 첫 값을 쓰고 needs_review로 표시한다.
"""

from __future__ import annotations

import json
import re
from dataclasses import dataclass, field
from pathlib import Path

from rapidfuzz.distance import Levenshtein

from ocr.pairing import Pair

SYNONYMS_PATH = Path(__file__).with_name("synonyms.json")
REVIEW_CONFIDENCE = 0.8  # 이보다 낮은 OCR 신뢰도는 needs_review (계획서 기준)
METHOD_RANK = {"exact": 0, "normalized": 1, "fuzzy": 2}


# ---- 사전 불러오기 -------------------------------------------------------------

@dataclass
class Dictionary:
    index: dict[str, str]            # 정리된 동의어 → 필드
    categories: dict[str, str]       # 필드 → payment | deduction | total
    no_fuzzy_words: list[str]

    @classmethod
    def load(cls, path: Path = SYNONYMS_PATH) -> "Dictionary":
        data = json.loads(path.read_text(encoding="utf-8"))
        index: dict[str, str] = {}
        categories = {}
        for field_name, spec in data["fields"].items():
            categories[field_name] = spec["category"]
            for syn in spec["synonyms"]:
                key, _ = clean(syn)
                if key in index and index[key] != field_name:
                    raise ValueError(f"동의어 '{syn}'이(가) {index[key]}와 {field_name}에 모두 들어 있어요.")
                index[key] = field_name
        return cls(index, categories, data.get("no_fuzzy_words", []))


# ---- 항목명 정리 -----------------------------------------------------------------

PAREN_RE = re.compile(r"[(\[（［](.*?)[)\]）］]")
SYMBOL_RE = re.compile(r"[·•:：\-_.*※,]")


def clean(label: str) -> tuple[str, list[str]]:
    """공백·기호를 지우고 괄호 부분을 따로 뗀다. ("기본급 (일할)" → "기본급", ["일할"])"""
    s = re.sub(r"\s+", "", label)
    qualifiers = [q for q in PAREN_RE.findall(s) if q]
    s = SYMBOL_RE.sub("", PAREN_RE.sub("", s))
    return s, qualifiers


# ---- 항목명 하나 매칭 --------------------------------------------------------------

@dataclass
class LabelMatch:
    field: str | None
    method: str | None              # exact | normalized | fuzzy | None
    matched_synonym: str | None = None
    distance: int | None = None
    reason: str | None = None       # 매칭 안 된 이유: not_in_dictionary | blocked_word | ambiguous | empty
    qualifiers: list[str] = field(default_factory=list)


def _lookup(d: Dictionary, key: str) -> tuple[str, str] | None:
    if key in d.index:
        return d.index[key], key
    if key.endswith("료") and key[:-1] in d.index:  # 건강보험료 → 건강보험
        return d.index[key[:-1]], key[:-1]
    return None


def match_label(label: str, d: Dictionary) -> LabelMatch:
    raw = re.sub(r"\s+", "", label)
    key, qualifiers = clean(label)

    # 1. exact
    if raw in d.index:
        return LabelMatch(d.index[raw], "exact", raw, 0, qualifiers=qualifiers)

    # 2. 정산·환급 등이 있으면 괄호 안이라도 멈춘다 ("건강보험(정산)"도 건강보험이 아님)
    if any(w in raw for w in d.no_fuzzy_words):
        return LabelMatch(None, None, reason="blocked_word", qualifiers=qualifiers)
    if not key:
        return LabelMatch(None, None, reason="empty", qualifiers=qualifiers)

    # 3. normalized
    found = _lookup(d, key)
    if found:
        return LabelMatch(found[0], "normalized", found[1], 0, qualifiers=qualifiers)

    # 4. fuzzy
    query = key[:-1] if key.endswith("료") and len(key) > 3 else key
    if len(query) <= 2:
        return LabelMatch(None, None, reason="not_in_dictionary", qualifiers=qualifiers)
    max_distance = 1 if len(query) <= 5 else 2
    best_distance, best = max_distance + 1, []
    for syn, field_name in d.index.items():
        dist = Levenshtein.distance(query, syn, score_cutoff=max_distance)
        if dist < best_distance:
            best_distance, best = dist, [(syn, field_name)]
        elif dist == best_distance:
            best.append((syn, field_name))
    if best_distance > max_distance:
        return LabelMatch(None, None, reason="not_in_dictionary", qualifiers=qualifiers)
    fields = {f for _, f in best}
    if len(fields) > 1:
        return LabelMatch(None, None, reason="ambiguous", qualifiers=qualifiers)
    syn, field_name = best[0]
    return LabelMatch(field_name, "fuzzy", syn, best_distance, qualifiers=qualifiers)


# ---- 짝 목록 전체 정규화 ----------------------------------------------------------

@dataclass
class NormalizedResult:
    fields: dict[str, dict]
    unmapped_items: list[dict]
    warnings: list[dict]

    def to_dict(self) -> dict:
        return {"fields": self.fields, "unmapped_items": self.unmapped_items, "warnings": self.warnings}


def normalize(pairs: list[Pair], d: Dictionary | None = None) -> NormalizedResult:
    d = d or Dictionary.load()
    fields: dict[str, dict] = {}
    unmapped, warnings = [], []

    for p in pairs:
        m = match_label(p.label, d)
        if m.field is None:
            unmapped.append({"source_label": p.label, "value": p.amount, "confidence": p.confidence,
                             "reason": m.reason, "qualifiers": m.qualifiers})
            continue

        source = {"source_label": p.label, "value": p.amount, "confidence": p.confidence,
                  "match_method": m.method, "matched_synonym": m.matched_synonym, "qualifiers": m.qualifiers}
        f = fields.get(m.field)
        if f is None:
            fields[m.field] = {"value": p.amount, "sources": [source]}
        elif d.categories[m.field] == "total":
            f["sources"].append(source)
            if f["value"] != p.amount:
                f["conflict"] = True
                warnings.append({"code": "DUPLICATE_TOTAL", "field": m.field,
                                 "message": f"{m.field}이(가) 서로 다른 값으로 두 번 나왔어요: "
                                            f"{f['value']:,} / {p.amount:,}"})
        else:
            f["value"] += p.amount  # 연장근로수당 + 휴일근로수당 등은 합산
            f["sources"].append(source)

    out = {}
    for name in d.categories:
        f = fields.get(name)
        if f is None:
            out[name] = {"value": None, "category": d.categories[name], "source_label": None, "confidence": None,
                         "match_method": None, "status": "missing", "sources": []}
            continue
        srcs = f["sources"]
        method = max((s["match_method"] for s in srcs), key=METHOD_RANK.get)
        confidence = min(s["confidence"] for s in srcs)
        needs_review = method == "fuzzy" or confidence < REVIEW_CONFIDENCE or f.get("conflict")
        out[name] = {
            "value": f["value"],
            "category": d.categories[name],
            "source_label": " + ".join(s["source_label"] for s in srcs),
            "confidence": confidence,
            "match_method": method,
            "status": "needs_review" if needs_review else "ok",
            "sources": srcs,
        }
    return NormalizedResult(out, unmapped, warnings)
