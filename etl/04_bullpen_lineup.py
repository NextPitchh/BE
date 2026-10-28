"""game_bullpen, game_batting_order 테이블용 CSV 생성.

출처
  - all_game_bullpen.parquet : 경기별 불펜 명단 (preview 의 pitcherBullpen). 선발투수는 들어 있지 않다.
  - output/game_pa.csv       : 실제 등판·타석 기록 (03_games_pa.py 산출물)
  - preview_full             : 선발 라인업 포지션

game_bullpen
  경기·팀별로 "이 경기에 나올 수 있었던 투수" 전부. API 가 어느 시점에서든 후보 목록과
  등판 완료(used) 여부를 바로 계산할 수 있도록 첫 등판 타석(first_pa_index)을 같이 둔다.
    used    = first_pa_index < 현재 pa_index
    current = 현재 타석 투수
  role: starter(그날 선발) | bullpen(불펜 명단) | unlisted(명단에 없는데 등판 — 2025 시즌 3명)

game_batting_order
  타순 자리(1~9)를 거쳐 간 타자 전부. 시점 t 의 라인업 = 자리마다 first_pa_index <= t 인 마지막 타자.
  선발은 실제 첫 타석 기준이다 (예고 라인업과 다른 1.7% 는 실제를 따른다). 포지션은 예고 라인업에서 가져온다.

실행
  python etl/04_bullpen_lineup.py   (03_games_pa.py 다음)
"""
import sys

import pandas as pd

from common import load_preview_lineups
from config import BULLPEN_PARQUET, OUTPUT_DIR, SEASON


def defending_team(game_pa):
    # 초 = 원정 공격·홈 수비, 말 = 홈 공격·원정 수비
    return game_pa["game_id"].str[10:12].where(game_pa["half"] == "top", game_pa["game_id"].str[8:10])


def attacking_team(game_pa):
    return game_pa["game_id"].str[8:10].where(game_pa["half"] == "top", game_pa["game_id"].str[10:12])


def build_bullpen(game_pa):
    listed = pd.read_parquet(BULLPEN_PARQUET, columns=["game_id", "team_code", "pitcher_id"])
    listed = listed[listed["game_id"].str[:4] == SEASON].drop_duplicates()
    listed["is_listed"] = True

    pa = game_pa.assign(team_code=defending_team(game_pa))
    pitched = pa.groupby(["game_id", "team_code", "pitcher_id"]).agg(
        first_pa_index=("pa_index", "min"),
        n_pa=("is_partial_pa", lambda s: int((~s).sum())),
    ).reset_index()
    starters = pa.groupby(["game_id", "team_code"])["pitcher_id"].first().rename("starter_id").reset_index()

    out = listed.merge(pitched, on=["game_id", "team_code", "pitcher_id"], how="outer")
    out = out.merge(starters, on=["game_id", "team_code"], how="left")
    out["is_listed"] = out["is_listed"].fillna(False).astype(bool)
    out["role"] = "bullpen"
    out.loc[~out["is_listed"], "role"] = "unlisted"
    out.loc[out["pitcher_id"] == out["starter_id"], "role"] = "starter"
    out["n_pa"] = out["n_pa"].fillna(0).astype(int)
    out["first_pa_index"] = out["first_pa_index"].astype("Int64")

    counts = out["role"].value_counts().to_dict()
    per_team = out[out["role"] == "bullpen"].groupby(["game_id", "team_code"]).size()
    print(f"game_bullpen: {len(out)}행 {counts}, 팀당 불펜 명단 평균 {per_team.mean():.1f}명")
    unlisted = out[out["role"] == "unlisted"]
    if len(unlisted):
        print("  명단 밖 등판:", unlisted[["game_id", "team_code", "pitcher_id", "first_pa_index"]].values.tolist())
    return out[["game_id", "team_code", "pitcher_id", "role", "is_listed", "first_pa_index", "n_pa"]]


def build_batting_order(game_pa):
    pa = game_pa.assign(team_code=attacking_team(game_pa))
    out = pa.groupby(["game_id", "team_code", "batting_order", "batter_id"]).agg(
        first_pa_index=("pa_index", "min"),
        last_pa_index=("pa_index", "max"),
    ).reset_index().rename(columns={"batting_order": "order_no"})
    out = out.sort_values(["game_id", "team_code", "order_no", "first_pa_index"])
    out["seq"] = out.groupby(["game_id", "team_code", "order_no"]).cumcount()   # 0 = 선발
    out["is_starter"] = out["seq"] == 0

    lineups = load_preview_lineups()[["game_id", "team_code", "batter_id", "position"]]
    out = out.merge(lineups, on=["game_id", "team_code", "batter_id"], how="left")

    starters = out[out["is_starter"]]
    print(f"game_batting_order: {len(out)}행 (선발 {len(starters)}, 교체 {(~out['is_starter']).sum()}), "
          f"포지션 있음 {out['position'].notna().sum()} / 선발 중 {starters['position'].notna().sum()}")
    slots = out[out["is_starter"]].groupby(["game_id", "team_code"]).size()
    if (slots != 9).any():
        print("  [경고] 선발 9자리가 다 차지 않은 팀:", slots[slots != 9].head().to_dict())
    return out[["game_id", "team_code", "order_no", "seq", "batter_id", "is_starter",
                "position", "first_pa_index", "last_pa_index"]]


def main():
    path = OUTPUT_DIR / "game_pa.csv"
    if not path.exists():
        sys.exit("output/game_pa.csv 가 없습니다. 03_games_pa.py 를 먼저 실행하세요.")
    game_pa = pd.read_csv(path, usecols=["game_id", "pa_index", "half", "pitcher_id", "batter_id",
                                         "batting_order", "is_partial_pa"])

    bullpen = build_bullpen(game_pa)
    bullpen.to_csv(OUTPUT_DIR / "game_bullpen.csv", index=False)

    order = build_batting_order(game_pa)
    order.to_csv(OUTPUT_DIR / "game_batting_order.csv", index=False)

    players = set(pd.read_csv(OUTPUT_DIR / "players.csv", usecols=["player_id"])["player_id"])
    missing = (set(bullpen["pitcher_id"]) | set(order["batter_id"])) - players
    print(f"[검증] players 에 없는 선수 {len(missing)}명", sorted(missing)[:10] if missing else "")


if __name__ == "__main__":
    main()
