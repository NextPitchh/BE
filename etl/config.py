"""ETL 공통 경로/설정.

모든 ETL 스크립트는 이 파일의 경로만 참조한다. 저장소 위치가 바뀌면 여기만 고친다.
환경변수로 덮어쓸 수 있다 (EC2 등 다른 머신에서 실행할 때).
"""
import os
from pathlib import Path

GRADPROJECT = Path(os.environ.get("GRADPROJECT_DIR", Path(__file__).resolve().parents[2]))

# 원본 크롤링 데이터 (KBO_Crawler)
CRAWLER_DATA = GRADPROJECT / "KBO_Crawler" / "data"
PREVIEW_DIR = CRAWLER_DATA / "preview_full"
PBP_DIR = CRAWLER_DATA / "pbp_full"

# 가공 데이터 (kbo-pitcher-change-ai-)
MODEL_REPO = GRADPROJECT / "kbo-pitcher-change-ai-"
PA_PARQUET = MODEL_REPO / "data_analysis" / "results" / "all_pa_with_li.parquet"
BULLPEN_PARQUET = MODEL_REPO / "data_analysis" / "all_game_bullpen.parquet"
PITCHER_HISTORY_PARQUET = MODEL_REPO / "data_analysis" / "all_pitcher_history.parquet"   # 등판 단위 기록 (2016~2025)
PRIOR_WPA_OPTIONB_PARQUET = MODEL_REPO / "data_analysis" / "results" / "prior_wpa_optionb.parquet"

# 서비스 대상 시즌 (명세 v0.3: 2025 시즌 리플레이)
SEASON = "2025"

# 산출물 (DB 적재용 CSV, 선수 사진)
OUTPUT_DIR = Path(__file__).resolve().parent / "output"
PHOTO_DIR = OUTPUT_DIR / "players"

# 사진 원본 경로 (명세 2-2). 서빙은 자체 호스팅 경로로 한다.
PHOTO_SOURCE_URL = "https://sports-phinf.pstatic.net/player/kbo/default/{player_id}.png?type=w150"
PHOTO_SERVE_PATH = "/static/players/{player_id}.webp"
