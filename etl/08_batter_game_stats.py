"""batter_game_stats 테이블용 CSV 생성 — 경기 시작 시점의 타자 카드 정보 (명세 v0.3 §2-3 Batter).

행
  game_batting_order 의 (경기, 타자) 전부 = 그 경기에 실제 타석에 선 타자. 대타 포함.

시점 규칙 (미래 정보 차단)
  모든 값은 "그 경기 전까지" 2025 시즌 기록만 쓴다. 더블헤더 2차전은 1차전 기록을 포함한다
  (07_pitcher_game_stats.py 와 같은 규칙).

항목
  season      타석, 타수, 안타, 홈런, 타율, 출루율, 장타율, OPS
              타수·안타 정의와 볼넷(사구·고의4구 포함)은 KBO_Crawler build_matchup_history.py 와 같다.
              출루율 = (안타 + 볼넷) / (타수 + 볼넷 + 희생플라이)
  recent_avg  직전 10경기(타석이 있었던 경기) 타율. recent_ab 는 그 타수.
              데이터셋 batter_recent_avg 는 쓰지 않는다 — 네이버 "오늘 타율(todayHra)" 이라 경기 중에 바뀌고
              그 경기 결과가 섞여 있다 (2025 타자-경기 16,000여 개 중 2/3 가 한 경기 안에서 값이 바뀜).

참고
  원본에는 공 없이 선언된 자동 고의4구 타석이 없다 (2025 약 93건 추정). 볼넷이 그만큼 적어 출루율이 아주 조금 낮다.

실행
  python etl/08_batter_game_stats.py   (04_bullpen_lineup.py 다음 — game_batting_order.csv, game_pa.csv 사용)
"""
import pandas as pd

from common import AB_RESULTS, HIT_RESULTS, PA_RESULTS, asof_prior, order_key
from config import OUTPUT_DIR

KEY = ["game_id", "batter_id"]
RECENT_GAMES = 10
COUNT_COLS = ["pa", "ab", "h", "hr", "bb", "sf", "tb"]


def per_game_counts():
    pa = pd.read_csv(OUTPUT_DIR / "game_pa.csv", usecols=["game_id", "batter_id", "result", "is_partial_pa"])
    pa = pa[~pa["is_partial_pa"]]                        # 끝나지 않은 타석은 결과가 복사된 값이라 뺀다
    r = pa["result"]
    pa = pa.assign(
        pa=r.isin(PA_RESULTS), ab=r.isin(AB_RESULTS), h=r.isin(HIT_RESULTS), hr=r.eq("HR"),
        bb=r.eq("BB"), sf=r.eq("SF"),
        tb=r.map({"1B": 1, "2B": 2, "3B": 3, "HR": 4}).fillna(0).astype(int),
    )
    g = pa.groupby(KEY, as_index=False)[COUNT_COLS].sum()
    g["ok"] = order_key(g["game_id"])
    return g.sort_values(["batter_id", "ok"]).reset_index(drop=True)


def season(rows, games):
    cum = games.copy()
    cum[COUNT_COLS] = cum.groupby("batter_id")[COUNT_COLS].cumsum()
    s = asof_prior(rows, cum, COUNT_COLS, by="batter_id")
    ab = s["ab"].where(s["ab"] > 0)
    obp_den = (s["ab"] + s["bb"] + s["sf"]).where(lambda x: x > 0)
    s["avg"] = (s["h"] / ab).round(3)
    s["obp"] = ((s["h"] + s["bb"]) / obp_den).round(3)
    s["slg"] = (s["tb"] / ab).round(3)
    s["ops"] = (s["obp"] + s["slg"]).round(3)       # 반올림한 출루율 + 장타율 (화면 표기와 같게)
    return s


def recent(rows, games):
    """직전 10경기 누적 = 시즌 누적 - 11경기 전까지의 누적."""
    cum = games.copy()
    g = cum.groupby("batter_id")
    cum["c_ab"], cum["c_h"] = g["ab"].cumsum(), g["h"].cumsum()
    g = cum.groupby("batter_id")
    cum["recent_ab"] = cum["c_ab"] - g["c_ab"].shift(RECENT_GAMES).fillna(0)
    cum["recent_h"] = cum["c_h"] - g["c_h"].shift(RECENT_GAMES).fillna(0)
    r = asof_prior(rows, cum, ["recent_ab", "recent_h"], by="batter_id")
    r["recent_avg"] = (r["recent_h"] / r["recent_ab"].where(r["recent_ab"] > 0)).round(3)
    return r[KEY + ["recent_ab", "recent_avg"]]


def main():
    rows = pd.read_csv(OUTPUT_DIR / "game_batting_order.csv", usecols=KEY).drop_duplicates()
    rows["ok"] = order_key(rows["game_id"])
    games = per_game_counts()

    stats = season(rows, games).merge(recent(rows, games), on=KEY)
    int_cols = COUNT_COLS + ["recent_ab"]
    stats[int_cols] = stats[int_cols].astype(int)

    cols = ["game_id", "batter_id", "pa", "ab", "h", "hr", "bb", "sf", "tb",
            "avg", "obp", "slg", "ops", "recent_ab", "recent_avg"]
    stats = stats[cols].sort_values(KEY)
    assert not stats.duplicated(KEY).any() and len(stats) == len(rows)
    stats.to_csv(OUTPUT_DIR / "batter_game_stats.csv", index=False)

    print(f"batter_game_stats: {len(stats)}행 ({stats['game_id'].nunique()}경기, 타자 {stats['batter_id'].nunique()}명)")
    print(f"  시즌 첫 타석 전(기록 없음) {int((stats['pa'] == 0).sum())}행")


if __name__ == "__main__":
    main()
