# 머지 전 검증 — 레거시 엔드포인트 1개 이관

- 대상: `upstream/main...HEAD` (커밋 8건, 29개 파일, +3803 / -10)
- 요청 범위: 레거시 엔드포인트 1개를 동작을 바꾸지 않고 modern 으로 이관.
- 체크리스트: `templates/approval-checklist.md` v0

판정: 반려

| 기준 | 결과 | 근거 |
|---|---|---|
| 테스트 통과 | 충족 | `cd modern/api && ./gradlew test` → 51 통과 · 0 실패 · 0 건너뜀. `cd characterization && npm test`(레거시 8081) → 28 통과 · 0 실패. `TARGET_BASE_URL=http://localhost:8080 npm test`(새 API) → 27 통과 · 0 실패 · 1 건너뜀(`example-units.test.js:21`, `legacyOnly` 가드, item-bank 와 무관). item-bank 12 케이스는 새 API 에서도 모두 통과. 단, `ItemLevelSearchController` 의 테스트는 없음(이슈 #1) |
| 치명 이슈 0건 | 미충족 | #1 |
| 요청 범위 이탈 없음 | 미충족 | #2, #3 |
| 컨벤션 준수 | 미충족 | #1, #4 |

## 이슈 목록

| # | 심각도 | 파일:줄번호 | 근거 | 수정 방향 |
|---|---|---|---|---|
| 1 | 치명 | `modern/api/src/main/java/com/example/item/ItemLevelSearchController.java:29-30` | `catch (Exception e) {}` 빈 catch 로 DB 오류를 삼킨다. 쿼리가 실패해도 200 + 빈 배열을 돌려줘 장애가 정상처럼 보인다(CLAUDE.md §3 "빈 `catch` 블록 금지", §2 "예외를 삼키지 않는다"). 같은 파일 `:15,:17` 컨트롤러가 `JdbcTemplate` 필드 · 생성자 인자를 가지고 `:27` 에 SQL 문자열이 있으며, `:23,:31` 에 `System.out.println`, `:21` 반환 타입 `List<Map<String,Object>>`(응답은 `*Response` record DTO), 테스트도 없음 | 이 컨트롤러는 삭제하거나(요청과 무관, #2) 유지해야 한다면 Controller → Service → Repository 로 나누고 `*Response` record 로 반환, 빈 catch · println 제거(필요 시 slf4j), 테스트 추가. 허락 없이 지우지 않고 사람이 결정 |
| 2 | 경고 | `modern/api/src/main/java/com/example/item/ItemLevelSearchController.java:1-34` | 이관 요청(`search.php` → `/api/items/search`)과 무관한 신규 엔드포인트 `GET /api/items/by-level`. 커밋 `6ee187d` 메시지에 "후속 수정 필요" 라고 위반을 스스로 적어 두었다. 새 엔드포인트는 `/api/<도메인 복수형>` 규칙(CLAUDE.md §4)과도 겹치는 `/api/items/search` 와 용도가 중복 | 이 PR 에서 분리한다(별도 PR 로 이동하거나 제거) |
| 3 | 경고 | `characterization/lib/normalize.mjs:127-198`, `characterization/tests/normalize.unit.test.js:32-170` | 비교용 정규화 결과에 `warnings` 필드를 추가해 기존 테스트 기대값 6곳을 고쳤다. CLAUDE.md §3 · §5 는 `characterization/` 의 테스트 코드 수정을 금지하고, 바꿔야 하면 멈추고 물으라고 한다. 기존 스냅샷을 바꾸지는 않았고(기존 `example-units` 스냅샷은 diff 없음) 새 스냅샷만 추가했으나, 정규화 형식 변경은 사전 승인 기록이 없다 | 승인 여부를 사람이 확인한다. 승인되지 않았다면 `warnings` 를 별도 필드로 분리해 기존 형식을 유지 |
| 4 | 경고 | `modern/api/src/main/java/com/example/item/ItemSearchService.java:161` | `tagRepository.findAll().stream().anyMatch(...)` 로 태그 존재 확인을 위해 전체 태그를 읽는다. 검색 요청마다 전체 조회가 일어나 태그가 늘면 느려진다 | `TagRepository.existsByName(name)` 같은 조회 메서드로 교체. 레거시 동작(정확 일치)은 유지 |
| 5 | 제안 | `modern/api/src/main/java/com/example/item/ItemSearchService.java:20-26` | 클래스 Javadoc 이 "버그로 보여도 그대로 둔다"고 밝힌 의심 동작(난이도 미입력 시 5 제외, `:38`)이 코드 주석에는 있으나 "의심 동작 목록"으로는 정리돼 있지 않다 | 답변 · PR 설명에 의심 동작 목록을 따로 적는다(CLAUDE.md §5) |

## 확인 필요

- 이관 대상 `search.php` 의 이관 코드가 레거시 BR 문서(`docs/item-bank/BUSINESS-RULES.md`)와 줄 단위로 일치하는지는 characterization 12 케이스 범위에서만 확인했다. 케이스에 없는 경우(예: `name[]` 배열 파라미터, `page` 끝 줄바꿈)는 단위 테스트(`ItemSearchServiceTest`, `ItemSearchControllerTest`)에 의존한다.
- `.claude/skills/verify/` 는 아직 커밋되지 않은 상태이며 이번 diff 범위에 포함되지 않는다.
- 이 PR 에는 이관과 무관한 문서 · CLAUDE.md · 스킬 · `mise.toml` 이 함께 들어 있다(`docs/**` 12건, `CLAUDE.md`, `.claude/skills/*`, `mise.toml`). 요청 문장만으로는 설명되지 않으므로 같은 PR 로 묶을지 사람이 결정한다.
