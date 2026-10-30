"""여러 ETL 스크립트가 같이 쓰는 원본 읽기 함수."""
import json

import numpy as np
import pandas as pd

from config import PREVIEW_DIR, SEASON


def load_preview_lineups():
    """preview 의 선발 라인업 → (game_id, team_code, half, order_no, batter_id, position).

    half 는 그 팀이 공격하는 반이닝이다 (원정 = top, 홈 = bot).
    예고 라인업이라 실제와 다를 수 있다 (2025: 1,440개 중 1,416개 9자리 완전 일치).
    """
    rows = []
    for f in sorted((PREVIEW_DIR / SEASON).glob("*.json")):
        d = json.loads(f.read_text(encoding="utf-8"))["result"]["previewData"]
        info = d["gameInfo"]
        for key, code, half in (("awayTeamLineUp", info["aCode"], "top"),
                                ("homeTeamLineUp", info["hCode"], "bot")):
            for p in (d.get(key) or {}).get("fullLineUp") or []:
                if p.get("batorder"):
                    rows.append({
                        "game_id": f.stem, "team_code": code, "half": half,
                        "order_no": int(p["batorder"]), "batter_id": int(p["playerCode"]),
                        "position": p.get("positionName"),
                    })
    return pd.DataFrame(rows)


# ── 타석 결과 분류 (KBO_Crawler build_matchup_history.py 와 동일) ─────────────
# 볼넷(BB)에는 사구·고의4구가 합쳐져 있다. wOBA 가중치는 2016 wOBA scale (팀 지정).
W_HR, W_3B, W_2B, W_1B, W_BB = 1.70, 1.37, 1.08, 0.77, 0.62
AB_RESULTS = {"1B", "2B", "3B", "HR", "SO", "GO", "FO", "OUT", "GDP"}
HIT_RESULTS = {"1B", "2B", "3B", "HR"}
PA_RESULTS = AB_RESULTS | {"BB", "SF"}          # 유효 타석 (UNK 제외)


def order_key(game_ids):
    """경기 순서 정수: YYYYMMDD × 10 + 더블헤더 번호 (game_id 13번째 자리, 보통 0)."""
    return game_ids.str[:8].astype(np.int64) * 10 + game_ids.str[12].astype(np.int64)


def asof_prior(rows, events, value_cols, by):
    """rows 의 각 (game_id, by) 에 대해 그 경기 '전' 마지막 events 행의 누적값을 붙인다. 없으면 0.

    rows, events 모두 경기 순서 컬럼 ok (order_key) 가 있어야 한다.
    """
    left = rows[["game_id", by, "ok"]].sort_values("ok")
    right = events[[by, "ok"] + value_cols].sort_values("ok")
    m = pd.merge_asof(left, right, on="ok", by=by, allow_exact_matches=False)
    m[value_cols] = m[value_cols].fillna(0)
    return m[["game_id", by] + value_cols]
