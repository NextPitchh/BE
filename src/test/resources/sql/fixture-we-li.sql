-- WeLiLookupServiceTest 용. 값은 etl/output/we_li_table.csv 에서 그대로 가져옴
INSERT INTO we_li_table (inning, half, out_count, base_state, score_diff, we, leverage_index) VALUES
    (9, 'bot', 2, '123', -4, 0.144341, 3.376431),
    (9, 'bot', 2, '123', -1, 0.461695, 9.793598),
    (9, 'bot', 2, '123',  1, 1.000000, 0.000000),
    (5, 'top', 0, '0',    4, 0.938700, 0.158963);
INSERT INTO we_li_table VALUES (5, 'top', 1, '0', 4, 0.955000, 0.120000);

-- 나머지 상황은 전부 WE 0.5, LI 1.0 으로 채운다. WeLiLookupService 는 표를 한 번 읽어 캐시하므로,
-- 이 fixture 를 쓰는 모든 테스트가 같은 완전한 표를 보게 해야 테스트 순서와 상관없이 결과가 같다.
INSERT INTO we_li_table
SELECT i, h, o, b, d, 0.5, 1.0
FROM generate_series(1, 9) AS i,
     (VALUES ('top'), ('bot')) AS hh(h),
     generate_series(0, 2) AS o,
     (VALUES ('0'), ('1'), ('2'), ('3'), ('12'), ('13'), ('23'), ('123')) AS bb(b),
     generate_series(-4, 4) AS d
ON CONFLICT DO NOTHING;
