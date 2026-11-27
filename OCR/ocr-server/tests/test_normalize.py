"""항목명 정규화 규칙 테스트."""

import pytest

from ocr.normalize import Dictionary, match_label, normalize
from ocr.pairing import Pair
from ocr.recognize import TextBox
from ocr.schema import STANDARD_FIELDS

D = Dictionary.load()
RESULTS = []  # (이름, 결과, 기대값). 맨 끝 테스트 함수가 하나씩 확인한다


def check(name, got, expected):
    RESULTS.append((name, got, expected))


def m(label):
    r = match_label(label, D)
    return (r.field, r.method) if r.field else (None, r.reason)


# ---- 사전 그대로 (exact) ----
check("기본급", m("기본급"), ("base_salary", "exact"))
check("중식대", m("중식대"), ("meal_allowance", "exact"))
check("갑근세", m("갑근세"), ("income_tax", "exact"))
check("주민세", m("주민세"), ("local_income_tax", "exact"))
check("차인지급액", m("차인지급액"), ("net_pay", "exact"))
check("공백 포함 '기 본 급'", m("기 본 급"), ("base_salary", "exact"))

# ---- 정리 후 일치 (normalized) ----
check("괄호: 기본급(일할)", m("기본급(일할)"), ("base_salary", "normalized"))
check("끝의 료: 건강보험료", m("건강보험료"), ("health_insurance", "normalized"))
check("끝의 료: 장기요양보험료", m("장기요양보험료"), ("long_term_care", "normalized"))
check("기호: 식대·", m("식대·"), ("meal_allowance", "normalized"))

# ---- OCR 오타 (fuzzy) ----
check("중식데 → 중식대", m("중식데"), ("meal_allowance", "fuzzy"))
check("국민연긍 → 국민연금", m("국민연긍"), ("national_pension", "fuzzy"))
check("장기요양보헙 → 장기요양보험", m("장기요양보헙"), ("long_term_care", "fuzzy"))
check("연장근로수딩 → 연장근로수당", m("연장근로수딩"), ("overtime_pay", "fuzzy"))
check("지방소둑세 → 지방소득세", m("지방소둑세"), ("local_income_tax", "fuzzy"))

# ---- 잡으면 안 되는 것 ----
check("2글자 오타는 fuzzy 안 함: 식데", m("식데"), (None, "not_in_dictionary"))
check("소득세 ≠ 지방소득세", m("소득세"), ("income_tax", "exact"))
check("정산: 건강보험정산", m("건강보험정산"), (None, "blocked_word"))
check("정산이 괄호 안: 건강보험(정산)", m("건강보험(정산)"), (None, "blocked_word"))
check("환급: 소득세환급", m("소득세환급"), (None, "blocked_word"))
check("소급: 기본급소급", m("기본급소급"), (None, "blocked_word"))
check("사전에 없는 수당: 직책수당", m("직책수당"), (None, "not_in_dictionary"))
check("사전에 없는 수당: 자격수당", m("자격수당"), (None, "not_in_dictionary"))
check("사전에 없는 수당: 가족수당", m("가족수당"), (None, "not_in_dictionary"))
check("휴일수당은 사전에 있음", m("휴일수당"), ("overtime_pay", "exact"))
check("고용보험 ≠ 건강보험 (2글자 차이)", m("고용보험"), ("employment_insurance", "exact"))
check("오타 + 끝의 료: 고용보엄료", m("고용보엄료"), ("employment_insurance", "fuzzy"))
check("4글자에 2글자 오타는 매칭 안 함: 고용부엄", m("고용부엄"), (None, "not_in_dictionary"))
check("애매하면 매칭 안 함: 지급계/공제계 사이", m("지제계"), (None, "ambiguous"))


# ---- 합산·합계 처리 ----
def p(label, amount, conf=0.99):
    b = TextBox(label, conf, 0, 0, 10, 10)
    return Pair(label, amount, str(amount), conf, 0, b, b)


r = normalize([p("연장근로수당", 150000), p("휴일근로수당", 120000)], D)
check("같은 필드 합산", r.fields["overtime_pay"]["value"], 270000)
check("합산 원본 보관", r.fields["overtime_pay"]["source_label"], "연장근로수당 + 휴일근로수당")

r = normalize([p("지급합계", 3000000), p("지급총액", 3000000)], D)
check("합계 필드는 합산 안 함", (r.fields["payment_total"]["value"], r.fields["payment_total"]["status"]), (3000000, "ok"))
r = normalize([p("지급합계", 3000000), p("총지급액", 3100000)], D)
check("합계가 서로 다르면 needs_review", (r.fields["payment_total"]["value"], r.fields["payment_total"]["status"],
                                     len(r.warnings)), (3000000, "needs_review", 1))

r = normalize([p("중식데", 200000)], D)
check("fuzzy는 항상 needs_review", r.fields["meal_allowance"]["status"], "needs_review")
check("fuzzy도 원본 항목명 보관", r.fields["meal_allowance"]["source_label"], "중식데")
r = normalize([p("소득세", 0, conf=0.45)], D)
check("낮은 OCR 신뢰도는 needs_review", r.fields["income_tax"]["status"], "needs_review")
r = normalize([p("직책수당", 100000)], D)
check("매칭 안 되면 unmapped", (r.unmapped_items[0]["source_label"], r.unmapped_items[0]["value"]), ("직책수당", 100000))
check("없는 필드는 missing", (r.fields["bonus"]["value"], r.fields["bonus"]["status"]), (None, "missing"))

@pytest.mark.parametrize("name,got,expected", RESULTS, ids=[r[0] for r in RESULTS])
def test_normalize(name, got, expected):
    assert got == expected


def test_dictionary_matches_schema():
    """synonyms.json의 필드·지급/공제 구분이 출력 스키마와 같아야 한다."""
    assert D.categories == STANDARD_FIELDS
