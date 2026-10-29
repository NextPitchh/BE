-- PitcherStatsServiceTest 용: 한 경기, 투수 2명 (기록 있는 투수 / 시즌 첫 등판 전 투수)
INSERT INTO teams (team_code, name) VALUES ('HH', '한화'), ('KT', 'KT');
INSERT INTO players (player_id, name, throws) VALUES (60841, '김서현', 'R'), (61895, '주현상', 'R');
INSERT INTO games (game_id, game_date, away_team, home_team, final_away, final_home, went_to_extras, is_cold_game,
                   is_excluded, available_innings_away, available_innings_home,
                   high_leverage_innings_away, high_leverage_innings_home, n_pa)
VALUES ('20250601HHKT02025', '2025-06-01', 'HH', 'KT', 4, 3, false, false, false,
        '{5,6,7,8,9}', '{5,6,7,8,9}', '{}', '{}', 76);

INSERT INTO pitcher_game_stats VALUES
    -- 157 아웃 = 52.1 이닝
    ('20250601HHKT02025', 60841, 55, 157, 61, 24, 3, 38, 4, 1.18,
     118, 0.241, 0.283, 0.098, 143, 0.267, 0.301, 0.131,
     4.12, 2, 0, 3, 35, 1.68, 84),
    -- 시즌 첫 등판 전: 비율·쉰 날·quality 없음
    ('20250601HHKT02025', 61895, 0, 0, 0, 0, 0, 0, 0, NULL,
     0, NULL, NULL, NULL, 0, NULL, NULL, NULL,
     NULL, NULL, 0, 0, 0, NULL, NULL);
