"""teams, players 테이블용 CSV 생성.

출처
  - teams   : preview_full/{SEASON}/*.json 의 gameInfo (팀 코드·약칭·정식 명칭)
  - players : preview_full/{SEASON}/*.json 의 선발투수 playerInfo, fullLineUp,
              pitcherBullpen, batterCandidate (playerCode·이름·투타·등번호·생년월일)

전제 (2026-10-27 검증 완료)
  - preview 의 playerCode 와 all_pa_with_li.parquet 의 pitcher_id / batter_id 는 같은 ID 체계다.
    2025 PA 데이터의 투수 281명·타자 297명 전원이 preview 에 이름이 있고,
    pbp 중계문구 이름과도 299/299 일치했다. 따라서 매핑 테이블을 만들지 않는다.

출력
  output/teams.csv, output/players.csv  (load.sh 로 DB 적재)

실행
  python etl/01_teams_players.py
"""
import json
import sys
from collections import Counter

import pandas as pd

from config import OUTPUT_DIR, PA_PARQUET, PREVIEW_DIR, SEASON

LINEUP_KEYS = ("fullLineUp", "pitcherBullpen", "batterCandidate")


def parse_hand(text):
    """preview 의 투타 문구 → (throws, is_under, hit_type).

    throws 는 데이터셋 pitcher_throws·명세 Pitcher.throws 와 같은 컨벤션: 'L' | 'R' | 'U'(언더핸드)
    예) "우투좌타" → ("R", False, "L")
        "우완투수" → ("R", False, None)   # 투수 명단 문구에는 타격 정보가 없다
        "우완언더" → ("R", True,  None)   # 언더핸드 여부는 투수 문구에만 있다
        "우투양타" → ("R", False, "S")
    """
    if not text:
        return None, False, None
    throws = "L" if text[0] == "좌" else "R" if text[0] == "우" else None
    hit_type = None
    if text.endswith("타"):
        hit_type = {"좌": "L", "우": "R", "양": "S"}.get(text[-2])
    return throws, "언더" in text, hit_type


def iter_preview_files():
    files = sorted((PREVIEW_DIR / SEASON).glob("*.json"))
    if not files:
        sys.exit(f"preview 파일이 없습니다: {PREVIEW_DIR / SEASON}")
    for f in files:
        data = json.loads(f.read_text(encoding="utf-8"))["result"]["previewData"]
        yield f.stem, data


def collect():
    teams = {}
    rows = []  # 선수 한 명이 한 경기 preview 에 등장한 기록
    for game_id, d in iter_preview_files():
        info = d["gameInfo"]
        gdate = str(info["gdate"])
        for side in ("a", "h"):
            teams[info[f"{side}Code"]] = {
                "team_code": info[f"{side}Code"],
                "name": info[f"{side}Name"],
                "full_name": info[f"{side}FullName"],
                "logo_url": None,
                "_date": gdate,
            }

        for starter_key in ("awayStarter", "homeStarter"):
            p = (d.get(starter_key) or {}).get("playerInfo")
            if p and p.get("pCode"):
                rows.append({
                    "player_id": int(p["pCode"]), "name": p["name"], "hand": p.get("hitType"),
                    "back_number": p.get("backnum"), "birth": p.get("birth"),
                    "height": p.get("height"), "weight": p.get("weight"), "_date": gdate,
                })
        for lineup_key in ("awayTeamLineUp", "homeTeamLineUp"):
            lineup = d.get(lineup_key) or {}
            for k in LINEUP_KEYS:
                for p in lineup.get(k) or []:
                    rows.append({
                        "player_id": int(p["playerCode"]), "name": p["playerName"],
                        "hand": p.get("hitType"), "back_number": p.get("backnum"),
                        "birth": p.get("birth"), "height": p.get("height"),
                        "weight": p.get("weight"), "_date": gdate,
                    })
    return teams, pd.DataFrame(rows)


def build_players(raw):
    raw = raw.sort_values("_date")
    parsed = raw["hand"].apply(parse_hand)
    raw["throws"] = parsed.str[0]
    raw["is_under"] = parsed.str[1]
    raw["hit_type"] = parsed.str[2]

    def latest_non_null(s):
        s = s.dropna()
        return s.iloc[-1] if len(s) else None

    # 이름·신체정보는 가장 최근 경기 기준. 투타는 시즌 중 바뀌지 않으므로 최빈값.
    def mode_non_null(s):
        s = s.dropna()
        return Counter(s).most_common(1)[0][0] if len(s) else None

    g = raw.groupby("player_id")
    players = pd.DataFrame({
        "name": g["name"].agg(latest_non_null),
        "throws": g["throws"].agg(mode_non_null),
        "is_under": g["is_under"].any(),
        "hit_type": g["hit_type"].agg(mode_non_null),
        "back_number": g["back_number"].agg(latest_non_null),
        "birth_date": g["birth"].agg(latest_non_null),
        "height_cm": g["height"].agg(latest_non_null),
        "weight_kg": g["weight"].agg(latest_non_null),
    }).reset_index()

    # 한 번이라도 언더 문구로 등록된 투수는 'U'
    players.loc[players["is_under"], "throws"] = "U"
    players = players.drop(columns="is_under")

    players["birth_date"] = pd.to_datetime(players["birth_date"], format="%Y%m%d", errors="coerce").dt.date
    for c in ("height_cm", "weight_kg"):
        players[c] = pd.to_numeric(players[c], errors="coerce")
    players["back_number"] = players["back_number"].replace("", None)
    # 사진은 02_player_photos.py 가 내려받은 뒤 채운다
    players["photo_url"] = None
    return players


def check_coverage(players):
    """2025 PA 데이터에 나오는 선수가 전부 players 에 있는지 확인한다."""
    pa = pd.read_parquet(PA_PARQUET, columns=["game_id", "pitcher_id", "batter_id",
                                              "pitcher_throws", "batter_hit_type"])
    pa = pa[pa["game_id"].str[:4] == SEASON]
    ids = set(players["player_id"])
    missing_p = set(pa["pitcher_id"]) - ids
    missing_b = set(pa["batter_id"]) - ids
    print(f"[검증] PA 투수 {pa['pitcher_id'].nunique()}명 중 누락 {len(missing_p)}명")
    print(f"[검증] PA 타자 {pa['batter_id'].nunique()}명 중 누락 {len(missing_b)}명")
    if missing_p or missing_b:
        print("  누락 ID:", sorted(missing_p | missing_b)[:20])

    # 투타 정보가 PA 데이터와 일치하는지 (참고용).
    # PA 쪽 결측은 야수가 마운드에 오른 경우라 비교에서 뺀다.
    # PA 에서 한 번이라도 나온 값과 같으면 일치로 본다.
    idx = players.set_index("player_id")

    def mismatches(id_col, val_col, players_col):
        seen = pa.dropna(subset=[val_col]).groupby(id_col)[val_col].agg(set)
        seen = seen[seen.index.isin(ids)]
        bad = [(i, idx.at[i, "name"], idx.at[i, players_col], sorted(v))
               for i, v in seen.items() if idx.at[i, players_col] not in v]
        return bad, len(seen)

    bad_t, n_t = mismatches("pitcher_id", "pitcher_throws", "throws")
    bad_b, n_b = mismatches("batter_id", "batter_hit_type", "hit_type")
    print(f"[검증] 투수 투구손 불일치 {len(bad_t)}/{n_t}, 타자 타격손 불일치 {len(bad_b)}/{n_b}")
    for row in bad_t + bad_b:
        print("  (ID, 이름, players값, PA값)", row)
    return not (missing_p or missing_b)


def main():
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    teams, raw = collect()

    teams_df = pd.DataFrame(sorted(teams.values(), key=lambda t: t["team_code"])).drop(columns="_date")
    teams_df.to_csv(OUTPUT_DIR / "teams.csv", index=False)
    print(f"teams.csv: {len(teams_df)}개 팀 → {', '.join(teams_df['team_code'])}")

    players = build_players(raw)
    players.to_csv(OUTPUT_DIR / "players.csv", index=False)
    print(f"players.csv: {len(players)}명 "
          f"(투수손 있음 {players['throws'].notna().sum()}, 타격손 있음 {players['hit_type'].notna().sum()}, "
          f"언더핸드(U) {(players['throws'] == 'U').sum()})")

    ok = check_coverage(players)
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
