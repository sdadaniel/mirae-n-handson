# assignment-thymeleaf ERD

근거: `db/mariadb/init/01-schema.sql:75~115`. 분석 범위: 배포 폼과 그 호출 코드가 닿는 테이블만.

---

## 1. 테이블 정의

### class
| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| id | INT | PK | |
| name | VARCHAR(50) | NOT NULL | |
| teacher_id | VARCHAR(20) | NOT NULL | 표준 형식 `teacher-XXXX` (코드만, 스키마 제약 없음) |

### assignment
| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| id | INT | PK | |
| title | VARCHAR(200) | NOT NULL | |
| unit_id | INT | NOT NULL, FK→unit.id | |
| due_at | DATETIME | NOT NULL | |
| status | CHAR(1) | NOT NULL DEFAULT 'O' | 스키마 주석: "O=진행 C=마감". 코드는 O·X·C·R·D 5가지를 처리한다(ARCHITECTURE.md 4절) |

### distribution
| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| id | INT | PK | AUTO_INCREMENT 없음. `MAX(id)+1` 채번 |
| assignment_id | INT | NOT NULL, FK→assignment.id | |
| class_id | INT | NOT NULL, FK→class.id | |
| distributed_at | DATETIME | NOT NULL | |
| redistributed | TINYINT | NOT NULL DEFAULT 0 | 0=배포, 1=재배포됨 |

`(assignment_id, class_id)` UNIQUE 제약 없음. 중복 방지는 `DistributionDao.countByAssignmentAndClass()` 코드로만 한다.

### submission
| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| id | INT | PK | |
| distribution_id | INT | NOT NULL, FK→distribution.id | |
| student_id | VARCHAR(20) | NOT NULL | 표준 형식 `STU-XXXX`(스키마 주석), 8자 고정(코드 검증) |
| submitted_at | DATETIME | NOT NULL | 스키마는 NOT NULL이지만 `DistributionService`와 RowMapper는 null을 처리한다 |
| score | DECIMAL(5,1) | NULL | 0~100 제약 없음. 범위 검사는 `DistributionService:443` 에서만 |

---

## 2. 관계

```
unit ←────────── assignment ←────────── distribution ──────────→ class
                                              │
                                              └──────────────── submission
```

---

## 3. 읽기·쓰기 위치 (배포 폼 범위)

| 테이블 | 읽기 | 쓰기 |
|---|---|---|
| class | `ClassDao.findAll()` · `ClassDao.findById()` | 없음 |
| assignment | `AssignmentDao.findAllForSelect()` · `findById()` · `findAllForList()` | 없음 |
| distribution | `DistributionDao.findById()` · `findAllForList()` · `countByAssignmentAndClass()` · `nextId()` | `insert()` · `markRedistributed()` |
| submission | `DistributionDao.findSubmissions()` | 없음 |
| unit | `AssignmentDao`의 JOIN으로 읽음 (`unit_code`, `unit_name`) | 없음 |

---

## 4. UPDATE 없는 테이블

`class` · `assignment` · `submission` 테이블에는 이 모듈이 쓰는 UPDATE가 없다.
`distribution` 의 UPDATE는 `markRedistributed()`(재배포 플래그 `0→1`) 한 종류뿐이다.
