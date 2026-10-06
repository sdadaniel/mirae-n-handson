# 장애 분석: b-deploy-5xx

> **작성 범위:** 1절 수집 범위 · 2절 타임라인 · 3절 가설 표(3.1~3.3)까지만 작성했다.
> 로그 반증 실행 · 코드 대조 · 판정(4단계)과 요약 · 원인 · 수정안 · 재발 방지 · 모니터링 · 확인하지 못한 것(리포트 3~8)은 **아직 쓰지 않았다**. 그래서 이 문서는 원인을 말하지 않는다.

대상 로그: `incident-logs/b-deploy-5xx/`

## 1. 수집 범위

> 이 절은 분석할 시간 범위만 정한다. 원인은 다루지 않는다.
> 파일은 통째로 읽지 않고 `wc` · `head` · `tail` · `grep` · `awk` · `sed -n` 으로 필요한 줄만 집계했다.

### 1.1 파일별 기록 기간 · 시각 형식

| 파일 | 줄 수 | 기록 기간 (KST) | 시각 형식 | 타임존 |
|---|---|---|---|---|
| `app.log` | 2,938 (타임스탬프 줄 1,275 = INFO 1,194 · WARN 35 · ERROR 46, 나머지 1,663 은 스택트레이스) | 2026-09-17 09:00:04 ~ 14:39:57 | ISO-8601, 밀리초 · 오프셋 포함 `2026-09-17T13:46:38.107+09:00` | KST (`+09:00`) |
| `nginx-access.log` | 20,365 | 2026-09-17 09:00:00 ~ 14:39:58 | nginx combined `[17/Sep/2026:13:46:38 +0900]`, 초 단위 | KST (`+0900`) |
| `deploy-history.md` | 39 | 2026-09-01 ~ 2026-09-17 배포 4건 | `2026-09-17 13:40:05`, 초 단위, 오프셋 없음 | KST — 문서 머리말에 "시각은 모두 KST" 라고 적혀 있다(`deploy-history.md:3`) |

**타임존 교차 확인** — 세 파일 모두 KST 라고 밝히고 있지만, 같은 사건으로 한 번 더 맞췄다.

- 재배포 실패 1건: `nginx-access.log:17155` 13:46:38 `POST …/42/redistribute 500` ↔ `app.log:1275` 13:46:38.113 `unhandled exception on /api/distributions/42/redistribute`. 같은 초다.
- 재기동: `app.log:1241` 13:41:50.144 Graceful shutdown complete ~ `app.log:1263` 13:42:00.290 Tomcat started 사이에 nginx 502 8건(13:41:51~59)이 모두 들어간다. 배포 이력의 배포 시작 13:40:05 · 완료 13:42:31(`deploy-history.md:7`)과도 겹친다.
- 그래서 시각 변환은 하지 않는다. 이 문서의 시각은 전부 KST 다.

**로그에 없는 필드** — `nginx-access.log` 는 combined 형식(따옴표 필드 7개가 20,365줄 전부)이라 `$request_time` · `$upstream_addr` · `$upstream_response_time` 이 없다. 응답 시간과 어느 upstream 이 응답했는지는 이 로그로 알 수 없다.

### 1.2 이상 줄 집계

**상태 코드 · 레벨 전체 분포**

| 파일 | 값 | 건수 |
|---|---|---|
| `nginx-access.log` | 200 | 19,278 |
| | 404 | 969 |
| | 409 | 35 |
| | 400 | 29 |
| | **500** | **46** (본문 176 바이트, 전부 같은 크기) |
| | **502** | **8** (본문 157 바이트) |
| `app.log` | ERROR | 46 — 전부 `GlobalExceptionHandler : unhandled exception on …` |
| | WARN | 35 — 전부 `state conflict: 마감된 과제는 재배포할 수 없습니다` (409) |

- 404(없는 문항 · 없는 경로 · favicon 등)와 WARN 409(마감 과제 재배포 시도)는 09:06 부터 14:35 까지 고르게 흩어져 있어 **기준 잡음**으로 분류하고 이상 줄 집계에서 뺀다.

**5xx 분 단위 (nginx)**

| 분 (KST) | 500 | 502 | | 분 (KST) | 500 | 502 |
|---|---|---|---|---|---|---|
| 09:52 | 3 | | | 14:02 | 2 | |
| 10:31 | 1 | | | 14:03 | 4 | |
| 11:17 | 1 | | | 14:06 | 1 | |
| 11:18 | 2 | | | 14:11 | 3 | |
| **13:41** | | **8** | | 14:15 | 1 | |
| 13:46 | 2 | | | 14:16 | 2 | |
| 13:47 | 1 | | | 14:22 | 3 | |
| 13:50 | 4 | | | 14:27 | 2 | |
| 13:52 | 1 | | | 14:28 | 1 | |
| 13:53 | 1 | | | 14:31 | 2 | |
| 13:55 | 3 | | | 14:32 | 1 | |
| 13:58 | 2 | | | 14:34 | 3 | |

나머지 분은 5xx 0건. app.log ERROR 46건의 분 분포는 nginx 500 과 분 단위로 1:1 일치한다.

**5xx 를 묶음으로 나눈 결과** (원인 판단 아님, 모양만 나눈 것)

| 묶음 | 기간 (KST) | 상태 | 건수 | 경로 | 클라이언트 |
|---|---|---|---|---|---|
| ① 배포 전 500 | 09:52:17 ~ 11:18:02 | 500 | 7 | `POST /api/distributions/41/redistribute` 6 · `GET /api/distributions/41` 1 | 교사단말A |
| ② 재기동 502 | 13:41:51 ~ 13:41:59 | 502 | 8 | 여러 경로(문항 · 단원 · 배포 조회, `/`) | 10.20.x.x 8개 단말 |
| ③ 배포 후 500 | 13:46:38 ~ 14:34:45 | 500 | 39 | `POST /api/distributions/42/redistribute` 19 · `…/43/redistribute` 20 | 교사단말A |

**10분 단위 기준선 (nginx 전체 요청 · 5xx · 재배포 요청)**

| 구간 | 전체 | 5xx | redistribute | | 구간 | 전체 | 5xx | redistribute |
|---|---|---|---|---|---|---|---|---|
| 09:00 | 571 | 0 | 2 | | 11:50 | 634 | 0 | 6 |
| 09:10 | 625 | 0 | 1 | | 12:00 | 544 | 0 | 8 |
| 09:20 | 648 | 0 | 1 | | 12:10 | 519 | 0 | 6 |
| 09:30 | 668 | 0 | 3 | | 12:20 | 534 | 0 | 6 |
| 09:40 | 530 | 0 | 3 | | 12:30 | 540 | 0 | 4 |
| 09:50 | 583 | 3 | 7 | | 12:40 | 562 | 0 | 1 |
| 10:00 | 632 | 0 | 3 | | 12:50 | 516 | 0 | 4 |
| 10:10 | 648 | 0 | 2 | | 13:00 | 569 | 0 | 4 |
| 10:20 | 590 | 0 | 3 | | 13:10 | 624 | 0 | 8 |
| 10:30 | 562 | 1 | 4 | | 13:20 | 648 | 0 | 3 |
| 10:40 | 583 | 0 | 2 | | 13:30 | 663 | 0 | 0 |
| 10:50 | 606 | 0 | 6 | | **13:40** | 625 | **11** | 5 |
| 11:00 | 682 | 0 | 4 | | **13:50** | 549 | **11** | **15** |
| 11:10 | 651 | 3 | 4 | | **14:00** | 613 | **7** | 7 |
| 11:20 | 605 | 0 | 1 | | **14:10** | 665 | **6** | 6 |
| 11:30 | 535 | 0 | 3 | | **14:20** | 605 | **6** | 9 |
| 11:40 | 655 | 0 | 3 | | **14:30** | 581 | **6** | 8 |

### 1.3 분석 구간 (이상 구간 · 앞뒤 버퍼)

- **이상 구간:** 13:41:48(재기동 시작) ~ 14:34:45(마지막 500). 5xx 47건(502 8 · 500 39).
- **앞 버퍼:** 13:30 ~ 13:41:47 — 5xx 0건, 재배포 요청도 정상(13:41:20 `…/43/redistribute 200`, `nginx-access.log:16829`).
- **뒤 버퍼: 없다.** 로그가 14:39:58(`nginx-access.log:20365`)에서 끝나고, 14:34:45 이후 5분 동안은 학급 1 재배포 요청 자체가 없다. 장애가 끝났는지(롤백 · 수정 여부)는 이 로그로 판단할 수 없다.
- **별도 관찰 구간:** 09:52 ~ 11:18 의 묶음 ① 500 7건. 배포보다 약 4시간 앞서지만 오류 메시지가 묶음 ③ 과 같아(2.2 참고) 분석 범위에 넣는다.
- 요청량은 10분당 516~682건으로 하루 내내 같은 폭이고, 이상 구간에도 이 폭을 벗어나지 않는다.

### 1.4 사용한 집계 명령

```bash
cd incident-logs/b-deploy-5xx
wc -l app.log nginx-access.log deploy-history.md
head -3 nginx-access.log; tail -2 nginx-access.log; head -2 app.log; tail -3 app.log

# nginx 상태 코드 분포 · 형식 확인(따옴표 필드 수)
awk '{print $9}' nginx-access.log | sort | uniq -c
awk -F'"' '{print NF}' nginx-access.log | sort | uniq -c

# app 레벨 분포
awk '{print $2}' app.log | sort | uniq -c

# nginx 5xx 분 단위
awk '$9>=500{print substr($4,14,5), $9}' nginx-access.log | sort | uniq -c

# app ERROR · WARN 줄번호 · 메시지
awk '/ ERROR /{print NR": "substr($1,12,8)" "substr($0, index($0,": ")+2, 150)}' app.log
awk '/ WARN /{print NR": "substr($1,12,8)" "substr($0, index($0,"] c.")+2, 160)}' app.log

# ERROR 를 (경로 · 없는 엔티티 id · 서비스 스택 프레임)으로 묶기
awk '/JpaObjectRetrievalFailureException/{t=$0; sub(/.*identifier value /,"",t); id=t}
     /unhandled exception on/{u=$NF; ts=substr($1,12,8)}
     /at com.example.assignment.(DistributionService|DistributionController)\./{sub(/^[ \t]*at /,""); f=f" "$0}
     /common frames omitted/{print ts, u, id, f; f=""}' app.log | sort | uniq -c

# 10분 단위 전체 · 5xx · redistribute
awk '{m=substr($4,14,4)"0"; t[m]++; if($9>=500)e[m]++; if($7~/redistribute/)r[m]++}
     END{for(m in t) print m, t[m], e[m]+0, r[m]+0}' nginx-access.log | sort

# 재기동 구간 앞뒤 (app · nginx)
awk '$1>="2026-09-17T13:38" && $1<"2026-09-17T13:47"' app.log
awk '$4>="[17/Sep/2026:13:40:00" && $4<"[17/Sep/2026:13:43:30" && $9!=200 {print NR, $4, $6, $7, $9}' nginx-access.log
```

## 2. 타임라인

### 2.1 읽는 법

- 출처 표기: `a:줄` = `app.log`, `n:줄` = `nginx-access.log`, `d:줄` = `deploy-history.md`. 범위는 `a~b`, 나열은 `·`.
- 정밀도: app 은 밀리초, nginx · 배포 이력은 초. **서로 다른 파일의 사건이 같은 초 안에 있으면 (선후 불확실)** 로 표시한다. 같은 파일 안의 순서는 줄 순서를 따른다.
- 별칭(마스킹 규칙):
  - **API-1** = 배포 대상 호스트(item-bank-api 운영 서버, 이번 v1.4.2 가 올라간 곳).
  - **API-2** = 대기 호스트(평소 정지). 이번 로그에는 등장하지 않는다.
  - **교사단말A** = 이 로그에서 처음 요청한 교사 화면 클라이언트. 재배포(`POST …/redistribute`) 요청 152건 중 125건과 500 46건 전부를 보냈다.
  - **교사단말B** = 두 번째로 처음 요청한 교사 화면 클라이언트. 재배포 요청 27건을 보냈고 5xx 는 0건이다.
  - 나머지 학생 단말 IP 는 `10.20.x.x` 로 적는다.
- 반복 메시지는 첫 발생 줄 + 분당 건수로 적는다. 발췌는 80자 이내, 학생 식별자 · 이메일 · 토큰은 발췌에서 뺐다.

### 2.2 타임라인 (2026-09-17, KST)

| 시각 | 출처 | 사건 | 발췌 |
|---|---|---|---|
| 09:00:00 | n:1 | nginx 로그 시작. 이후 10분당 516~682건 | `"GET /api/units/M5-3/items HTTP/1.1" 200` |
| 09:00:02 · 09:00:04 | n:4 · n:9 | 교사단말A · 교사단말B 첫 요청 | `"GET /api/items/17" 200` · `"GET /api/items/47" 404` |
| 09:06:17 ~ 14:35:26 | a:24 ~ a:2925 (35줄) | WARN 409 마감 과제 재배포 거절, 분당 0~1건, 하루 내내 고르게 분포(기준 잡음) | `state conflict: 마감된 과제는 재배포할 수 없습니다: distributionId=3` |
| 09:07:01 ~ 13:41:20 | a:28 ~ a:1238 (66줄) | 재배포 성공 INFO. 배포 42·43(학급 1) 18건 포함 모두 성공 | `redistributed distribution 43 (assignment 6, class 1)` |
| **09:52:17** | n:3150 · a:194 (선후 불확실) | **첫 이상 신호.** 배포 41 재배포 500 | `POST /api/distributions/41/redistribute HTTP/1.1" 500 176` |
| 09:52:17 | a:195 · a:224 | 예외: 과제(Assignment) id 9 가 없음 | `JpaObjectRetrievalFailureException: Entity …Assignment … value \`9\` does not exist` |
| 09:52:17 | a:205 · a:206 · a:214 | 스택 경로 ① | `DistributionService.loadOrThrow(DistributionService.java:65)` ← `redistribute(:51)` ← `DistributionController.redistribute(:38)` |
| 09:52:17 ~ 09:52:41 | a:194 · a:232 · a:270 / n:3150 · n:3162 · n:3174 | 같은 요청 3회 반복, 분당 3건 | `unhandled exception on /api/distributions/41/redistribute` |
| 10:31:05 | a:451 / n:5563 | 배포 41 단건 조회도 500, 같은 예외(id 9) | `unhandled exception on /api/distributions/41` |
| 11:17:40 ~ 11:18:02 | a:655 · a:695 · a:733 / n:8442 · n:8466 · n:8468 | 배포 41 재배포 500 3회. 묶음 ① 끝(총 7건) | `unhandled exception on /api/distributions/41/redistribute` |
| 13:40:05 | d:7 | v1.4.2 배포 시작(배포자 `[EMAIL]`) | `재배포 응답에 학급 배포 이력(history) 포함` |
| 13:41:20 | n:16829 · a:1238 (선후 불확실) | 재기동 전 마지막 학급 1 재배포, 성공 | `POST /api/distributions/43/redistribute HTTP/1.1" 200 183` |
| 13:41:48.214 | a:1240 | 재기동 시작 | `GracefulShutdown : Commencing graceful shutdown` |
| 13:41:50.144 | a:1241 | 종료 완료 | `Graceful shutdown complete` |
| 13:41:51 ~ 13:41:59 | n:16865 ~ n:16872 (8줄) | **502 8건**, 경로 무관, 10.20.x.x 8개 단말. 분당 8건 | `"GET /api/units HTTP/1.1" 502 157` |
| 13:41:55.902 | a:1245 | v1.4.2 기동 시작 | `Starting ItemBankApplication v1.4.2 using Java 21.0.4` |
| 13:42:00.304 | a:1264 | 기동 완료. shutdown 시작부터 약 12초 | `Started ItemBankApplication in 4.921 seconds` |
| 13:42:10.447 | a:1266 | 재기동 후 첫 재배포(배포 7, 학급 3) 성공 | `redistributed distribution 7 (assignment 5, class 3)` |
| 13:42:31 | d:7 | 배포 이력상 "배포 완료" | `2026-09-17 13:42:31` |
| **13:46:38.107** | a:1274 | 배포 42(학급 1) 재배포 INFO — 이 줄은 남는다 | `redistributed distribution 42 (assignment 4, class 1)` |
| 13:46:38.113 | a:1275 / n:17155 (선후 불확실) | **6 ms 뒤 ERROR, 배포 후 첫 500.** 같은 예외(id 9) | `unhandled exception on /api/distributions/42/redistribute` |
| 13:46:38 | a:1276 · a:1286 · a:1294 · a:1304 | 스택 경로 ② — 경로 ① 과 다름 | `DistributionService.listByClass(DistributionService.java:38)` ← `DistributionController.redistribute(:40)` |
| 13:50:12.717 / .723 | a:1395 · a:1396 / n:17365 (선후 불확실) | 배포 43(학급 1)도 같은 모양. INFO 뒤 6 ms 에 ERROR | `redistributed distribution 43 (assignment 6, class 1)` |
| 13:46 ~ 14:34 | a:1275 ~ a:2887 (39건) / n:17155 ~ n:20084 | 배포 42 19건 · 43 20건 500, 분당 1~4건. 39건 모두 INFO "redistributed" 직후 6 ms 에 ERROR | `unhandled exception on /api/distributions/43/redistribute` |
| 13:50:00 · 13:56:43 등 | a:1393 · a:1754 등 | 같은 기간 학급 2 · 3 재배포(배포 44 · 8 · 5 · 7)는 성공 | `redistributed distribution 44 (assignment 5, class 2)` |
| 14:34:45 | a:2887 / n:20084 (선후 불확실) | 마지막 500 | `POST /api/distributions/43/redistribute HTTP/1.1" 500 176` |
| 14:39:57 · 14:39:58 | a:2938 · n:20365 | 로그 끝. 롤백 · 재기동 흔적 없음 | `no handler for /favicon.ico` |

**재배포 요청 집계 (nginx, 재기동 전 / 후)**

| 배포 id | 학급 (app INFO 기준) | 재기동 전 | 재기동 후 |
|---|---|---|---|
| 41 | 확인 불가 | 500 × 6 | 요청 없음 |
| 42 | 1 | 200 × 5 | **500 × 19** |
| 43 | 1 | 200 × 13 | **500 × 20** |
| 44 | 2 | 200 × 17 | 200 × 1 |
| 8 | 2 | 200 × 8 | 200 × 1 |
| 5 · 6 · 7 | 3 | 200 × 4 · 9 · 10 | 200 × 2 · — · 2 |
| 1 · 2 · 3 | — | 409 × 12 · 9 · 10 | 409 × — · 2 · 2 |

혼동 주의: `GET /api/distributions/9 → 404`(n:16800 등, "배포가 없습니다: id=9")는 **배포** 9 이고, 500 예외의 `Assignment identifier 9` 는 **과제** 9 다. 서로 다른 엔티티다.

### 2.3 이 로그로 확인할 수 없는 것

- **배포 41 의 학급.** 배포 41 은 `loadOrThrow` 에서 실패해 "redistributed distribution 41 (…, class N)" INFO 가 한 번도 남지 않았다. 학급 1 소속인지 로그로는 알 수 없다.
- **과제 9 가 언제 · 왜 없어졌는지.** 로그 시작(09:00) 전에 이미 없었는지, 09:00~09:52 사이에 지워졌는지 알 수 없다. DB 로그(binlog · general log)가 없다.
- **배포 42 · 43 재배포가 500 이 나기 전에 커밋됐는지.** INFO "redistributed" 는 남았지만 트랜잭션 경계 · 커밋 · 롤백 로그가 없다. 39회 반복 요청으로 재배포가 중복 적용됐는지도 알 수 없다.
- **재기동 전 버전.** app.log 첫 부분에 기동 줄이 없어 v1.4.1 이었다는 것은 배포 이력(d:8)에만 근거한다. 스택의 `DistributionController` 줄번호가 :38 → :40 으로 바뀐 것은 코드가 바뀌었다는 정황일 뿐이다.
- **응답 시간 · upstream.** nginx 에 `$request_time` · `$upstream_addr` 가 없다. 502 가 API-1 재기동 때문인지, 다른 upstream 이 있었는지 nginx 로는 확인할 수 없고 nginx error.log 도 없다.
- **교사단말A 의 반복 요청이 사람의 재시도인지 화면의 자동 재시도인지.** User-Agent · 요청 간격만으로는 단정할 수 없다.
- **배포 시각 차이.** app 기준 기동 완료 13:42:00(a:1264)과 배포 이력의 완료 13:42:31(d:7)이 31초 다르고, 중단 시간도 이력의 "예상 10초"(d:18)보다 긴 약 12초다. 배포 파이프라인 로그가 없어 차이의 이유는 알 수 없다.
- **장애 종료 여부.** 14:34:45 뒤로 학급 1 재배포 요청이 없고 로그가 14:39:58 에 끝난다.

## 3. 가설과 검증

### 3.1 증상과 원인을 나누기

- **증상 1 (502 8건):** 13:41:51~59 에 경로와 상관없이 502.
- **증상 2 (500 46건):** 전부 `JpaObjectRetrievalFailureException: Entity Assignment … 9 does not exist`. 배포 전 7건(배포 41, 스택 ①)과 배포 후 39건(배포 42 · 43, 스택 ②)으로 나뉜다.
- "500 이 가장 많다" · "배포 직후 터졌다" 는 둘 다 증상이다. 첫 이상 신호는 배포보다 약 4시간 앞선 09:52:17(n:3150 · a:194)이므로, 원인 후보는 배포 변경 하나로 좁히지 않고 계층별로 세운다.

### 3.2 가설 표

| # | 계층 | 가설 | 지지 근거 (타임라인 줄) | 반증 조건 (이게 보이면 틀림) | 확인 방법 |
|---|---|---|---|---|---|
| H1 | 코드 · 배포 | v1.4.2 가 재배포 뒤에 `listByClass` 로 **학급 전체 배포 목록**을 읽게 바뀌었고, 그 목록에 과제 9 를 참조하는 행이 섞여 있어 학급 1 재배포가 모두 500 이 됐다. 재배포 자체는 그 전에 끝난다 | a:1274 → a:1275 (INFO 뒤 6 ms 에 ERROR, 39건 모두) · a:1286 · a:1294 (스택 ② `listByClass` ← `Controller.redistribute(:40)`) · d:16 (변경 지점: 재배포 후 `listByClass(classId)` 호출 추가) · 재배포 집계표(42 · 43 은 재기동 전 200 → 후 500) | ① 재기동(a:1245) **전**에도 `listByClass` 프레임이 있는 ERROR 가 있다 ② 재기동 후 학급 2 · 3 재배포(배포 44 · 8 · 5 · 7)도 같은 예외로 500 ③ 재기동 후 학급 1 재배포 중 200 이 1건이라도 있다 | `awk` 로 ERROR 묶음별 스택 프레임 · 줄번호 범위 비교(C1) · 재배포 id × 상태 × 재기동 전후 집계(C2). 이후 `DistributionController.redistribute` · `DistributionService.listByClass` 와 v1.4.1 대비 diff 대조 |
| H2 | 데이터 (DB) | 과제 9 가 없는데 이를 참조하는 배포 행(최소 배포 41)이 남아 있는 **고아 참조**가 배포 전부터 있었다. 배포는 이 데이터를 더 많은 요청이 읽게 만들었을 뿐이다 | n:3150 · a:194 · a:195 (09:52, 배포 4시간 전 같은 예외 id 9) · a:205 (`loadOrThrow`) · n:5563 · a:451 (단건 조회도 500) · 배포 전후 예외 메시지가 같음(id 9, 46건 전부) | ① 과제 9 가 DB 에 있다 ② 배포 41 이 과제 9 를 참조하지 않는다 ③ 학급 1 배포 행 중 과제 9 를 참조하는 행이 없다 ④ 배포 전 ERROR 의 없는 id 가 9 가 아니다 | 로그: ERROR 별 `identifier value` 값 집계(C3). DB: **로컬 compose DB(`readonly` 계정)** 에서 배포 → 과제 LEFT JOIN 으로 고아 행 조회(C4). 운영 데이터는 DBA 에게 같은 쿼리를 요청 — 운영 DB 에 직접 접속하는 명령은 만들지 않는다 |
| H3 | 인프라 (배포 방식) | 502 8건은 단일 호스트(API-1) 무중단 아닌 재기동의 공백(약 12초)이고, 500 과는 독립된 증상이다 | a:1240 · a:1241 (13:41:48~50 종료) · n:16865 ~ n:16872 (502, 13:41:51~59) · a:1263 · a:1264 (13:42:00 기동) · d:18 (단일 호스트 재기동, 예상 중단 10초) | ① 502 가 13:41:48 ~ 13:42:00 밖에도 있다 ② 502 응답 직후 같은 요청이 500 으로 이어진다 ③ 재기동 공백 안에 app 이 요청을 처리한 줄이 있다 | 502 줄의 시각 범위 · 경로 · 직후 같은 단말 요청 상태 집계(C5). 재기동 구간 app 줄 확인(C6) |
| H4 | 트래픽 · 클라이언트 | 요청량 증가나 한 단말의 반복 재시도가 장애를 만들었다(또는 키웠다) | 500 46건 전부 교사단말A · 배포 42 · 43 을 19 · 20회 반복 · 13:50 구간 재배포 요청 15건(하루 중 최대) | ① 10분당 전체 요청이 기준선(516~682)을 벗어나지 않는다 ② 첫 시도부터 500 이다(재시도가 원인이 아니라 결과) ③ 교사단말B 가 같은 배포를 요청해도 500 | 10분 · 1분 단위 요청량 비교(C7), 교사단말A 의 배포 42 · 43 첫 시도 상태와 시도 간격(C8) |

### 3.3 확인 명령

> 4단계에서 실행할 명령이다. **이번에는 실행 결과를 판정에 쓰지 않았다.** `<교사단말A>` 는 원본 IP 자리표시자다(원본 값은 리포트에 적지 않는다).

```bash
cd incident-logs/b-deploy-5xx

# C1 (H1 ①) ERROR 묶음별 서비스 · 컨트롤러 스택 프레임과 첫/끝 줄번호
awk '/unhandled exception on/{s=NR; u=$NF}
     /at com.example.assignment.(DistributionService|DistributionController)\.[a-zA-Z]+\(/{sub(/^[ \t]*at /,""); f=f" "$0}
     /common frames omitted/{print s, u, f; f=""}' app.log \
  | awk '{k=$3" "$4; if(!(k in a)) a[k]=$1; b[k]=$1; c[k]++} END{for(k in c) print c[k], a[k]"~"b[k], k}'

# C2 (H1 ②③ · H4 ③) 재배포 id × 클라이언트 × 상태, 재기동(n:16865) 전/후
awk '$6=="\"POST" {split($7,p,"/"); ph=(NR<16865)?"pre":"post"; print ph, $1, p[4], $9}' nginx-access.log \
  | sort | uniq -c

# C3 (H2 ④) ERROR 별 없는 엔티티 · id
grep -o 'Entity `[^`]*` with identifier value `[0-9]*`' app.log | sort | uniq -c

# C4 (H2 ①②③) 로컬 compose DB 전용 — 운영 DB 아님. 운영 값은 DBA 에게 같은 쿼리를 요청
docker compose --profile modern up -d
docker compose --profile modern exec mariadb \
  mariadb -ureadonly -p[SECRET] itembank -e "
    SELECT d.id, d.class_id, d.assignment_id
      FROM distribution d LEFT JOIN assignment a ON a.id = d.assignment_id
     WHERE a.id IS NULL OR d.id = 41 OR d.assignment_id = 9;"
# (테이블 · 컬럼명은 db/mariadb/init/ 스키마로 4단계에서 확정한다)

# C5 (H3 ①②) 502 시각 범위 · 경로, 같은 단말의 직후 요청 상태
awk '$9==502{print NR, $4, $7}' nginx-access.log
awk '$9==502{ip[$1]=NR} NR>16872 && ($1 in ip) && !($1 in seen){seen[$1]=1; print NR, $4, $7, $9}' nginx-access.log

# C6 (H3 ③) 재기동 공백 안 app 줄
awk '$1>="2026-09-17T13:41:48" && $1<"2026-09-17T13:42:01"' app.log | cut -c1-140

# C7 (H4 ①) 1분 단위 요청량, 13:30~14:40
awk '$4>="[17/Sep/2026:13:30" && $4<"[17/Sep/2026:14:40"{print substr($4,14,5)}' nginx-access.log | sort | uniq -c

# C8 (H4 ②) 교사단말A 의 배포 42 · 43 재배포 시도 순서 · 상태 · 시각
awk '$1=="<교사단말A>" && $7 ~ /\/(42|43)\/redistribute/ {print NR, substr($4,14,8), $7, $9}' nginx-access.log
```

---

마스킹 적용: IP 20건(별칭 API-1 · API-2 · 교사단말A · 교사단말B 17 · `10.20.x.x` 3) · 이메일 1건 · 학생 식별자 0건(발췌에서 뺌) · 토큰 0건(배포 명령의 인증 토큰 줄은 인용하지 않음) · 비밀번호 · 접속 문자열 1건
