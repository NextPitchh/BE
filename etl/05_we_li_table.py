"""we_li_table 테이블용 CSV 생성 — 상황(이닝·초말·아웃·주자·점수차) → 승리확률(WE)·LI.

왜 필요한가
  게임이 실제 기록에서 갈라지면(분기) 그 뒤 상황은 데이터에 없다. 서버가 매 타석
  WE(승률 그래프)와 LI(레버리지 게이지)를 다시 구해야 하는데, 계산 로직을 자바로
  옮기면 파이썬 원본과 어긋날 위험이 있다. 그래서 가능한 상황 전부를 파이썬 원본
  함수로 미리 계산해 표로 넣고, 서버는 표에서 찾기만 한다.

계산 (kbo-pitcher-change-ai- 원본 함수를 그대로 호출)
  WE = we_re_lookup.get_we_with_out_correction  (문형우 외 2016 + 아웃 보정 Option B)
  LI = build_li_baseline.build_lookup_table / li_baseline.json 의 li_league_avg  (리그 평균 = 1.0)
  둘 다 공격팀 관점. 점수차는 원본과 같이 ±4 로 잘라서 찾는다 (5점 차 이상은 4점 차와 같은 값).

범위
  이닝 1~9 × 초/말 × 아웃 0~2 × 주자 8 × 점수차 -4~+4 = 3,888행.
  원본 데이터에 연장 타석이 없어 10회 이후는 다루지 않는다 (9회말 종료 동점 = 0.5).

검증
  2025 game_pa 의 we_before·leverage_index 를 이 표로 다시 찾아 전부 같은지 확인한다.

실행
  python etl/05_we_li_table.py   (03_games_pa.py 다음 — 검증에 output/game_pa.csv 사용)
"""
import json
import sys

import numpy as np
import pandas as pd

from config import MODEL_REPO, OUTPUT_DIR

sys.path.insert(0, str(MODEL_REPO))
from data_analysis.methods.build_li_baseline import build_lookup_table  # noqa: E402
from data_analysis.methods.leverage_index import load_league_distribution  # noqa: E402
from data_analysis.methods.we_re_lookup import SCORE_DIFF_MAX, SCORE_DIFF_MIN, get_we_with_out_correction  # noqa: E402

RESULTS = MODEL_REPO / "data_analysis" / "results"
KEY = ["inning", "half", "out_count", "base_state", "score_diff"]


def build_table():
    league = load_league_distribution(RESULTS / "league_pa_distribution.json")
    li_league_avg = json.loads((RESULTS / "li_baseline.json").read_text(encoding="utf-8"))["li_league_avg"]
    li_raw = build_lookup_table(league)

    rows = []
    for (inning, half, out_count, base_state, score_diff), raw in li_raw.items():
        rows.append({
            "inning": inning, "half": half, "out_count": out_count, "base_state": base_state,
            "score_diff": score_diff,
            "we": get_we_with_out_correction(inning, half, score_diff, out_count, base_state),
            "leverage_index": raw / li_league_avg,
        })
    table = pd.DataFrame(rows).sort_values(KEY).reset_index(drop=True)
    print(f"we_li_table: {len(table)}행, 점수차 {SCORE_DIFF_MIN}~{SCORE_DIFF_MAX}, "
          f"LI 최대 {table['leverage_index'].max():.2f}, li_league_avg = {li_league_avg:.6f}")
    return table


def verify(table):
    """실제 2025 타석의 we_before / leverage_index 가 표에서 찾은 값과 같은지."""
    path = OUTPUT_DIR / "game_pa.csv"
    if not path.exists():
        print("[검증] output/game_pa.csv 가 없어 건너뜀 (03_games_pa.py 먼저 실행)")
        return
    pa = pd.read_csv(path, usecols=["inning", "half", "out_count", "base_state", "score_diff_attacker",
                                    "we_before", "leverage_index"], dtype={"base_state": str})
    pa["score_diff"] = pa["score_diff_attacker"].clip(SCORE_DIFF_MIN, SCORE_DIFF_MAX)
    m = pa.merge(table, on=KEY, how="left", validate="many_to_one")
    missing = m["we"].isna().sum()
    we_err = (m["we"] - m["we_before"]).abs()
    li_err = (m["leverage_index_y"] - m["leverage_index_x"]).abs()
    print(f"[검증] 2025 타석 {len(pa)}개: 표에 없는 상황 {missing}개, "
          f"WE 최대 오차 {we_err.max():.2e}, LI 최대 오차 {li_err.max():.2e}")
    if missing or we_err.max() > 1e-4 or li_err.max() > 1e-4:
        sys.exit("[검증 실패] 표가 원본 데이터와 다릅니다.")


def main():
    table = build_table()
    verify(table)
    table.to_csv(OUTPUT_DIR / "we_li_table.csv", index=False, float_format="%.6f")


if __name__ == "__main__":
    main()
