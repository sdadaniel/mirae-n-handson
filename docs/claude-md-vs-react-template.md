# CLAUDE.md vs templates/CLAUDE.react.md 비교

2026-09-29 기준. 원본 문서: https://claude.ai/code/artifact/3b0e5b14-b06e-4670-9c36-85ad7c276a1c

현재 `CLAUDE.md` 는 `modern/web` 의 **실제 코드** 구조를 따르고, `templates/CLAUDE.react.md` 는 코드에 없는 구조(`apiClient` · `pages/` · `store/` · msw · 컴포넌트별 폴더)를 가정한다. 두 파일이 다르게 말하는 곳은 대부분 이 차이에서 나온다.

- 금지 사항(`any` · `@ts-ignore` · `dangerouslySetInnerHTML` · `console.log` · lock 파일)과 결과 보고 규칙은 CLAUDE.md 에도 들어 있다.
- CLAUDE.md 에 없는 것은 주로 컴포넌트 설계 · 상태 관리 규칙이다(props 타입, 파생 값, `useEffect` 동기화 금지, 로딩 · 오류 · 빈 결과 표시 등).
- 설치 명령이 다르다: 템플릿은 `npm install`, CLAUDE.md 는 `npm ci`.

비교 대상: 작업 중 디스크에서 고쳐진 **현재** `CLAUDE.md`(123줄, 커밋 전), `templates/CLAUDE.react.md`. 확인용으로 `modern/web` 코드도 봤다.

## 1. 템플릿에는 있는데 CLAUDE.md 에는 없는 항목

템플릿 규칙 15개가 CLAUDE.md 에 없다. 절반 이상이 컴포넌트 · 상태 관리 규칙이다. 마지막 열은 현재 `modern/web` 코드가 그 규칙을 이미 지키는지다.

| 구분 | 템플릿 항목 | 현재 코드는 |
| --- | --- | --- |
| 컴포넌트 | `defaultProps` 금지 (CLAUDE.md 는 `React.FC` · 클래스만 금지) | 지킴 |
| 컴포넌트 | props 타입은 파일 안에 `type <이름>Props` 로 선언하고 export | 확인 안 함 |
| 컴포넌트 | 한 파일에 컴포넌트 하나, 200줄이 넘으면 쪼갠다 | 지킴 (가장 큰 파일 1.8K) |
| 컴포넌트 | 컴포넌트는 props 로만 데이터를 받는다 | 확인 안 함 |
| 상태 | 화면 로컬 상태는 `useState` · `useReducer`, 두 화면 이상이 공유하는 것만 Context 로 | 해당 없음 (Context 없음) |
| 상태 | 파생 값은 상태에 저장하지 않고 렌더 시 계산 | 확인 안 함 |
| 상태 | `useEffect` 로 상태 A → B 를 동기화하는 패턴 금지 | 확인 안 함 |
| API | 컴포넌트에서 HTTP 상태 코드를 직접 비교하지 않는다 | 지킴 (`useApiQuery` 의 `toErrorMessage` 에서만 비교) |
| API | 화면용 이름이 필요하면 훅 안에서 매핑 | 해당 없음 |
| API | 로딩 · 오류 · 빈 결과 세 상태를 모두 표시, 빈 결과는 백엔드 `message` 우선 | 확인 안 함 |
| 테스트 | 테스트 내용: 렌더 1개 + 사용자 상호작용 1개 이상 | 일부. `AsyncSection` · `ItemDetail` · `StatusBadge` · `UnitList` 는 테스트 파일이 없음 |
| 테스트 | 컴포넌트 스냅샷 테스트를 만들지 않는다 | 지킴 |
| 이름 | 컴포넌트 · 타입 PascalCase, 파일명 = 컴포넌트명 | 지킴 |
| 금지 | UI · 상태 관리 라이브러리 도입은 팀 합의 사항 | 해당 없음 (CLAUDE.md 는 `package.json` 변경 시 묻기만 있음) |
| 완료 기준 | 5항목 체크리스트 | 해당 없음. 내용은 대부분 CLAUDE.md 1장 · 3장에 흩어져 있고, 체크리스트 형식만 없음 |

"확인 안 함"은 grep 한 번으로 판단할 수 없어 코드를 읽어 봐야 하는 항목이다.

## 2. CLAUDE.md 에만 있는 항목

CLAUDE.md 는 저장소 전체를 다루므로 `modern/api` · 인프라 · characterization · 기타 폴더 내용은 전부 CLAUDE.md 에만 있다. 아래는 프론트엔드에 직접 관련된 항목만 추렸다.

| 구분 | CLAUDE.md 항목 |
| --- | --- |
| 명령 | 테스트 파일 하나만: `npx vitest run src/components/ItemTable.test.tsx` |
| 결과 보고 | 검사를 실행하지 않았으면 "실행하지 않음"이라고 적는다 |
| API | `src/api/items.ts` 같은 엔드포인트 함수 계층 (훅 → `api/*.ts` → `getJson`) |
| API | `VITE_API_BASE ?? http://localhost:8080`, `VITE_API_BASE=""` 이면 Vite 프록시(`/api` → 8080)를 탄다 |
| API | 백엔드 CORS 는 5173 의 GET 만 허용, 범위를 넓히지 않는다 |
| 상태 | 조회 훅은 `useApiQuery` 위에 만든다, `QueryState<T>` (`idle` · `loading` · `success` · `error`) |
| 테스트 | 새 **훅**도 테스트 파일을 같은 폴더에 둔다 (템플릿은 컴포넌트만) |
| 테스트 | 목 데이터는 `src/test/fixtures.ts` |
| 금지 | `characterization/__snapshots__/` 를 손으로 고치지 않는다, 이관 비교가 실패하면 이관 코드를 고친다 |
| 금지 | 의존성 변경을 물을 때 이유와 대안을 함께 적는다 |
| 기타 | 답변과 문서는 한국어로 쓴다, 참가자가 만들 파일을 미리 만들지 않는다 |
| 도메인 | 학급 · 과제 · 배포 · 제출 · `STU-<숫자>` 까지 포함한 용어집 (템플릿은 문항 용어 4개만) |

## 3. 두 파일이 서로 다르게 말하는 항목

불일치 11건 중 8건은 템플릿이 현재 코드와 다르고, CLAUDE.md 는 코드와 같다. 나머지 3건은 규칙의 강도 · 범위 차이다.

| 항목 | 템플릿 | CLAUDE.md | 현재 코드 | 코드와 맞는 쪽 |
| --- | --- | --- | --- | --- |
| 설치 명령 | `npm install` ("lock 을 바꾸지 않는다"고 적음) | `npm ci`, `npm install` 금지 | README · 커밋 `53eb84c` 모두 `npm ci` | CLAUDE.md. 템플릿은 명령과 설명이 서로 모순(`npm install` 은 lock 을 바꿀 수 있음) |
| 공통 API 함수 | `src/api/client.ts` 의 `apiClient` | `getJson()` | `getJson` 만 있고 `apiClient` 없음 | CLAUDE.md |
| 데이터 훅 위치 | `src/api/use*.ts` (`useItems` 등) | `src/hooks/use<이름>.ts` | `src/hooks/useUnits.ts` · `useItem.ts` · `useUnitItems.ts`, `useItems` 없음 | CLAUDE.md |
| 컴포넌트 파일 구조 | `components/<이름>/` 폴더에 `tsx` · `test.tsx` · `index.ts` (+ `module.css`) | `components/` 에 컴포넌트와 테스트, 테스트는 같은 폴더 | 평평한 `components/`, `index.ts` · CSS 모듈 없음(`styles.css` 하나) | CLAUDE.md |
| 화면 · 공유 상태 폴더 | `src/pages/`, `src/store/` (Context) | 언급 없음 | 둘 다 없음. 화면은 `App.tsx` → `ItemBrowser` | CLAUDE.md (없는 폴더를 적지 않음) |
| 데이터 흐름 | 페이지 → 데이터 훅 → `apiClient` → `modern/api` | 컴포넌트 → `hooks/use*` → `api/*.ts` → `getJson()` → `modern/api` | CLAUDE.md 와 같음 | CLAUDE.md |
| 테스트 네트워크 목킹 | `src/test/msw/` 핸들러 | `src/test/mockFetch.ts` | msw 없음(`package.json` 에도 없음), `mockFetch` 가 `vi.spyOn(fetch)` | CLAUDE.md |
| 포맷터 | ESLint · Prettier 설정을 따른다 | 언급 없음 | ESLint 만 있음, Prettier 설정 · 의존성 없음 | 템플릿이 없는 도구를 가리킴 |
| `getByTestId` | "마지막 수단" | 쓰면 이유를 주석으로 적는다 | 쓴 곳 없음 | 강도 차이 (CLAUDE.md 가 더 구체적) |
| `.env*` | 읽거나 만들지 않는다 | 만들지 않는다 (읽기는 막지 않음) | — | 범위 차이 (템플릿이 더 엄격) |
| 테스트 의무 범위 | 컴포넌트마다 테스트 (기존 컴포넌트 포함) + 상호작용 1개 | **새** 컴포넌트 · 훅마다 테스트 파일, 내용 요건 없음 | 기존 4개 컴포넌트에 테스트 없음 | 범위 차이. 템플릿을 그대로 적용하면 현재 코드가 위반 |

같은 내용: 두 파일 모두 `npm run lint && npm run typecheck && npm test`(커밋 전 필수), `npm run build`, 실패 시 "완료" 금지 · 실패 테스트 이름만 보고, `getJson`/`apiClient` 한 곳으로 모든 요청, `types.ts` 에 백엔드 필드명 그대로, 컴포넌트 안 `fetch` 금지, `any` · `as unknown as` · `@ts-ignore` · `dangerouslySetInnerHTML` · `console.log` 금지, `package-lock.json` 손대지 않기, 의존성은 먼저 묻기, `on<동작>` / `handle<동작>`, `eslint-disable` 사유, `getByRole` 우선, 실제 8080 호출 금지, 요청 밖 파일 정리 금지, `legacy/` 수정 금지를 적고 있다.

## 다음 단계 후보

- [ ] 표 1 의 컴포넌트 · 상태 규칙 중 CLAUDE.md 에 옮길 것 고르기
- [ ] 템플릿을 현재 코드에 맞출지 정하기 (`apiClient` → `getJson`, `src/hooks/`, `mockFetch`, `npm ci`, `pages/` · `store/` · Prettier 삭제)
- [ ] 기존 4개 컴포넌트(`AsyncSection` · `ItemDetail` · `StatusBadge` · `UnitList`)의 테스트 부재를 그대로 둘지 정하기
