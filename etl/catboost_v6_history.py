"""검증된 v6 동결 이력을 로컬 PostgreSQL에만 원자적으로 적재한다."""
import argparse
import csv
import hashlib
import io
import json
import os
import re
import subprocess
from pathlib import Path

VERSION = "pa-service-train-history-2016-2023-v1"
HISTORY_SHA = "63010d94ab60031d5940f8dc1f6688ad5e0088e8986607cd581223071c4584bd"
BATTER_SHA = "d3ee73a325954023656cfad61dc3346b8782f6e6fd1d43ed7034e6acaf131345"
MATCHUP_SHA = "eb2f66989ab20f933e6bf8b897f7fcb2997b2e09079951eff2c778fbf45d371f"
SCHEMA = Path(__file__).parent / "schema/ai/catboost_v6_history.sql"


def read_checked(path, digest):
    data = path.read_bytes()
    if hashlib.sha256(data).hexdigest() != digest:
        raise ValueError(f"SHA-256 불일치: {path.name}")
    return data


def verify(source):
    history = json.loads(read_checked(source / "train_history_2023.json", HISTORY_SHA))
    if history.get("as_of_exclusive") != "2024-01-01":
        raise ValueError("이력 기준 시점 불일치")
    outputs = []
    for filename, digest, ids, key, expected in (
        ("batter_train_history.csv", BATTER_SHA, ("batter_id",), "batter", 656),
        ("matchup_train_history.csv", MATCHUP_SHA, ("pitcher_id", "batter_id"), "matchup", 95448),
    ):
        data = read_checked(source / filename, digest)
        reader = csv.DictReader(io.StringIO(data.decode("utf-8-sig"), newline=""))
        if reader.fieldnames != ["version", *ids, "prior_ab", "prior_hits"]:
            raise ValueError(f"열 불일치: {filename}")
        seen = set()
        for row in reader:
            if None in row or row["version"] != VERSION:
                raise ValueError(f"버전 또는 열 값 오류: {filename}")
            values = [row[x] for x in (*ids, "prior_ab", "prior_hits")]
            if any(not v or not v.isascii() or not v.isdecimal() or (len(v) > 1 and v[0] == "0") for v in values):
                raise ValueError(f"정수 형식 오류: {filename}")
            numbers = list(map(int, values))
            if any(n <= 0 or n > 999999999 for n in numbers[:len(ids)]) or not 0 <= numbers[-1] <= numbers[-2]:
                raise ValueError(f"이력 값 오류: {filename}")
            identity = ":".join(row[x] for x in ids)
            if identity in seen or history[key].get(identity) != numbers[-2:]:
                raise ValueError(f"중복 또는 JSON 불일치: {filename} {identity}")
            seen.add(identity)
        if len(seen) != expected or len(history[key]) != expected:
            raise ValueError(f"행 수 불일치: {filename}")
        outputs.append(data.decode("utf-8-sig").replace("\r\n", "\n").rstrip("\n"))
        print(f"{filename}: {len(seen)}행, 키·JSON·SHA-256 확인")
    return outputs


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True, help="AI 저장소의 catboost_service_v6 폴더")
    parser.add_argument("--load", action="store_true", help="검증 후 명시한 로컬 DB에만 적재")
    parser.add_argument("--database", help="로컬 DB 이름")
    parser.add_argument("--user", help="로컬 DB 사용자")
    parser.add_argument("--port", type=int, default=5433)
    parser.add_argument("--psql", default="psql", help="psql 실행 파일")
    args = parser.parse_args()
    batter, matchup = verify(args.source)
    if not args.load:
        print("검증 전용: DB 변경 없음")
        return
    if not args.database or not args.user or not all(re.fullmatch(r"[A-Za-z0-9_]+", x) for x in (args.database, args.user)) or not 1 <= args.port <= 65535:
        parser.error("--load에는 단순한 --database, --user와 올바른 --port가 필요합니다")
    sql = f"""BEGIN;
SELECT pg_advisory_xact_lock(7060662023);
{SCHEMA.read_text(encoding='utf-8')}
INSERT INTO ai_pa_history_snapshot VALUES ('{VERSION}', DATE '2024-01-01', 656, 95448, '{HISTORY_SHA}', '{BATTER_SHA}', '{MATCHUP_SHA}');
COPY ai_pa_batter_history (version,batter_id,prior_ab,prior_hits) FROM STDIN WITH (FORMAT csv, HEADER true);
{batter}
\\.
COPY ai_pa_matchup_history (version,pitcher_id,batter_id,prior_ab,prior_hits) FROM STDIN WITH (FORMAT csv, HEADER true);
{matchup}
\\.
DO $$ BEGIN
 IF (SELECT count(*) FROM ai_pa_batter_history WHERE version = '{VERSION}') <> 656 OR
    (SELECT count(*) FROM ai_pa_matchup_history WHERE version = '{VERSION}') <> 95448 THEN
   RAISE EXCEPTION 'Snapshot row counts differ';
 END IF;
END $$;
COMMIT;
"""
    env = os.environ.copy()
    for name in ("PGSERVICE", "PGSERVICEFILE", "PGOPTIONS", "PGHOSTADDR"):
        env.pop(name, None)
    subprocess.run([args.psql, "-X", "--host", "127.0.0.1", "--port", str(args.port),
                    "--dbname", args.database, "--username", args.user, "-v", "ON_ERROR_STOP=1"],
                   input=sql.encode("utf-8"), env=env, check=True)
    print("로컬 DB 트랜잭션 적재 완료")


if __name__ == "__main__":
    main()
