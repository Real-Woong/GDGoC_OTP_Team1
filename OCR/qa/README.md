# QA

> QA 담당이 작성합니다.

- 테스트 케이스와 평가셋 구성, 목표 수치를 여기에 정리합니다.
- **실제 급여명세서는 저장소에 올리지 않습니다.** 가짜 샘플이나, 동의를 받고 개인정보를 가린 파일만 올립니다. 실제 파일은 각자 컴퓨터의 `real_samples/` 폴더에 두면 Git에서 자동으로 제외됩니다.
- OCR 평가셋의 정답 파일은 OCR 출력과 같은 형식(`fields`의 `value`, `source_label`)으로 만들면 자동 비교할 수 있습니다. 예시는 [`ocr-server/samples/output/`](../ocr-server/samples/output/)의 `caseNN.json`을 참고하세요.
