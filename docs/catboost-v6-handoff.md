# CatBoost v6 클라이언트 연결 계약

`CatBoostV6Client`와 `CatBoostV6State`는 독립적인 호출 경계입니다. `PaOutcomeModel`, Log5 및 기존 타석 엔진을 변경하거나 모델을 자동 선택하지 않습니다. 이 코드는 `/simulate-pa`에 연결되지 않았으며 `app.ai.base-url`(기본 `http://localhost:8000`), `app.ai.timeout-ms`(기본 5000)로 호출 주소와 연결/요청 제한 시간을 설정합니다. 서버 오류, 응답 오류 또는 timeout은 예외로 전달합니다. 자동 Log5 전환은 구현하지 않았습니다.

```java
// completedTotal / completedInning은 현재 투수의 PA 전 완료 구수여야 합니다.
CatBoostV6State features = CatBoostV6State.from(session.gameId(), session.current(),
                                               completedTotal, completedInning);
Map<PaOutcome, Double> probabilities = catBoostV6Client.probabilities(features);
// 기존 엔진의 Dice와 RunnerAdvancement로 이어서 처리합니다.
```

현재 `PitcherDay`에는 이닝 구수가 없으며 분기 후 평균 구수를 더하는 기존 방식은 v6의 학습 의미와 다릅니다. 호출자가 투수별 완료 누적/이닝 구수를 별도로 보유하고, PA 결과별 frozen 구수 분포를 샘플링해 갱신해야 합니다. 이닝 변경, 투수 변경, undo와 난수 상태의 처리는 시뮬레이션 엔진 담당자와 협의가 필요합니다. 미래 실제 PA의 구수를 사용하지 마세요.

AI는 `model_version=catboost-pa-service-v6-b-7e1e4a64634c`, `feature_schema_version=pa-service-v6-b-before-pitch-v1`, `mapping_version=direct-web9-spring-gdp-v1`이어야 하며, 클라이언트는 9개 확률의 범위·합과 병살 불가능 상태를 검증합니다. 2025 경기와 1~9회만 지원합니다. 2023년 말 동결 타자/매치업 이력은 Python 모델이 직접 조회하며 Spring의 최신 이력으로 대체하지 않습니다. v6와 Log5는 독립 테스트에서 각각 log loss 1.788382와 1.782328이므로 v6가 더 정확하다고 주장하지 않습니다.

`CatBoostV6ClientTest`는 로컬 가짜 HTTP 서버에서 요청, 확률, 버전을 검사합니다. 환경변수 `KBO_AI_INTEGRATION_URL`이 설정된 경우 실제 FastAPI도 호출합니다. 이 테스트는 실제 Spring DB, 세션 진행 및 전체 경기 반복을 검증하지 않습니다. 기존 `feat/#25-simulate-pa`는 수정하지 않았습니다. 모델 선택, 누락 구수 상태의 추가, 선택에 따른 Log5 전환, `/repeat` 구현은 별도 후속 작업입니다.
