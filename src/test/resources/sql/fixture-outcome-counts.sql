-- Log5PaOutcomeModelTest 용: 리그 기준 1행 + 타자·투수 기록
INSERT INTO teams (team_code, name) VALUES ('HH', '한화'), ('KT', 'KT');
INSERT INTO players (player_id, name) VALUES (60841, '투수'), (51234, '타자');
INSERT INTO games (game_id, game_date, away_team, home_team, final_away, final_home, went_to_extras, is_cold_game,
                   is_excluded, available_innings_away, available_innings_home,
                   high_leverage_innings_away, high_leverage_innings_home, n_pa)
VALUES ('20250601HHKT02025', '2025-06-01', 'HH', 'KT', 4, 3, false, false, false,
        '{5,6,7,8,9}', '{5,6,7,8,9}', '{}', '{}', 76);

INSERT INTO league_outcome_rates VALUES ('2024', 56356, 0.183, 0.042, 0.004, 0.025, 0.104, 0.189, 0.237, 0.216, 0.338);

INSERT INTO outcome_counts VALUES
    ('20250601HHKT02025', 51234, 'B', 400, 60, 16, 2, 10, 40, 120, 80, 72),
    ('20250601HHKT02025', 60841, 'P', 300, 45, 12, 1, 6, 30, 90, 60, 56);
