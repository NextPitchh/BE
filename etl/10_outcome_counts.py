"""log5 주사위 재료 — 선수별 타석 결과 수(outcome_counts)와 리그 기준 비율(league_outcome_rates).

log5 (다항 odds-ratio)
  P(결과 i) ∝ 타자 비율_i × 투수 비율_i / 리그 비율_i   → 9종 합이 1 이 되게 나눈다.
  선수 비율은 표본이 작을 때 리그 쪽으로 당긴다: (선수 횟수_i + k × 리그_i) / (선수 타석 + k).
  계산은 서버(Spring)가 한다. 여기서는 횟수와 리그 비율만 만든다.

결과 분류 (명세 9종, 희생플라이 없음)
  1B 2B 3B HR BB SO FO GB   — 선수 횟수는 8칸. 땅볼(GO)과 병살(GDP)은 GB 하나로 센다.
  병살은 1루 주자가 있고 2아웃 전일 때만 나올 수 있어서, 서버가 그 상황에서만 GB 를 리그 병살 비율로 GO/GDP 로 나눈다.
  SF → FO, 볼넷에는 사구·고의4구 포함, 끝나지 않은 타석·UNK 는 뺀다.

outcome_counts
  행 = (경기, 선수, 역할 B/P). 타자는 game_batting_order, 투수는 game_bullpen 의 선수.
  값 = 그 경기 "전" 2025 시즌 누적 (더블헤더 2차전은 1차전 포함). 07·08 과 같은 시점 규칙.

league_outcome_rates
  2024 시즌 전체 (서비스 대상 2025 와 겹치지 않게). 2024 원본도 03 과 같은 방식으로 중계 문구에서 땅볼/플라이를 나눈다.
  dp_share = 병살 가능 상황(1루 주자, 0~1아웃)의 땅볼 아웃 중 병살 비율.

검증
  2025 실제 타석으로 k 별 예측 성능(log loss)을 리그 비율만 쓸 때와 비교해 출력한다.

실행
  python etl/10_outcome_counts.py   (03·04 다음 — game_pa.csv, game_bullpen.csv, game_batting_order.csv 사용)
"""
import importlib.util
import sys
from pathlib import Path

import numpy as np
import pandas as pd

from common import asof_prior, order_key
from config import OUTPUT_DIR

LEAGUE_SEASON = "2024"
CLASSES = ["1B", "2B", "3B", "HR", "BB", "SO", "FO", "GB"]
TO_CLASS = {"1B": "1B", "2B": "2B", "3B": "3B", "HR": "HR", "BB": "BB", "SO": "SO",
            "FO": "FO", "SF": "FO", "GO": "GB", "GDP": "GB"}
COUNT_COLS = ["pa"] + [f"c_{c.lower()}" for c in CLASSES]


def classify(pa):
    """결과 → 8칸. 끝나지 않은 타석, UNK, 분류 안 된 OUT 은 None."""
    cls = pa["result"].map(TO_CLASS)
    if "is_partial_pa" in pa:
        cls = cls.where(~pa["is_partial_pa"])
    return cls


def dp_opportunity(pa):
    return pa["base_state"].astype(str).str.contains("1") & (pa["out_count"] < 2)


# ── 2024 리그 기준 ─────────────────────────────────────────────────────
def league_2024():
    """03_games_pa.py 의 중계 문구 분류를 2024 에 그대로 적용한다."""
    spec = importlib.util.spec_from_file_location("games_pa", Path(__file__).with_name("03_games_pa.py"))
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    m.SEASON = LEAGUE_SEASON
    pa = m.load_pa()
    pa, _ = m.attach_relay_text(pa)
    pa = m.assign_result(pa)
    pa["cls"] = classify(pa)
    pa = pa.dropna(subset=["cls"])

    counts = pa["cls"].value_counts()
    rates = {f"r_{c.lower()}": counts.get(c, 0) / len(pa) for c in CLASSES}
    opp = pa[dp_opportunity(pa) & pa["result"].isin(["GO", "GDP"])]
    dp_share = (opp["result"] == "GDP").mean()
    row = {"season": LEAGUE_SEASON, "pa": len(pa), **rates, "dp_share": dp_share}
    print(f"[리그 {LEAGUE_SEASON}] 타석 {len(pa)}, " + ", ".join(f"{c} {rates[f'r_{c.lower()}']:.3f}" for c in CLASSES)
          + f", 병살 비율(기회 중 땅볼) {dp_share:.3f}")
    return row


# ── 2025 선수별 경기 전 누적 ─────────────────────────────────────────────
def load_pa_2025():
    pa = pd.read_csv(OUTPUT_DIR / "game_pa.csv", dtype={"base_state": str}, usecols=[
        "game_id", "batter_id", "pitcher_id", "out_count", "base_state", "result", "is_partial_pa"])
    pa["cls"] = classify(pa)
    return pa.dropna(subset=["cls"]).reset_index(drop=True)


def pregame_counts(pa, rows, player_col):
    onehot = pd.get_dummies(pa["cls"]).reindex(columns=CLASSES, fill_value=0).astype(int)
    onehot.columns = COUNT_COLS[1:]
    d = pd.concat([pa[["game_id", player_col]], onehot], axis=1).assign(pa=1)
    g = d.groupby(["game_id", player_col], as_index=False)[COUNT_COLS].sum()
    g["ok"] = order_key(g["game_id"])
    g = g.sort_values([player_col, "ok"])
    g[COUNT_COLS] = g.groupby(player_col)[COUNT_COLS].cumsum()
    r = rows.rename(columns={"player_id": player_col})
    out = asof_prior(r, g, COUNT_COLS, by=player_col).rename(columns={player_col: "player_id"})
    out[COUNT_COLS] = out[COUNT_COLS].astype(int)
    return out


def build_counts(pa):
    batters = pd.read_csv(OUTPUT_DIR / "game_batting_order.csv", usecols=["game_id", "batter_id"]).drop_duplicates()
    pitchers = pd.read_csv(OUTPUT_DIR / "game_bullpen.csv", usecols=["game_id", "pitcher_id"]).drop_duplicates()
    parts = []
    for role, df, col in (("B", batters, "batter_id"), ("P", pitchers, "pitcher_id")):
        rows = df.rename(columns={col: "player_id"})
        rows["ok"] = order_key(rows["game_id"])
        c = pregame_counts(pa, rows, col)
        c.insert(2, "role", role)
        parts.append(c)
    return pd.concat(parts, ignore_index=True)


# ── 예측 성능 확인 (서버의 log5 와 같은 식) ──────────────────────────────
def log5_probs(b, p, league, k):
    lr = np.array([league[f"r_{c.lower()}"] for c in CLASSES])
    bc, pc = b[COUNT_COLS[1:]].to_numpy(float), p[COUNT_COLS[1:]].to_numpy(float)
    br = (bc + k * lr) / (b["pa"].to_numpy(float)[:, None] + k)
    pr = (pc + k * lr) / (p["pa"].to_numpy(float)[:, None] + k)
    x = br * pr / lr
    return x / x.sum(axis=1, keepdims=True)


def evaluate(pa, counts, league):
    """2025 실제 타석: 실제 결과의 예측 확률로 log loss (낮을수록 좋음). 병살은 기회일 때만 나눈다."""
    b = counts[counts["role"] == "B"].drop(columns="role").rename(columns={"player_id": "batter_id"})
    p = counts[counts["role"] == "P"].drop(columns="role").rename(columns={"player_id": "pitcher_id"})
    t = pa.merge(b, on=["game_id", "batter_id"]).merge(p, on=["game_id", "pitcher_id"], suffixes=("_b", "_p"))
    bb = t[[f"{c}_b" for c in COUNT_COLS]].set_axis(COUNT_COLS, axis=1)
    pp = t[[f"{c}_p" for c in COUNT_COLS]].set_axis(COUNT_COLS, axis=1)
    opp = dp_opportunity(t).to_numpy()
    actual = t["result"].replace({"SF": "FO"}).to_numpy()
    col = {c: i for i, c in enumerate(CLASSES)}

    def loss(probs):
        gb = probs[:, col["GB"]]
        share = np.where(opp, league["dp_share"], 0.0)
        pick = np.empty(len(t))
        for i, a in enumerate(actual):
            if a == "GO":
                pick[i] = gb[i] * (1 - share[i])
            elif a == "GDP":
                pick[i] = gb[i] * share[i]
            else:
                pick[i] = probs[i, col[a]]
        return float(-np.log(np.clip(pick, 1e-9, 1)).mean())

    league_only = log5_probs(bb.assign(pa=0) * 0, pp.assign(pa=0) * 0, league, 1)
    print(f"[성능] 2025 실제 타석 {len(t)}개 log loss — 리그 비율만: {loss(league_only):.4f}")
    for k in (50, 100, 200, 400):
        print(f"        log5 k={k:<3}: {loss(log5_probs(bb, pp, league, k)):.4f}")


def main():
    league = league_2024()
    pa = load_pa_2025()
    counts = build_counts(pa)
    assert not counts.duplicated(["game_id", "player_id", "role"]).any()
    evaluate(pa, counts, league)

    counts.sort_values(["game_id", "role", "player_id"]).to_csv(OUTPUT_DIR / "outcome_counts.csv", index=False)
    pd.DataFrame([league]).to_csv(OUTPUT_DIR / "league_outcome_rates.csv", index=False, float_format="%.6f")
    print(f"outcome_counts: {len(counts)}행 (타자 {int((counts.role == 'B').sum())}, 투수 {int((counts.role == 'P').sum())}), "
          f"league_outcome_rates: 1행")


if __name__ == "__main__":
    main()
