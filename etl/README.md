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
| - | `load.sh` | DB 테이블 생성 + CSV 적재 | `output/*.csv`, `schema/*.sql` |

```bash
python etl/01_teams_players.py
python etl/02_player_photos.py          # 600명, 약 2~3분
python etl/03_games_pa.py               # 약 5초
python etl/04_bullpen_lineup.py
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
- 2025 원본에는 **공 없이 선언된 자동 고의4구 타석이 없다** (크롤러가 투구 이벤트만 줄로 만든다). 93건 추정.
  그래서 타순은 타석 수가 아니라 선수 기준으로 계산한다.
- `game_pa.is_partial_pa` = 끝나지 않은 타석(타석 중 투수 교체·대타, 주자 아웃으로 이닝 종료). 결과를 보여주지 않는다.
