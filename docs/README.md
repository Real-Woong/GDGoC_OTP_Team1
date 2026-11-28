# docs 폴더 안내

```
docs/
├─ MEETING_MATERIAL/            회의·발표에 쓰는 자료
│  └─ MVP/
│     ├─ README.md              draw.io / Eraser에 붙여넣는 방법, 색 의미
│     ├─ FOR_Draw_Io/           draw.io용: 완성 .drawio 3개 + Mermaid .mmd 3개
│     ├─ FOR_Eraser_Io/         Eraser용: .eraser 코드 3개
│     └─ SUMMARY/               회의용 한 장 요약 (결정 체크리스트)
├─ DATABASE/                    데이터 구조 설명
│  └─ MVP/erd.md                데모용 7개 테이블 설명 (기준: prisma/schema.prisma)
├─ SAMPLE_PAYSLIP/              테스트용 가짜 급여명세서 PDF 4건 + 정답 값(expected_values.json)
└─ ARCHIVE/                     현재 설계에 쓰지 않는 보관본
   ├─ FULL_20_TABLES/           축소 전 20개 테이블 스키마·ERD·시드
   └─ SIMULATION_FLOW/          시뮬레이션 중심이던 이전 사용자 흐름도
```

각 모델(사용자 흐름, ERD, 기술 구성도)은 `FOR_Draw_Io`와 `FOR_Eraser_Io`에 같은 내용이 형식만 달리 들어 있습니다. 내용을 바꿀 때는 두 폴더와 `.drawio` 파일을 함께 고쳐야 하고, ERD의 기준은 `prisma/schema.prisma`입니다.
