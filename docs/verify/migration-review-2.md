# 머지 전 검증 (2차) — 레거시 엔드포인트 1개 이관

- 대상: `upstream/main...HEAD` (HEAD `57c7ecb`, 커밋 9건, 29개 파일, +3803 / -10)
- 요청 범위: 레거시 엔드포인트 1개를 동작을 바꾸지 않고 modern 으로 이관. (`search.php` → `GET /api/items/search`)
- 체크리스트: `templates/approval-checklist.md` v0
- 이전 검증: `docs/verify/migration-review-1.md` (반려). 이번 검증은 1차 이슈의 처리 결과와 수정 커밋 `57c7ecb` 를 함께 본다.

판정: 반려

| 기준 | 결과 | 근거 |
|---|---|---|
| 테스트 통과 | 충족 | `cd modern/api && ./gradlew test --rerun-tasks` → 51 통과 · 0 실패 · 0 오류 · 0 건너뜀. `cd characterization && npm test`(레거시 8081) → 28 통과 · 0 실패. `TARGET_BASE_URL=http://localhost:8080 npm test`(새 API) → 27 통과 · 0 실패 · 1 건너뜀(`example-units.test.js` 의 `legacyOnly` 가드, item-bank 와 무관). 8080 이 HEAD 코드라는 간접 근거: 삭제된 `GET /api/items/by-level?level=1` 이 400(경로가 `/api/items/{id}` 로 떨어져 타입 불일치) |
| 치명 이슈 0건 | 충족 | 1차 #1(`ItemLevelSearchController` 빈 catch 등)은 `57c7ecb` 에서 파일 삭제로 해결. 새 치명 이슈 없음 |
| 요청 범위 이탈 없음 | 미충족 | #3(1차 #3, 미해결). 그 밖에 요청 문장으로 설명되지 않는 파일은 "확인 필요" 참고 |
| 컨벤션 준수 | 충족 | 변경된 Java 파일: 컨트롤러에 Repository · SQL 없음, `*Response` record 반환, 빈 catch · `System.out` 없음, slf4j 사용, 와일드카드 import 없음, 120자 초과 줄 0건(문자 수 기준), 테스트 `@DisplayName` 한국어 |

**승인으로 바뀌는 조건**: #3 의 `characterization/` 정규화 형식 변경을 사람이 승인했다는 기록이 남으면(또는 변경을 되돌리면) 네 기준이 모두 충족된다.

## 1차 이슈 처리 결과

| 1차 # | 심각도 | 상태 | 근거 |
|---|---|---|---|
| 1 | 치명 | 해결 | `ItemLevelSearchController.java` 삭제(`57c7ecb`). HEAD 에 파일 없음 |
| 2 | 경고 | 해결 | 같은 커밋에서 요청과 무관한 `GET /api/items/by-level` 제거 |
| 3 | 경고 | **미해결** | `characterization/lib/normalize.mjs` · `tests/normalize.unit.test.js` diff 가 1차와 같다. `57c7ecb` 는 이 파일을 건드리지 않았고, 승인 기록도 없다 |
| 4 | 경고 | 해결 | `ItemSearchService.java:162` 가 `tagRepository.findByName(tagName)` 한 건 조회 + `equals` 재비교로 바뀌었다. `tag.name` 은 `UNIQUE`(`db/mariadb/init/01-schema.sql:39`)이고 콜레이션이 `utf8mb4_unicode_ci`(`:40`)라 대소문자 무시 조회 결과는 최대 1건이므로, 레거시의 전체 태그 목록 `===` 비교(`legacy/item-bank-php/search.php:178`, `:252-257`)와 결과가 같다 |
| 5 | 제안 | 미해결 | 의심 동작 목록이 여전히 따로 정리되지 않음(아래 #5) |

## 이슈 목록

| # | 심각도 | 파일:줄번호 | 근거 | 수정 방향 |
|---|---|---|---|---|
| 3 | 경고 | `characterization/lib/normalize.mjs:127-198`, `characterization/tests/normalize.unit.test.js:32-170` | 정규화 결과에 `warnings` 필드를 추가하면서 기존 테스트 기대값 6곳을 고쳤다. CLAUDE.md §3 · §5 는 `characterization/` 테스트 코드를 고쳐야 하면 멈추고 물으라고 한다. 기존 스냅샷(`example-units`)은 바뀌지 않았고 비교를 느슨하게 만든 변경도 아니지만, 사전 승인 기록이 없다. 1차 이후 변화 없음 | 사람이 승인 여부를 결정하고 PR 설명에 기록한다. 승인되지 않으면 `warnings` 비교를 별도 함수 · 별도 테스트로 분리해 기존 `{status, rows, count, message}` 형식을 유지 |
| 5 | 제안 | `modern/api/src/main/java/com/example/item/ItemSearchService.java:22`, `:33` | 난이도 미입력 시 5 제외(BR-14) 같은 의심 동작이 Javadoc 에만 있고 CLAUDE.md §5 의 "의심 동작 목록"으로는 보고되지 않았다 | PR 설명이나 별도 문서에 의심 동작 목록을 적는다 |
| 6 | 제안 | `modern/api/src/test/java/com/example/item/ItemSearchServiceTest.java:179-181` | `:161` 주석이 설명하는 콜레이션 불일치 경로(DB 는 `calc` 로 `Calc` 를 찾아 주지만 `equals` 로 걸러야 함)는 Mockito 스텁으로만 검증된다. 테스트 DB(H2)는 대소문자를 구분해 `@DataJpaTest` 로는 이 경로를 재현할 수 없다 | 현 상태로도 충분하다. 필요하면 characterization 케이스에 `tag=calc`(대소문자만 다른 태그) 를 추가해 실제 MariaDB 에서 경고 문구를 비교 |

## 확인 필요

- 이 PR 에는 이관 요청으로 설명되지 않는 파일이 함께 들어 있다: `CLAUDE.md`, `.claude/skills/convention-check/SKILL.md`, `.claude/skills/document-module/SKILL.md`, `mise.toml`, `docs/assignment/*`, `docs/item-bank/*`, `docs/claude-md-vs-react-template.md`, `docs/unit-item-counts-plan.md`, `docs/verify/migration-review-1.md`. 같은 PR 로 묶을지 사람이 결정한다(1차와 같음).
- 커밋 `6ee187d` 에서 추가하고 `57c7ecb` 에서 지운 `ItemLevelSearchController` 는 최종 diff 에는 없지만 커밋 이력에는 남는다. 이력 정리(스쿼시) 여부는 사람이 결정한다.
- `.claude/skills/verify/` 는 아직 커밋되지 않았고 이번 diff 범위에 포함되지 않는다.
- 레거시는 태그 목록 조회가 실패하면(`search.php:178-179`) 모든 태그를 "등록되지 않음"으로 경고하고 계속 진행하지만, 새 코드는 `findByName` 실패 시 예외 → 500 이 된다. DB 장애 상황의 차이라 이관 범위로 볼지 사람이 판단한다.
- 새 API 대상 characterization 은 8080 에서 이미 떠 있던 서버로 실행했다. 서버가 HEAD 로 빌드됐다는 근거는 위 400 응답뿐이므로, 확실히 하려면 `bootRun` 을 다시 띄워 재실행한다.
