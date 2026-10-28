-- we_li_table (명세 v0.3 §3-6 /simulate-pa, §3-7 /advance-half 의 WE·LI 재계산)
-- 상황 → 공격팀 관점 승리확률(WE)과 LI. 서버는 계산하지 않고 이 표에서 찾기만 한다.
-- 점수차는 ±4 로 잘라서 찾는다 (원본 WE 테이블이 ±4 까지만 있다). 생성: etl/05_we_li_table.py
CREATE TABLE IF NOT EXISTS we_li_table (
    inning          SMALLINT    NOT NULL,      -- 1~9
    half            VARCHAR(3)  NOT NULL,      -- 'top' | 'bot'
    out_count       SMALLINT    NOT NULL,      -- 0~2
    base_state      VARCHAR(3)  NOT NULL,      -- '0','1','2','3','12','13','23','123'
    score_diff      SMALLINT    NOT NULL,      -- 공격팀 관점, -4~4
    we              REAL        NOT NULL,      -- 공격팀 관점 승리확률
    leverage_index  REAL        NOT NULL,      -- 리그 평균 = 1.0
    PRIMARY KEY (inning, half, out_count, base_state, score_diff)
);
