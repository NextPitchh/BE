-- BatterStatsServiceTest 용: 한 경기, 타자 2명 (기록 있는 타자 / 시즌 첫 타석 전 타자)
INSERT INTO teams (team_code, name) VALUES ('HH', '한화'), ('KT', 'KT');
INSERT INTO players (player_id, name, hit_type) VALUES (51234, '노시환', 'R'), (51235, '신인', 'L');
INSERT INTO games (game_id, game_date, away_team, home_team, final_away, final_home, went_to_extras, is_cold_game,
                   is_excluded, available_innings_away, available_innings_home,
                   high_leverage_innings_away, high_leverage_innings_home, n_pa)
VALUES ('20250601HHKT02025', '2025-06-01', 'HH', 'KT', 4, 3, false, false, false,
        '{5,6,7,8,9}', '{5,6,7,8,9}', '{}', '{}', 76);

INSERT INTO batter_game_stats VALUES
    ('20250601HHKT02025', 51234, 250, 220, 63, 22, 25, 3, 108, 0.286, 0.361, 0.492, 0.853, 32, 0.312),
    ('20250601HHKT02025', 51235, 0, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, NULL, 0, NULL);
