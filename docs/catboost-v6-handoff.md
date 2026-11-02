# CatBoost v6 확률 공급 계층 인계

## 현재 구현과 범위

`CatBoostV6FeatureAssembler`는 `SimState`와 호출자가 제공한 완료 구수로 `CatBoostV6State`를 만들고, `CatBoostHistoryRepository`에서 2023년 말 동결 이력을 조회합니다. `SelectedPaProbabilityProvider`는 설정에 따라 기존 `Log5PaOutcomeModel` 또는 기존 `CatBoostV6Client`를 호출합니다. 기본 모델은 `log5`이고, 설정 오류나 CatBoost·DB 장애를 Log5로 조용히 대체하지 않습니다.

이는 **독립적인 확률 공급 계층**입니다. 기존 `PaOutcomeModel` Bean을 교체하지 않고, `PlateAppearanceService`, `PlateAppearanceEngine`, `Dice`, `RunnerAdvancement`, `GameSession` 및 `/simulate-pa`를 변경하지 않았습니다. 설정만 바꾸어도 기존 엔진이 자동으로 CatBoost를 쓰지는 않습니다. 엔진 팀이 아래 인터페이스를 호출하도록 연결해야 합니다.

## 입력과 동결 이력

Python `POST /v1/pa/predict-service`에는 이닝·초말·아웃·주자·양 팀 점수·투수·타자·완료된 경기 총 구수와 해당 이닝 구수를 보냅니다. 요청은 2025 경기와 1~9회만 지원합니다. `CompletedPitchCounts`에는 해당 구수의 투수 ID를 명시하며, 현재 투수와 다르거나 누락되면 거부합니다. 가상 투수 교체 후 이전 투수의 카운터를 재사용하지 마세요. v5의 `vsHra`/`todayHra`는 사용하지 않습니다.

타자 타수·안타와 투수 대 타자 타수·안타는 `pa-service-train-history-2016-2023-v1`, 기준 `2024-01-01` 미만입니다. 기존 `batter_game_stats`는 해당 경기 전 2025 시즌, `matchups`는 해당 경기 전 누적 기록이므로 이 동결 이력과 다릅니다. 2024·2025 기록으로 업데이트하지 않습니다. 현재 선수 테이블에 없는 과거 선수도 있으므로 AI 이력 테이블은 `players` FK를 강제하지 않습니다.

Python API는 이력 값을 요청에서 받지 않고 동결 JSON에서 직접 조립합니다. Spring은 DB 이력을 요청에 끼워 넣지 않고, API 응답의 `features` 및 `history_cutoff`를 Spring의 이력과 동적 입력에 비교합니다. 값이 다르면 확률을 사용하지 않습니다. Repository는 조회마다 Snapshot 버전·기준 시점·기록된 해시와 실제 656/95,448행을 확인한 뒤에만 없는 선수·매치업을 0으로 처리합니다. 행 수 검사는 95,448행 테이블을 포함하므로 운영 반복 호출 전에 성능을 측정해야 합니다. DB의 동결 테이블은 승인된 적재 절차 외에는 수정하지 않도록 권한을 제한하세요. 행별 비교는 현재 요청의 선수·매치업에 대해 수행되며 DB 전체를 매 요청마다 다시 해싱하지는 않습니다.

## 안전한 검증과 로컬 DB 적재

`etl/schema/ai/catboost_v6_history.sql`은 기존 `etl/load.sh`의 `schema/*.sql` 대상이 아닙니다. 그 스크립트는 기존 테이블을 `TRUNCATE CASCADE`하므로 이 작업에 사용하지 마세요. 다음 스크립트는 기본적으로 읽기·검증만 합니다. CSV 열·타입·고유 키·행 수·SHA-256, 원본 JSON의 SHA-256 및 양쪽 전체 이력 일치 여부를 확인합니다.

```powershell
$source = 'D:\grad-project\kbo-pitcher-change-ai\outputs\serving\catboost_service_v6'
python -B etl/catboost_v6_history.py --source $source
```

검증 후 별도로 준비한 **로컬 테스트 DB**에만 명시적으로 적재합니다. `psql`이 PATH에 없으면 실행 파일 경로를 `--psql`에 지정합니다. DB 인증은 환경에 맞는 안전한 PostgreSQL 인증 설정을 사용하며 암호를 저장소나 명령 이력에 넣지 마세요.

```powershell
python -B etl/catboost_v6_history.py --source $source --load `
  --database testdb --user test_user --port 5433 `
  --psql 'C:\Program Files\PostgreSQL\18\bin\psql.exe'
```

`testdb`, `test_user`는 실제 테스트 DB 이름과 사용자로 바꿉니다. 적재는 `127.0.0.1`에만 연결하고 스키마 생성·Snapshot·두 테이블을 하나의 트랜잭션으로 처리합니다. 동일 버전이 있으면 덮어쓰지 않고 실패하며 이전 데이터는 유지됩니다. 로컬 포트를 운영 DB에 터널링한 상태에서 실행하지 마세요. 운영 RDS 적재는 이 스크립트의 지원 범위가 아닙니다. 모델 JSON과 CSV 모두 별도 전달이 필요하며 이 저장소에는 포함하지 않습니다.

## 팀원 엔진 연결 예시

`PA_MODEL=log5`가 기본이며, 독립 계층에서 v6를 선택할 때 `PA_MODEL=catboost-v6`, `AI_BASE_URL`, `AI_TIMEOUT_MS`를 설정합니다. 실제 AI 서버 배포는 별도입니다. 해당 설정은 아직 기존 엔진을 전환하지 않습니다.

```java
// 확률 호출 직전의 일관된 상태와 해당 투수의 실제/가상 완료 구수를 함께 확보합니다.
SimState current = session.current();
CompletedPitchCounts counts = new CompletedPitchCounts(
        current.currentPitcherId(), completedTotal, completedInning);
Map<PaOutcome, Double> probabilities = probabilityProvider.probabilities(
        session.gameId(), current, counts);
// 그 다음 기존 Dice로 결과를 뽑고 RunnerAdvancement를 실행합니다.
```

팀원은 `PlateAppearanceService`의 확률 획득 위치에서 `PaProbabilityProvider`를 호출하고, `PlateAppearanceEngine`의 PA 완료 처리에 구수 갱신을 연결해야 합니다. 결과별 `pitch_count_policy.json`(`pa-pitches-by-web-outcome-train2016-2023-v1`)의 학습 기간 경험분포에서 가상 구수를 표본 추출해 현재 투수의 총 구수와 이닝 구수에 더합니다. 새 투수의 0/0은 이번 경기 첫 등판임이 확인된 경우에만 사용합니다. 이닝 전환, 투수 교체, undo 시 투수별 상태와 난수 상태를 일관되게 복원해야 합니다. 미래 실제 PA의 구수를 복사하거나 현재 평균 구수를 정답인 것처럼 전달하지 마세요. 이 저장소에는 결과별 구수 샘플러나 경기 진행 규칙을 새로 구현하지 않았습니다.

## 검증과 한계

`CatBoostFeatureIntegrationTest`의 실제 DB·FastAPI 검증은 `KBO_AI_DB_TEST_URL`, `KBO_AI_DB_TEST_USER`, `KBO_AI_INTEGRATION_URL`을 지정할 때만 실행됩니다. 테스트 DB는 데이터 삭제 후 롤백을 포함하므로 **별도 테스트 DB**만 사용해야 합니다. 필요한 변수가 없으면 해당 검증은 건너뜁니다.

```powershell
$env:KBO_AI_DB_TEST_URL = 'jdbc:postgresql://127.0.0.1:5433/testdb'
$env:KBO_AI_DB_TEST_USER = 'test_user'
$env:KBO_AI_INTEGRATION_URL = 'http://127.0.0.1:8000'
.\gradlew.bat test --tests 'com.nemosdream.be.simulation.ai.*' --rerun-tasks
```

2026-11-02 로컬 검증에서는 별도 PostgreSQL 18 테스트 인스턴스에 동결 이력 656/95,448행을 적재하고, 중복 적재 거부·롤백, 이력 조회·Cold-start, 투수 교체 입력, 서버 응답 대조 및 실제 FastAPI 호출을 확인했습니다. 전체 79개 중 50개가 통과했고 Docker가 없어 기존 Testcontainers 기반 29개는 건너뛰었습니다. 그중 기존 Log5 DB 테스트 2개도 건너뛰었으며, Log5 순수 계산 테스트 6개는 통과했습니다. 테스트 서버는 종료하고 임시 DB 디렉터리는 정리했습니다. PostgreSQL 16 컨테이너 및 실제 운영 환경을 검증한 결과는 아닙니다.

CatBoost 모델은 `catboost-pa-service-v6-b-7e1e4a64634c`이며 2025 독립 테스트 log loss는 1.788382, Log5 재구성은 1.782328입니다. v6가 더 정확하다고 주장할 수 없습니다. 9종 확률, 버전, 병살 불가능 상태와 합계는 클라이언트가 검사합니다. 전체 경기 반복 `/repeat`는 구현하지 않았으며 담당자는 확정되지 않았습니다.
