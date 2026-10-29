"""li_percentile 테이블용 CSV 생성 — LI 값 → 리그 백분위 (명세 v0.3 §3-5 "리그 상위 X%").

정의
  percentile(v) = ⌊100 × (bandit train 구간 타석 중 LI < v 인 비율)⌋
  = "이 상황보다 덜 긴박했던 타석이 몇 %인가". 화면의 "리그 상위 X%" 는 100 - percentile.
  '<' (미만) + 내림이라 실제 LI 최댓값(9.79)에서도 99 → "상위 1%" 로 보인다 ("상위 0%" 방지).

기준 분포
  kbo-pitcher-change-ai-/bandit_game_split.csv 의 train 경기(2016~2023 초) 타석 약 39만 개.
  서비스 대상인 2025 는 test 구간이라 넣지 않는다 (quality_p 와 같은 train-frozen 원칙).

키
  li_value 는 0.00 ~ 10.00, 0.01 간격 (1,001행). 서버는 LI 를 소수 둘째 자리로 반올림해 찾는다.
  LI 는 we_li_table / game_pa 와 같은 정규화 값 (리그 평균 = 1.0, 최대 9.79).

실행
  python etl/06_li_percentile.py   (다른 단계와 독립)
"""
import numpy as np
import pandas as pd

from config import MODEL_REPO, OUTPUT_DIR, PA_PARQUET

SPLIT_CSV = MODEL_REPO / "bandit_game_split.csv"
LI_GRID_MAX = 10.0
LI_GRID_STEP = 0.01


def train_li():
    split = pd.read_csv(SPLIT_CSV, encoding="utf-8-sig", usecols=["game_id", "split"])
    train_ids = set(split.loc[split["split"] == "train", "game_id"])
    pa = pd.read_parquet(PA_PARQUET, columns=["game_id", "leverage_index"])
    li = pa.loc[pa["game_id"].isin(train_ids), "leverage_index"].dropna().to_numpy()
    print(f"train 경기 {len(train_ids)}개, 타석 {len(li)}개, LI 평균 {li.mean():.3f}, 최대 {li.max():.2f}")
    return np.sort(li)


def build_table(li_sorted):
    n_steps = round(LI_GRID_MAX / LI_GRID_STEP)
    grid = np.arange(n_steps + 1) / round(1 / LI_GRID_STEP)       # 0.00, 0.01, ... 10.00 (누적 오차 없음)
    below = np.searchsorted(li_sorted, grid, side="left")          # LI < v 인 타석 수
    percentile = np.floor(100 * below / len(li_sorted)).astype(int)
    table = pd.DataFrame({"li_value": grid, "percentile": percentile})

    for v in (0.5, 1.0, 1.5, 2.0, 3.0, 5.0, 9.79):
        p = table.loc[np.isclose(table["li_value"], v), "percentile"].item()
        print(f"  LI {v:>4} → {p:>2} (리그 상위 {100 - p}%)")
    return table


def main():
    table = build_table(train_li())
    assert table["percentile"].between(0, 100).all() and table["percentile"].is_monotonic_increasing
    table.to_csv(OUTPUT_DIR / "li_percentile.csv", index=False, float_format="%.2f")
    print(f"li_percentile: {len(table)}행")


if __name__ == "__main__":
    main()
