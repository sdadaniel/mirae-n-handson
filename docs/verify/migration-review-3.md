# 머지 전 검증 (3차) — 레거시 엔드포인트 1개 이관

- 대상: `upstream/main...HEAD -- modern characterization` (HEAD `dea5211`, 15개 파일, +1842 / -10)
- 요청 범위: 레거시 엔드포인트 1개를 동작을 바꾸지 않고 modern 으로 이관. (`legacy/item-bank-php/search.php` → `GET /api/items/search`)
- 체크리스트: `templates/approval-checklist.md` v1 (경고 1건 이상이면 반려)
- 수행: 체크리스트 대조는 reviewer Sub-agent, 테스트 실행은 tester Sub-agent, 최종 판정은 메인 세션
- 이전 검증: `docs/verify/migration-review-2.md` (반려)

판정: 반려

| 기준 | 결과 | 근거 |
|---|---|---|
| 테스트 통과 | 미충족 | `cd modern/api && ./gradlew test --rerun-tasks` → 51 통과 · **1 실패** · 0 건너뜀. 실패: `ItemSearchSpecsTest.keywordBackslashUsesDefaultLikeEscape`. `cd characterization && npm test`(레거시 8081) → 28 통과 · 0 실패. `TARGET_BASE_URL=http://localhost:8080 npm test`(새 API) → 27 통과 · 0 실패 · 1 건너뜀(`example-units.test.js` 의 레거시 전용 `skipIf`, 기존 코드) |
| 치명 이슈 0건 | 충족 | §2 의 4종(비밀값 · 데이터 손실 · 인증 우회 · SQL 문자열 조립) 없음. `ItemSearchSpecs.java:21` 의 `"%" + keyword + "%"` 는 Criteria `cb.like` 에 바인딩되는 값이다 |
| 요청 범위 이탈 없음 | 미충족 | #2 · #3. `characterization/` 의 정규화 형식 · 기존 테스트 기대값 · 기존 스냅샷 변경은 이관 요청으로 설명되지 않고 승인 기록도 없다. #1 은 "동작을 바꾸지 않고" 조건에 걸린다 |
| 컨벤션 준수 | 미충족 | #2 · #3 (CLAUDE.md §3 공통 · §5 의 `characterization/` 수정 금지, 경고 등급). Java 쪽 반려 항목(계층 · 빈 catch · 임시 출력)은 위반 없음 |

경고 3건(#1 · #2 · #3), 제안 3건(#4 · #5 · #6).

**승인으로 바뀌는 조건**
1. `ItemSearchSpecs.keywordContains` 가 레거시와 같은 LIKE 이스케이프로 동작해 `./gradlew test` 가 전부 통과한다(#1).
2. `characterization/` 의 `warnings` 필드 추가와 `example-units` 스냅샷 변경을 사람이 승인했다는 기록이 남거나, 변경을 되돌린다(#2 · #3).

## 이슈 목록

| # | 심각도 | 파일:줄번호 | 근거 | 수정 방향 |
|---|---|---|---|---|
| 1 | 경고 | `modern/api/src/main/java/com/example/item/ItemSearchSpecs.java:22` (실패 테스트 `ItemSearchSpecsTest.java:76`) | `cb.like(root.get("title"), like)` 를 이스케이프 문자 없이 호출하면 Hibernate 가 `like ? escape ''` 를 만들어 기본 이스케이프 `\` 를 끈다(tester 가 SQL 로그로 확인). 키워드 `\%` 검색 결과가 기대 `["50% 할인 분수", "할인율 100%"]` 대신 `["경로 C:\temp 분수"]` 로 나왔다. 레거시는 MariaDB 기본 이스케이프 `\` 를 쓴다(`search.php:97-98`). 이 테스트는 `dea5211` 에서 추가됐고 소스는 고쳐지지 않았다 | `cb.like(root.get("title"), like, '\\')`, `stem` 도 같게 이스케이프 문자를 명시한다. 고친 뒤 `./gradlew test` 재실행 |
| 2 | 경고 | `characterization/__snapshots__/example-units.test.js.snap:40` | 기존 스냅샷에 `"warnings": [],` 한 줄이 추가됐다(커밋 `41ef482`). CLAUDE.md §3 공통("`__snapshots__/` 를 손으로 고치지 않는다")과 §5("스냅샷을 바꿔야 하면 멈추고 묻는다")에 걸린다. 커밋 본문 · docs 에 승인 기록이 없다. **정정**: 1 · 2차 기록(`migration-review-2.md` 이슈 #3 근거)은 "example-units 스냅샷은 바뀌지 않았다"고 적었지만 실제로는 바뀌었다 | 사람이 승인 여부를 정해 PR 설명이나 docs 에 기록한다. 승인하지 않으면 `normalize` 를 4필드 형식으로 되돌리고 warnings 는 별도 함수로 비교한다(그러면 이 줄도 사라진다) |
| 3 | 경고 | `characterization/lib/normalize.mjs:127-198`, `characterization/tests/normalize.unit.test.js:55`, `:61`, `:74`, `:122`, `:132`, `:163`, `:168` | 정규화 결과에 `warnings` 필드를 추가하며 기존 테스트 기대값을 고쳤다. CLAUDE.md §5 위반, 승인 기록 없음. 2차 #3 과 diff 동일(미해결) | #2 와 같다 |
| 4 | 제안 | `modern/api/src/main/java/com/example/item/ItemSearchService.java:22`, `:33` | 의심 동작(난이도 미입력 시 5 제외, BR-14 — `search.php:213-215` 의 주석과 코드 불일치)이 Javadoc 에만 있고 CLAUDE.md §5 의 "의심 동작" 목록으로 보고되지 않았다(2차 #5, 미해결) | PR 설명이나 문서에 의심 동작 목록을 적는다 |
| 5 | 제안 | `characterization/tests/item-bank.test.js:11-70` | 대소문자만 다른 태그(`tag=calc`) 케이스가 없다. `ItemSearchService.java:161-162` 의 콜레이션 처리는 Mockito 스텁(`ItemSearchServiceTest.java:179-184`)으로만 검증된다(2차 #6, 미해결). 같은 이유로 `\` · `%` 를 포함한 키워드 케이스도 없어 #1 이 characterization 에서 걸러지지 않는다 | 실제 MariaDB 대상 characterization 에 `tag=calc`, 그리고 `%` · `\` 가 든 문항과 맞는 키워드 케이스를 추가한다(스냅샷 갱신은 사람 승인 후) |
| 6 | 제안 | `modern/api/src/test/java/com/example/item/ItemSearchSpecsTest.java:141-152` | `findAllByIdIn` 테스트가 태그 순서를 확인하지 않는다. 레거시는 `GROUP_CONCAT(... ORDER BY t.id)`(`db/mariadb/init/01-schema.sql:64`), modern 은 `@OrderBy("id ASC")`(`Item.java:59`) + `@EntityGraph` 에 기댄다 | 태그 2개 이상인 문항으로 `getTags()` 이름 순서를 단언한다 |

## 2차 이슈 처리 결과

| 2차 항목 | 상태 | 근거 |
|---|---|---|
| #3 경고 | 미해결 | 이번 #3. 스냅샷 변경(#2)이 추가로 드러났다 |
| #5 제안 | 미해결 | 이번 #4 |
| #6 제안 | 미해결 | 이번 #5 |
| 확인 필요: `.claude/skills/verify/` 미커밋 | 해결 | `3fd8f47`, `dea5211` 에 포함 |
| 확인 필요: 8080 이 HEAD 빌드인지 | 간접 확인 | 새 API 대상 item-bank 12건이 `warnings` 필드를 포함한 스냅샷과 일치했다(`characterization/lib/target.mjs:21-24` 의 경로 대응표 경유) |

## reviewer · tester 보고 대조

- 두 보고는 엇갈리지 않는다. reviewer 는 레거시 대조에서 "확인한 범위에서 관찰 가능한 차이 없음"이라고 하면서 LIKE 기본 이스케이프는 "실제 DB 로 확인되지 않음"으로 따로 남겼고, tester 의 실패 테스트가 바로 그 지점이다.
- 메인 세션이 레거시(8081)와 새 API(8080)에 `q=\%`, `q=\\` 를 직접 보냈다. 양쪽 모두 0건, 경고 문구도 같았다. 다만 시드에 `%` · `\` 를 포함한 문항이 없어 이 비교로는 차이를 가를 수 없다. 판정 근거는 #1 의 실패 테스트다.

## 확인 필요

- MariaDB 에서도 #1 이 재현되는지는 실제 DB 데이터로 확인하지 못했다. Hibernate 가 `escape ''` 를 붙이는 것은 SQL 생성 단계라 DB 와 무관할 가능성이 높지만, 확정하려면 `%` · `\` 가 든 문항으로 8081 · 8080 응답을 비교해야 한다(시드 변경은 사전 승인 필요).
- DB 장애 시 동작 차이: 레거시는 단원 · 태그 조회 실패 시 경고만 남기고 계속한다(`search.php:124-140`, `:177-179`). 새 코드는 예외 → 500(`ItemSearchService.java:128`, `:162`). 이관 범위로 볼지 사람이 판단한다(2차와 같음).
- 같은 이름의 일반 키 · 배열 키 혼재(`unit=A&unit[]=B`), 중첩 배열(`unit[a][b]=x`)에서 PHP `$_GET` 과 새 코드(`ItemSearchCondition.java:38-41`)의 동작이 다를 수 있다. PHP 동작 근거가 저장소에 없고 테스트도 없어 이슈로 올리지 않았다.
- 이번 pathspec 밖에 이관 요청으로 설명되지 않는 파일이 브랜치에 있다: `.mcp.json`, `dbhub.toml`, `mcp-skeleton/src/index.ts`, `.claude/agents/*`, `.claude/settings.json`, `.claude/skills/rca/*`, `docs/rca/*`, `docs/verify/vendor-*`, `templates/*` (2차 목록에 더해). 설정 파일(`.claude/settings.json`, `.mcp.json`, `dbhub.toml`)은 체크리스트 §3 항목 4 대상이다. 같은 PR 로 묶을지 사람이 정한다.
- `ItemLevelSearchController` 의 추가(`6ee187d`) · 삭제(`57c7ecb`) 이력이 남아 있다. 스쿼시 여부는 사람이 정한다.
