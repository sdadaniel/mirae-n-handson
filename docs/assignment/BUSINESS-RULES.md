# assignment-thymeleaf 비즈니스 규칙 후보

대상: `legacy/assignment-thymeleaf/`. 범위: 배포 폼(`distribution_form.html`)과 그 화면이 부르는 코드. 진입점·의존 관계는 [ARCHITECTURE.md](ARCHITECTURE.md), 테이블은 [ERD.md](ERD.md) 를 따른다.

작성 기준
- 파일 경로는 `legacy/assignment-thymeleaf/src/main/java/com/example/assign/` 기준이다. 스키마만 `01-schema.sql`(= `db/mariadb/init/01-schema.sql`)로 적는다.
- 확신도 **확실**: 조건문·SQL로 확인. **추정**: 이름·주석·설정으로 짐작. 코드 실행·DB 접속은 하지 않았다.
- 주석과 코드가 다르면 코드 기준으로 쓰고 주석 내용을 비고에 남겼다.

---

## 1. 배포 폼 — 선택 목록

### BR-01
- 규칙: 과제 선택 목록에는 상태가 `O`(진행) · `X`(연장) · `C`(종료)인 과제만 나온다.
- 근거: `dao/AssignmentDao.java:35`
- 근거 코드:
  ```java
  WHERE a.status IN ('O', 'X', 'C')
  ```
- 확신도: 확실
- 비고: 상태 `R`(검수중) · `D`(삭제)는 목록에 나오지 않는다. 삭제 기준은 스키마 주석 "O=진행 C=마감"과 코드가 다르다(ERD.md 1절).

### BR-02
- 규칙: 마감이 지난 과제는 폼에서 `disabled`(선택 불가)로 표시한다. 단, 상태가 `X`(연장)이면 마감이 지나도 선택 가능하다.
- 근거: `templates/distribution_form.html:16`
- 근거 코드:
  ```html
  th:disabled="${a.dueAt != null and a.dueAt.isBefore(now) and a.status != 'X'}"
  ```
- 확신도: 확실
- 비고: `disabled` 는 HTML 힌트이고, 서버측 마감 검사는 BR-04(컨트롤러)·BR-05(서비스)에서 따로 한다.

### BR-03
- 규칙: 학급 선택 목록에는 `class` 테이블 전체를 id 오름차순으로 보여 준다.
- 근거: `dao/ClassDao.java:21`
- 근거 코드:
  ```java
  SELECT id, name, teacher_id FROM `class` ORDER BY id ASC
  ```
- 확신도: 확실
- 비고: 삭제·비활성 필터 없음.

---

## 2. 배포 생성 (POST /distributions)

### BR-04
- 규칙: 마감이 지난 과제는 컨트롤러에서 배포를 거부한다. 단, 상태가 `X`(연장)이면 마감 후에도 배포를 허용한다.
- 근거: `web/DistributionController.java:60-64`
- 근거 코드:
  ```java
  if (a.getDueAt() != null && a.getDueAt().isBefore(now)) {
      if (!"X".equals(a.getStatus())) {
          ra.addFlashAttribute("error", "마감(…)이 지난 과제는 배포할 수 없습니다. …");
          return "redirect:/distributions/new";
  ```
- 확신도: 확실
- 비고: 같은 과제를 서비스(`DistributionService:49`)에서 한 번 더 조회해 존재 여부를 재확인한다.

### BR-05
- 규칙: 상태 `D`(삭제)인 과제는 서비스에서 배포를 차단한다.
- 근거: `service/DistributionService.java:56-58`
- 근거 코드:
  ```java
  if ("D".equals(a.getStatus())) {
      throw new IllegalStateException("삭제된 과제는 배포할 수 없습니다.");
  ```
- 확신도: 확실
- 비고: BR-01 목록 필터로도 이미 제외된다. 이중 방어.

### BR-06
- 규칙: 같은 학급에 같은 과제를 두 번 배포할 수 없다. (DB UNIQUE 제약 없음, 코드로만 막는다.)
- 근거: `dao/DistributionDao.java:46-52`, `service/DistributionService.java:59-61`
- 근거 코드:
  ```java
  // 같은 학급 + 같은 과제 는 한 건만 존재해야 한다 (UNIQUE 제약은 없음, 여기서 막는다)
  if (distributionDao.countByAssignmentAndClass(assignmentId, classId) > 0) {
      throw new IllegalStateException("이미 이 학급에 배포된 과제입니다. …");
  ```
- 확신도: 확실
- 비고: 동시 요청이 들어오면 두 건이 저장될 수 있다(**추정**; 실행 미확인). 이관 시 UNIQUE 제약 추가 여부를 판단해야 한다.

### BR-07
- 규칙: 배포 id는 트랜잭션 안에서 `MAX(id)+1`로 채번한다. 배포가 없으면 1이다.
- 근거: `dao/DistributionDao.java:54-57`
- 근거 코드:
  ```java
  Long max = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM distribution", Long.class);
  return (max == null ? 0L : max.longValue()) + 1L;
  ```
- 확신도: 확실
- 비고: `distribution.id`는 AUTO_INCREMENT가 없다(`01-schema.sql:92`). 동시 채번 충돌 가능성 있음(**추정**).

### BR-08
- 규칙: 새 배포는 `redistributed=0`으로 저장한다.
- 근거: `dao/DistributionDao.java:61`
- 근거 코드:
  ```java
  INSERT INTO distribution (id, assignment_id, class_id, distributed_at, redistributed) VALUES (?, ?, ?, ?, 0)
  ```
- 확신도: 확실

---

## 3. 재배포 (POST /distributions/{id}/redistribute)

### BR-09
- 규칙: 작업자 ID가 없거나 공백이면 `SYSTEM`으로 기록하고 notes에 남긴다. 20자를 넘으면 잘라 경고한다.
- 근거: `service/DistributionService.java:83-93`
- 근거 코드:
  ```java
  if (op.length() == 0) { op = "SYSTEM"; notes.add("작업자 미지정 → SYSTEM 으로 기록"); }
  else if (op.length() > 20) { op = op.substring(0, 20); warnings.add("작업자 ID가 20자를 넘어 잘렸습니다."); }
  ```
- 확신도: 확실
- 비고: `class.teacher_id` 최대 길이도 20자(`01-schema.sql:78`)다.

### BR-10
- 규칙: 사유가 비어 있으면 `(사유 없음)`으로 대체한다. 200자를 넘으면 197자 + `...`으로 잘라 경고한다. `<` · `>` 는 HTML 엔티티로 치환한다.
- 근거: `service/DistributionService.java:108-117`
- 근거 코드:
  ```java
  if (rsn.length() == 0) { rsn = "(사유 없음)"; }
  else if (rsn.length() > 200) { rsn = rsn.substring(0, 197) + "..."; }
  if (rsn.indexOf('<') >= 0 || rsn.indexOf('>') >= 0) { rsn = rsn.replace("<", "&lt;").replace(">", "&gt;"); }
  ```
- 확신도: 확실

### BR-11
- 규칙: 사유 키워드로 분류를 매긴다. 오류/장애=1, 연장/추가=2, 정정/수정=3, 재시험/재평가=4, 사유없음=9, 그 밖=0(기타).
- 근거: `service/DistributionService.java:119-150`
- 근거 코드:
  ```java
  if (rsn.contains("오류") || rsn.contains("장애")) { reasonCategory = 1; }
  else if (rsn.contains("연장") || rsn.contains("추가")) { reasonCategory = 2; }
  ...
  ```
- 확신도: 확실
- 비고: 첫 번째 매칭 분류가 적용된다. "오류 연장"이면 1로 분류된다.

### BR-12
- 규칙: 상태 `D`(삭제) · `R`(검수중) 과제는 재배포할 수 없다.
- 근거: `service/DistributionService.java:199-221`
- 근거 코드:
  ```java
  case "D": result.setCode("E103"); result.setMessage("삭제된 과제는 재배포할 수 없습니다."); return result;
  case "R": result.setCode("E104"); result.setMessage("검수중인 과제는 재배포할 수 없습니다."); return result;
  ```
- 확신도: 확실

### BR-13
- 규칙: 마감 후 72시간(3일) 이상 경과하면 재배포를 거부한다. 마감이 없거나 마감 전이거나 유예 기간(72시간 미만) 이내면 허용한다. 상태 `X`(연장)이면 마감 경과에도 허용한다.
- 근거: `service/DistributionService.java:307-325`
- 근거 코드:
  ```java
  // 재배포는 마감 후 7일 이내에만 허용한다 (교무 지침 §4.2)
  if (hoursAfterDue >= 72) {
      allowed = false;
      denyReason = "마감 후 유예 기간이 지났습니다. …";
  ```
- 확신도: 확실
- 비고: **주석과 코드가 다르다.** 주석(`:318`)은 "마감 후 7일 이내"라고 하지만, 코드는 `hoursAfterDue >= 72`(3일 기준)으로 거부한다. 교무 지침 원본을 확인해야 한다.

### BR-14
- 규칙: 이미 재배포된 건(`redistributed=1`)은 원칙적으로 두 번째 재배포를 거부한다. 예외: 상태 `X`(연장)이거나 사유 분류가 시스템 오류(1)이면 허용한다.
- 근거: `service/DistributionService.java:329-337`
- 근거 코드:
  ```java
  if (dist.getRedistributed() == 1) {
      if (extended) { notes.add("이미 재배포된 건이지만 '연장' 상태 → 재배포 다시 허용"); }
      else if (reasonCategory == 1) { notes.add("…시스템 오류 사유 → 재배포 다시 허용"); }
      else { allowed = false; denyReason = "이미 재배포된 배포입니다. …"; }
  ```
- 확신도: 확실

### BR-15
- 규칙: 재배포는 기존 제출을 삭제하지 않는다. 미제출자만 재제출 대상으로 계산한다.
- 근거: `service/DistributionService.java:483-486`
- 근거 코드:
  ```java
  // 재배포는 기존 제출을 지우지 않는다. 미제출자만 '재제출 대상' 으로 계산해 화면에 알려 준다.
  int kept = total - notSubmitted;
  int reopen = notSubmitted;
  ```
- 확신도: 확실
- 비고: 상태 `X`(연장)이면 채점 전 제출자도 재제출 대상에 포함(BR-16). 사유 분류 재평가(4)이면 전원 재제출 대상이 된다(BR-17).

### BR-16
- 규칙: 상태 `X`(연장)이면 채점 전 제출자도 재제출 대상에 포함한다.
- 근거: `service/DistributionService.java:487-490`
- 근거 코드:
  ```java
  if (extended) { reopen = notSubmitted + submittedNoScore; }
  ```
- 확신도: 확실

### BR-17
- 규칙: 사유 분류가 재평가(4)이면 전원이 재제출 대상이 된다. 기존 점수는 그대로 유지한다.
- 근거: `service/DistributionService.java:492-495`
- 근거 코드:
  ```java
  if (reasonCategory == 4) { reopen = total; notes.add("재평가 사유 → 전원 재제출 대상 (기존 점수는 유지)"); }
  ```
- 확신도: 확실

### BR-18
- 규칙: 재배포 성공 시 `distribution.redistributed`를 1로 갱신한다. 갱신 건수가 0이면 오류(E107)로 처리한다.
- 근거: `service/DistributionService.java:620-625`, `dao/DistributionDao.java:65-67`
- 근거 코드:
  ```java
  int updated = distributionDao.markRedistributed(distributionId);
  if (updated == 0) { result.setCode("E107"); … return result; }
  ```
  ```java
  UPDATE distribution SET redistributed = 1 WHERE id = ?
  ```
- 확신도: 확실

---

## 4. AppClock — 시각 고정

### BR-19
- 규칙: `AppClock.now()`는 기본적으로 `2026-09-15 00:00:00` 고정 시각을 반환한다. JVM 속성 `app.clock=system`을 지정해야 실제 시각을 쓴다.
- 근거: `AppClock.java:8-19`
- 근거 코드:
  ```java
  private static final String FIXED = "2026-09-15 00:00:00";
  // …
  String sys = System.getProperty("app.clock");
  if (sys != null && sys.equals("system")) { return LocalDateTime.now().withNano(0); }
  return LocalDateTime.parse(FIXED, FMT);
  ```
- 확신도: 확실
- 비고: 주석 "2024-03 QA 기간 중 임시로 고정. 운영 반영 전 되돌릴 것 (아직 안 됨)". 마감 판정(BR-04·BR-13)·배포 시각이 모두 이 고정 시각 기준으로 동작한다.

---

## 5. 주석만 있음 · 코드로 확인되지 않은 내용

| 내용 | 위치 | 상태 |
|---|---|---|
| `assignment.status` 값: "O=진행 C=마감" | `01-schema.sql:87` | 주석만 있음. 코드는 O·X·C·R·D 5가지를 처리한다. |
| "재배포는 마감 후 7일 이내에만 허용" | `DistributionService:318` | 코드와 다름. 코드는 72시간(3일) 기준(BR-13). 교무 지침 §4.2 원본 미확인. |
| `submission.submitted_at`는 NOT NULL | `01-schema.sql:109` | 스키마는 NOT NULL이지만 서비스·RowMapper에서 null 처리 코드 존재. |
| 동시 채번 충돌 시 동작 | `DistributionDao:54-57` | 추정만 기재. 실행 미확인(BR-07 비고). |

---

## 6. 표본 점검 추천

1. **BR-13 (주석 vs 코드 불일치)** — 재배포 유예 기간이 "7일"(주석 §4.2)인지 "3일(72시간)"(코드)인지 교무 지침 원본으로 확인. 이관 후 서비스에 반영되는 기준이 바뀔 수 있다.
2. **BR-19 (AppClock 고정 시각, 추정 아님 · 이관 영향 최대)** — 운영 환경에서 `app.clock=system` 없이 배포하면 모든 마감 판정이 `2026-09-15` 기준으로 돌아간다. 이관 시 이 플래그 또는 코드 되돌리기 여부를 반드시 확인.
3. **BR-06 (중복 배포 방지, 동시성 추정)** — DB UNIQUE 제약 없이 코드로만 막는다. 동시 요청 시 두 건 저장 가능성이 있다. 이관 시 UNIQUE 제약 추가 또는 비관적 락 도입 여부를 확인.
