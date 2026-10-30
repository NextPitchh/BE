-- MatchupServiceTest 용: 투수 3명 × 타자 2명 일부
INSERT INTO teams (team_code, name) VALUES ('HH', '한화'), ('KT', 'KT');
INSERT INTO players (player_id, name, throws, hit_type) VALUES
    (60841, '투수R', 'R', NULL), (60842, '투수L', 'L', NULL), (60843, '투수U', 'U', NULL),
    (51234, '좌타자', NULL, 'L'), (51235, '스위치', NULL, 'S');
INSERT INTO games (game_id, game_date, away_team, home_team, final_away, final_home, went_to_extras, is_cold_game,
                   is_excluded, available_innings_away, available_innings_home,
                   high_leverage_innings_away, high_leverage_innings_home, n_pa)
VALUES ('20250601HHKT02025', '2025-06-01', 'HH', 'KT', 4, 3, false, false, false,
        '{5,6,7,8,9}', '{5,6,7,8,9}', '{}', '{}', 76);

INSERT INTO matchups (game_id, pitcher_id, batter_id, handedness_key, pa, ab, h, avg, woba, iso, base_woba) VALUES
    -- 상대 전적 있음, 리그 평균(0.2924)보다 0.05 높음 → 투수 불리
    ('20250601HHKT02025', 60841, 51234, 'R_L', 12, 10, 4, 0.4, 0.345, 0.15, 0.2953),
    -- 첫 대결 → vs_current_pitcher null, 판정은 좌우 기준값으로 (0.2953, 차이 0.003 → 보통)
    ('20250601HHKT02025', 60841, 51235, 'R_L', 0, 0, 0, NULL, 0.2953, 0.1305, 0.2953),
    -- 상대 wOBA 가 낮음 → 투수 유리
    ('20250601HHKT02025', 60842, 51234, 'L_L', 5, 5, 0, 0.0, 0.24, 0.07, 0.283),
    -- 언더핸드 × 스위치 타자: 타석을 정할 수 없어 판정 없음
    ('20250601HHKT02025', 60843, 51235, 'U_S', 0, 0, 0, NULL, 0.3244, 0.2283, 0.3244);
