"""Frozen B1 데이터를 검증하고 명시된 로컬 DB에만 원자적으로 적재한다."""
import argparse
import csv
import hashlib
import io
import math
import os
import re
import subprocess
from datetime import datetime
from pathlib import Path

VERSION = 'b1-2025-game-start-asof-v1'
MODEL = 'b1-optionb-chronology67360-v3'
SNAP_SHA = '80050207db66b5c9e9444f7115bd9ff6fe9979fbbc1c17e052930ab26ae8ff4d'
LI_SHA = '0e0b1dd20592903c9bbde710ccbe66e5ff601997c214ad9ba3422b7f10924757'
SCHEMA = Path(__file__).parent / 'schema/ai/b1_recommend.sql'
SNAP_FIELDS = ('game_id','team_code','pitcher_id','pitcher_name','profile_asof_date','prior_n_apps',
               'profile_source','feature_schema_version','latest_included_game_id','same_day_unresolved',
               'prior_pitcher_reward_mean','prior_wpa_std','prior_bb_rate','prior_so_rate','prior_hr_rate',
               'prior_avg_pa_per_app')
LI_FIELDS = ('inning','half','out_count','base_state','score_diff_attacker','_li_raw','leverage_index')


def read(source, name, digest, fields):
    raw = (source / name).read_bytes()
    if hashlib.sha256(raw).hexdigest() != digest:
        raise ValueError('SHA-256 불일치: ' + name)
    reader = csv.DictReader(io.StringIO(raw.decode('utf-8-sig'), newline=''))
    if reader.fieldnames != list(fields):
        raise ValueError('CSV 열 불일치: ' + name)
    rows = list(reader)
    if any(None in row or any(value is None for value in row.values()) for row in rows):
        raise ValueError('CSV 열 누락 또는 초과: ' + name)
    return rows


def encode(rows, fields):
    out = io.StringIO(newline='')
    writer = csv.writer(out, lineterminator='\n')
    writer.writerow(('version', *fields))
    for row in rows:
        writer.writerow((VERSION, *(row[field] for field in fields)))
    return out.getvalue().rstrip('\n')


def verify(source):
    snapshots = read(source, 'pitcher_snapshots.csv', SNAP_SHA, SNAP_FIELDS)
    li = read(source, 'b1_train_frozen_li_lookup.csv', LI_SHA, LI_FIELDS)
    keys = set()
    for row in snapshots:
        gid, team, pid = row['game_id'], row['team_code'], row['pitcher_id']
        key = (gid, team, pid)
        if (key in keys or not re.fullmatch(r'2025[0-9]{4}[A-Z]{4}[012]2025', gid)
                or team not in (gid[8:10],gid[10:12]) or not re.fullmatch(r'[1-9][0-9]{0,8}',pid)
                or row['feature_schema_version'] != 'b1-li-88-v1'
                or row['profile_asof_date'] != gid[:8] or row['same_day_unresolved'] not in ('True','False')
                or not row['prior_n_apps'].isdigit()):
            raise ValueError('Snapshot 키·버전·시점 오류: ' + str(key))
        keys.add(key)
        row['profile_asof_date'] = datetime.strptime(row['profile_asof_date'], '%Y%m%d').date().isoformat()
        previous=row['latest_included_game_id']
        if int(row['prior_n_apps']) == 0:
            if previous or row['profile_source'] != 'no_prior_history' or any(row[x] for x in SNAP_FIELDS[10:]):
                raise ValueError('이력 없는 투수의 null 정책 오류: ' + str(key))
        else:
            if not re.fullmatch(r'20[0-9]{6}[A-Z]{4}[012]20[0-9]{2}',previous) or row['profile_source'] != 'prior_appearances_aggregate':
                raise ValueError('이전 등판 출처 오류: ' + str(key))
            if previous[:8] > gid[:8] or (previous[:8] == gid[:8] and not
                    (previous[:12] == gid[:12] and previous[12] == '1' and gid[12] == '2')):
                raise ValueError('현재 경기 또는 미래 이력 포함: ' + str(key))
        for field in SNAP_FIELDS[10:]:
            if row[field] and not math.isfinite(float(row[field])):
                raise ValueError('유한하지 않은 Snapshot 값: ' + str(key))
    expected={(str(i),half,str(o),base,str(diff)) for i in range(1,10) for half in ('top','bot')
              for o in range(3) for base in ('0','1','2','3','12','13','23','123') for diff in range(-4,5)}
    actual=set()
    for row in li:
        key=tuple(row[x] for x in LI_FIELDS[:5])
        if key in actual or key not in expected or not all(math.isfinite(float(row[x])) for x in LI_FIELDS[5:]) or float(row['leverage_index']) < 0:
            raise ValueError('LI 키 또는 값 오류: ' + str(key))
        actual.add(key)
    if len(keys) != 18155 or actual != expected or len(li) != 3888:
        raise ValueError('행 수 또는 상태 공간 불일치')
    print('Snapshot 18155행, TRAIN LI 3888행: SHA-256·키·시점 검증 통과')
    return encode(snapshots, SNAP_FIELDS), encode(li, LI_FIELDS)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', required=True, type=Path)
    parser.add_argument('--load', action='store_true')
    parser.add_argument('--database')
    parser.add_argument('--user')
    parser.add_argument('--port', type=int, default=5433)
    parser.add_argument('--psql', default='psql')
    args=parser.parse_args()
    snap, li=verify(args.source)
    if not args.load:
        print('검증 전용: DB 변경 없음')
        return
    if not args.database or not args.user or not all(re.fullmatch(r'[A-Za-z0-9_]+', x) for x in (args.database,args.user)) or not 1 <= args.port <= 65535:
        parser.error('--load에는 로컬 DB 이름·사용자·포트가 필요합니다')
    sql=f'''BEGIN;
SELECT pg_advisory_xact_lock(706062025);
{SCHEMA.read_text(encoding='utf-8')}
INSERT INTO bandit_dataset_manifest VALUES ('{VERSION}','{MODEL}','{SNAP_SHA}','{LI_SHA}',18155,3888);
COPY bandit_pitcher_snapshots (version,{','.join(SNAP_FIELDS)}) FROM STDIN WITH (FORMAT csv, HEADER true);
{snap}
\\.
COPY bandit_train_li_lookup (version,inning,half,out_count,base_state,score_diff_attacker,li_raw,leverage_index) FROM STDIN WITH (FORMAT csv, HEADER true);
{li}
\\.
DO $$ BEGIN
 IF (SELECT count(*) FROM bandit_pitcher_snapshots WHERE version='{VERSION}') <> 18155 OR
    (SELECT count(*) FROM bandit_train_li_lookup WHERE version='{VERSION}') <> 3888 THEN
   RAISE EXCEPTION 'B1 row count mismatch';
 END IF;
 IF EXISTS (SELECT 1 FROM bandit_pitcher_snapshots s JOIN games g ON g.game_id=s.game_id
            WHERE s.version='{VERSION}' AND s.team_code NOT IN (g.home_team,g.away_team)) THEN
   RAISE EXCEPTION 'B1 team/game mismatch';
 END IF;
 IF EXISTS (SELECT 1 FROM bandit_pitcher_snapshots s LEFT JOIN game_bullpen b
            ON b.game_id=s.game_id AND b.team_code=s.team_code AND b.pitcher_id=s.pitcher_id AND b.is_listed
            WHERE s.version='{VERSION}' AND b.pitcher_id IS NULL) OR
    EXISTS (SELECT 1 FROM game_bullpen b JOIN games g ON g.game_id=b.game_id
            LEFT JOIN bandit_pitcher_snapshots s ON s.version='{VERSION}' AND s.game_id=b.game_id
                 AND s.team_code=b.team_code AND s.pitcher_id=b.pitcher_id
            WHERE b.is_listed AND g.game_date >= DATE '2025-01-01' AND g.game_date < DATE '2026-01-01'
                  AND s.pitcher_id IS NULL) THEN
   RAISE EXCEPTION 'B1 snapshot and 2025 listed roster differ';
 END IF;
END $$;
COMMIT;
'''
    env=os.environ.copy()
    for key in ('PGSERVICE','PGSERVICEFILE','PGOPTIONS','PGHOSTADDR'):
        env.pop(key,None)
    env['PGCLIENTENCODING']='UTF8'
    subprocess.run([args.psql,'-X','-w','--host','127.0.0.1','--port',str(args.port),
                    '--dbname',args.database,'--username',args.user,'-v','ON_ERROR_STOP=1'],
                   input=sql.encode('utf-8'),env=env,check=True)
    print('로컬 DB 적재 완료')


if __name__=='__main__':
    main()
