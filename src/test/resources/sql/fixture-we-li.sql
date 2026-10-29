-- WeLiLookupServiceTest 용. 값은 etl/output/we_li_table.csv 에서 그대로 가져옴
INSERT INTO we_li_table (inning, half, out_count, base_state, score_diff, we, leverage_index) VALUES
    (9, 'bot', 2, '123', -4, 0.144341, 3.376431),
    (9, 'bot', 2, '123', -1, 0.461695, 9.793598),
    (9, 'bot', 2, '123',  1, 1.000000, 0.000000),
    (5, 'top', 0, '0',    4, 0.938700, 0.158963);
