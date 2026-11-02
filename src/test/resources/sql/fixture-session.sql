-- SessionApiTest 용 작은 경기: HH(원정) @ KT(홈), 내 팀 = KT (초 수비), 5회초 시작
-- 시작 직전 점수 HH 4 : KT 0 → 5회초 공격팀 점수차 +4 (fixture-we-li.sql 의 (5, top, 0, '0', 4) 행)
INSERT INTO teams (team_code, name) VALUES ('HH', '한화'), ('KT', 'KT');
INSERT INTO players (player_id, name, throws, hit_type)
SELECT id, '선수' || id, 'R', 'R' FROM generate_series(51001, 51009) AS id
UNION ALL SELECT id, '선수' || id, 'R', 'L' FROM generate_series(52001, 52009) AS id
UNION ALL SELECT id, '투수' || id, 'R', NULL FROM (VALUES (60001), (60002), (60003), (60004), (61001)) AS t(id);
INSERT INTO games (game_id, game_date, away_team, home_team, final_away, final_home, went_to_extras, is_cold_game,
                   is_excluded, available_innings_away, available_innings_home,
                   high_leverage_innings_away, high_leverage_innings_home, n_pa) VALUES
    ('20250601HHKT02025', '2025-06-01', 'HH', 'KT', 5, 2, false, false, false, '{5,6,7,8,9}', '{5,6,7,8,9}', '{}', '{}', 8),
    ('20250602HHKT02025', '2025-06-02', 'HH', 'KT', 1, 0, false, true, true, '{5}', '{5}', '{}', '{}', 40);

INSERT INTO game_pa (game_id, pa_index, inning, half, out_count, base_state, score_home, score_away, score_diff_attacker,
                     batter_id, pitcher_id, batting_order, result, result_raw, relay_text, runs_scored, we_before, we_after,
                     leverage_index, pitch_count_before, pitches_in_pa, is_partial_pa, is_pitcher_change, inning_ended,
                     bullpen_source, data_quality_flag) VALUES
    ('20250601HHKT02025', 0, 1, 'top', 0, '0',  0, 0, 0, 51001, 60001, 1, 'HR', 'HR', NULL, 1, 0.5, 0.6, 1, 0, 3, false, false, false, 'actual', NULL),
    ('20250601HHKT02025', 1, 1, 'top', 0, '0',  0, 1, 1, 51002, 60001, 2, 'SO', 'SO', NULL, 0, 0.6, 0.6, 1, 3, 5, false, false, true, 'actual', NULL),
    ('20250601HHKT02025', 2, 1, 'bot', 0, '0',  0, 1, -1, 52001, 61001, 1, '1B', '1B', NULL, 0, 0.4, 0.45, 1, 0, 2, false, false, false, 'actual', NULL),
    ('20250601HHKT02025', 3, 1, 'bot', 0, '1',  0, 1, -1, 52002, 61001, 2, 'GDP', 'GDP', NULL, 0, 0.45, 0.35, 1, 2, 4, false, false, true, 'actual', NULL),
    -- KT 투수 교체 (60001 → 60002), 3점
    ('20250601HHKT02025', 4, 4, 'top', 0, '12', 0, 1, 1, 51003, 60002, 3, 'HR', 'HR', NULL, 3, 0.6, 0.8, 2, 0, 6, false, true, true, 'actual', NULL),
    ('20250601HHKT02025', 5, 4, 'bot', 0, '0',  0, 4, -4, 52003, 61001, 3, 'BB', 'BB', NULL, 0, 0.1, 0.12, 1, 6, 5, false, false, true, 'actual', NULL),
    -- 시작 지점 t0 = 6: 실제 감독은 여기서 60003 으로 교체
    ('20250601HHKT02025', 6, 5, 'top', 0, '0',  0, 4, 4, 51004, 60003, 4, 'FO', 'OUT', NULL, 0, 0.94, 0.95, 0.2, 0, 4, false, true, false, 'actual', NULL),
    -- 5번 자리 대타 51009
    ('20250601HHKT02025', 7, 5, 'top', 1, '0',  0, 4, 4, 51009, 60003, 5, 'GO', 'OUT', NULL, 0, 0.95, 0.96, 0.2, 4, 3, false, false, false, 'actual', NULL);

-- 선발 9명씩. HH 5번 자리는 t0 뒤(타석 7)에 대타가 나왔지만 시작 시점 라인업은 선발이다
INSERT INTO game_batting_order (game_id, team_code, order_no, seq, batter_id, is_starter, position, first_pa_index, last_pa_index)
SELECT '20250601HHKT02025', 'HH', n, 0, 51000 + n, true, CASE n WHEN 1 THEN '중견수' WHEN 4 THEN '지명타자' END, n - 1, n - 1
FROM generate_series(1, 9) AS n
UNION ALL
SELECT '20250601HHKT02025', 'KT', n, 0, 52000 + n, true, NULL, n + 1, n + 1 FROM generate_series(1, 9) AS n;
INSERT INTO game_batting_order VALUES ('20250601HHKT02025', 'HH', 5, 1, 51009, false, NULL, 7, 7);

-- KT 불펜: 60001 선발(이미 등판), 60002 (현재), 60003 (실제 5회 교체), 60004 (안 나옴)
INSERT INTO game_bullpen (game_id, team_code, pitcher_id, role, is_listed, first_pa_index, n_pa) VALUES
    ('20250601HHKT02025', 'KT', 60001, 'starter', false, 0, 2),
    ('20250601HHKT02025', 'KT', 60002, 'bullpen', true, 4, 1),
    ('20250601HHKT02025', 'KT', 60003, 'bullpen', true, 6, 2),
    ('20250601HHKT02025', 'KT', 60004, 'bullpen', true, NULL, 0),
    ('20250601HHKT02025', 'HH', 61001, 'starter', false, 2, 3);

-- log5 리그 기준 (outcome_counts 는 비워 두면 리그 비율 그대로)
INSERT INTO league_outcome_rates VALUES ('2024', 56356, 0.183, 0.042, 0.004, 0.025, 0.104, 0.189, 0.237, 0.216, 0.338);
