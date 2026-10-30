# ETL — 서비스 DB 적재용 데이터 생성

`KBO_Crawler`(원본)와 `kbo-pitcher-change-ai-`(가공 데이터)를 읽어 PostgreSQL 에 넣을 CSV 를 만든다.
세 저장소가 같은 상위 폴더(`gradproject/`)에 있다고 가정한다. 다르면 `GRADPROJECT_DIR` 환경변수로 지정한다.

## 준비

```bash
# BE 저장소 루트에서
python3 -m venv etl/.venv && source etl/.venv/bin/activate
pip install -r etl/requirements.txt

# 로컬 DB (PostgreSQL 16, localhost:5433, kmb/kmb/kmoneyball)
docker compose up -d
```

## 실행 순서

| 순서 | 스크립트 | 만드는 것 | 원천 |
|---|---|---|---|
| 1 | `01_teams_players.py` | `output/teams.csv`, `output/players.csv` | `preview_full/2025/*.json` |
| 2 | `02_player_photos.py` | `output/players/{id}.webp`, players.csv 의 `photo_url` | 네이버 선수 사진 |
| 3 | `03_games_pa.py` | `output/games.csv`, `output/game_pa.csv` | `all_pa_with_li.parquet` + `pbp_full/2025` 중계 문구 |
| 4 | `04_bullpen_lineup.py` | `output/game_bullpen.csv`, `output/game_batting_order.csv` | `all_game_bullpen.parquet` + game_pa.csv + preview 포지션 |
| 5 | `05_we_li_table.py` | `output/we_li_table.csv` (상황 → WE·LI, 3,888행) | `kbo-pitcher-change-ai-` 의 WE·LI 계산 함수 |
| 6 | `06_li_percentile.py` | `output/li_percentile.csv` (LI → 리그 백분위, 1,001행) | `all_pa_with_li.parquet` + `bandit_game_split.csv` (train 경기만) |
| 7 | `07_pitcher_game_stats.py` | `output/pitcher_game_stats.csv` (경기 전 투수 카드 기록, 19,592행) | `all_pitcher_history.parquet`, `prior_wpa_optionb.parquet` + game_pa.csv·game_bullpen.csv |
| 8 | `08_batter_game_stats.py` | `output/batter_game_stats.csv` (경기 전 타자 카드 기록, 16,321행) | game_pa.csv·game_batting_order.csv |
| 9 | `09_matchups.py` | `output/matchups.csv` (경기 전 투수 × 상대 타자 전적, 222,468행) | `all_pa_enriched_v3_1.parquet`, `handedness_baseline.json` + game_bullpen·game_batting_order·players |
| - | `load.sh` | DB 테이블 생성 + CSV 적재 | `output/*.csv`, `schema/*.sql` |

```bash
python etl/01_teams_players.py
python etl/02_player_photos.py          # 600명, 약 2~3분
python etl/03_games_pa.py               # 약 5초
python etl/04_bullpen_lineup.py
python etl/05_we_li_table.py            # 2025 실제 타석 WE·LI 와 전부 일치하는지 검증까지
python etl/06_li_percentile.py          # 다른 단계와 독립
python etl/07_pitcher_game_stats.py     # 04 다음. quality_p 가 데이터셋과 같은지 검증까지
python etl/08_batter_game_stats.py      # 04 다음
python etl/09_matchups.py               # 04 다음. 데이터셋 matchup 값과 같은지 검증까지
DATABASE_URL=postgresql://kmb:kmb@localhost:5433/kmoneyball bash etl/load.sh
```

- 01 을 다시 돌리면 `photo_url` 이 비워진다. 이어서 02 를 다시 돌리면 이미 받은 사진은 건너뛰고 `photo_url` 만 다시 채운다.
- `output/` 은 git 에 올리지 않는다. 사진(`output/players/`)은 서버의 `/static/players/` 경로로 서빙한다.

## 데이터 컨벤션 메모

- `player_id` = 데이터셋 `pitcher_id`/`batter_id` = 네이버 `playerCode`. 매핑 테이블 없음 (2025 투수 281·타자 297명 전원, 이름 299/299 일치 확인).
- `throws` 는 `L | R | U(언더핸드)`. 데이터셋 `pitcher_throws`, 명세 `Pitcher.throws` 와 같다.
- 팀은 `team_code` 로만 식별한다 (SSG = `SK`, 키움 = `WO`, 두산 = `OB`, KIA = `HT`, 롯데 = `LT`).
- `game_pa.result` 는 원본 중계 문구로 다시 분류한 값이다. 가공 데이터의 `OUT` 을 `GO`/`FO` 로 나누고,
  `OUT` 으로 잘못 들어간 "스트라이크 낫 아웃"을 `SO` 로 바꾼다. 원래 값은 `result_raw` 에 남는다.
  `SF`(희생플라이), `UNK`(결과 불명)는 명세 9종에 없어 API 에서 어떻게 보낼지 정해야 한다.
- `we_before`/`we_after` 는 공격팀 관점(Option B) 그대로 저장한다. my_team 관점 변환은 API 책임이다.
- `we_li_table` 은 파이썬 원본 함수로 미리 계산한 표다. 서버는 WE·LI 를 직접 계산하지 않고 이 표에서 찾는다
  (점수차는 ±4 로 잘라서 조회 — 원본 WE 테이블 범위). 원본 계산이 바뀌면 05 를 다시 돌려 적재한다.
- 2025 원본에는 **공 없이 선언된 자동 고의4구 타석이 없다** (크롤러가 투구 이벤트만 줄로 만든다). 93건 추정.
  그래서 타순은 타석 수가 아니라 선수 기준으로 계산한다.
- `game_pa.is_partial_pa` = 끝나지 않은 타석(타석 중 투수 교체·대타, 주자 아웃으로 이닝 종료). 결과를 보여주지 않는다.
- `li_percentile` 은 bandit **train 구간(2016~2023)** 타석의 LI 분포로 만든다. 서비스 대상인 2025 는 test 구간이라
  기준 분포에 넣지 않는다 (quality_p 와 같은 원칙). 백분위 = ⌊100 × (LI 가 그 값보다 낮은 타석 비율)⌋,
  화면의 "리그 상위 X%" 는 `100 - 백분위` (최댓값 LI 9.79 도 99 → "상위 1%").
- `pitcher_game_stats` 는 **그 경기 전까지** 기록만 쓴다 (더블헤더 2차전은 1차전 포함). 시즌 기록·좌우 성적·피로도는
  2025 시즌, quality 는 2016~ 통산이다. 카드는 "구원으로 넣으면" 정보라 선발 투수도 불펜 quality 공식을 쓴다.
  ERA 는 원본에 자책점 구분이 없어 만들지 않는다. 이닝은 타석 결과로 잡은 아웃만 세서 도루저지·견제사 아웃만큼 조금 적다.
- `batter_game_stats.recent_avg` 는 직접 계산한 직전 10경기 타율이다. 데이터셋 `batter_recent_avg` 는 네이버 "오늘 타율"
  (`todayHra`)이라 경기 중에 값이 바뀌고 그 경기 결과가 섞여 있어 쓰지 않는다.
- `matchups` 는 그 경기에 나올 수 있었던 투수 × 상대 타자 **모든 조합**이다 (사용자가 실제와 다른 투수를 골라도 바로 조회).
  좌우 기준값은 `players` 의 선수별 손 정보로 정한다. 데이터셋 `handedness_key` 는 같은 선수도 타석마다 흔들리는 경우가 있어
  (사이드암을 R/U 로 번갈아 등) 2025 실제 대결의 1.7% 는 데이터셋 `matchup_woba_shrunk` 와 다르다. 유리/불리 판정 기준은
  `application.yaml` 의 `app.matchup` (임시값, 회의 안건).
