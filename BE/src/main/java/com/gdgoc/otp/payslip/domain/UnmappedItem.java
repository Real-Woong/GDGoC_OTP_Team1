package com.gdgoc.otp.payslip.domain;

/**
 * 표준 필드 14개 중 어디에도 맞추지 못한 명세서 항목.
 * Payslip.unmappedItems(JSON 배열)의 원소 하나다.
 *
 * <p>예: 회사 고유 수당 "명절 상여" 30만 원 → {@code new UnmappedItem("명절 상여", 300000)}</p>
 *
 * <p>record: 값만 담는 불변 클래스를 한 줄로 만드는 Java 문법.
 * 생성자, getter(label(), amount()), equals/hashCode가 자동으로 생긴다.</p>
 *
 * @param label  명세서에 적힌 원문 항목명
 * @param amount 금액(원). 금액을 읽지 못했으면 null
 */
public record UnmappedItem(String label, Integer amount) {
}
