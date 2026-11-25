# MVP 모델 코드 (draw.io / Eraser 전용)

MVP의 사용자 흐름, ERD, 기술 구성도를 **텍스트 코드**로 보관한 폴더입니다. 코드를 각 도구에 붙여넣으면 다이어그램이 만들어지고, 팀원이 도구에서 바로 수정할 수 있습니다. 라벨은 한글입니다.

| 모델 | draw.io용 (Mermaid) | Eraser용 (Eraser 코드) |
|---|---|---|
| 사용자 흐름 | [`drawio/user-flow.mmd`](drawio/user-flow.mmd) | [`eraser/user-flow.eraser`](eraser/user-flow.eraser) |
| ERD (7개 테이블) | [`drawio/erd.mmd`](drawio/erd.mmd) | [`eraser/erd.eraser`](eraser/erd.eraser) |
| 기술 구성도 | [`drawio/architecture.mmd`](drawio/architecture.mmd) | [`eraser/architecture.eraser`](eraser/architecture.eraser) |

완성된 draw.io 파일은 한 단계 위의 [`docs/`](../)에 있습니다: `user-flow.drawio`, `erd-mvp.drawio`, `architecture.drawio`.

## draw.io에 넣는 법

1. [app.diagrams.net](https://app.diagrams.net)을 열고 빈 다이어그램을 만든다.
2. 메뉴 **Arrange → Insert → Advanced → Mermaid** 를 연다.
3. `.mmd` 파일의 내용을 붙여넣고 **Insert** 를 누른다.

도형과 연결선이 편집 가능한 형태로 들어갑니다. Mermaid로 변환되는 것까지는 확인했지만, 도형 배치가 겹치면 직접 정리해야 합니다.

## Eraser에 넣는 법

1. [eraser.io](https://www.eraser.io)에서 새 파일을 만들고 다이어그램을 추가한다.
2. 다이어그램 종류를 고른다. 사용자 흐름은 **Flowchart**, ERD는 **Entity Relationship**, 기술 구성도는 **Cloud Architecture** 이다.
3. 코드 창에 `.eraser` 파일의 내용을 붙여넣는다.

**주의: Eraser 코드는 실제 Eraser에서 렌더링해 검증하지 못했습니다.** 문법이 맞지 않거나 아이콘 이름이 없으면 오류가 나거나 아이콘이 비어 보일 수 있습니다. 그 경우 오류 메시지를 보고 해당 줄만 고치면 됩니다.

## 색 의미

- **기술 구성도:** 초록 = 코드에서 확인됨, 파랑 = 팀원 문서에서 제안됨, 노랑 = 아직 미정, 회색 = MVP 범위 밖
- **사용자 흐름:** 노랑 = 분기, 파랑 = 로그인 필요, 빨강 = 요청 실패, 보라 = 빈 결과, 초록 = 정상 결과, 회색 = 후속 확장

## 이름 규칙

ERD의 테이블과 컬럼 이름은 `prisma/schema.prisma`와 같게 **영어**로 두고, 설명은 한글(주석 또는 컬럼 설명)로 적었습니다. 스키마가 바뀌면 `erd.mmd`와 `erd.eraser`도 같이 고쳐야 합니다.

## 내용이 아직 확정이 아닌 부분

화면 이름과 순서는 팀 합의 전 초안입니다. 기술 구성도에서 초록(코드 확인)은 PostgreSQL과 Prisma뿐이고, 나머지는 제안 또는 미정입니다.
