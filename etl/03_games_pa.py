"""games, game_pa 테이블용 CSV 생성.

출처
  - all_pa_with_li.parquet (2025 시즌 PA, v3 + LI + quality_p) — 상황·결과·WE·LI·투구수
  - pbp_full/2025/*.csv (원본 투구 단위 중계) — 타석 결과 문구(relay_text)

왜 원본 중계 문구가 필요한가
  가공 데이터의 pa_result 는 아웃을 전부 "OUT" 하나로 합쳐 두었다.
  명세 v0.3 의 결과 코드는 땅볼(GO)과 플라이(FO)를 나누므로, 원본 문구로 다시 분류한다.
  같은 과정에서 "스트라이크 낫 아웃"이 OUT 으로 들어가 있던 것도 SO 로 바로잡는다.
  가공 데이터의 원래 값은 result_raw 에 그대로 남긴다.

원본 ↔ 가공 데이터 타석 대응
  원본은 공 하나가 한 줄이다. (이닝, 공격팀, 타자, 투수) 가 바뀌는 지점마다 타석을 끊으면
  가공 데이터의 타석 순서와 1:1 로 맞는다. 투수를 키에 넣는 이유: 타석 도중 투수가 바뀌면
  가공 데이터는 그 타석을 두 줄로 기록한다. 2025 시즌 720경기 55,267타석 전부 일치 확인 (2026-10-27).
  하나라도 어긋나면 이 스크립트는 멈춘다.

알려진 데이터 누락: 공 없이 선언된 자동 고의4구
  크롤러는 투구 이벤트가 있을 때만 줄을 만든다. 네이버가 자동 고의4구를 공 4개로 기록한 경우는 남지만,
  투구 없이 기록한 경우는 그 타석이 원본·가공 데이터 모두에 없다. 그래서 타순을 "몇 번째 타석"으로
  세면 밀린다. 타순은 선수 기준으로 정한다 (mark_partial_and_order 참고).

출력
  output/games.csv, output/game_pa.csv

실행
  python etl/03_games_pa.py
"""
import re
import sys
from collections import Counter

import pandas as pd

from common import load_preview_lineups
from config import OUTPUT_DIR, PA_PARQUET, PBP_DIR, SEASON

DEMO_INNINGS = range(5, 10)   # 명세 1-4: 시작 이닝은 5~9회
HIGH_LI = 1.5                 # /config 레버리지 밴드 "고" 하한
MAX_HIGH_LEVERAGE_INNINGS = 2

PA_COLUMNS = [
    "game_id", "_orig_idx", "inning", "half", "home_or_away", "out_count", "base_state",
    "score_diff", "score_diff_attacker", "batter_id", "pitcher_id", "pa_result", "runs_scored",
    "we_before", "we_after", "leverage_index", "total_pitch_count", "pitches_per_pa",
    "is_pitcher_change", "inning_ended", "bullpen_source", "data_quality_flag",
]

FIELDER = r"^(투수|포수|1루수|2루수|3루수|유격수|좌익수|중견수|우익수)\s*"


def classify_out(text):
    """가공 데이터에서 OUT 인 타석의 원본 문구 → 명세 결과 코드.

    2025 시즌 OUT 문구 분포(상위): 플라이 아웃 8,183 / 땅볼 아웃 7,723 / 땅볼로 출루 1,300 /
    파울플라이 913 / 스트라이크 낫 아웃 838 / 라인드라이브 620 / 희생번트 428 / 번트 아웃 85 / 인필드플라이 70

    규칙 (순서대로 적용)
      - "낫 아웃", "낫아웃", "쓰리번트"  → SO  (기록상 삼진. 낫아웃 출루도 투수 삼진으로 기록된다)
      - "플라이", "라인드라이브"          → FO  (파울플라이, 인필드플라이, 직선타 포함)
      - "땅볼", "번트", "삼중살"          → GO  (야수선택 "땅볼로 출루", 희생번트 포함)
      - 그 외                             → None (호출 쪽에서 OUT 유지 + 집계)
    """
    body = str(text).split(" : ", 1)[-1]
    body = re.sub(r"\(.*?\)", "", body).strip()
    body = re.sub(FIELDER, "", body)
    if "낫 아웃" in body or "낫아웃" in body or "쓰리번트" in body:
        return "SO"
    if "플라이" in body or "라인드라이브" in body:
        return "FO"
    if "땅볼" in body or "번트" in body or "삼중살" in body:
        return "GO"
    return None


def load_pa():
    pa = pd.read_parquet(PA_PARQUET, columns=PA_COLUMNS)
    pa = pa[pa["game_id"].str[:4] == SEASON].sort_values(["game_id", "_orig_idx"]).reset_index(drop=True)
    pa["pa_index"] = pa.groupby("game_id").cumcount()
    return pa


def pbp_plate_appearances(game_id):
    """원본 투구 단위 중계 → 타석 단위 (inning, home_or_away, batter_id, pitcher_id, relay_text)."""
    pb = pd.read_csv(PBP_DIR / SEASON / f"{game_id}.csv", encoding="utf-8-sig")
    pb = pb[pb["inning"] <= 9].copy()            # 가공 데이터는 연장(10회~)을 제외했다
    pb["relay_text"] = pb["relay_text"].fillna("")
    key_cols = ["inning", "home_or_away", "batter_id", "pitcher_id"]
    pa_no = (pb[key_cols] != pb[key_cols].shift()).any(axis=1).cumsum()
    return pb.groupby(pa_no).agg(
        inning=("inning", "first"), home_or_away=("home_or_away", "first"),
        batter_id=("batter_id", "first"), pitcher_id=("pitcher_id", "first"),
        relay_text=("relay_text", lambda s: next((t for t in s if t), "")),
    ).reset_index(drop=True)


def max_raw_inning(game_id):
    pb = pd.read_csv(PBP_DIR / SEASON / f"{game_id}.csv", encoding="utf-8-sig", usecols=["inning"])
    return int(pb["inning"].max())


def attach_relay_text(pa):
    texts, extra = [], {}
    key = ["inning", "home_or_away", "batter_id", "pitcher_id"]
    for game_id, g in pa.groupby("game_id", sort=False):
        raw = pbp_plate_appearances(game_id)
        if len(raw) != len(g) or not (raw[key].to_numpy() == g[key].to_numpy()).all():
            sys.exit(f"[중단] 원본과 가공 데이터의 타석 순서가 다릅니다: {game_id} "
                     f"(원본 {len(raw)}, 가공 {len(g)})")
        texts.extend(raw["relay_text"].tolist())
        extra[game_id] = max_raw_inning(game_id) > 9
    pa["relay_text"] = texts
    return pa, extra


def assign_result(pa):
    pa["result_raw"] = pa["pa_result"]
    pa["result"] = pa["pa_result"]
    is_out = pa["pa_result"] == "OUT"
    mapped = pa.loc[is_out, "relay_text"].apply(classify_out)
    pa.loc[is_out, "result"] = mapped.fillna("OUT")

    unresolved = pa.loc[is_out & (pa["result"] == "OUT"), "relay_text"]
    print(f"[결과 분류] OUT {is_out.sum()}건 → {Counter(mapped.dropna()).most_common()}, "
          f"미분류 {len(unresolved)}건")
    if len(unresolved):
        print("  미분류 문구 예:", unresolved.head(5).tolist())
    return pa


def add_scores(pa):
    """타석 시작 시점의 홈/원정 점수. 가공 데이터는 점수차(score_diff)만 있어서 runs_scored 를 누적한다."""
    away_runs = pa["runs_scored"].where(pa["home_or_away"] == 0, 0)
    home_runs = pa["runs_scored"].where(pa["home_or_away"] == 1, 0)
    pa["score_away"] = away_runs.groupby(pa["game_id"]).cumsum() - away_runs
    pa["score_home"] = home_runs.groupby(pa["game_id"]).cumsum() - home_runs
    bad = (pa["score_home"] - pa["score_away"] != pa["score_diff"]).sum()
    print(f"[검증] 누적 점수 vs score_diff 불일치 {bad}건")
    if bad:
        sys.exit("[중단] 점수 재구성이 score_diff 와 맞지 않습니다.")
    return pa


def assign_batting_order(rows, starters):
    """한 팀 한 경기의 타석들 → 각 타석 타자의 타순(1~9).

    - 예고 라인업의 선발 타순으로 시작한다.
    - 이미 타순이 있는 타자는 그 타순 그대로.
    - 처음 보는 타자(대타·교체, 예고와 다른 선발)는 "이후 다시 나오지 않는 선수"의 자리를 이어받는다.
      후보가 여럿이면 직전 타자 다음 자리에서 가장 가까운 곳. 후보가 없으면 직전 타자 다음 자리.
      직전 타자 다음 자리로만 넣으면, 바로 앞 타석이 누락된 경우 멀쩡한 선수를 밀어내 뒤 타순이 전부 밀린다.
    직전 타자 다음 자리가 아닌 타순이 나오면 그 사이 타석이 데이터에 없는 것이다 (자동 고의4구 누락).
    """
    rows = list(rows)
    last_seen = {batter: i for i, (batter, _) in enumerate(rows)}
    slot_of = dict(starters)                      # batter_id -> 타순
    occupant = {s: b for b, s in slot_of.items()}  # 타순 -> batter_id
    prev, orders, skipped = 0, [], 0
    for i, (batter, partial) in enumerate(rows):
        expected = prev % 9 + 1
        if batter not in slot_of:
            free = [s for s in range(1, 10) if last_seen.get(occupant.get(s), -1) < i]
            slot = min(free, key=lambda s: (s - expected) % 9) if free else expected
            old = occupant.get(slot)
            if old is not None:
                del slot_of[old]
            occupant[slot] = batter
            slot_of[batter] = slot
        slot = slot_of[batter]
        if not partial:
            skipped += (slot - expected) % 9
            prev = slot
        orders.append(slot)
    return orders, skipped


def mark_partial_and_order(pa):
    """끝나지 않은 타석 표시 + 타순 계산.

    is_partial_pa=True 인 줄은 타자의 타석이 끝나지 않은 기록이다. 같은 팀의 다음 타석에 같은 타자가 다시 나온다.
      - 타석 도중 투수 교체: 가공 데이터가 한 타석을 두 줄로 남기고 앞 줄에도 최종 결과를 복사해 둔다.
      - 타석 도중 주자 아웃(도루자·견제사)으로 이닝 종료: 그 타자는 다음 이닝 첫 타자로 다시 나온다.
      - 타석 도중 대타 교체: 다음 줄은 같은 타순의 다른 타자다.
    뒤의 두 경우는 결과 문구가 없어 result=UNK 로 남아 있다. 그래서 UNK 도 끝나지 않은 타석으로 본다.
    리플레이에서 이 줄의 결과는 보여주지 않고 타석 수에서도 뺀다. 상태 변화(점수·아웃·주자)는 그대로 유효하다.
    """
    team = pa.groupby(["game_id", "half"])
    same_batter_next = (team["batter_id"].shift(-1) == pa["batter_id"]).fillna(False)
    pa["is_partial_pa"] = same_batter_next | (pa["result"] == "UNK")

    lineups = load_preview_lineups()
    starters = {k: list(zip(g["batter_id"], g["order_no"]))
                for k, g in lineups.groupby(["game_id", "half"])}
    pa["batting_order"] = 0
    total_skipped = 0
    for key, g in pa.groupby(["game_id", "half"], sort=False):
        orders, skipped = assign_batting_order(zip(g["batter_id"], g["is_partial_pa"]), starters.get(key, []))
        pa.loc[g.index, "batting_order"] = orders
        total_skipped += skipped

    slots_per_batter = pa.groupby(["game_id", "half", "batter_id"])["batting_order"].nunique()
    print(f"[타순] 끝나지 않은 타석 {pa['is_partial_pa'].sum()}건 "
          f"(결과 분포 {pa.loc[pa['is_partial_pa'], 'result'].value_counts().to_dict()})")
    print(f"[타순] 데이터에 없는 타석(건너뛴 타순) {total_skipped}건, "
          f"한 경기에서 두 타순에 나온 타자 {(slots_per_batter > 1).sum()}명")
    return pa


def build_game_pa(pa):
    out = pd.DataFrame({
        "game_id": pa["game_id"],
        "pa_index": pa["pa_index"],
        "inning": pa["inning"],
        "half": pa["half"],
        "out_count": pa["out_count"],
        "base_state": pa["base_state"],
        "score_home": pa["score_home"],
        "score_away": pa["score_away"],
        "score_diff_attacker": pa["score_diff_attacker"],
        "batter_id": pa["batter_id"],
        "pitcher_id": pa["pitcher_id"],
        "batting_order": pa["batting_order"],
        "result": pa["result"],
        "result_raw": pa["result_raw"],
        "relay_text": pa["relay_text"],
        "runs_scored": pa["runs_scored"],
        "we_before": pa["we_before"],          # 공격팀 관점 (Option B). 관점 변환은 API 가 한다
        "we_after": pa["we_after"],
        "leverage_index": pa["leverage_index"],
        # total_pitch_count 는 이 타석 첫 공의 누적 번호다 → 타석 시작 전 투구수 = -1
        "pitch_count_before": pa["total_pitch_count"] - 1,
        "pitches_in_pa": pa["pitches_per_pa"],
        "is_partial_pa": pa["is_partial_pa"],
        "is_pitcher_change": pa["is_pitcher_change"],
        "inning_ended": pa["inning_ended"],
        "bullpen_source": pa["bullpen_source"].replace({"preview": "actual"}),
        # inning1_nonzero_start: 1회말 첫 타석 점수차≠0 표시. 1회초 득점이면 정상이라 제외하지 않는다
        "data_quality_flag": pa["data_quality_flag"].replace("", None),
    })
    return out


def pg_array(values):
    return "{" + ",".join(str(v) for v in values) + "}"


def defense_innings(g, defending_half):
    """해당 팀 수비 반이닝 기준 시작 가능 이닝과 승부처 이닝."""
    d = g[g["half"] == defending_half]
    available = [i for i in DEMO_INNINGS if (d["inning"] == i).any()]
    max_li = d[d["inning"].isin(available)].groupby("inning")["leverage_index"].max()
    high = max_li[max_li >= HIGH_LI].sort_values(ascending=False).head(MAX_HIGH_LEVERAGE_INNINGS)
    return available, sorted(high.index.tolist())


def build_games(pa, went_extra):
    rows = []
    for game_id, g in pa.groupby("game_id", sort=False):
        last = g.iloc[-1]
        final_away = int(last["score_away"] + (last["runs_scored"] if last["home_or_away"] == 0 else 0))
        final_home = int(last["score_home"] + (last["runs_scored"] if last["home_or_away"] == 1 else 0))
        # 홈팀 수비 = 초(top), 원정팀 수비 = 말(bot)
        avail_home, high_home = defense_innings(g, "top")
        avail_away, high_away = defense_innings(g, "bot")
        is_cold = int(last["inning"]) < 9
        rows.append({
            "game_id": game_id,
            "game_date": pd.to_datetime(game_id[:8], format="%Y%m%d").date(),
            "away_team": game_id[8:10],
            "home_team": game_id[10:12],
            # 연장 경기는 데이터셋이 9회까지만 있어 9회 종료 스코어(동점)다
            "final_away": final_away,
            "final_home": final_home,
            "went_to_extras": went_extra[game_id],
            "is_cold_game": is_cold,
            "is_excluded": is_cold,             # 명세 1-4: 콜드게임 제외 (2025 서스펜디드 없음)
            "available_innings_away": pg_array(avail_away),
            "available_innings_home": pg_array(avail_home),
            "high_leverage_innings_away": pg_array(high_away),
            "high_leverage_innings_home": pg_array(high_home),
            "n_pa": len(g),
        })
    return pd.DataFrame(rows)


def main():
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    pa = load_pa()
    print(f"2025 PA {len(pa)}건, {pa['game_id'].nunique()}경기")
    pa, went_extra = attach_relay_text(pa)
    print("[검증] 원본 중계와 타석 순서 전 경기 일치")
    pa = assign_result(pa)
    pa = add_scores(pa)
    pa = mark_partial_and_order(pa)

    game_pa = build_game_pa(pa)
    game_pa.to_csv(OUTPUT_DIR / "game_pa.csv", index=False)
    print(f"game_pa.csv: {len(game_pa)}행, 결과 분포 {game_pa['result'].value_counts().to_dict()}")

    games = build_games(pa, went_extra)
    games.to_csv(OUTPUT_DIR / "games.csv", index=False)
    print(f"games.csv: {len(games)}경기 (콜드 {games['is_cold_game'].sum()}, "
          f"연장 {games['went_to_extras'].sum()}, 서비스 대상 {(~games['is_excluded']).sum()})")


if __name__ == "__main__":
    main()
