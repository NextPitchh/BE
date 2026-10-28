-- game_bullpen, game_batting_order (명세 v0.3 §3-5 /recommend 후보, §2-1 upcoming_batters)

-- 경기·팀별로 이 경기에 나올 수 있었던 투수 전부
--   시점 t(pa_index) 기준: used = first_pa_index < t, current = 그 타석 투수, 나머지 = available
CREATE TABLE IF NOT EXISTS game_bullpen (
    game_id         VARCHAR(20) NOT NULL REFERENCES games(game_id),
    team_code       VARCHAR(4)  NOT NULL REFERENCES teams(team_code),
    pitcher_id      INTEGER     NOT NULL REFERENCES players(player_id),
    role            VARCHAR(10) NOT NULL,      -- 'starter' | 'bullpen' | 'unlisted'(명단 밖 등판, 야수 등판 등)
    is_listed       BOOLEAN     NOT NULL,      -- 경기 전 불펜 명단에 있었나
    first_pa_index  SMALLINT,                  -- 첫 등판 타석. 등판 안 했으면 NULL
    n_pa            SMALLINT    NOT NULL,      -- 이 경기에서 상대한 타자 수
    PRIMARY KEY (game_id, team_code, pitcher_id)
);

-- 타순 자리(1~9)를 거쳐 간 타자 전부. 시점 t 의 라인업 = 자리마다 first_pa_index <= t 인 마지막 타자
CREATE TABLE IF NOT EXISTS game_batting_order (
    game_id         VARCHAR(20) NOT NULL REFERENCES games(game_id),
    team_code       VARCHAR(4)  NOT NULL REFERENCES teams(team_code),
    order_no        SMALLINT    NOT NULL,      -- 1~9
    seq             SMALLINT    NOT NULL,      -- 0 = 선발, 1.. = 교체 순서
    batter_id       INTEGER     NOT NULL REFERENCES players(player_id),
    is_starter      BOOLEAN     NOT NULL,
    position        VARCHAR(10),               -- 예고 라인업 포지션 (교체 선수는 NULL)
    first_pa_index  SMALLINT    NOT NULL,
    last_pa_index   SMALLINT    NOT NULL,
    PRIMARY KEY (game_id, team_code, order_no, seq)
);
