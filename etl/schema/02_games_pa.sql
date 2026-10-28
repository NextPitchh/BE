-- games, game_pa (명세 v0.3 §3-3 /games, §3-4 /sessions, §3-7 /advance-half, §3-9 /we-trace)

CREATE TABLE IF NOT EXISTS games (
    game_id                     VARCHAR(20) PRIMARY KEY,   -- "20250322HHKT02025"
    game_date                   DATE        NOT NULL,
    away_team                   VARCHAR(4)  NOT NULL REFERENCES teams(team_code),
    home_team                   VARCHAR(4)  NOT NULL REFERENCES teams(team_code),
    final_away                  SMALLINT    NOT NULL,      -- 연장 경기는 9회 종료 스코어(동점)
    final_home                  SMALLINT    NOT NULL,
    went_to_extras              BOOLEAN     NOT NULL,
    is_cold_game                BOOLEAN     NOT NULL,
    is_excluded                 BOOLEAN     NOT NULL,      -- true 면 /games 목록에서 뺀다
    -- 팀별 수비 반이닝 기준. 원정팀 수비 = 말, 홈팀 수비 = 초
    available_innings_away      SMALLINT[]  NOT NULL,
    available_innings_home      SMALLINT[]  NOT NULL,
    high_leverage_innings_away  SMALLINT[]  NOT NULL,
    high_leverage_innings_home  SMALLINT[]  NOT NULL,
    n_pa                        SMALLINT    NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_games_teams ON games (away_team, home_team);

-- 실제 타석 기록. (game_id, pa_index) 는 bandit_scores 와 맞추는 키다 — 경기 첫 타석이 0
CREATE TABLE IF NOT EXISTS game_pa (
    game_id              VARCHAR(20) NOT NULL REFERENCES games(game_id),
    pa_index             SMALLINT    NOT NULL,
    inning               SMALLINT    NOT NULL,
    half                 VARCHAR(3)  NOT NULL,            -- 'top' | 'bot'
    out_count            SMALLINT    NOT NULL,            -- 타석 시작 시점
    base_state           VARCHAR(3)  NOT NULL,            -- '0','1','2','3','12','13','23','123'
    score_home           SMALLINT    NOT NULL,            -- 타석 시작 시점
    score_away           SMALLINT    NOT NULL,
    score_diff_attacker  SMALLINT    NOT NULL,            -- 공격팀 관점. 재변환 금지
    batter_id            INTEGER     NOT NULL REFERENCES players(player_id),
    pitcher_id           INTEGER     NOT NULL REFERENCES players(player_id),
    batting_order        SMALLINT    NOT NULL,            -- 1~9. 선수 기준으로 계산 (자동 고의4구 누락 때문에 타석 수로 세지 않는다)
    result               VARCHAR(4)  NOT NULL,            -- 1B 2B 3B HR BB SO GO FO GDP + SF, UNK, OUT(미분류)
    result_raw           VARCHAR(4)  NOT NULL,            -- 가공 데이터 원래 값 (OUT 합쳐진 상태)
    relay_text           TEXT,                            -- 원본 중계 문구. PAResult.description 용
    runs_scored          SMALLINT    NOT NULL,
    we_before            REAL        NOT NULL,            -- 공격팀 관점, Option B
    we_after             REAL        NOT NULL,
    leverage_index       REAL        NOT NULL,
    pitch_count_before   SMALLINT    NOT NULL,            -- 이 타석 전까지 현재 투수 누적 투구수
    pitches_in_pa        SMALLINT    NOT NULL,
    is_partial_pa        BOOLEAN     NOT NULL,            -- 끝나지 않은 타석(투수 교체로 쪼개짐, 주자 아웃으로 이닝 종료). 결과 표시·타석 수에서 뺀다
    is_pitcher_change    BOOLEAN     NOT NULL,            -- 이 타석에서 투수가 바뀌었나 = 실제 감독의 교체 결정
    inning_ended         BOOLEAN     NOT NULL,
    bullpen_source       VARCHAR(10) NOT NULL,            -- 'actual' | 'estimated'
    data_quality_flag    VARCHAR(30),
    PRIMARY KEY (game_id, pa_index)
);
