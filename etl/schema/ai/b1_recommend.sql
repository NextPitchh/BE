-- 별도 AI 적재 전용. 기존 etl/load.sh에서 실행되지 않는다.
CREATE TABLE IF NOT EXISTS bandit_dataset_manifest (
  version varchar(64) PRIMARY KEY,
  model_version varchar(64) NOT NULL,
  snapshot_sha256 char(64) NOT NULL,
  li_sha256 char(64) NOT NULL,
  snapshot_rows integer NOT NULL,
  li_rows integer NOT NULL
);
CREATE TABLE IF NOT EXISTS bandit_pitcher_snapshots (
  version varchar(64) NOT NULL REFERENCES bandit_dataset_manifest(version),
  game_id varchar(20) NOT NULL REFERENCES games(game_id),
  team_code varchar(4) NOT NULL REFERENCES teams(team_code),
  pitcher_id integer NOT NULL REFERENCES players(player_id),
  pitcher_name text,
  profile_asof_date date NOT NULL,
  prior_n_apps integer NOT NULL CHECK (prior_n_apps >= 0),
  profile_source text NOT NULL,
  feature_schema_version text NOT NULL,
  latest_included_game_id varchar(20),
  same_day_unresolved boolean NOT NULL,
  prior_pitcher_reward_mean double precision,
  prior_wpa_std double precision,
  prior_bb_rate double precision,
  prior_so_rate double precision,
  prior_hr_rate double precision,
  prior_avg_pa_per_app double precision,
  PRIMARY KEY (version, game_id, team_code, pitcher_id)
);
CREATE TABLE IF NOT EXISTS bandit_train_li_lookup (
  version varchar(64) NOT NULL REFERENCES bandit_dataset_manifest(version),
  inning integer NOT NULL CHECK (inning BETWEEN 1 AND 9),
  half varchar(3) NOT NULL CHECK (half IN ('top','bot')),
  out_count integer NOT NULL CHECK (out_count BETWEEN 0 AND 2),
  base_state varchar(3) NOT NULL CHECK (base_state IN ('0','1','2','3','12','13','23','123')),
  score_diff_attacker integer NOT NULL CHECK (score_diff_attacker BETWEEN -4 AND 4),
  li_raw double precision NOT NULL,
  leverage_index double precision NOT NULL CHECK (leverage_index >= 0),
  PRIMARY KEY (version, inning, half, out_count, base_state, score_diff_attacker)
);
