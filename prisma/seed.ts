import { PrismaClient } from '@prisma/client';

const prisma = new PrismaClient();

// 구조 확인용 시드 예시 (축소 스키마 기준). 실행 환경/계산 로직은 이 설계의 범위 밖이다.
// TODO: 공식 출처로 검증 필요. 아래 모든 규칙 값·기한은 더미이며 DRAFT로 둔다.
// 실제 세법을 표현하지 않으며 사용자 안내에 사용하면 안 된다.
async function main() {
  await prisma.taxRuleVersion.upsert({
    where: { taxYear_version: { taxYear: 2026, version: '2026-demo-v1' } },
    update: {},
    create: {
      taxYear: 2026,
      version: '2026-demo-v1',
      status: 'DRAFT',
      sourceUrl: null,
      parameters: {
        schemaVersion: 'demo-v1',
        // TODO: 공식 출처로 검증 필요. 감면 요건(나이, 기업 규모, 기간, 한도)은 검증 후 채운다.
        smeEmployeeReduction: null,
        // TODO: 공식 출처로 검증 필요. 4대보험 요율, 지방소득세/소득세 비율 검증 기준.
        validation: { localIncomeTaxRelativeRate: null, insuranceRates: null },
      },
    },
  });

  // TODO: 공식 출처로 검증 필요 (제목, 대상, 기한 규칙 모두 더미).
  const templates = [
    {
      key: 'sme-reduction-application',
      title: '중소기업 취업자 소득세 감면 신청서 제출',
      description: '더미: 대상 요건과 신청 기한은 공식 출처로 검증 필요',
      dueRule: 'TODO_END_OF_MONTH_AFTER_JOIN',
    },
    {
      key: 'dependent-registration',
      title: '부양가족 정보 회사에 제출',
      description: '더미: 요건과 중복공제 주의사항은 공식 출처로 검증 필요',
      dueRule: 'TODO',
    },
  ];

  for (const t of templates) {
    await prisma.taskTemplate.upsert({
      where: { key: t.key },
      update: {},
      create: t,
    });
  }
}

main().catch(console.error).finally(() => prisma.$disconnect());
