"""규칙 검증(validate.py)과 출력 구조(schema.py) 테스트. OCR 없이 값만으로 확인한다."""

import copy

import pytest
from pydantic import ValidationError

from ocr.schema import STANDARD_FIELDS, PayslipResult
from ocr.validate import validate

# case01과 같은 값. 2026년 가정 요율로 모두 맞게 계산된 명세서
GOOD = {
    "base_salary": 2_800_000, "meal_allowance": 200_000, "car_allowance": None, "overtime_pay": 150_000,
    "bonus": None, "payment_total": 3_150_000,
    "national_pension": 140_120, "health_insurance": 106_050, "long_term_care": 13_930,
    "employment_insurance": 26_550, "income_tax": 78_000, "local_income_tax": 7_800,
    "deduction_total": 372_450, "net_pay": 2_777_550,
}


def make_fields(values: dict, status_override: dict | None = None) -> dict:
    fields = {}
    for name, value in values.items():
        missing = value is None
        fields[name] = {
            "value": value, "category": STANDARD_FIELDS[name],
            "source_label": None if missing else name, "confidence": None if missing else 0.99,
            "match_method": None if missing else "exact", "status": "missing" if missing else "ok",
            "sources": [] if missing else [{"source_label": name, "value": value, "confidence": 0.99,
                                            "match_method": "exact", "matched_synonym": name, "qualifiers": []}],
        }
    for name, status in (status_override or {}).items():
        fields[name]["status"] = status
    return fields


def unmapped(label, value):
    return {"source_label": label, "value": value, "confidence": 0.99, "reason": "not_in_dictionary", "qualifiers": []}


def run(values=GOOD, items=(), year=2026, **kw):
    fields = make_fields(values, kw.get("status"))
    items = [dict(u) for u in items]
    result = validate(fields, items, year)
    levels = {c["code"]: c["level"] for c in result["checks"]}
    return result, levels, fields, items


def with_values(**changes):
    v = dict(GOOD)
    v.update(changes)
    return v


# ---- 정상 ----

def test_consistent_payslip_passes():
    result, levels, _, _ = run()
    assert result["summary"] == "pass"
    assert all(level == "pass" for level in levels.values()), levels
    assert result["rate_year"] == 2026


# ---- 필수 ----

def test_no_amounts_fails():
    result, levels, _, _ = run({k: None for k in GOOD})
    assert result["summary"] == "fail" and levels["NO_AMOUNTS"] == "fail"


def test_no_totals_fails():
    result, levels, _, _ = run(with_values(payment_total=None, net_pay=None))
    assert result["summary"] == "fail" and levels["NO_TOTALS"] == "fail"


def test_missing_base_salary_is_only_warning():
    result, levels, _, _ = run(with_values(base_salary=None))
    assert levels["MISSING_BASE_SALARY"] == "warning"
    assert result["summary"] == "warning"


# ---- 합계 ----

def test_payment_sum_mismatch_warns_and_marks_field():
    # OCR이 기본급을 2,600,000으로 잘못 읽은 경우
    result, levels, fields, _ = run(with_values(base_salary=2_600_000))
    assert levels["SUM_PAYMENT"] == "warning"
    assert fields["payment_total"]["status"] == "needs_review"


def test_net_mismatch_warns():
    _, levels, fields, _ = run(with_values(net_pay=2_777_000))
    assert levels["SUM_NET"] == "warning"
    assert fields["net_pay"]["status"] == "needs_review"


def test_unmapped_payment_item_is_placed_by_sums():
    # 직책수당 100,000이 지급에 들어가야 지급합계가 맞는다
    _, levels, _, items = run(with_values(payment_total=3_250_000, net_pay=2_877_550),
                              items=[unmapped("직책수당", 100_000)])
    assert levels["SUM_PAYMENT"] == "pass" and levels["SUM_DEDUCTION"] == "pass"
    assert levels["UNMAPPED_CATEGORY"] == "pass"
    assert items[0]["category"] == "payment"


def test_unmapped_deduction_item_is_placed_by_sums():
    # 건강보험정산 12,340이 공제에 들어가야 공제합계가 맞는다
    _, levels, _, items = run(with_values(deduction_total=384_790, net_pay=2_765_210),
                              items=[unmapped("건강보험정산", 12_340)])
    assert levels["UNMAPPED_CATEGORY"] == "pass"
    assert items[0]["category"] == "deduction"


def test_unmapped_item_that_fits_nowhere_warns():
    _, levels, _, items = run(items=[unmapped("기타", 5_000)])
    assert levels["UNMAPPED_CATEGORY"] == "warning"
    assert items[0].get("category") is None


# ---- 요율 ----

def test_local_income_tax_must_be_ten_percent():
    _, levels, fields, _ = run(with_values(local_income_tax=8_800, deduction_total=373_450, net_pay=2_776_550))
    assert levels["RATE_LOCAL_INCOME_TAX"] == "warning"
    assert fields["local_income_tax"]["status"] == "needs_review"


def test_pension_within_loose_tolerance_passes():
    # 기준소득월액 차이로 5% 다른 경우는 정상으로 본다
    _, levels, _, _ = run(with_values(national_pension=133_120, deduction_total=365_450, net_pay=2_784_550))
    assert levels["RATE_NATIONAL_PENSION"] == "pass"


def test_pension_far_off_warns():
    _, levels, _, _ = run(with_values(national_pension=90_000, deduction_total=322_330, net_pay=2_827_670))
    assert levels["RATE_NATIONAL_PENSION"] == "warning"


def test_unknown_year_skips_rate_checks():
    result, levels, _, _ = run(year=2031)
    assert levels["RATE_TABLE"] == "skipped"
    assert not any(code.startswith("RATE_") and code != "RATE_TABLE" for code in levels)
    assert result["summary"] == "pass"


def test_rates_come_from_config_not_code():
    # 요율표를 바꾸면 결과가 바뀌어야 한다 (하드코딩이 아님)
    from ocr.validate import load_rates
    rates = load_rates()
    changed = copy.deepcopy(rates)
    changed["years"]["2026"]["local_income_tax"]["ratio_of_income_tax"] = 0.2
    result = validate(make_fields(GOOD), [], 2026, rates=changed)
    assert {c["code"]: c["level"] for c in result["checks"]}["RATE_LOCAL_INCOME_TAX"] == "warning"


# ---- 입사일 ----

def test_join_date_after_pay_period_warns():
    result = validate(make_fields(GOOD), [], 2026, join_date="2026-05-02", pay_period="2026-03")
    assert {c["code"]: c["level"] for c in result["checks"]}["JOIN_DATE_AFTER_PERIOD"] == "warning"


def test_join_date_in_pay_period_is_fine():
    result = validate(make_fields(GOOD), [], 2026, join_date="2026-03-18", pay_period="2026-03")
    assert "JOIN_DATE_AFTER_PERIOD" not in {c["code"] for c in result["checks"]}


# ---- 인식 품질 ----

def test_low_confidence_field_warns():
    result, levels, _, _ = run(status={"income_tax": "needs_review"})
    assert levels["FIELD_NEEDS_REVIEW"] == "warning"
    assert result["summary"] == "warning"


# ---- Pydantic 출력 구조 ----

def build_output(values=GOOD):
    fields = make_fields(values)
    validation = validate(fields, [], 2026)
    return {"pay_period": "2026-03",
            "join_date": {"value": "2025-07-01", "source_text": "2025-07-01", "confidence": 0.99,
                          "status": "needs_review"},
            "hints": [], "fields": fields, "unmapped_items": [],
            "unmatched_amounts": [], "validation": validation}


def test_schema_accepts_valid_output():
    PayslipResult.model_validate(build_output())


@pytest.mark.parametrize("name,break_it", [
    ("필드 누락", lambda o: o["fields"].pop("bonus")),
    ("모르는 필드", lambda o: o["fields"].__setitem__("tip", o["fields"]["bonus"])),
    ("음수 금액", lambda o: o["fields"]["base_salary"].update(value=-1)),
    ("10억 초과", lambda o: o["fields"]["base_salary"].update(value=2_000_000_000)),
    ("금액이 문자열", lambda o: o["fields"]["base_salary"].update(value="2,800,000")),
    ("지급/공제 구분 틀림", lambda o: o["fields"]["income_tax"].update(category="payment")),
    ("missing인데 값 있음", lambda o: o["fields"]["base_salary"].update(status="missing")),
    ("정해지지 않은 상태 값", lambda o: o["fields"]["base_salary"].update(status="maybe")),
    ("신뢰도 1 초과", lambda o: o["fields"]["base_salary"].update(confidence=1.5)),
    ("귀속월 형식", lambda o: o.update(pay_period="2026-13")),
    ("입사일 형식", lambda o: o["join_date"].update(value="2025/07/01")),
    ("입사일 찾았는데 ok", lambda o: o["join_date"].update(status="ok")),
    ("모르는 힌트 코드", lambda o: o.update(hints=[{"code": "SOMETHING", "message": "x"}])),
    ("summary가 결과와 다름", lambda o: o["validation"].update(summary="warning")),
], ids=lambda x: x if isinstance(x, str) else "")
def test_schema_rejects_broken_output(name, break_it):
    output = build_output()
    break_it(output)
    with pytest.raises(ValidationError):
        PayslipResult.model_validate(output)
