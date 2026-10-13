# 머지 전 검증 — 외주사 PR 1 (`vendor-prs/pr-1-missing-tests.patch`)

- 대상: `vendor-prs/pr-1-missing-tests.patch` (커밋 `1a29f03`, 파일 3개, +31 / -1). patch 는 적용하지 않고 내용만 읽어 판정했다.
- 요청 범위: "단원별 문항 수 통계 API 추가" (patch `Subject:` 줄, `vendor-prs/pr-1-missing-tests.patch:4`). 본문 설명은 "단원마다 공개 상태 문항이 몇 개인지 한 번에 볼 수 있도록 `GET /api/units/item-counts` 를 추가"(`:9-10`).
- 체크리스트: `templates/approval-checklist.md` v0
- 테스트: 요청에 따라 실행하지 않았다. "테스트 통과" 기준은 "확인 필요"로 둔다.
- 줄번호 표기: `파일:줄번호` 는 **patch 적용 후** 파일 기준이다. 대응하는 patch 줄을 괄호로 함께 적는다.

판정: 반려

| 기준 | 결과 | 근거 |
|---|---|---|
| 테스트 통과 | 확인 필요 | 실행하지 않음(요청). patch 본문의 "기존 테스트 전부 통과 확인(./gradlew test)"(`patch:14`)은 실행 기록이 없어 근거로 쓰지 않았다. 체크리스트 1의 "테스트가 충분하다"는 미충족(#1) |
| 치명 이슈 0건 | 충족 | 비밀값 리터럴 · SQL 문자열 결합 · 데이터 삭제/덮어쓰기 코드 없음. 치명 이슈 0건 |
| 요청 범위 이탈 없음 | 충족 | 변경 파일 3개(`UnitController` · `UnitItemCountResponse` · `UnitService`)가 모두 "단원별 문항 수 통계 API"로 설명된다. 의존성 · 스키마 · 설정 변경 없음 |
| 컨벤션 준수 | 미충족 | #1 — 새 엔드포인트 · 새 public 서비스 메서드에 테스트가 없다(CLAUDE.md "modern/api 테스트": 새 엔드포인트 · 새 public 서비스 메서드마다 테스트를 1개 이상 추가) |

**승인으로 바뀌는 조건**: #1 의 테스트(컨트롤러 `@WebMvcTest` 1개 이상 + `UnitServiceTest` 에 `countActiveItemsByUnit` 단위 테스트 1개 이상)를 추가하고, `cd modern/api && ./gradlew test` 실행 결과(통과 · 실패 수)를 받으면 다시 판정한다.

## 체크리스트 대조

### 1. 테스트 통과

| 항목 | 결과 | 근거 |
|---|---|---|
| 변경 모듈의 테스트 명령을 실제로 실행했다 | 확인 필요 | 요청에 따라 실행하지 않음 |
| 실행 결과 실패 · 오류 0건 | 확인 필요 | 실행하지 않음 |
| 테스트가 충분하다 | 미충족 | patch 에 테스트 파일 변경이 0건(`patch:16-19` 의 변경 파일 목록). #1 |
| 기존 테스트를 지우거나 건너뛰게 바꾼 곳이 없다 | 충족 | 테스트 파일을 건드리지 않음 |

### 2. 치명 이슈 0건

| 항목 | 결과 | 근거 |
|---|---|---|
| 비밀값 리터럴 없음 | 충족 | 추가된 줄에 계정 · 토큰 · 키 없음 |
| SQL 문자열 결합 없음 | 충족 | 파생 쿼리 메서드 `countByUnitIdAndStatus`(기존, `ItemRepository.java:31`)만 사용 |
| 데이터 삭제 · 덮어쓰기 코드는 요청에 명시 | 충족 | 조회 전용. 클래스가 `@Transactional(readOnly = true)`(`UnitService.java:10`) |
| 심각한 문제가 없다 | 충족 | 치명으로 볼 문제 없음. 성능 우려는 경고 #2 |

### 3. 요청 범위 이탈 없음

| 항목 | 결과 | 근거 |
|---|---|---|
| 요청 문장 확인 | 충족 | `patch:4` "단원별 문항 수 통계 API 추가" |
| 모든 변경 파일이 요청으로 설명된다 | 충족 | 컨트롤러 메서드 · 응답 DTO · 서비스 메서드 3개 모두 해당 API 구성 요소 |
| 무관한 변경이 지나치게 많지 않다 | 충족 | 무관한 변경 0건 |
| 의존성 · 스키마 · 설정 변경은 요청에 포함 | 충족 | 해당 변경 없음 |

### 4. 컨벤션 준수

| 항목 | 결과 | 근거 |
|---|---|---|
| CLAUDE.md 컨벤션 · 금지 사항 대조 | 미충족 | #1 (테스트 누락). 그 밖의 항목은 아래 행 참고 |
| 컨트롤러에서 Repository · SQL 직접 호출 없음 | 충족 | `UnitController.java:27-29` 는 `unitService.countActiveItemsByUnit()` 만 호출 |
| 예외를 삼키는 곳 없음 | 충족 | 추가된 `catch` 없음 |
| 임시 출력 없음 | 충족 | `System.out` · `printStackTrace` 없음. 로그는 slf4j `log.debug`(`UnitService.java:42`), 식별자 · 이메일 · 토큰 인자 없음 |
| 팀 스타일 | 충족 | `*Response` record 반환(`UnitItemCountResponse.java:4`), 컨트롤러 반환 타입에 `@Entity` 없음, 공개 문항 조건 `ItemStatus.ACTIVE`(`UnitService.java:40`), 와일드카드 import 없음, 추가된 줄 최대 107자(문자 수 기준, `UnitItemCountResponse.java:7`)로 120자 이내, 경로가 `/api/units` 아래 |

## 이슈 목록

| # | 심각도 | 파일:줄번호 | 근거 | 수정 방향 |
|---|---|---|---|---|
| 1 | 경고 | `modern/api/src/main/java/com/example/item/UnitController.java:26-29` (`patch:32-35`), `modern/api/src/main/java/com/example/item/UnitService.java:37` (`patch:78`) | 새 엔드포인트 `GET /api/units/item-counts` 와 새 public 메서드 `countActiveItemsByUnit()` 를 추가했지만 테스트 파일 변경이 0건이다(`patch:16-19`). CLAUDE.md "modern/api 테스트" 의 "새 엔드포인트 · 새 public 서비스 메서드마다 테스트를 1개 이상 추가" 위반 → 컨벤션 기준 미충족. 기존 `UnitServiceTest` 는 `@Mock UnitRepository` 하나로 `@InjectMocks` 하므로(`UnitServiceTest.java:19-23`) 새 생성자 인자 `itemRepository` 에는 null 이 주입되고, 새 메서드를 검증하는 테스트가 없으면 이 경로는 전혀 실행되지 않는다 | `UnitServiceTest` 에 `@Mock ItemRepository` 를 추가하고 ① 저장소 순서 유지 ② 문항 0개 단원이 0 으로 포함됨 ③ `ItemStatus.ACTIVE` 로 조회함을 검증하는 Mockito 테스트를 넣는다. 컨트롤러는 `@WebMvcTest(UnitController.class)` + `@MockBean UnitService` 로 200 · JSON 배열 · 필드명(`code`, `name`, `grade`, `activeItemCount`)을 검증한다. `@ActiveProfiles("test")`, camelCase 메서드명, 한국어 `@DisplayName`, 픽스처는 `ItemFixtures.unit(...)` |
| 2 | 경고 | `modern/api/src/main/java/com/example/item/UnitService.java:38-41` (`patch:79-82`) | 단원 목록을 가져온 뒤 단원마다 `itemRepository.countByUnitIdAndStatus(...)` 를 한 번씩 호출한다(N+1 쿼리). 단원 수만큼 count 쿼리가 하나의 읽기 트랜잭션(커넥션 1개) 안에서 순차 실행된다. 단원 수는 요청 데이터가 아니라 DB 데이터라 CLAUDE.md 금지 사항("요청 데이터 크기에 비례하는 루프")에 그대로 해당하지는 않지만, Hikari 풀 최대 5 · 타임아웃 3초 환경에서 단원이 늘면 응답 시간과 커넥션 점유가 선형으로 커진다 | `ItemRepository` 에 단원별 공개 문항 수를 한 번에 세는 집계 쿼리(`select i.unit.id, count(i) ... where i.status = :status group by i.unit.id`, 파라미터 바인딩)를 추가하고, 서비스에서 단원 목록과 `Map<unitId, count>` 를 합쳐 없는 단원은 0 으로 채운다. 쿼리 테스트는 `@DataJpaTest` 로 추가 |
| 3 | 제안 | `modern/api/src/main/java/com/example/item/UnitItemCountResponse.java:4` (`patch:46`) | 기존 단원 응답 `UnitResponse` 는 `id` 를 포함하는데(`UnitResponse.java:4`) 새 응답은 `id` 를 뺐다. 두 단원 API 의 응답 모양이 달라 화면에서 합칠 때 키가 엇갈릴 수 있다 | 의도한 생략인지 확인하고, 아니면 `Integer id` 를 첫 필드로 추가해 `UnitResponse` 와 맞춘다 |

## 확인 필요

- **테스트 실행 결과**: 요청에 따라 실행하지 않았다. 판정을 마치려면 patch 적용 브랜치에서 `cd modern/api && ./gradlew test` 를 실행해 통과 · 실패 수를 받아야 한다. 기존 `UnitServiceTest` 는 `ItemRepository` mock 이 없어도 Mockito 가 생성자에 null 을 넘기므로 깨지지 않을 것으로 보이지만, 실행으로 확인하지 않았다.
- **patch 본문 "기존 테스트 전부 통과 확인"**: 외주사의 실행 로그나 결과 수치가 첨부되지 않아 검증할 수 없다.
- **같은 도메인 안의 Repository 주입**: `UnitService` 가 `ItemRepository` 를 직접 주입받는다. 둘 다 `com.example.item` 패키지라 CLAUDE.md "다른 도메인의 Repository 는 주입하지 않는다" 규칙에는 걸리지 않는다고 봤다. 팀이 단원 · 문항을 별도 도메인으로 본다면 `ItemService` 를 거치도록 바꿔야 하므로 팀 판단이 필요하다.
- **경로 충돌**: `/api/units/item-counts` 와 기존 `/api/units/{code}/items` 는 경로 세그먼트 수가 달라 충돌하지 않는다고 판단했다. 단, 이후 `/api/units/{code}` 단건 조회가 생기면 `item-counts` 가 단원 코드로 해석될 수 있으니 그때 다시 확인해야 한다.
- **CORS**: 새 경로도 `/api/**` 아래 GET 이라 `WebConfig` 변경은 필요 없다. `WebConfigTest` 는 `/api/units` 만 검사한다.
