"""pitcher_game_stats 테이블용 CSV 생성 — 경기 시작 시점의 투수 카드 정보 .

행
  game_bullpen 의 (경기, 투수) 전부 = 그 경기에 나올 수 있었던 투수. 선발·명단 밖 등판 포함.

시점 규칙 (미래 정보 차단)
  모든 값은 "그 경기 전까지" 기록만 쓴다. 같은 날 더블헤더 2차전은 1차전 기록을 포함한다
  (경기 순서 = 날짜 → game_id, 모델 저장소의 prior_* 와 같은 순서).

항목
  season          2025 시즌 그 경기 전까지: 등판 수, 아웃 수(이닝 = outs/3), 삼진, 볼넷, 사구, 피안타, 피홈런, WHIP
                  WHIP = (피안타 + 볼넷) / 이닝, 사구는 넣지 않는다 (공식 정의).
                  ERA 는 만들지 않는다 — 원본에 자책점(earned run) 구분이 없어 계산할 수 없다. API 는 null.
  vs_batter_hand  2025 시즌 그 경기 전까지 좌/우 타자 상대 타석 수, 타율, wOBA, ISO.
                  wOBA 가중치·타수 정의·스위치 타자 규칙은 KBO_Crawler build_matchup_history.py 와 같다
                  (볼넷에 사구·고의4구 포함, 스위치 타자는 우투수 상대 좌타석 / 좌투수 상대 우타석, 언더핸드 상대는 제외).
  workload        2025 시즌 그 경기 전까지: 타석당 투구 수, 마지막 등판 후 쉰 날(days_rest),
                  직전까지 연속 등판 일수, 최근 7일 등판 수, 최근 3일 투구 수.
  quality         kbo-pitcher-change-ai- 의 불펜 quality_p 공식 그대로 (2016~ 통산 Option B WPA, train 기준분포).
                  카드는 "구원으로 넣으면" 정보라 선발 투수도 불펜 공식을 쓴다.
                  quality_percentile = round(quality_p × 50)  (quality_p = 2 × 백분위 이므로).

검증
  실제로 구원 등판한 (경기, 투수)에서 이 스크립트의 quality_p 가 데이터셋 quality_p 와 같은지 확인한다.

실행
  python etl/07_pitcher_game_stats.py   (04_bullpen_lineup.py 다음 — game_bullpen.csv, game_pa.csv 사용)
"""
import sys
from collections import defaultdict
from datetime import date, timedelta

import numpy as np
import pandas as pd

from config import MODEL_REPO, OUTPUT_DIR, PITCHER_HISTORY_PARQUET, PRIOR_WPA_OPTIONB_PARQUET, SEASON

sys.path.insert(0, str(MODEL_REPO))
from data_analysis.methods.pitcher_quality import (  # noqa: E402
    BASELINE_PATH_BULLPEN, apply_shrinkage, load_quality_baseline,
)

# build_matchup_history.py 와 동일
W_HR, W_3B, W_2B, W_1B, W_BB = 1.70, 1.37, 1.08, 0.77, 0.62
AB_RESULTS = {"1B", "2B", "3B", "HR", "SO", "GO", "FO", "OUT", "GDP"}
HIT_RESULTS = {"1B", "2B", "3B", "HR"}
PA_RESULTS = AB_RESULTS | {"BB", "SF"}

KEY = ["game_id", "pitcher_id"]


def order_key(game_ids):
    """경기 순서 정수: YYYYMMDD × 10 + 더블헤더 번호 (game_id 13번째 자리, 보통 0)."""
    return game_ids.str[:8].astype(np.int64) * 10 + game_ids.str[12].astype(np.int64)


def asof_prior(rows, events, value_cols):
    """rows 각 (경기, 투수)에 대해, 그 경기 '전' 마지막 events 행의 누적값을 붙인다. 없으면 0."""
    left = rows[KEY + ["ok"]].sort_values("ok")
    right = events[["pitcher_id", "ok"] + value_cols].sort_values("ok")
    m = pd.merge_asof(left, right, on="ok", by="pitcher_id", allow_exact_matches=False)
    m[value_cols] = m[value_cols].fillna(0)
    return m[KEY + value_cols]


# ── 등판 단위 (2016~2025) ───────────────────────────────────────────────
def load_appearances():
    hist = pd.read_parquet(PITCHER_HISTORY_PARQUET, columns=[
        "pitcher_id", "game_id", "half", "appearance_order", "n_pa", "outs_recorded", "total_pitches"])
    opb = pd.read_parquet(PRIOR_WPA_OPTIONB_PARQUET, columns=[
        "pitcher_id", "game_id", "half", "appearance_order", "app_wpa_optionb"])
    app = hist.merge(opb, on=["pitcher_id", "game_id", "half", "appearance_order"], how="left", validate="one_to_one")
    # 한 경기에 같은 투수가 두 줄인 원본 이상치는 합친다
    app = app.groupby(KEY, as_index=False).agg(
        n_pa=("n_pa", "sum"), outs=("outs_recorded", "sum"), pitches=("total_pitches", "sum"),
        wpa=("app_wpa_optionb", "sum"))
    app["ok"] = order_key(app["game_id"])
    app["date"] = pd.to_datetime(app["game_id"].str[:8])
    return app.sort_values(["pitcher_id", "ok"]).reset_index(drop=True)


def quality(rows, app):
    """불펜 quality_p (pitcher_quality.compute_quality_p 의 불펜 분기를 벡터로)."""
    app = app.copy()
    g = app.groupby("pitcher_id")
    app["c_wpa"], app["c_pa"] = g["wpa"].cumsum(), g["n_pa"].cumsum()
    m = asof_prior(rows, app, ["c_wpa", "c_pa"])

    baseline = load_quality_baseline(MODEL_REPO / BASELINE_PATH_BULLPEN)   # 원본 경로가 저장소 기준 상대 경로
    ok = m["c_pa"] >= 1
    wpa_per_pa = -m["c_wpa"] / m["c_pa"].where(ok)
    shrunk = apply_shrinkage(wpa_per_pa, m["c_pa"], baseline["league_mean_wpa_per_pa"], baseline["shrinkage_k"])
    pct = np.interp(shrunk, baseline["percentile_grid_values"], baseline["percentile_grid_pct"]) / 100.0
    m["quality_p"] = np.where(ok, 2.0 * pct, np.nan)
    m["quality_percentile"] = np.round(m["quality_p"] * 50).astype("Int64")
    return m[KEY + ["quality_p", "quality_percentile"]]


def season_appearances(rows, app):
    """2025 시즌 등판·아웃·투구·피로도."""
    s = app[app["game_id"].str[:4] == SEASON].copy()
    g = s.groupby("pitcher_id")
    s["g"] = g.cumcount() + 1
    s["c_outs"], s["c_pa"], s["c_pitches"] = g["outs"].cumsum(), g["n_pa"].cumsum(), g["pitches"].cumsum()
    m = asof_prior(rows, s, ["g", "c_outs", "c_pa", "c_pitches"])
    m = m.rename(columns={"c_outs": "outs"})
    m["avg_pitches_per_pa"] = (m["c_pitches"] / m["c_pa"].where(m["c_pa"] > 0)).round(2)

    by_pitcher = defaultdict(list)                       # 투수 → [(경기순서, 날짜, 투구수)]
    for r in s.itertuples():
        by_pitcher[r.pitcher_id].append((r.ok, r.date.date(), r.pitches))

    fatigue = []
    for r in rows.itertuples():
        today = date(int(r.game_id[:4]), int(r.game_id[4:6]), int(r.game_id[6:8]))
        before = [(d, p) for k, d, p in by_pitcher.get(r.pitcher_id, []) if k < r.ok]
        days = {d for d, _ in before}
        last = max(days) if days else None
        consecutive, d = 0, today - timedelta(days=1)
        while d in days:
            consecutive, d = consecutive + 1, d - timedelta(days=1)
        fatigue.append({
            "game_id": r.game_id, "pitcher_id": r.pitcher_id,
            "days_rest": max((today - last).days - 1, 0) if last else None,  # 어제(또는 더블헤더 1차전) 던졌으면 0
            "consecutive_days": consecutive,
            "appearances_last_7d": sum(1 for d0, _ in before if (today - d0).days <= 7),
            "pitches_last_3d": sum(p for d0, p in before if (today - d0).days <= 3),
        })
    f = pd.DataFrame(fatigue)
    f["days_rest"] = f["days_rest"].astype("Int64")
    return m[KEY + ["g", "outs", "avg_pitches_per_pa"]].merge(f, on=KEY)


# ── 타석 단위 (2025, game_pa.csv) ────────────────────────────────────────
def load_pa():
    pa = pd.read_csv(OUTPUT_DIR / "game_pa.csv",
                     usecols=["game_id", "pitcher_id", "batter_id", "result", "relay_text", "is_partial_pa"])
    pa = pa[~pa["is_partial_pa"]].copy()                 # 끝나지 않은 타석은 결과가 복사된 값이라 뺀다
    players = pd.read_csv(OUTPUT_DIR / "players.csv", usecols=["player_id", "throws", "hit_type"])
    pa = pa.merge(players.rename(columns={"player_id": "pitcher_id", "throws": "p_throws"})[["pitcher_id", "p_throws"]],
                  on="pitcher_id", how="left")
    pa = pa.merge(players.rename(columns={"player_id": "batter_id", "hit_type": "b_hit"})[["batter_id", "b_hit"]],
                  on="batter_id", how="left")
    side = pa["b_hit"].copy()
    sw = side.eq("S")
    side[sw & pa["p_throws"].eq("R")] = "L"
    side[sw & pa["p_throws"].eq("L")] = "R"
    pa["side"] = side.where(side.isin(["L", "R"]))      # 언더핸드 상대 스위치 타석·미상은 좌우 집계에서 빠짐

    r = pa["result"]
    pa["is_pa"] = r.isin(PA_RESULTS)
    pa["ab"] = r.isin(AB_RESULTS)
    pa["h"] = r.isin(HIT_RESULTS)
    pa["hbp"] = r.eq("BB") & pa["relay_text"].fillna("").str.contains("몸에 맞는 볼")
    pa["bb_all"] = r.eq("BB")                            # wOBA 용 (사구·고의4구 포함)
    pa["bb"] = pa["bb_all"] & ~pa["hbp"]                 # 카드·WHIP 용
    pa["so"] = r.eq("SO")
    pa["sf"] = r.eq("SF")
    pa["n1"], pa["n2"], pa["n3"], pa["hr"] = r.eq("1B"), r.eq("2B"), r.eq("3B"), r.eq("HR")
    pa["ok"] = order_key(pa["game_id"])
    return pa


def season_counts(rows, pa):
    cols = ["so", "bb", "hbp", "h", "hr"]
    per_game = pa.groupby(["pitcher_id", "game_id", "ok"], as_index=False)[cols].sum().sort_values(["pitcher_id", "ok"])
    per_game[cols] = per_game.groupby("pitcher_id")[cols].cumsum()
    return asof_prior(rows, per_game, cols)


def vs_hand(rows, pa):
    cols = ["is_pa", "ab", "h", "n1", "n2", "n3", "hr", "bb_all", "sf"]
    out = rows[KEY].copy()
    for hand in ("L", "R"):
        sub = pa[pa["side"] == hand]
        per_game = sub.groupby(["pitcher_id", "game_id", "ok"], as_index=False)[cols].sum().sort_values(["pitcher_id", "ok"])
        per_game[cols] = per_game.groupby("pitcher_id")[cols].cumsum()
        c = asof_prior(rows, per_game, cols)
        ab = c["ab"].where(c["ab"] > 0)
        den = (c["ab"] + c["bb_all"] + c["sf"]).where(lambda x: x > 0)
        p = f"vs_{hand.lower()}_"
        c[p + "pa"] = c["is_pa"].astype(int)
        c[p + "avg"] = (c["h"] / ab).round(3)
        c[p + "woba"] = ((W_1B * c["n1"] + W_2B * c["n2"] + W_3B * c["n3"] + W_HR * c["hr"] + W_BB * c["bb_all"]) / den).round(3)
        c[p + "iso"] = ((c["n2"] + 2 * c["n3"] + 3 * c["hr"]) / ab).round(3)
        out = out.merge(c[KEY + [p + "pa", p + "avg", p + "woba", p + "iso"]], on=KEY)
    return out


def verify_quality(stats):
    """실제 구원 등판 행: 이 스크립트 quality_p == 데이터셋 quality_p ?"""
    from config import PA_PARQUET
    ds = pd.read_parquet(PA_PARQUET, columns=["game_id", "pitcher_id", "is_starter", "quality_p"])
    ds = ds[(ds["game_id"].str[:4] == SEASON) & ~ds["is_starter"]].drop_duplicates(KEY)
    m = ds.merge(stats[KEY + ["quality_p"]], on=KEY, suffixes=("_ds", ""))
    both = m["quality_p_ds"].notna() & m["quality_p"].notna()
    err = (m.loc[both, "quality_p_ds"] - m.loc[both, "quality_p"]).abs()
    nan_mismatch = (m["quality_p_ds"].isna() != m["quality_p"].isna()).sum()
    print(f"[검증] 2025 구원 등판 {len(m)}건: quality_p 최대 오차 {err.max():.2e}, "
          f"오차>1e-6 {int((err > 1e-6).sum())}건, NaN 불일치 {nan_mismatch}건")
    if err.max() > 1e-4 or nan_mismatch:
        sys.exit("[검증 실패] quality_p 가 데이터셋과 다릅니다.")


def main():
    rows = pd.read_csv(OUTPUT_DIR / "game_bullpen.csv", usecols=KEY).drop_duplicates()
    rows["ok"] = order_key(rows["game_id"])

    app = load_appearances()
    pa = load_pa()
    stats = (season_appearances(rows, app)
             .merge(season_counts(rows, pa), on=KEY)
             .merge(vs_hand(rows, pa), on=KEY)
             .merge(quality(rows, app), on=KEY))

    ip = stats["outs"] / 3
    stats["whip"] = ((stats["h"] + stats["bb"]) / ip.where(ip > 0)).round(2)
    int_cols = ["g", "outs", "so", "bb", "hbp", "h", "hr", "consecutive_days", "appearances_last_7d", "pitches_last_3d"]
    stats[int_cols] = stats[int_cols].astype(int)
    verify_quality(stats)
    stats["quality_p"] = stats["quality_p"].round(4)

    cols = ["game_id", "pitcher_id", "g", "outs", "so", "bb", "hbp", "h", "hr", "whip",
            "vs_l_pa", "vs_l_avg", "vs_l_woba", "vs_l_iso", "vs_r_pa", "vs_r_avg", "vs_r_woba", "vs_r_iso",
            "avg_pitches_per_pa", "days_rest", "consecutive_days", "appearances_last_7d", "pitches_last_3d",
            "quality_p", "quality_percentile"]
    stats = stats[cols].sort_values(KEY)
    assert not stats.duplicated(KEY).any() and len(stats) == len(rows)
    stats.to_csv(OUTPUT_DIR / "pitcher_game_stats.csv", index=False)

    print(f"pitcher_game_stats: {len(stats)}행 ({stats['game_id'].nunique()}경기, 투수 {stats['pitcher_id'].nunique()}명)")
    print(f"  시즌 첫 등판 전(기록 없음) {int((stats['g'] == 0).sum())}행, quality 없음 {int(stats['quality_p'].isna().sum())}행")


if __name__ == "__main__":
    main()
