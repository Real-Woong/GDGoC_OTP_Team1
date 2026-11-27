"""OCR 7단계: 규칙 기반 검증.

정규화 결과를 검사해서 검사마다 pass / warning / fail / skipped 를 매긴다.
  - fail     확인 화면으로 넘길 수 없음 (금액이 하나도 없음, 합계 기준이 전혀 없음)
  - warning  사용자 확인이 필요함. 관련 필드는 status를 needs_review로 바꾼다
  - skipped  필요한 값이 없어서 검사하지 못함 (예: 요율표에 없는 연도)

검사 목록
  필수   NO_AMOUNTS(fail), NO_TOTALS(fail), MISSING_BASE_SALARY, MISSING_NET_PAY
  합계   SUM_PAYMENT, SUM_DEDUCTION, SUM_NET, UNMAPPED_CATEGORY
         - unmapped 항목(직책수당, 건강보험정산 등)은 지급/공제를 모르므로, 지급·공제에 나눠 넣는
           모든 경우를 시도해서 합계가 맞는 배치를 찾는다. 배치가 하나로 정해지면 category를 채운다.
  요율   RATE_LOCAL_INCOME_TAX, RATE_LONG_TERM_CARE (규칙이 단순해 신뢰도 높음)
         RATE_EMPLOYMENT_INSURANCE (비과세 판정에 따라 달라짐)
         RATE_NATIONAL_PENSION, RATE_HEALTH_INSURANCE (기준소득월액 기준이라 정상이어도 다를 수 있음, 오차 넓게)
  입사일 JOIN_DATE_AFTER_PERIOD (입사일이 귀속월보다 뒤)
  인식   FIELD_NEEDS_REVIEW (fuzzy 매칭·낮은 OCR 신뢰도)

요율·허용 오차는 rates.json에서 읽는다.
"""

from __future__ import annotations

import itertools
import json
import math
from pathlib import Path

RATES_PATH = Path(__file__).with_name("rates.json")
MAX_UNMAPPED_FOR_SEARCH = 10  # 2^10 = 1024가지. 이보다 많으면 배치 탐색을 건너뛴다

PAYMENT_FIELDS = ["base_salary", "meal_allowance", "car_allowance", "overtime_pay", "bonus"]
DEDUCTION_FIELDS = ["national_pension", "health_insurance", "long_term_care",
                    "employment_insurance", "income_tax", "local_income_tax"]
LEVEL_RANK = {"pass": 0, "skipped": 0, "warning": 1, "fail": 2}


def floor10(x: float) -> int:
    return int(math.floor(round(x, 6) / 10) * 10)


def load_rates(path: Path = RATES_PATH) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def check(code, level, field=None, expected=None, actual=None, message=None) -> dict:
    return {"code": code, "level": level, "field": field, "expected": expected, "actual": actual, "message": message}


def validate(fields: dict[str, dict], unmapped: list[dict], pay_year: int | None,
             rates: dict | None = None, join_date: str | None = None, pay_period: str | None = None) -> dict:
    """fields·unmapped를 그 자리에서 고친다(status, category). 검증 결과 dict를 돌려준다."""
    rates = rates or load_rates()
    v = {k: f["value"] for k, f in fields.items()}
    checks: list[dict] = []

    # ---- 필수 ----
    if all(x is None for x in v.values()) and not unmapped:
        checks.append(check("NO_AMOUNTS", "fail", message="명세서에서 금액을 하나도 찾지 못했어요."))
        return finish(checks, fields, None)
    if v["payment_total"] is None and v["net_pay"] is None:
        checks.append(check("NO_TOTALS", "fail", message="지급합계와 실지급액을 모두 찾지 못해 금액을 확인할 수 없어요."))
    if v["base_salary"] is None:
        checks.append(check("MISSING_BASE_SALARY", "warning", "base_salary", message="기본급을 찾지 못했어요."))
    if v["net_pay"] is None:
        checks.append(check("MISSING_NET_PAY", "warning", "net_pay", message="실지급액을 찾지 못했어요."))

    # ---- 합계 ----
    checks += check_sums(v, unmapped)

    # ---- 요율 ----
    year_rates = rates["years"].get(str(pay_year)) if pay_year else None
    if year_rates is None:
        checks.append(check("RATE_TABLE", "skipped",
                            message=f"{pay_year}년 요율표가 없어 요율 검사를 건너뛰었어요." if pay_year
                            else "귀속 연도를 알 수 없어 요율 검사를 건너뛰었어요."))
    else:
        checks += check_rates(v, year_rates, rates["tolerances"])

    # ---- 입사일 ----
    # 귀속월보다 뒤에 입사했다면 날짜를 잘못 읽었거나 다른 날짜를 잡은 것
    if join_date and pay_period and join_date[:7] > pay_period:
        checks.append(check("JOIN_DATE_AFTER_PERIOD", "warning", "join_date",
                            message=f"입사일({join_date})이 귀속월({pay_period})보다 뒤예요. 입사일을 확인해 주세요."))

    # ---- 인식 품질 ----
    for name, f in fields.items():
        if f["status"] == "needs_review":
            reason = "철자를 추정해서 맞췄어요" if f["match_method"] == "fuzzy" else "글자를 읽은 신뢰도가 낮아요"
            checks.append(check("FIELD_NEEDS_REVIEW", "warning", name, actual=f["value"],
                                message=f"'{f['source_label']}' 값을 확인해 주세요. ({reason})"))

    return finish(checks, fields, pay_year if year_rates else None)


def finish(checks: list[dict], fields: dict[str, dict], rate_year: int | None) -> dict:
    # warning이 걸린 필드는 확인 화면에서 강조되도록 needs_review로 바꾼다
    for c in checks:
        if c["level"] == "warning" and c["field"] in fields and fields[c["field"]]["status"] == "ok":
            fields[c["field"]]["status"] = "needs_review"
    worst = max((c["level"] for c in checks), key=LEVEL_RANK.get, default="pass")
    summary = "pass" if worst == "skipped" else worst
    return {"summary": summary, "rate_year": rate_year, "checks": checks}


# ---- 합계 검증 ----------------------------------------------------------------

def check_sums(v: dict, unmapped: list[dict]) -> list[dict]:
    out = []
    pay_known = sum(v[k] or 0 for k in PAYMENT_FIELDS)
    ded_known = sum(v[k] or 0 for k in DEDUCTION_FIELDS)
    pt, dt, net = v["payment_total"], v["deduction_total"], v["net_pay"]
    values = [u["value"] for u in unmapped]

    def pay_ok(mask):
        return pt is None or pay_known + sum(x for x, m in zip(values, mask) if m) == pt

    def ded_ok(mask):
        return dt is None or ded_known + sum(x for x, m in zip(values, mask) if not m) == dt

    if len(values) <= MAX_UNMAPPED_FOR_SEARCH:
        masks = list(itertools.product([True, False], repeat=len(values)))  # True = 지급
        both = [m for m in masks if pay_ok(m) and ded_ok(m)]
        any_pay = any(pay_ok(m) for m in masks)
        any_ded = any(ded_ok(m) for m in masks)
    else:
        both, any_pay, any_ded = [], pay_ok([True] * len(values)), ded_ok([False] * len(values))

    if pt is None:
        out.append(check("SUM_PAYMENT", "skipped", "payment_total", message="지급합계가 없어 검사하지 못했어요."))
    elif any_pay:
        out.append(check("SUM_PAYMENT", "pass", "payment_total", pt, pt))
    else:
        out.append(check("SUM_PAYMENT", "warning", "payment_total", pt, pay_known,
                         f"지급 항목을 더한 값({pay_known:,}원)이 지급합계({pt:,}원)와 달라요."))

    if dt is None:
        out.append(check("SUM_DEDUCTION", "skipped", "deduction_total", message="공제합계가 없어 검사하지 못했어요."))
    elif any_ded:
        out.append(check("SUM_DEDUCTION", "pass", "deduction_total", dt, dt))
    else:
        out.append(check("SUM_DEDUCTION", "warning", "deduction_total", dt, ded_known,
                         f"공제 항목을 더한 값({ded_known:,}원)이 공제합계({dt:,}원)와 달라요."))

    # unmapped 항목의 지급/공제 배치
    if unmapped:
        if len(both) >= 1 and all(m == both[0] for m in both):
            for u, is_pay in zip(unmapped, both[0]):
                u["category"] = "payment" if is_pay else "deduction"
            out.append(check("UNMAPPED_CATEGORY", "pass",
                             message="합계가 맞도록 분류되지 않은 항목의 지급/공제를 정했어요."))
        elif any_pay and any_ded and not both:
            out.append(check("UNMAPPED_CATEGORY", "warning",
                             message="분류되지 않은 항목을 지급·공제 어느 쪽에 넣어도 두 합계가 동시에 맞지 않아요."))
        else:
            out.append(check("UNMAPPED_CATEGORY", "warning",
                             message="분류되지 않은 항목이 지급인지 공제인지 확인해 주세요."))

    if pt is not None and dt is not None and net is not None:
        level = "pass" if pt - dt == net else "warning"
        out.append(check("SUM_NET", level, "net_pay", pt - dt, net,
                         None if level == "pass" else
                         f"지급합계 − 공제합계({pt - dt:,}원)가 실지급액({net:,}원)과 달라요."))
    else:
        out.append(check("SUM_NET", "skipped", "net_pay", message="합계 값이 없어 실지급액을 검사하지 못했어요."))
    return out


# ---- 요율 검증 ----------------------------------------------------------------

def within(expected: int, actual: int, tol: dict) -> bool:
    return abs(expected - actual) <= max(tol["abs"], abs(expected) * tol["rel"])


def check_rates(v: dict, r: dict, tol: dict) -> list[dict]:
    out = []

    def rate_check(code, field, expected, note=""):
        actual = v[field]
        if expected is None or actual is None:
            out.append(check(code, "skipped", field, message="필요한 값이 없어 검사하지 못했어요."))
            return
        ok = within(expected, actual, tol[field])
        out.append(check(code, "pass" if ok else "warning", field, expected, actual,
                         None if ok else f"계산한 값({expected:,}원)과 명세서 값({actual:,}원)이 달라요.{note}"))

    # 과세 급여 = 지급합계 - 비과세(식대·차량유지비 한도까지)
    taxable = None
    if v["payment_total"] is not None:
        nontax = sum(min(v[k] or 0, limit) for k, limit in r["nontaxable_limits"].items())
        taxable = v["payment_total"] - nontax

    # 신뢰도 높은 검사
    rate_check("RATE_LOCAL_INCOME_TAX", "local_income_tax",
               None if v["income_tax"] is None else floor10(v["income_tax"] * r["local_income_tax"]["ratio_of_income_tax"]))
    rate_check("RATE_LONG_TERM_CARE", "long_term_care",
               None if v["health_insurance"] is None
               else floor10(v["health_insurance"] * r["long_term_care"]["ratio_of_health_insurance"]))

    # 중간
    rate_check("RATE_EMPLOYMENT_INSURANCE", "employment_insurance",
               None if taxable is None else floor10(taxable * r["employment_insurance"]["rate"]),
               " 비과세 항목 판정에 따라 다를 수 있어요.")

    # 느슨한 검사: 이번 달 급여가 아니라 신고된 기준소득월액·보수월액으로 계산하므로 달라도 정상일 수 있음
    pension_base = taxable
    p = r["national_pension"]
    if pension_base is not None:
        if p.get("base_monthly_min") is not None:
            pension_base = max(pension_base, p["base_monthly_min"])
        if p.get("base_monthly_max") is not None:
            pension_base = min(pension_base, p["base_monthly_max"])
    rate_check("RATE_NATIONAL_PENSION", "national_pension",
               None if pension_base is None else floor10(pension_base * p["rate"]),
               " 국민연금은 기준소득월액으로 계산해서 이번 달 급여와 다를 수 있어요.")
    rate_check("RATE_HEALTH_INSURANCE", "health_insurance",
               None if taxable is None else floor10(taxable * r["health_insurance"]["rate"]),
               " 건강보험은 보수월액으로 계산해서 이번 달 급여와 다를 수 있어요.")
    return out
