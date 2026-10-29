-- li_percentile (명세 v0.3 §3-5 /recommend 의 li_percentile, "리그 상위 X%" 표시)
-- LI 값(0.01 간격) → bandit train 구간 타석 중 그보다 LI 가 낮았던 비율(%). 생성: etl/06_li_percentile.py
-- li_value 는 NUMERIC 이라 0.01 단위가 정확히 저장된다 (REAL 키는 2.41 이 2.4099999 가 될 수 있다).
CREATE TABLE IF NOT EXISTS li_percentile (
    li_value    NUMERIC(4, 2)   PRIMARY KEY,    -- 0.00 ~ 10.00
    percentile  SMALLINT        NOT NULL        -- 0 ~ 100, 화면은 "리그 상위 (100 - percentile)%"
);
