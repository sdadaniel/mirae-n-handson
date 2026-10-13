# CROSS-CHECK — 레거시 비즈니스 규칙 (legacy/item-bank-php)

코드에서 직접 추출한 규칙 목록. 주석과 코드가 다를 경우 코드를 기준으로 함.
범위: 문항 검색 조건 조합, 등록 검증, 목록 기본 정렬·제외 조건.

---

## 검색 — 공개 여부 (제외 조건)

### CX-01 검색 대상은 `v_item_public` 뷰 — status = 'A' 인 문항만 노출

`search.php`와 뷰 정의 모두 `v_item_public`을 직접 조회하며, 이 뷰는 `item.status = 'A'`인 행만 포함한다.

```php
// search.php:521-523
$select = "SELECT id, title, unit_code, level, tag_names, created_at";
$from   = " FROM v_item_public";
```
```sql
-- db/mariadb/init/01-schema.sql:52,70
CREATE OR REPLACE VIEW v_item_public AS
...
WHERE i.status = 'A';
```

---

### CX-02 난이도 미지정 시 level < 5 문항만 조회 (최상 난이도 5 제외)

난이도 파라미터가 빈 문자열이면 `AND level < 5` 조건이 추가된다.

```php
// search.php:214-215
if ($level == '') {
    $where .= " AND level < 5";
```

---

## 검색 — 조건 조합

### CX-03 단원 필터는 `unit_code` 정확 일치 (LIKE 아님)

단원 코드는 등호(`=`) 비교. 형식이 달라도 그대로 조회한다.

```php
// search.php:118-120
$where .= " AND unit_code = ?";
$types .= 's';
$values[] = $unit;
```

---

### CX-04 태그 필터는 태그 이름 정확 일치 — 부분 일치 미지원

태그는 `EXISTS` 서브쿼리로 이름(`t.name = ?`) 정확 일치 조회.

```php
// search.php:244-246
$where .= " AND EXISTS (SELECT 1 FROM item_tag it JOIN tag t ON t.id = it.tag_id"
        . " WHERE it.item_id = v_item_public.id AND t.name = ?)";
```

---

### CX-05 난이도 필터는 1~5 정수 정확 일치; 범위 밖 값은 그대로 정수 비교

1~5 패턴 일치 시 `= ?`로 조회, 그 밖의 값도 경고만 추가하고 정수로 변환해 조회한다.

```php
// search.php:217-229
else if (preg_match('/^[1-5]$/', $level)) {
    $where .= " AND level = ?";
    ...
} else {
    $warnings[] = '난이도는 1~5 사이여야 합니다.';
    $where .= " AND level = ?";
    $values[] = (int)$level;
```

---

### CX-06 키워드는 `title` 과 `stem` 양쪽을 `LIKE '%q%'`로 검색

두 컬럼을 OR로 연결.

```php
// search.php:97-101
$like = '%' . $q . '%';
$where .= " AND (title LIKE ? OR stem LIKE ?)";
$types .= 'ss';
```

---

### CX-07 키워드는 100자까지만 사용; 초과분은 잘라내고 경고

```php
// search.php:86-89
if (mb_strlen($q, 'UTF-8') > 100) {
    $q = mb_substr($q, 0, 100, 'UTF-8');
    $warnings[] = '키워드가 너무 길어 100자까지만 사용했습니다.';
```

---

### CX-08 태그 이름은 50자까지만 사용; 초과분은 잘라내고 경고

```php
// search.php:240-243
if (mb_strlen($tag, 'UTF-8') > 50) {
    $tag = mb_substr($tag, 0, 50, 'UTF-8');
    $warnings[] = '태그 이름이 너무 길어 50자까지만 사용했습니다.';
```

---

## 검색 — 기본 정렬

### CX-09 정렬 미지정 시 기본 정렬은 `level DESC, id ASC`

sort 파라미터가 없거나 유효하지 않으면 동일한 ORDER BY를 적용한다.

```php
// search.php:317-319
case '':
    // 기본 정렬: 어려운 문항부터, 같은 난이도면 번호 순
    $orderBy = " ORDER BY level DESC, id ASC";
```

---

### CX-10 `sort=unit` 정렬 시 2차 키는 `level DESC`, 3차 키는 `id ASC`

```php
// search.php:293-299
case 'unit':
    if ($dir === 'desc') {
        $orderBy = " ORDER BY unit_code DESC, level DESC, id ASC";
    } else {
        $orderBy = " ORDER BY unit_code ASC, level DESC, id ASC";
    }
```

---

### CX-11 `sort=title` 및 `sort=id` 의 2차 키는 `id ASC`

```php
// search.php:285-292
case 'title':
    if ($dir === 'desc') {
        $orderBy = " ORDER BY title DESC, id ASC";
    } else {
        $orderBy = " ORDER BY title ASC, id ASC";
    }
```

---

### CX-12 `sort=level` 과 `sort=created` 의 기본 방향은 `desc`; 나머지는 `asc`

sort가 지정되고 dir이 비어 있을 때의 기본 방향.

```php
// search.php:329-331
$dir = ($sort === 'level' || $sort === 'created') ? 'desc' : 'asc';
```

---

### CX-13 한 페이지 20건, 최대 999페이지

```php
// search.php:349
$offset = ($page - 1) * 20;

// search.php:345-347
if ($page > 999) {
    $page = 999;
```

---

## 등록 검증

### CX-14 제목은 5자 이상 200자 이하 (앞뒤 공백 제거 후)

```php
// register.php:49-54
if (mb_strlen($title, 'UTF-8') < 5) {
    $errors[] = '제목은 5자 이상 입력해야 합니다.';
}
if (mb_strlen($title, 'UTF-8') > 200) {
    $errors[] = '제목은 200자를 넘을 수 없습니다.';
```

---

### CX-15 지문(stem)은 필수

```php
// register.php:55-58
if ($stem === '') {
    $errors[] = '지문을 입력해야 합니다.';
}
```

---

### CX-16 단원은 필수 — 목록에 존재하는 ID여야 한다

```php
// register.php:60-68
$unitOk = false;
foreach ($units as $u) {
    if ((string)$u['id'] === $unitId) { $unitOk = true; }
}
if (!$unitOk) { $errors[] = '단원을 선택해야 합니다.'; }
```

---

### CX-17 난이도는 1~5 정수여야 한다

```php
// register.php:70-72
if (!preg_match('/^[1-5]$/', $level)) {
    $errors[] = '난이도는 1~5 사이여야 합니다.';
```

---

### CX-18 태그는 화이트리스트 — 목록에 없는 태그 ID는 무시하고 저장하지 않는다

목록에 없는 ID는 `$validTagIds`에 추가되지 않아 INSERT에 포함되지 않는다.

```php
// register.php:74-80
foreach ($tagIds as $tid) {
    foreach ($tags as $t) {
        if ((string)$t['id'] === (string)$tid) {
            $validTagIds[] = (int)$tid;
        }
    }
}
```

---

### CX-19 등록 직후 status = 'R'(검수중) — 검수 완료 전까지 검색 화면에 노출되지 않는다

```php
// register.php:92-95
$stmt = $conn->prepare(
    "INSERT INTO item (id, unit_id, title, stem, level, status, created_at, updated_at)
     VALUES (?, ?, ?, ?, ?, 'R', NOW(), NOW())"
);
```

---

## 단원 목록

### CX-20 단원 목록의 공개 문항 수는 status = 'A' 인 문항만 카운트 (삭제·검수중 제외)

```sql
-- units.php:16-18
(SELECT COUNT(*) FROM item i WHERE i.unit_id = u.id AND i.status = 'A') AS item_count
```

---

### CX-21 단원 목록 정렬은 grade ASC, code ASC

```sql
-- units.php:18-19
FROM unit u
ORDER BY u.grade ASC, u.code ASC
```
