"""선수 사진 사전 수집 (명세 2-2: 자체 호스팅, 외부 CDN 핫링킹 금지).

  - output/players.csv 의 player_id 마다 네이버 원본 이미지를 내려받는다.
  - 사진이 없는 선수는 네이버가 캐릭터 플레이스홀더(no-image)를 200 으로 돌려준다.
    파일 크기는 실사진과 비슷해서(약 34KB vs 37KB) 크기로는 못 거른다.
    → 존재하지 않는 ID 로 플레이스홀더를 한 번 받아 해시를 구하고, 같은 해시면 버린다.
  - Pillow 가 있으면 .webp 로 변환해 저장한다. 없으면 실행을 멈춘다(명세가 .webp 경로를 요구).
  - 저장 후 players.csv 의 photo_url 을 채운다. 사진 없는 선수는 비워둔다 → 프론트가 이니셜 아바타로 폴백.

실행
  pip install pillow
  python etl/02_player_photos.py            # 전체
  python etl/02_player_photos.py --limit 5  # 시험 실행
"""
import argparse
import hashlib
import io
import sys
import time
import urllib.error
import urllib.request

import pandas as pd

from config import OUTPUT_DIR, PHOTO_DIR, PHOTO_SERVE_PATH, PHOTO_SOURCE_URL

PLACEHOLDER_PROBE_ID = 99999  # 존재하지 않는 선수 ID → 플레이스홀더가 돌아온다
REQUEST_INTERVAL_SEC = 0.2    # 원본 서버 부담을 줄이기 위한 요청 간격


def fetch(player_id):
    req = urllib.request.Request(PHOTO_SOURCE_URL.format(player_id=player_id),
                                 headers={"User-Agent": "Mozilla/5.0"})
    try:
        with urllib.request.urlopen(req, timeout=10) as res:
            return res.read()
    except urllib.error.HTTPError as e:
        if e.code == 404:
            return None
        raise


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--limit", type=int, default=None)
    parser.add_argument("--force", action="store_true", help="이미 받은 사진도 다시 받기")
    args = parser.parse_args()

    try:
        from PIL import Image
    except ImportError:
        sys.exit("Pillow 가 필요합니다: pip install pillow")

    players_path = OUTPUT_DIR / "players.csv"
    if not players_path.exists():
        sys.exit("output/players.csv 가 없습니다. 01_teams_players.py 를 먼저 실행하세요.")
    players = pd.read_csv(players_path, dtype={"back_number": "string"})
    PHOTO_DIR.mkdir(parents=True, exist_ok=True)

    placeholder = fetch(PLACEHOLDER_PROBE_ID)
    placeholder_hash = hashlib.md5(placeholder).hexdigest() if placeholder else None
    print(f"플레이스홀더 해시: {placeholder_hash}")

    targets = players["player_id"].tolist()[: args.limit] if args.limit else players["player_id"].tolist()
    saved, no_photo, failed = 0, 0, []
    for i, pid in enumerate(targets, 1):
        out = PHOTO_DIR / f"{pid}.webp"
        if out.exists() and not args.force:
            saved += 1
            continue
        try:
            body = fetch(pid)
        except Exception as e:  # 네트워크 오류는 기록만 하고 계속 진행
            failed.append((pid, str(e)))
            continue
        if body is None or hashlib.md5(body).hexdigest() == placeholder_hash:
            no_photo += 1
        else:
            Image.open(io.BytesIO(body)).save(out, "WEBP", quality=85)
            saved += 1
        if i % 50 == 0:
            print(f"  {i}/{len(targets)} 처리")
        time.sleep(REQUEST_INTERVAL_SEC)

    # 실제로 파일이 있는 선수만 photo_url 을 채운다
    players["photo_url"] = [
        PHOTO_SERVE_PATH.format(player_id=pid) if (PHOTO_DIR / f"{pid}.webp").exists() else None
        for pid in players["player_id"]
    ]
    players.to_csv(players_path, index=False)

    total_mb = sum(f.stat().st_size for f in PHOTO_DIR.glob("*.webp")) / 1e6
    print(f"사진 있음 {saved}, 사진 없음 {no_photo}, 실패 {len(failed)} / 대상 {len(targets)}")
    print(f"저장 위치 {PHOTO_DIR} ({total_mb:.1f}MB)")
    if failed:
        print("실패 목록:", failed[:10])


if __name__ == "__main__":
    main()
