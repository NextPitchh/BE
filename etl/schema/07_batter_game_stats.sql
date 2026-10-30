-- batter_game_stats (명세 v0.3 §2-3 Batter 의 season / recent_avg)
-- 경기 시작 시점의 타자 카드 정보. 모든 값은 그 경기 "전" 2025 시즌 기록만 쓴다.
-- 행 = game_batting_order 의 (경기, 타자) 전부. 생성: etl/08_batter_game_stats.py
CREATE TABLE IF NOT EXISTS batter_game_stats (
    game_id     VARCHAR(20) NOT NULL REFERENCES games(game_id),
    batter_id   INTEGER     NOT NULL REFERENCES players(player_id),
    -- 시즌 누적 (그 경기 전까지)
    pa          SMALLINT    NOT NULL,      -- 타석 (UNK 제외)
    ab          SMALLINT    NOT NULL,      -- 타수
    h           SMALLINT    NOT NULL,      -- 안타
    hr          SMALLINT    NOT NULL,
    bb          SMALLINT    NOT NULL,      -- 볼넷 (사구·고의4구 포함)
    sf          SMALLINT    NOT NULL,      -- 희생플라이
    tb          SMALLINT    NOT NULL,      -- 루타
    avg         REAL,                      -- 타율. 타수 0 이면 NULL (이하 같음)
    obp         REAL,                      -- 출루율 = (h + bb) / (ab + bb + sf)
    slg         REAL,                      -- 장타율 = tb / ab
    ops         REAL,                      -- obp + slg
    -- 직전 10경기
    recent_ab   SMALLINT    NOT NULL,
    recent_avg  REAL,
    PRIMARY KEY (game_id, batter_id)
);
