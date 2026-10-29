#!/usr/bin/env bash
# output/*.csv 를 PostgreSQL 에 적재한다.
#   DATABASE_URL=postgresql://user:pass@host:5432/dbname ./etl/load.sh
# 테이블을 만들고(없으면), 기존 데이터를 비운 뒤 CSV 로 다시 채운다. 몇 번을 돌려도 결과가 같다.
set -euo pipefail

: "${DATABASE_URL:?DATABASE_URL 환경변수를 설정하세요}"
ETL_DIR="$(cd "$(dirname "$0")" && pwd)"
OUT="$ETL_DIR/output"
export PGOPTIONS="-c client_min_messages=warning"   # TRUNCATE CASCADE 안내 메시지 숨김

for sql in "$ETL_DIR"/schema/*.sql; do
  psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -q -f "$sql"
done

load() {  # load <table> <csv>
  local table="$1" csv="$2"
  [ -f "$csv" ] || { echo "건너뜀: $csv 없음"; return; }
  local cols; cols="$(head -1 "$csv")"
  psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -q \
    -c "TRUNCATE $table CASCADE" \
    -c "\copy $table ($cols) FROM '$csv' WITH (FORMAT csv, HEADER true)"
  echo "$table: $(psql "$DATABASE_URL" -tAc "SELECT count(*) FROM $table")행"
}

load teams   "$OUT/teams.csv"
load players "$OUT/players.csv"
load games   "$OUT/games.csv"
load game_pa "$OUT/game_pa.csv"
load game_bullpen       "$OUT/game_bullpen.csv"
load game_batting_order "$OUT/game_batting_order.csv"
load we_li_table "$OUT/we_li_table.csv"
load li_percentile "$OUT/li_percentile.csv"
