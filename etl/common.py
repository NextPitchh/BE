"""여러 ETL 스크립트가 같이 쓰는 원본 읽기 함수."""
import json

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
