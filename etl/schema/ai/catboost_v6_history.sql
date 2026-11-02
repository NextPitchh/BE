-- 별도 실행 전용: 기존 load.sh의 schema/*.sql 대상에 포함되지 않습니다.
-- 이력에는 현재 서비스 선수 명단에 없는 과거 선수도 있으므로 players FK를 두지 않습니다.
CREATE TABLE IF NOT EXISTS ai_pa_history_snapshot (
    version varchar(80) PRIMARY KEY,
    as_of_exclusive date NOT NULL,
    batter_rows integer NOT NULL CHECK (batter_rows >= 0),
    matchup_rows integer NOT NULL CHECK (matchup_rows >= 0),
    history_sha256 char(64) NOT NULL,
    batter_csv_sha256 char(64) NOT NULL,
    matchup_csv_sha256 char(64) NOT NULL
);
CREATE TABLE IF NOT EXISTS ai_pa_batter_history (
    version varchar(80) NOT NULL REFERENCES ai_pa_history_snapshot(version),
    batter_id integer NOT NULL CHECK (batter_id > 0),
    prior_ab integer NOT NULL CHECK (prior_ab >= 0),
    prior_hits integer NOT NULL CHECK (prior_hits BETWEEN 0 AND prior_ab),
    PRIMARY KEY (version, batter_id)
);
CREATE TABLE IF NOT EXISTS ai_pa_matchup_history (
    version varchar(80) NOT NULL REFERENCES ai_pa_history_snapshot(version),
    pitcher_id integer NOT NULL CHECK (pitcher_id > 0),
    batter_id integer NOT NULL CHECK (batter_id > 0),
    prior_ab integer NOT NULL CHECK (prior_ab >= 0),
    prior_hits integer NOT NULL CHECK (prior_hits BETWEEN 0 AND prior_ab),
    PRIMARY KEY (version, pitcher_id, batter_id)
);
