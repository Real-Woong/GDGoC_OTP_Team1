-- =====================================================================
-- V2: 데모용 기본 데이터 (prisma/seed.ts와 같은 내용)
--
-- ⚠️ 모든 값은 더미다. 공식 출처(국세청·법령)로 검증하기 전에는 실제 안내에 쓰면 안 된다.
--    그래서 규칙 상태는 DRAFT, 기한 규칙은 TODO로 둔다.
-- =====================================================================

-- 규칙 버전 1개. parameters 안의 null은 "아직 검증된 값이 없음"을 뜻한다.
INSERT INTO tax_rule_version (created_at, updated_at, tax_year, version, status, parameters, source_url)
VALUES (now(), now(), 2026, '2026-demo-v1', 'DRAFT',
        '{
          "schemaVersion": "demo-v1",
          "smeEmployeeReduction": null,
          "validation": { "localIncomeTaxRelativeRate": null, "insuranceRates": null }
        }'::jsonb,
        NULL);

-- 할 일 템플릿 2개
INSERT INTO task_template (created_at, updated_at, key, title, description, due_rule)
VALUES (now(), now(), 'sme-reduction-application',
        '중소기업 취업자 소득세 감면 신청서 제출',
        '더미: 대상 요건과 신청 기한은 공식 출처로 검증 필요',
        'TODO_END_OF_MONTH_AFTER_JOIN'),
       (now(), now(), 'dependent-registration',
        '부양가족 정보 회사에 제출',
        '더미: 요건과 중복공제 주의사항은 공식 출처로 검증 필요',
        'TODO');
