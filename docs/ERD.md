# K-Moneyball 서비스 DB ERD

PostgreSQL 13개 테이블 + Redis 세션. 2025 시즌만 적재한다.
✅ = ETL 완료(`etl/`), ⬜ = 예정.

```mermaid
erDiagram
    teams ||--o{ games : "away_team / home_team"
    teams ||--o{ game_bullpen : team_code
    teams ||--o{ game_batting_order : team_code
    players ||--o{ game_pa : "batter_id / pitcher_id"
    players ||--o{ game_bullpen : pitcher_id
    players ||--o{ game_batting_order : batter_id
    players ||--o{ pitcher_game_stats : pitcher_id
    players ||--o{ batter_game_stats : batter_id
    players ||--o{ matchups : "pitcher_id / batter_id"
    games ||--|{ game_pa : game_id
    games ||--|{ game_bullpen : game_id
    games ||--|{ game_batting_order : game_id
    games ||--o{ pitcher_game_stats : game_id
    games ||--o{ batter_game_stats : game_id
    games ||--o{ matchups : game_id
    game_pa ||--o{ bandit_scores : "game_id + pa_index"
    game_bullpen ||--o{ bandit_scores : "후보 = 그 시점 available 투수"

    teams {
        varchar team_code PK "HH, SK ..."
        varchar name "한화"
        varchar full_name "한화 이글스"
        varchar logo_url
    }
    players {
        int player_id PK "= 데이터셋 pitcher_id/batter_id = 네이버 playerCode"
        varchar name
        char throws "L R U(언더)"
        char hit_type "L R S"
        varchar back_number
        date birth_date
        varchar photo_url "없으면 NULL"
    }
    games {
        varchar game_id PK
        date game_date
        varchar away_team FK
        varchar home_team FK
        smallint final_away
        smallint final_home
        bool went_to_extras
        bool is_cold_game
        bool is_excluded "true면 목록 제외"
        smallint[] available_innings_away
        smallint[] available_innings_home
        smallint[] high_leverage_innings_away
        smallint[] high_leverage_innings_home
    }
    game_pa {
        varchar game_id PK,FK
        smallint pa_index PK "경기 첫 타석 0"
        smallint inning
        varchar half "top bot"
        smallint out_count
        varchar base_state
        smallint score_home
        smallint score_away
        smallint score_diff_attacker
        int batter_id FK
        int pitcher_id FK
        smallint batting_order
        varchar result "GO FO SO 1B ... SF UNK"
        varchar result_raw
        text relay_text
        smallint runs_scored
        real we_before "공격팀 관점"
        real we_after
        real leverage_index
        smallint pitch_count_before
        smallint pitches_in_pa
        bool is_partial_pa
        bool is_pitcher_change
        bool inning_ended
        varchar bullpen_source
    }
    game_bullpen {
        varchar game_id PK,FK
        varchar team_code PK,FK
        int pitcher_id PK,FK
        varchar role "starter bullpen unlisted"
        bool is_listed
        smallint first_pa_index "NULL=미등판"
        smallint n_pa
    }
    game_batting_order {
        varchar game_id PK,FK
        varchar team_code PK,FK
        smallint order_no PK "1~9"
        smallint seq PK "0=선발"
        int batter_id FK
        bool is_starter
        varchar position
        smallint first_pa_index
        smallint last_pa_index
    }
    pitcher_game_stats {
        varchar game_id PK,FK
        int pitcher_id PK,FK
        smallint g "이하 그 경기 전 2025 시즌 누적"
        smallint outs "이닝 = outs / 3"
        smallint so
        smallint bb "사구 제외"
        smallint hbp
        smallint h
        smallint hr
        real whip "ERA 는 없음 (자책점 구분 불가)"
        smallint vs_l_pa
        real vs_l_avg
        real vs_l_woba
        real vs_l_iso
        smallint vs_r_pa
        real vs_r_avg
        real vs_r_woba
        real vs_r_iso
        real avg_pitches_per_pa
        smallint days_rest "어제 던졌으면 0"
        smallint consecutive_days
        smallint appearances_last_7d
        smallint pitches_last_3d
        real quality_p "불펜 공식, 2016~ 통산"
        smallint quality_percentile "round(quality_p x 50)"
    }
    batter_game_stats {
        varchar game_id PK,FK
        int batter_id PK,FK
        smallint pa "이하 그 경기 전 2025 시즌 누적"
        smallint ab
        smallint h
        smallint hr
        smallint bb "사구·고의4구 포함"
        smallint sf
        smallint tb
        real avg
        real obp
        real slg
        real ops
        smallint recent_ab "직전 10경기"
        real recent_avg "직전 10경기 타율"
    }
    matchups {
        varchar game_id PK,FK
        int pitcher_id PK,FK
        int batter_id PK,FK
        varchar handedness_key "R_L 등. 이하 그 경기 전 2016~ 통산"
        smallint pa "0 = 첫 대결"
        smallint ab
        smallint h
        real avg "보정 없음"
        real woba "좌우 기준값으로 당긴 값 (matchup_woba_shrunk)"
        real iso "matchup_iso_shrunk"
        real base_woba "좌우 기준 wOBA"
    }
    we_li_table {
        smallint inning PK
        varchar half PK
        smallint out_count PK
        varchar base_state PK
        smallint score_diff PK "공격팀 관점, -4~4 (±4 로 잘라서 조회)"
        real we "공격팀 관점"
        real leverage_index
    }
    li_percentile {
        numeric li_value PK "0.00~10.00, 0.01 간격"
        smallint percentile "bandit train 구간(2016~2023) 타석 중 LI 가 더 낮은 비율"
    }
    bandit_scores {
        varchar game_id PK,FK
        smallint pa_index PK,FK
        int pitcher_id PK,FK
        real model_score "타석당 기대 WPA, 투수 관점"
        smallint model_rank
        varchar model_version "B1-..."
    }
```

## 테이블별 상태와 담당

| 테이블 | 상태 | 행 수| 쓰는 API |
|---|---|---|---|
| teams | ✅ 01 | 10  | /teams |
| players | ✅ 01·02 | 600  | 전부 |
| games | ✅ 03 | 720 (서비스 714) | /games |
| game_pa | ✅ 03 | 55,267  | /sessions, /recommend, /advance-half, /we-trace |
| game_bullpen | ✅ 04 | 19,592  | /recommend |
| game_batting_order | ✅ 04 | 16,321  | /sessions, /simulate-pa |
| pitcher_game_stats | ✅ 07 | 19,592 | /recommend |
| batter_game_stats | ✅ 08 | 16,321 | 타자 카드 |
| matchups | ✅ 09 | 222,468 | /recommend, 타자 카드 |
| we_li_table | ✅ 05 | 3,888 | /simulate-pa, /advance-half |
| li_percentile | ✅ 06 | 1,001 | /recommend |
| bandit_scores | ⬜ | | /recommend (분기 전) |

## 시점 t 에서 자주 쓰는 조회

```sql
-- 현재 라인업 (공격팀 team_code, 현재 타석 t)
SELECT DISTINCT ON (order_no) order_no, batter_id
FROM game_batting_order
WHERE game_id = :gid AND team_code = :team AND first_pa_index <= :t
ORDER BY order_no, seq DESC;

-- 불펜 상태 (수비팀)
SELECT pitcher_id, role,
       CASE WHEN pitcher_id = :current_pitcher THEN 'current'
            WHEN first_pa_index < :t          THEN 'used'
            ELSE 'available' END AS availability
FROM game_bullpen
WHERE game_id = :gid AND team_code = :team;
```

## Redis (세션)

```
키   session:{session_id}     TTL 30분 (요청마다 연장)
값   { game_id, my_team, seed, dice_count, state, history[], used_pitchers[],
       pitch_counts{}, decisions[], is_diverged, diverged_at }
```
