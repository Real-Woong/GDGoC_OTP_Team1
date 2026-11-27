"""개발용 샘플로 전체 파이프라인을 돌리는 통합 테스트. 실제 OCR을 하므로 느리다(1장 약 10초).

실행: .venv/bin/python -m pytest -m slow
"""

import json
from pathlib import Path

import pytest

from ocr.pipeline import run
from ocr.prepare import PrepareError

SAMPLES = Path(__file__).parent.parent / "samples" / "output"
CASES = [json.loads(p.read_text(encoding="utf-8")) for p in sorted(SAMPLES.glob("case0[1-5].json"))]

pytestmark = pytest.mark.slow


@pytest.fixture(scope="module", params=CASES, ids=[c["case_id"] for c in CASES])
def case(request):
    answer = request.param
    result = run((SAMPLES / answer["file"]).read_bytes(), pdf_password=answer.get("pdf_password"))
    return answer, result


def test_standard_fields_match_answer(case):
    answer, result = case
    got = {k: f["value"] for k, f in result["fields"].items()}
    expected = {k: f["value"] for k, f in answer["fields"].items()}
    assert got == expected


def test_unmapped_items_match_answer(case):
    answer, result = case
    got = sorted((u["source_label"], u["value"], u["category"]) for u in result["unmapped_items"])
    expected = sorted((u["source_label"], u["value"], u["category"]) for u in answer["unmapped_items"])
    assert got == expected


def test_pay_period(case):
    answer, result = case
    assert result["pay_period"] == answer["pay_period"]


def test_join_date(case):
    answer, result = case
    assert result["join_date"]["value"] == answer["join_date"]
    assert result["join_date"]["status"] == "needs_review"  # 찾아도 사용자 확인 필요


def test_hints(case):
    answer, result = case
    codes = sorted(h["code"] for h in result["hints"])
    if answer["case_id"] == "case05":  # 2026-03-18 입사, 3월분 일할계산
        assert codes == ["FIRST_MONTH", "JOINED_THIS_YEAR", "PRORATED_PAY"]
    else:
        assert codes == []


def test_no_wrong_warnings(case):
    """샘플은 모두 계산이 맞으므로 합계·요율 경고가 나오면 안 된다. 인식 신뢰도 경고만 허용."""
    _, result = case
    warnings = [c["code"] for c in result["validation"]["checks"] if c["level"] == "warning"]
    assert set(warnings) <= {"FIELD_NEEDS_REVIEW"}, warnings


def test_personal_data_not_in_output(case):
    """주민번호·계좌번호가 결과 JSON 어디에도 남지 않아야 한다."""
    _, result = case
    text = json.dumps(result, ensure_ascii=False)
    assert "[주민등록번호]" not in text  # 마스킹된 박스는 짝이 되지 않으므로 출력에 나오지도 않음
    for secret in ["000101-3000000", "990215-2000000", "990101-1000000", "010505-4000000", "020820-3000000",
                   "000-0000-0000-00", "111-22-333333", "9999-00-123456", "321-654-987000", "555-555-555555"]:
        assert secret not in text


def test_receipt_is_not_a_payslip():
    with pytest.raises(PrepareError) as e:
        run((SAMPLES / "case06_not_payslip.png").read_bytes())
    assert (e.value.code, e.value.http_status) == ("NOT_A_PAYSLIP", 422)
