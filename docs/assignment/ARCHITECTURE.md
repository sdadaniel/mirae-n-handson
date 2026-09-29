# assignment-thymeleaf 아키텍처

대상: `legacy/assignment-thymeleaf/`. 분석 범위: **과제 배포 폼 화면**(`distribution_form.html`)과 그 화면이 부르는 코드.

읽은 파일: `web/DistributionController.java` · `service/DistributionService.java` · `dao/DistributionDao.java` · `dao/AssignmentDao.java` · `dao/ClassDao.java` · `model/DistributionRow.java` · `model/RedistributeResult.java` · `web/DbErrorAdvice.java` · `AppClock.java` · `templates/distribution_form.html` · `db/mariadb/init/01-schema.sql`(73~115줄).

---

## 1. 진입점 · 주요 파일

| 파일 | 역할 |
|---|---|
| `templates/distribution_form.html` | 배포 폼 화면. `GET /distributions/new` 로 열리고 `POST /distributions` 로 제출 |
| `web/DistributionController.java` | 배포 폼 GET·POST, 재배포 POST 처리. `AssignmentDao`·`ClassDao` 직접 의존 |
| `service/DistributionService.java` | `distribute()`·`redistribute()` 비즈니스 로직 |
| `dao/DistributionDao.java` | `distribution`·`submission` 테이블 CRUD |
| `dao/AssignmentDao.java` | `assignment` 조회. 배포 폼 선택 목록(`findAllForSelect`)과 존재 검증(`findById`) 제공 |
| `dao/ClassDao.java` | `class` 테이블 전체 조회 및 단건 조회 |
| `AppClock.java` | 현재 시각 제공. 현재 **고정 시각** `2026-09-15 00:00:00` 반환(주석: "운영 전 되돌릴 것, 아직 안 됨") |
| `web/DbErrorAdvice.java` | `DataAccessException` 전파 시 `db_error.html` 렌더링. 단, 배포·재배포 경로는 컨트롤러에서 try-catch 처리하므로 이 핸들러에 닿지 않는다 |

---

## 2. 외부 의존

- **DB**: MariaDB `itembank`(포트 3306). `item-bank-php` · `modern/api` 와 공유. 접속 설정: `application.properties`
- **시각**: `AppClock.now()` — JVM 속성 `app.clock=system` 없으면 고정 시각 반환
- **공유 레이아웃**: `templates/layout.html` — `th:replace` 로 nav·head·footer 포함

---

## 3. 호출 흐름

### 새 배포 (`POST /distributions`)

```
distribution_form.html
  → DistributionController.create(assignmentId, classId)
      AssignmentDao.findById()          ← 과제 존재 확인 (컨트롤러)
      AppClock.now()                    ← 마감 판정
      DistributionService.distribute()  @Transactional
        AssignmentDao.findById()        ← 과제 존재·상태 확인 (서비스)
        ClassDao.findById()             ← 학급 존재 확인
        DistributionDao.countByAssignmentAndClass()  ← 중복 배포 방지
        DistributionDao.nextId()        ← MAX(id)+1 채번
        DistributionDao.insert()        ← INSERT
  → redirect:/distributions (성공) | redirect:/distributions/new (실패)
```

### 재배포 (`POST /distributions/{id}/redistribute`)

```
distributions.html (목록 화면의 재배포 버튼)
  → DistributionController.redistribute(id, reason, operator)
      DistributionService.redistribute()  @Transactional
        DistributionDao.findById()         ← 배포 존재
        AssignmentDao.findById()           ← 과제 상태·마감
        ClassDao.findById()                ← 학급 존재
        DistributionDao.findSubmissions()  ← 제출 현황 집계
        DistributionDao.markRedistributed() ← UPDATE redistributed=1
        DistributionDao.findById()          ← 갱신 확인
  → redirect:/distributions
```

---

## 4. 중복 의존 주의

`DistributionController`가 `AssignmentDao`·`ClassDao`를 직접 주입받는다. 같은 과제 존재 확인이 컨트롤러(`DistributionController:53`)와 서비스(`DistributionService:49`) 두 곳에서 이뤄진다. 이관 시 어느 계층에서 검증할지 정해야 한다.
