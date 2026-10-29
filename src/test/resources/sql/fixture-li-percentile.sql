-- 테스트용 li_percentile: 0.00 ~ 10.00 전 구간, 백분위 = 인덱스 / 10 (0.00→0, 2.41→24, 10.00→100)
INSERT INTO li_percentile (li_value, percentile)
SELECT i / 100.0, i / 10 FROM generate_series(0, 1000) AS i;
