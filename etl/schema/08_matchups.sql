-- matchups (명세 v0.3 §2-3 Batter.vs_current_pitcher, §2-2 Pitcher.matchup_vs_batter)
-- 경기 시작 시점의 투수 × 타자 상대 전적. 그 경기 "전" 두 선수의 모든 대결 (2016~ 통산).
-- 행 = 경기마다 (출전 가능 투수 = game_bullpen) × (상대 팀 타자 = game_batting_order). 생성: etl/09_matchups.py
CREATE TABLE IF NOT EXISTS matchups (
    game_id         VARCHAR(20) NOT NULL REFERENCES games(game_id),
    pitcher_id      INTEGER     NOT NULL REFERENCES players(player_id),
    batter_id       INTEGER     NOT NULL REFERENCES players(player_id),
    handedness_key  VARCHAR(4)  NOT NULL,      -- 투수 손 _ 타자 타석 ('R_L' 등). 'U_S', 'R_U' 처럼 타석이 정해지지 않으면 좌우 판정 불가
    pa              SMALLINT    NOT NULL,      -- 상대 타석. 0 = 첫 대결
    ab              SMALLINT    NOT NULL,      -- 상대 타수
    h               SMALLINT    NOT NULL,      -- 상대 안타
    avg             REAL,                      -- 상대 타율 (보정 없음). 타수 0 이면 NULL
    woba            REAL        NOT NULL,      -- 좌우 기준값 쪽으로 당긴 상대 wOBA (데이터셋 matchup_woba_shrunk)
    iso             REAL        NOT NULL,      -- 같은 방식의 ISO (matchup_iso_shrunk)
    base_woba       REAL        NOT NULL,      -- 좌우 기준 wOBA. 첫 대결이면 woba 와 같다
    PRIMARY KEY (game_id, pitcher_id, batter_id)
);
