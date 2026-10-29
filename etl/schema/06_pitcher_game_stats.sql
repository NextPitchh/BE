-- pitcher_game_stats (명세 v0.3 §2-2 Pitcher 의 season / vs_batter_hand / workload / quality_percentile)
-- 경기 시작 시점의 투수 카드 정보. 모든 값은 그 경기 "전" 2025 시즌 기록만 쓴다 (quality 는 2016~ 통산).
-- 행 = game_bullpen 의 (경기, 투수) 전부. 생성: etl/07_pitcher_game_stats.py
-- ERA 는 없다: 원본에 자책점 구분이 없어 계산할 수 없다 (API 는 null).
CREATE TABLE IF NOT EXISTS pitcher_game_stats (
    game_id              VARCHAR(20) NOT NULL REFERENCES games(game_id),
    pitcher_id           INTEGER     NOT NULL REFERENCES players(player_id),
    -- 시즌 누적 (그 경기 전까지)
    g                    SMALLINT    NOT NULL,      -- 등판 수
    outs                 SMALLINT    NOT NULL,      -- 잡은 아웃 수. 이닝 = outs / 3 (표기 52.1 = 157 아웃)
    so                   SMALLINT    NOT NULL,
    bb                   SMALLINT    NOT NULL,      -- 볼넷 (고의4구 포함, 사구 제외)
    hbp                  SMALLINT    NOT NULL,      -- 사구
    h                    SMALLINT    NOT NULL,      -- 피안타
    hr                   SMALLINT    NOT NULL,      -- 피홈런
    whip                 REAL,                      -- (h + bb) / 이닝. 이닝 0 이면 NULL
    -- 좌/우 타자 상대 (그 경기 전까지). 표본 없으면 NULL
    vs_l_pa              SMALLINT    NOT NULL,
    vs_l_avg             REAL,
    vs_l_woba            REAL,
    vs_l_iso             REAL,
    vs_r_pa              SMALLINT    NOT NULL,
    vs_r_avg             REAL,
    vs_r_woba            REAL,
    vs_r_iso             REAL,
    -- 피로도 (그 경기 전까지)
    avg_pitches_per_pa   REAL,                      -- 시즌 타석당 투구 수
    days_rest            SMALLINT,                  -- 마지막 등판 후 쉰 날. 어제 던졌으면 0, 시즌 첫 등판 전이면 NULL
    consecutive_days     SMALLINT    NOT NULL,      -- 어제까지 연속 등판 일수
    appearances_last_7d  SMALLINT    NOT NULL,      -- 최근 7일 등판 수
    pitches_last_3d      SMALLINT    NOT NULL,      -- 최근 3일 투구 수
    -- 불펜 quality (참고 표시용, 순위 계산에 안 씀)
    quality_p            REAL,                      -- 0~2, 평균 1. 이전 기록 없으면 NULL
    quality_percentile   SMALLINT,                  -- 0~100 = round(quality_p × 50)
    PRIMARY KEY (game_id, pitcher_id)
);
