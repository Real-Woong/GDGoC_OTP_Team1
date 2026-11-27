"""OCR 최종 출력 형식 (Pydantic).

여기서 잡는 것은 '출력 구조'다: 키가 다 있는지, 금액이 정수이고 범위 안인지, 상태 값이 정해진 값인지,
지급/공제 구분이 맞는지. 이 검사에 실패하면 우리 코드의 버그이므로 예외를 낸다(API에서는 500).

"기본급이 명세서에 없음" 같은 내용 문제는 여기서 막지 않는다. 그건 validate.py의 warning이다.
"""

from __future__ import annotations

from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

# 표준 필드와 지급/공제 구분. synonyms.json과 같아야 한다(테스트에서 확인).
STANDARD_FIELDS: dict[str, Literal["payment", "deduction", "total"]] = {
    "base_salary": "payment",
    "meal_allowance": "payment",
    "car_allowance": "payment",
    "overtime_pay": "payment",
    "bonus": "payment",
    "payment_total": "total",
    "national_pension": "deduction",
    "health_insurance": "deduction",
    "long_term_care": "deduction",
    "employment_insurance": "deduction",
    "income_tax": "deduction",
    "local_income_tax": "deduction",
    "deduction_total": "total",
    "net_pay": "total",
}

MAX_AMOUNT = 1_000_000_000  # 월 10억 원. 이보다 크면 OCR이 자릿수를 잘못 읽은 것

Amount = Annotated[int, Field(ge=0, le=MAX_AMOUNT)]
SignedAmount = Annotated[int, Field(ge=-MAX_AMOUNT, le=MAX_AMOUNT)]  # 정산·환급은 음수일 수 있음
Confidence = Annotated[float, Field(ge=0.0, le=1.0)]


class Strict(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)


class Source(Strict):
    source_label: str = Field(min_length=1)
    value: SignedAmount
    confidence: Confidence
    match_method: Literal["exact", "normalized", "fuzzy"]
    matched_synonym: str
    qualifiers: list[str]


class FieldValue(Strict):
    value: Amount | None
    category: Literal["payment", "deduction", "total"]
    source_label: str | None
    confidence: Confidence | None
    match_method: Literal["exact", "normalized", "fuzzy"] | None
    status: Literal["ok", "needs_review", "missing"]
    sources: list[Source]

    @model_validator(mode="after")
    def missing_means_empty(self):
        # missing ⇔ 값 없음. 둘이 어긋나면 코드 버그다.
        if (self.status == "missing") != (self.value is None):
            raise ValueError("status가 missing이면 value가 null이어야 하고, 그 반대도 마찬가지예요.")
        if self.value is not None and not self.sources:
            raise ValueError("값이 있는 필드는 원본(sources)이 있어야 해요.")
        return self


class UnmappedItem(Strict):
    source_label: str = Field(min_length=1)
    value: SignedAmount
    confidence: Confidence
    reason: Literal["not_in_dictionary", "blocked_word", "ambiguous", "empty"]
    qualifiers: list[str]
    # 합계 검증에서 지급/공제 배치가 하나로 정해지면 채운다. 정해지지 않으면 null
    category: Literal["payment", "deduction"] | None = None


class UnmatchedAmount(Strict):
    text: str
    amount: SignedAmount
    score: Confidence


class Check(Strict):
    code: str
    level: Literal["pass", "warning", "fail", "skipped"]
    field: str | None = None
    expected: int | None = None
    actual: int | None = None
    message: str | None = None


class Validation(Strict):
    summary: Literal["pass", "warning", "fail"]
    rate_year: int | None
    checks: list[Check]

    @model_validator(mode="after")
    def summary_is_worst(self):
        levels = {c.level for c in self.checks}
        worst = "fail" if "fail" in levels else "warning" if "warning" in levels else "pass"
        if self.summary != worst:
            raise ValueError(f"summary({self.summary})가 가장 나쁜 검사 결과({worst})와 달라요.")
        return self


class JoinDate(Strict):
    value: str | None = Field(default=None, pattern=r"^(19|20)\d{2}-\d{2}-\d{2}$")
    source_text: str | None
    confidence: Confidence | None
    # 찾았어도 사용자 확인이 필요하므로 ok는 없다
    status: Literal["needs_review", "missing"]

    @model_validator(mode="after")
    def missing_means_empty(self):
        if (self.status == "missing") != (self.value is None):
            raise ValueError("입사일 status와 value가 맞지 않아요.")
        return self


class Hint(Strict):
    code: Literal["PRORATED_PAY", "FIRST_MONTH", "JOINED_THIS_YEAR"]
    message: str


class PayslipResult(Strict):
    pay_period: str | None = Field(default=None, pattern=r"^20\d{2}-(0[1-9]|1[0-2])$")
    join_date: JoinDate
    hints: list[Hint]
    fields: dict[str, FieldValue]
    unmapped_items: list[UnmappedItem]
    unmatched_amounts: list[UnmatchedAmount]
    validation: Validation

    @field_validator("fields")
    @classmethod
    def all_standard_fields(cls, v: dict[str, FieldValue]):
        if set(v) != set(STANDARD_FIELDS):
            raise ValueError(f"표준 필드가 맞지 않아요. 빠짐 {set(STANDARD_FIELDS) - set(v)}, "
                             f"추가 {set(v) - set(STANDARD_FIELDS)}")
        for name, f in v.items():
            if f.category != STANDARD_FIELDS[name]:
                raise ValueError(f"{name}의 지급/공제 구분이 {f.category}로 되어 있어요. "
                                 f"{STANDARD_FIELDS[name]}여야 해요.")
        return v
