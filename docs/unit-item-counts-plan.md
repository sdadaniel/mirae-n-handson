# 단원별 · 난이도별 공개 문항 수 조회 API — 구현 계획

2026-09-29 기준. 상태: 계획만 확정, 아직 구현하지 않음.

## Context
- 목표: `modern/api` 에 단원(unit) × 난이도(level 1~5)별 **공개(`status='A'`) 문항 수**를 돌려주는 GET API 추가.
- 현재 상태: 작업 트리에 이 기능의 시작 흔적이 untracked 로 있다.
  - `item/UnitItemCountResponse.java` — `(id, code, name, grade, long itemCount)` 단원별 합계만 있음, 난이도 분해 없음
  - `test/.../item/UnitControllerTest.java` — `GET /api/units/item-counts` 를 테스트하지만 `UnitService.countActiveItemsByUnit()` 이 없어 **지금은 컴파일 실패** 상태
  - 계획은 이 두 파일을 이어받아 확장한다(같은 URL · 같은 메서드 이름 유지).

## API 모양 (제안)
`GET /api/units/item-counts` → 200, 배열(학년 · 코드 순, 문항 0개 단원도 포함)
```json
[{ "id":1, "code":"M5-1", "name":"분수의 덧셈과 뺄셈", "grade":5,
   "itemCount":3,
   "levels":[{"level":1,"count":0},{"level":2,"count":1},{"level":3,"count":0},{"level":4,"count":1},{"level":5,"count":1}] }]
```
- `levels` 는 항상 1~5 다섯 칸(빈 칸은 0) — 화면에서 표로 그리기 쉽게.
- 엔드포인트는 기존 `/api/units` 아래(CLAUDE.md "새 엔드포인트는 `/api/<도메인 복수형>`").

## 1. 파일 목록

| 구분 | 경로 (`modern/api/src/...`) | 내용 |
|---|---|---|
| 수정 | `main/java/com/example/item/ItemRepository.java` | JPQL 집계 쿼리 1개 추가: `select new ...UnitLevelCount(i.unit.id, i.level, count(i)) from Item i where i.status = :status group by i.unit.id, i.level` |
| 신규 | `main/java/com/example/item/UnitLevelCount.java` | 집계 결과 프로젝션 record `(Integer unitId, Integer level, long count)` — 패키지 내부용 |
| 신규 | `main/java/com/example/item/LevelCountResponse.java` | 응답 record `(int level, long count)` |
| 수정(untracked) | `main/java/com/example/item/UnitItemCountResponse.java` | `List<LevelCountResponse> levels` 필드 추가 |
| 수정 | `main/java/com/example/item/UnitService.java` | `ItemRepository` 주입, `countActiveItemsByUnit()` 추가. `MIN_LEVEL = 1`, `MAX_LEVEL = 5` 상수 |
| 수정 | `main/java/com/example/item/UnitController.java` | `@GetMapping("/item-counts")` → `unitService.countActiveItemsByUnit()` |
| 수정(untracked) | `test/java/com/example/item/UnitControllerTest.java` | `levels` 검증 추가 |
| 수정 | `test/java/com/example/item/UnitServiceTest.java` | `@Mock ItemRepository` 추가 + 새 메서드 테스트 |
| 수정 | `test/java/com/example/item/ItemRepositoryTest.java` | 집계 쿼리 `@DataJpaTest` 추가(기존 시드 재사용) |

## 2. 계층과 호출 방향
```
UnitController (item, 웹)            — 서비스만 호출, 반환 List<UnitItemCountResponse>
  → UnitService (item, 서비스)        — @Transactional(readOnly=true)
      → UnitRepository.findAllByOrderByGradeAscCodeAsc()   (단원 목록, 쿼리 1회)
      → ItemRepository.countActiveByUnitAndLevel(ACTIVE)   (group by 집계, 쿼리 1회)
          → Unit / Item 엔티티
```
- 같은 `item` 도메인 안이므로 `UnitService` 가 `ItemRepository` 를 직접 주입(기존 `ItemService` 가 `UnitRepository` 를 주입하는 것과 같은 방식). 다른 도메인 서비스 주입은 필요 없음.
- 쿼리는 단원 수와 무관하게 **항상 2회**. 기존 `countByUnitIdAndStatus` 를 단원마다 부르는 N+1 방식은 쓰지 않는다.
- 서비스는 집계 결과를 `Map<unitId, Map<level, count>>` 로 모은 뒤 단원 목록 순서대로 1~5 칸을 채워 DTO 생성(메모리 연산, DB 호출 루프 없음).
- 로그: `log.debug("counted items for {} units", n)` 정도, 학생 식별자 등 없음. 예외 처리 추가 없음(실패는 `GlobalExceptionHandler` 가 500).

## 3. 테스트와 실행 명령

| 테스트 | 종류 | 검증 내용 |
|---|---|---|
| `UnitControllerTest.listItemCountsReturnsArray` (확장) | `@WebMvcTest` + `@MockBean UnitService` | 200, 배열 길이, `code` · `itemCount` · `levels[1].level/count` |
| `UnitServiceTest.countActiveItemsByUnitFillsLevels` | Mockito | 1~5 다섯 칸, 없는 난이도는 0, `itemCount` = 합계, 단원 순서 유지 |
| `UnitServiceTest.countActiveItemsByUnitIncludesEmptyUnit` | Mockito | 문항 없는 단원도 `itemCount 0`, levels 모두 0 |
| `ItemRepositoryTest.countsActiveByUnitAndLevel` | `@DataJpaTest` (H2) | 기존 시드 기준 M5-1 → level 2·4·5 각 1, M5-3 → level 1 1; `R` · `D` 문항 제외 |

- 모두 `@ActiveProfiles("test")`, camelCase 이름 + 한국어 `@DisplayName`, 픽스처는 `ItemFixtures`.

```bash
cd modern/api && ./gradlew test --tests 'com.example.item.ItemRepositoryTest'
cd modern/api && ./gradlew test --tests 'com.example.item.UnitServiceTest'
cd modern/api && ./gradlew test --tests 'com.example.item.UnitControllerTest'
cd modern/api && ./gradlew test          # 최종 전체
```
(선택) 수동 확인: 루트에서 `docker compose --profile modern up -d` → `./gradlew bootRun` → `curl localhost:8080/api/units/item-counts`

## 4. 완료 기준
- `./gradlew test` 전체 통과, 통과 · 실패 수를 답변에 기록(하나라도 실패하면 "완료"라고 쓰지 않음).
- 위 4개 테스트가 존재하고 통과.
- 컨트롤러에 Repository · SQL 없음, 반환 타입에 `@Entity` 없음, 공개 문항(`ItemStatus.ACTIVE`)만 집계.
- 난이도 범위는 상수(`MIN_LEVEL`/`MAX_LEVEL`), 인자 없는 `now()` · `System.out` · 빈 catch 없음, 120자 · 와일드카드 import 규칙 준수.
- 변경 파일이 위 표의 범위를 벗어나지 않음(벗어나면 파일과 이유를 답변에 기재).

## 5. 하지 않을 일
- `modern/web` 화면 · 훅 · 타입 추가 (별도 요청 시)
- `legacy/` · `characterization/__snapshots__/` 수정
- `build.gradle` 의존성 추가, `db/**/init/` 스키마 · 시드 변경, `application.yml` DB · Hikari 설정 변경
- CORS(`config/WebConfig`) 변경 — GET 이라 기존 허용 범위로 충분
- 단원 코드 · 학년 · 상태 필터 같은 쿼리 파라미터, 캐싱, 페이징 (요청 범위 밖)
- 기존 `countByUnitIdAndStatus` · 기존 엔드포인트 동작 변경, 관련 없는 파일 포맷팅
- 커밋 · 푸시 (요청 시에만)
