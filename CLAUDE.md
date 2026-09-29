# CLAUDE.md

Claude Code 심화 과정 실습 저장소. 모든 코드 · 데이터 · 로그는 더미 에듀테크 도메인(문항 은행 · 과제 배포 · 성적 집계)이다.

- 답변과 문서는 한국어로 쓴다.
- `legacy/` 는 분석 · 이관 대상이며, 허락 없이 수정하지 않는다.
- 루트 `.env` 를 만들지 않는다. README "이 저장소에 넣지 않은 것" 표의 파일은 요청 없이 만들지 않는다.

## 1. 빌드 · 테스트 명령

```bash
# modern/api — 테스트 프로필은 H2(MariaDB 모드)라 DB 없이 돌아간다
cd modern/api && ./gradlew test
cd modern/api && ./gradlew test --tests 'com.example.item.ItemControllerTest'                     # 클래스 하나
cd modern/api && ./gradlew test --tests 'com.example.item.ItemControllerTest.getItemReturnsJson'  # 메서드 하나
cd modern/api && ./gradlew bootRun     # 8080. 먼저 루트에서 docker compose --profile modern up -d

# modern/web
cd modern/web && npm ci                                          # npm install 금지 (lock 파일 보존)
cd modern/web && npm run dev                                     # 5173
cd modern/web && npm run lint && npm run typecheck && npm test   # 커밋 전 필수
cd modern/web && npx vitest run src/components/ItemTable.test.tsx  # 파일 하나
cd modern/web && npm run build
```

- `modern/api` 를 고친 뒤에는 `./gradlew test` 를, `modern/web` 을 고친 뒤에는 `lint` · `typecheck` · `test` 를 실행하고 통과 · 실패 수를 답변에 적는다. 실행하지 않았으면 "실행하지 않음"이라고 적는다.
- 하나라도 실패하면 답변에 "완료"라고 쓰지 않는다. 답변에는 실패한 테스트 이름만 남기고 전체 로그를 붙이지 않는다.

## 2. 코딩 컨벤션

### modern/api
- 패키지는 도메인 단위(`com.example.item`, `com.example.assignment`)로 나누고, 호출 순서는 Controller → Service → Repository → 엔티티다.
- 서비스가 다른 도메인의 데이터를 쓸 때는 그 도메인의 서비스를 주입받는다.
- 응답은 `*Response` record DTO로 반환한다. 컨트롤러 메서드의 반환 타입에 `@Entity` 클래스가 나오지 않는다.
- 예외 → HTTP 변환은 `common/GlobalExceptionHandler` 에서만 한다: `NotFoundException`→404, 검증 · 타입 불일치 · `IllegalArgumentException`→400, `IllegalStateException`→409, 그 밖→500. 응답 본문은 `ErrorResponse`.
- `catch` 블록은 로그를 남기고 다시 던지거나 도메인 예외로 바꿔 던진다.
- 로그는 `org.slf4j.Logger` 로만 남긴다. 정상 흐름은 `INFO` 이하, 복구 가능한 실패는 `WARN`, 요청을 실패시키는 예외는 `ERROR`.
- 로그 인자에 학생 식별자(`STU-…`) · 이메일 · 토큰 값을 넣지 않는다.
- 현재 시각은 주입받은 `Clock`(`common/ClockConfig`)으로 구한다. `LocalDateTime.now()` 처럼 인자 없는 `now()` 는 쓰지 않는다.
- 공개 문항만 노출한다: 조회 조건은 `status = 'A'`(`ItemStatus`)다.
- 클래스 `PascalCase`, 메서드 · 필드 `camelCase`, 상수 `UPPER_SNAKE_CASE`. 들여쓰기 4칸, 한 줄 120자 이내, 와일드카드 import 없음.
- 숫자 리터럴 규칙값(예: 난이도 상한 5)은 `static final` 상수로 둔다.

### modern/api 테스트
- 새 엔드포인트 · 새 public 서비스 메서드마다 테스트를 1개 이상 추가한다.
- 컨트롤러는 `@WebMvcTest` + `@MockBean` 서비스, 서비스는 Mockito 단위 테스트, 쿼리는 `@DataJpaTest` 로 쓴다. 모두 `@ActiveProfiles("test")`.
- 테스트 메서드 이름은 camelCase, `@DisplayName` 에 한국어 설명. 문항 픽스처는 `ItemFixtures` 를 쓴다.

### modern/web
- 모든 API 호출은 `src/api/client.ts` 의 `getJson()` 을 거친다. 엔드포인트 함수는 `src/api/*.ts`, 응답 타입은 `src/api/types.ts` 에 두고 백엔드 JSON 필드명을 그대로 쓴다.
- 조회 훅은 `src/hooks/` 에 `use<이름>.ts` 로 만들고 `useApiQuery` 위에 올린다(`QueryState<T>`: `idle|loading|success|error`).
- 컴포넌트는 함수 컴포넌트로만 쓴다. `React.FC` 타입과 클래스 컴포넌트는 쓰지 않는다.
- 이벤트 핸들러 prop 이름은 `on<동작>`, 구현 함수 이름은 `handle<동작>`.
- `eslint-disable` 주석에는 같은 줄에 사유를 적는다.

### modern/web 테스트
- 새 컴포넌트 · 새 훅마다 `<이름>.test.ts(x)` 를 같은 폴더에 둔다.
- fetch 목킹은 `src/test/mockFetch.ts`, 데이터는 `src/test/fixtures.ts` 를 쓴다. 테스트에서 실제 `localhost:8080` 을 호출하지 않는다.
- 요소 조회는 `getByRole` · `getByLabelText` 를 먼저 쓴다. `getByTestId` 를 쓰면 이유를 주석으로 적는다.

## 3. 금지 사항

### modern/api
- 컨트롤러 클래스에 Repository 필드 · 생성자 인자, SQL 문자열이 없어야 한다.
- 빈 `catch` 블록, `System.out.println`, `System.err.println`, `e.printStackTrace()` 금지.
- 컨트롤러 · 서비스에서 try/catch 로 HTTP 상태 코드를 만들지 않는다.
- `@Transactional` 메서드 안에서 외부 HTTP 호출이나 요청 데이터 크기에 비례하는 루프를 돌리지 않는다(Hikari 풀 최대 5, 타임아웃 3초).
- `application.yml` 의 DB 접속 · Hikari 풀 설정을 바꾸지 않는다.
- 운영 비밀값(계정 · 토큰 · 키)을 코드 · 설정에 리터럴로 넣지 않는다. 실습용 더미 값(`app-pass`)은 예외.
- `prod-db` 등 운영 DB 호스트에 접속하는 명령 · 설정을 만들지 않는다.
- CORS(`config/WebConfig`)는 `/api/**` 에 `http://localhost:5173` 의 GET 만 허용한다. 이 범위를 넓히지 않는다.

### modern/web
- `any`, `as unknown as`, `@ts-ignore` 금지. 타입을 못 맞추면 이유를 답변에 적고 묻는다.
- `src/components/` 안에서 `fetch` 를 직접 호출하지 않는다.
- `dangerouslySetInnerHTML` 금지.
- 커밋에 `console.log` 를 남기지 않는다.
- `.env*` 파일을 만들지 않고, 비밀값 · 토큰을 프론트 코드에 넣지 않는다.
- `package-lock.json` 을 손으로 고치거나 지우지 않는다.

### 공통
- 요청에 없는 파일은 포맷팅 · import 정리 목적으로도 고치지 않는다. 고쳤다면 파일과 이유를 답변에 적는다.
- `characterization/__snapshots__/` 를 손으로 고치지 않는다. 이관 후 비교가 실패하면 테스트 · 스냅샷이 아니라 이관 코드를 고친다.

## 4. 팀 규칙

- 컨트롤러에서 Repository 를 직접 호출하지 않는다.
- 예외를 삼키지 않는다. 로그는 SLF4J 로만 남긴다.
- 의존성 추가(`build.gradle` 의 `dependencies`, `package.json`)와 DB 스키마 · 시드 변경(`db/**/init/`)은 먼저 묻는다. 물을 때 이유와 대안을 함께 적는다.

## 5. 아키텍처 안내

도메인 용어: 문항 `item` · 단원 `unit` · 난이도 `level`(1~5) · 태그 `tag` / 학급 `class` · 과제 `assignment` · 배포 `distribution` · 제출 `submission` / 학생 식별자 `STU-<숫자>`.

| 모듈 | 위치 | 스택 | 주소 | Compose 프로필 |
|---|---|---|---|---|
| 문항 은행 (레거시) | `legacy/item-bank-php/` | PHP 7.4 + MariaDB | 8081 | `php` |
| 과제 배포 (레거시) | `legacy/assignment-thymeleaf/` | Spring MVC + Thymeleaf + JDBC | 8082 | `thymeleaf` |
| 성적 집계 (레거시) | `legacy/grade-mssql/` | MS-SQL 저장 프로시저 + Java 호출부 | 8083 | `mssql` |
| 현행 API | `modern/api/` | Spring Boot 3.3 · Java 21 · Gradle | 8080 | `modern` (DB만) |
| 현행 화면 | `modern/web/` | React 18 · TS · Vite | 5173 | — |

- `php` · `thymeleaf` · `modern` 은 MariaDB(3306, DB `itembank`)를 공유한다. 스키마 · 시드는 `db/mariadb/init/`, MS-SQL 은 `db/mssql/init/`. 볼륨이 없어 `down` → `up -d` 하면 시드 상태로 돌아간다.
- docker compose 는 `up` · `down` · `build` · `logs` 모두 `--profile` 을 붙인다. 조회용 계정: MariaDB `readonly`/`readonly-pass`, MS-SQL `readonly`/`Readonly-pass1`.

```
modern/api/src/main/java/com/example/
├── item/         문항 · 단원 · 태그 (Item*, Unit*, Tag*)
├── assignment/   배포 · 재배포 · 학급 리포트 (Distribution*, Report*)
├── common/       GlobalExceptionHandler, ErrorResponse, NotFoundException, ClockConfig
└── config/       WebConfig (CORS)
modern/web/src/
├── api/          client.ts(getJson) · items.ts(엔드포인트 함수) · types.ts
├── hooks/        useApiQuery 와 그 위의 조회 훅
├── components/   화면 컴포넌트와 테스트
└── test/         mockFetch.ts · fixtures.ts
```

- 기존 엔드포인트: `GET /api/units`, `GET /api/units/{code}/items`, `GET /api/items/{id}`, `GET /api/distributions/{id}`, `POST /api/distributions/{id}/redistribute`, `GET /api/classes/{id}/report`. 새 엔드포인트는 `/api/<도메인 복수형>` 아래에 둔다.
- web 데이터 흐름: 컴포넌트 → `hooks/use*` → `api/*.ts` → `getJson()` → `modern/api`. 기본 대상은 `VITE_API_BASE ?? http://localhost:8080`, `VITE_API_BASE=""` 이면 Vite 프록시(`/api` → 8080).
- 레거시 규칙을 옮길 때는 근거를 `파일:줄번호` 로 답변에 적는다.
- `characterization/`: 레거시 응답을 `{status, rows, count, message}` 로 정규화한 스냅샷 비교. 규칙은 `characterization/README.md`.
- 참고: `templates/`(유형별 CLAUDE.md · 체크리스트), `mcp-skeleton/`(MCP SDK v2, zod 4 — v1 예제 사용 금지), `specs/`(신규 스펙 · 시작 골격), `scripts/hook-node.sh`(Hook 실행기, node 없으면 exit 2).
