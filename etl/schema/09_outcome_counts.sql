-- log5 주사위 재료 (명세 §2-4 PAResult.probs). 생성: etl/10_outcome_counts.py
-- 서버가 P(결과) ∝ 타자 비율 × 투수 비율 / 리그 비율 로 9종 확률을 계산하고 주사위를 굴린다.

-- 선수별 그 경기 "전" 2025 시즌 타석 결과 수. 땅볼 아웃과 병살은 c_gb 하나로 센다 (병살은 상황에 따라 서버가 나눔)
CREATE TABLE IF NOT EXISTS outcome_counts (
    game_id     VARCHAR(20) NOT NULL REFERENCES games(game_id),
    player_id   INTEGER     NOT NULL REFERENCES players(player_id),
    role        CHAR(1)     NOT NULL,      -- 'B' 타자로서 / 'P' 투수로서
    pa          SMALLINT    NOT NULL,      -- 아래 8칸의 합
    c_1b        SMALLINT    NOT NULL,
    c_2b        SMALLINT    NOT NULL,
    c_3b        SMALLINT    NOT NULL,
    c_hr        SMALLINT    NOT NULL,
    c_bb        SMALLINT    NOT NULL,      -- 사구·고의4구 포함
    c_so        SMALLINT    NOT NULL,
    c_fo        SMALLINT    NOT NULL,      -- 플라이 아웃 (희생플라이 포함)
    c_gb        SMALLINT    NOT NULL,      -- 땅볼 아웃 + 병살
    PRIMARY KEY (game_id, player_id, role)
);

-- 리그 기준 비율 (2024 시즌 전체, 서비스 대상 2025 와 겹치지 않음)
CREATE TABLE IF NOT EXISTS league_outcome_rates (
    season      VARCHAR(4)  PRIMARY KEY,
    pa          INTEGER     NOT NULL,
    r_1b        REAL        NOT NULL,
    r_2b        REAL        NOT NULL,
    r_3b        REAL        NOT NULL,
    r_hr        REAL        NOT NULL,
    r_bb        REAL        NOT NULL,
    r_so        REAL        NOT NULL,
    r_fo        REAL        NOT NULL,
    r_gb        REAL        NOT NULL,
    dp_share    REAL        NOT NULL       -- 병살 가능 상황(1루 주자, 0~1아웃)의 땅볼 아웃 중 병살 비율
);
