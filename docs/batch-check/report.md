# 배치 · 데이터마트 점검 리포트

**점검 기간 2026-09-08 ~ 2026-09-17(10일), 이상 7건(4개 날짜)**

- 대상: `pipeline-samples/batch-logs/*.log`, `pipeline-samples/datamart-counts.csv`
- 기준: ① 실행 여부(누락 · 시작만 있고 종료 없음) ② 같은 날 같은 job 성공 2회 이상 ③ 적재 건수가 직전 7일 평균에서 ±30% 초과 또는 0건 ④ 소요 시간이 평소의 2배 초과
- 계산 방법: 스크립트로 계산했다(암산 아님).
  - 건수: CSV 에서 그 job 의 직전 행 최대 7개 평균과 비교했다.
  - 소요 시간: START~SUCCESS 타임스탬프 차이를 그 job 의 다른 날 중앙값과 비교했다.
- 원인은 로그에 근거가 없으면 "확인 필요"로 두었다. "영향 추정"은 로그 · CSV 에서 읽히는 범위만 적었다.
- 학생 식별자와 운영자 이메일은 적지 않았다.

## 이상 목록

| 날짜 | job | 점검 항목 | 근거 | 영향 추정 | 다음 확인 사항 |
|---|---|---|---|---|---|
| 2026-09-09 | build_item_stats | ① 실행 여부: 시작만 있고 종료 기록 없음 | `2026-09-09.log:2` `status=START` → `:4` `RUNNING progress=25% rows=3109` 뒤로 이 job 의 줄 없음. CSV 에 `2026-09-09,build_item_stats` 행 없음(이날 행 4개) | `dm.item_stats_daily` 에 09-09치가 없거나 일부만 들어갔을 수 있다(추정). 원인 확인 필요 | 00:30:57 이후 실행 서버의 프로세스 · 시스템 로그(pid=40655) 확인, `build_item_stats.lock` 이 그 뒤 어떤 상태였는지 확인, 09-09치 재처리 이력과 대상 테이블 건수 조회 |
| 2026-09-12 | export_grades | ④ 지연 | `2026-09-12.log:16` `elapsed=963s`. 다른 날 중앙값 319s 의 3.02배. 이 job 의 WARN · ERROR 없음 | 다음 job 시작(01:30) 전에 끝났고(01:16:04) 건수도 정상(CSV 22행, 8257)이라 이후 job 에 준 영향은 로그상 보이지 않는다. 원인 확인 필요 | 이 실행의 step 별 소요 시간(`:10`~`:16`) 확인, 그 시각 원천 DB 부하 · 락 대기 지표 확인 |
| 2026-09-15 | extract_submissions | ④ 지연 | `2026-09-15.log:20` `elapsed=1668s`. 다른 날 중앙값 659s 의 2.53배. `:19` WARN `note="source read slow: grade-db 10.20.2.41 replica lag 412s"` | 후속 load_datamart 가 cron 대로 02:00 에 시작했다(`:21`). 지연과 그 뒤 지연 · 중복 사이에 관계가 있는지는 확인 필요 | 그 시각 grade-db 복제 지연 지표를 보고, 로그에 적힌 replica lag 와 소요 시간 증가가 이어지는지 확인 |
| 2026-09-15 | load_datamart | ② 중복 실행 | 같은 날 SUCCESS 2회: `2026-09-15.log:40` (`run_id=ldm-20260916-0200-edf5`, cron), `:42` (`run_id=ldm-20260916-0258-8963`, manual). `:30` force 재실행 요청, `:31` `lock=held_by ... action=ignored reason="--force"`, `:43` `lock=release_skipped reason="not owner (bypassed)"` | 같은 날짜 데이터가 두 번 적재됐을 수 있다. 바로 아래 행의 건수 2배와 맞아떨어진다(추정) | 적재 대상 테이블(`dm.daily_submission_fact`)에서 09-15치 중복 행이 있는지 직접 조회, 그 뒤 락 파일 상태 확인, `--force` 재실행 절차 확인 |
| 2026-09-15 | load_datamart | ④ 지연 | 첫 실행 `2026-09-15.log:40` `elapsed=4357s`. 다른 날 중앙값 901s 의 4.84배. `:27` `elapsed 40m exceeds p95 17m`, `:28` sla-monitor `status=NOT_FINISHED sla=02:45` | SLA 02:45 를 넘겼고, 이 SLA 알림 뒤에 수동 재실행이 이어졌다(`:30`). 첫 실행이 왜 느렸는지는 확인 필요 | 첫 실행의 step 별 로그와 DB 쪽 락 · 세션 지표 확인, 02:47 이후 진행률(`:29` 64%)이 어떻게 이어졌는지 확인 |
| 2026-09-15 | load_datamart | ③ 적재 건수 | CSV 39행 `2026-09-15,load_datamart,369064`. 직전 7일 평균 184617.4 대비 +99.9%. 두 실행이 각각 `rows=184532`(`:40`, `:42`), 합 369064(명령으로 계산). `:47` dq-check `deviation=+99%` | 이 테이블을 입력으로 쓰는 build_report_cache 가 `input_rows=369064` 로 실행됐다(`:45`). 리포트 캐시에 영향이 갔을 수 있다(추정). 다만 그 CSV 건수는 1271 로 정상 범위였다 | build_report_cache 09-15 결과를 정상일과 비교, CSV `row_count` 가 실행별 합계인지 테이블 실제 건수인지 집계 방식 확인 |
| 2026-09-17 | build_item_stats | ③ 적재 건수 | CSV 46행 `2026-09-17,build_item_stats,7284`. 직전 7일 평균 12453.1 대비 −41.5%. 로그 `2026-09-17.log:8` `SUCCESS rows=7284`, WARN · ERROR 없음 | `dm.item_stats_daily` 의 09-17치가 평소보다 적다. 원천 데이터가 실제로 적었는지, 빠진 것인지는 확인 필요 | 원천 `itembank.item_response` 의 09-17 건수를 조회해 7284 와 비교, 같은 날 extract_submissions(42360, 정상 범위)와 맞춰 보기 |

## 이상 없음으로 판정한 날짜

- 2026-09-08 (이전 데이터가 없어 ③ 적재 건수는 판정하지 않음)
- 2026-09-10
- 2026-09-11
- 2026-09-13
- 2026-09-14
- 2026-09-16

> 참고: 모든 날짜의 load_datamart 에 `step=validate skipped=1 reason="class_id is NULL"` WARN 이 반복된다(같은 학생 1명, 예: `2026-09-08.log:22`). 네 기준 어디에도 해당하지 않아 이상 목록에서 뺐다.
