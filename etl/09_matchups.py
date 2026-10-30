"""matchups 테이블용 CSV 생성 — 경기 시작 시점의 투수 × 타자 상대 전적 (명세 v0.3 §2-3 vs_current_pitcher, §2-2 matchup_vs_batter).

행
  경기마다 (그 경기에 나올 수 있었던 투수 = game_bullpen) × (상대 팀에서 타석에 선 타자 = game_batting_order).
  사용자가 실제와 다른 투수를 골라도 상대 전적을 바로 보여줄 수 있게 가능한 조합을 전부 만든다.

시점 규칙 (미래 정보 차단)
  그 경기 "전" 두 선수의 모든 대결 (2016~ 통산). 같은 경기 안의 앞 타석은 넣지 않는다 (경기 전 값).

계산 (KBO_Crawler build_matchup_history.py / build_enriched_v3.py 와 동일)
  ab, h, avg  : 실제 상대 타수·안타·타율 (보정 없음)
  woba, iso   : 표본이 작아 좌우 기준값 쪽으로 당긴 값 (shrunk). w = ab / (ab + 10)
                woba = w × 상대 wOBA + (1 - w) × 좌우 기준 wOBA   (iso 도 같은 방식)
                좌우 기준값 = handedness_baseline.json (투수 손 _ 타자 타석, 스위치 타자는 투수 반대 타석)
  base_woba   : 그 조합의 좌우 기준 wOBA (상대 전적이 없을 때 woba 와 같다)
  handedness_key : 투수 손 _ 타자 타석 (R_L 등). UNK(투수 손 미상)·U/S(타석 미상) 가 들어 있으면 좌우 판정 근거가 없다.
  데이터셋 matchup_* 와 같은 원본(all_pa_enriched_v3_1.parquet)으로 센다.

검증
  2025 실제 대결의 그 경기 첫 타석에서 데이터셋 prior_matchup_ab·hits, matchup_woba_shrunk·iso_shrunk 와 같은지 확인한다.
  좌우 기준값은 players 테이블의 선수별 손 정보(API 가 보여주는 값)로 정한다. 데이터셋 handedness_key 는 같은 선수도
  타석마다 값이 흔들리는 경우가 있어(사이드암 R/U, 좌타자 한 타석만 R 등) 2025 실제 대결의 약 1.7% 가 다르다.

유리/불리 판정(matchup_vs_batter)은 기준이 회의 안건이라 여기서 정하지 않는다. 서버가 woba 와 설정값으로 판정한다.

실행
  python etl/09_matchups.py   (04_bullpen_lineup.py 다음 — game_bullpen.csv, game_batting_order.csv, players.csv 사용)
"""
import json
import sys

import numpy as np
import pandas as pd

from common import AB_RESULTS, HIT_RESULTS, PA_RESULTS, W_1B, W_2B, W_3B, W_BB, W_HR, order_key
from config import MODEL_REPO, OUTPUT_DIR, SEASON

PA_V3_1_PARQUET = MODEL_REPO / "data_analysis" / "results" / "all_pa_enriched_v3_1.parquet"
HANDEDNESS_BASELINE = MODEL_REPO / "data_analysis" / "handedness_baseline.json"
BASE_K = 10
KEY = ["game_id", "pitcher_id", "batter_id"]
COUNT_COLS = ["pa", "ab", "h", "n1", "n2", "n3", "hr", "bb", "sf"]


def pair_id(pitcher_id, batter_id):
    """(투수, 타자) → 정수 하나. asof 조인 키 (선수 ID 는 6자리 미만)."""
    return pitcher_id.astype(np.int64) * 1_000_000 + batter_id.astype(np.int64)


def per_game_pair_counts():
    """전 시즌 (투수, 타자, 경기) 별 대결 수. 데이터셋 matchup 과 같이 모든 행을 센다 (pa = 행 수)."""
    pa = pd.read_parquet(PA_V3_1_PARQUET, columns=["game_id", "pitcher_id", "batter_id", "pa_result"])
    r = pa["pa_result"].astype(str)
    pa = pa.assign(pa=1, ab=r.isin(AB_RESULTS), h=r.isin(HIT_RESULTS), n1=r.eq("1B"), n2=r.eq("2B"),
                   n3=r.eq("3B"), hr=r.eq("HR"), bb=r.eq("BB"), sf=r.eq("SF"))
    g = pa.groupby(KEY, as_index=False)[COUNT_COLS].sum()
    g["ok"] = order_key(g["game_id"])
    g["pair"] = pair_id(g["pitcher_id"], g["batter_id"])
    g = g.sort_values(["pair", "ok"]).reset_index(drop=True)
    g[COUNT_COLS] = g.groupby("pair")[COUNT_COLS].cumsum()
    return g


def candidate_pairs():
    """경기별 (출전 가능 투수) × (상대 팀 타자)."""
    bullpen = pd.read_csv(OUTPUT_DIR / "game_bullpen.csv", usecols=["game_id", "team_code", "pitcher_id"])
    batters = pd.read_csv(OUTPUT_DIR / "game_batting_order.csv", usecols=["game_id", "team_code", "batter_id"])
    batters = batters.drop_duplicates().rename(columns={"team_code": "batting_team"})
    rows = bullpen.merge(batters, on="game_id")
    rows = rows[rows["team_code"] != rows["batting_team"]][KEY].drop_duplicates().reset_index(drop=True)
    rows["ok"] = order_key(rows["game_id"])
    rows["pair"] = pair_id(rows["pitcher_id"], rows["batter_id"])
    return rows


def handedness_base(rows):
    """좌우 기준 wOBA·ISO. build_matchup_history.pitcher_key / apply_shrinkage 와 같은 규칙."""
    baseline = json.loads(HANDEDNESS_BASELINE.read_text(encoding="utf-8"))["baseline"]
    glob = baseline["_GLOBAL"]
    players = pd.read_csv(OUTPUT_DIR / "players.csv", usecols=["player_id", "throws", "hit_type"]).set_index("player_id")

    throws = rows["pitcher_id"].map(players["throws"]).where(lambda s: s.isin(["L", "R", "U"]), "UNK")
    side = rows["batter_id"].map(players["hit_type"]).fillna("U")
    side = side.mask(side.eq("S") & throws.eq("R"), "L").mask(side.eq("S") & throws.eq("L"), "R")
    key = throws + "_" + side

    def lookup(field, fallback):
        return key.map(lambda k: (baseline.get(k) or glob).get(field) or fallback).astype(float)

    return key, lookup("base_woba_effective", glob["woba"]), lookup("iso", glob["iso"])


def build(rows, counts):
    """그 경기 전 마지막 누적 대결 수 + 좌우 기준값 보정 (common.asof_prior 와 같은 방식, 키만 (투수, 타자) 쌍)."""
    left = rows.sort_values("ok")
    right = counts[["pair", "ok"] + COUNT_COLS].sort_values("ok")
    m = pd.merge_asof(left, right, on="ok", by="pair", allow_exact_matches=False)
    m[COUNT_COLS] = m[COUNT_COLS].fillna(0).astype(int)

    key, base_woba, base_iso = handedness_base(m)
    ab = m["ab"].to_numpy(float)
    w = ab / (ab + BASE_K)
    with np.errstate(invalid="ignore", divide="ignore"):
        den = m["ab"] + m["bb"] + m["sf"]
        raw_woba = np.where(den > 0, (W_1B * m["n1"] + W_2B * m["n2"] + W_3B * m["n3"] + W_HR * m["hr"]
                                      + W_BB * m["bb"]) / den, 0.0)
        raw_iso = np.where(ab > 0, (m["n2"] + 2 * m["n3"] + 3 * m["hr"]) / ab, 0.0)
        m["avg"] = np.where(ab > 0, m["h"] / ab, np.nan)
    m["handedness_key"] = key
    m["base_woba"] = base_woba
    m["woba"] = w * raw_woba + (1 - w) * base_woba
    m["iso"] = w * raw_iso + (1 - w) * base_iso
    return m


def verify(stats):
    """2025 실제 대결: 그 경기 첫 타석의 데이터셋 값 == 이 표 값 ?"""
    ds = pd.read_parquet(PA_V3_1_PARQUET, columns=[
        "game_id", "_orig_idx", "pitcher_id", "batter_id", "handedness_key", "prior_matchup_ab", "prior_matchup_hits",
        "matchup_woba_shrunk", "matchup_iso_shrunk"])
    ds = ds[ds["game_id"].str[:4] == SEASON].sort_values("_orig_idx").drop_duplicates(KEY)
    m = ds.merge(stats, on=KEY, how="left", validate="one_to_one")
    missing = int(m["ab"].isna().sum())
    m = m.dropna(subset=["ab"])
    count_bad = int(((m["prior_matchup_ab"] != m["ab"]) | (m["prior_matchup_hits"] != m["h"])).sum())
    same_key = m["handedness_key_x"] == m["handedness_key_y"]
    same = m[same_key]
    err = max((same["matchup_woba_shrunk"] - same["woba"]).abs().max(), (same["matchup_iso_shrunk"] - same["iso"]).abs().max())
    print(f"[검증] 2025 실제 대결 {len(m) + missing}쌍: 표에 없음 {missing}, 타수·안타 불일치 {count_bad}, "
          f"좌우 키가 같은 {len(same)}쌍의 wOBA·ISO 최대 오차 {err:.2e}")
    print(f"       좌우 키가 다른 {int((~same_key).sum())}쌍은 데이터셋 손 정보가 타석마다 흔들려서다 "
          f"(예: 사이드암을 R/U 로 번갈아, 좌타자를 한 타석만 R). 여기는 players 의 선수별 값 하나를 쓴다.")
    return missing, count_bad, err


def main():
    rows = candidate_pairs()
    stats = build(rows, per_game_pair_counts())
    missing, count_bad, err = verify(stats)
    if missing or count_bad or err > 1e-6:
        sys.exit("[검증 실패] 상대 전적이 데이터셋과 다릅니다.")

    for c in ("avg", "woba", "iso", "base_woba"):
        stats[c] = stats[c].round(4)
    cols = ["game_id", "pitcher_id", "batter_id", "handedness_key", "pa", "ab", "h", "avg", "woba", "iso", "base_woba"]
    stats = stats[cols].sort_values(KEY)
    assert not stats.duplicated(KEY).any()
    stats.to_csv(OUTPUT_DIR / "matchups.csv", index=False)
    print(f"matchups: {len(stats)}행 ({stats['game_id'].nunique()}경기), "
          f"상대 전적 있음 {int((stats['pa'] > 0).sum())}행 ({(stats['pa'] > 0).mean():.0%})")


if __name__ == "__main__":
    main()
