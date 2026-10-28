-- ApiIntegrationTest 용 최소 데이터
INSERT INTO teams (team_code, name, full_name, logo_url) VALUES
    ('HH', '한화', '한화 이글스', NULL),
    ('KT', 'KT', 'kt wiz', NULL),
    ('LG', 'LG', 'LG 트윈스', NULL);

INSERT INTO games (game_id, game_date, away_team, home_team, final_away, final_home, went_to_extras, is_cold_game,
                   is_excluded, available_innings_away, available_innings_home,
                   high_leverage_innings_away, high_leverage_innings_home, n_pa) VALUES
    -- HH 원정
    ('20250401HHKT02025', '2025-04-01', 'HH', 'KT', 6, 9, false, false, false, '{5,6,7,8}', '{5,6,7,8,9}', '{7}', '{8,9}', 80),
    -- HH 홈
    ('20250402LGHH02025', '2025-04-02', 'LG', 'HH', 2, 3, false, false, false, '{5,6,7,8,9}', '{5,6,7,8,9}', '{5}', '{6,9}', 75),
    -- 콜드게임 → 목록 제외
    ('20250403HHKT02025', '2025-04-03', 'HH', 'KT', 1, 0, false, true, true, '{5}', '{5,6}', '{}', '{}', 40),
    -- 다른 시즌
    ('20240501HHKT02024', '2024-05-01', 'HH', 'KT', 4, 4, true, false, false, '{5,6,7,8,9}', '{5,6,7,8,9}', '{9}', '{9}', 90),
    -- HH 가 안 뛴 경기
    ('20250404KTLG02025', '2025-04-04', 'KT', 'LG', 3, 1, false, false, false, '{5,6,7,8,9}', '{5,6,7,8}', '{7}', '{7}', 77);
