"""분리 테스트 DB 전용: 검증된 실제 2025 타석 접두부로 세션 생성 테스트를 준비한다."""
import argparse
import csv
import hashlib
import io
import os
import subprocess
from pathlib import Path

import pandas as pd

GAME='20250322HHKT02025'


def copy(table, columns, rows):
    out=io.StringIO(newline=''); writer=csv.writer(out,lineterminator='\n'); writer.writerows(rows)
    return f"COPY {table} ({columns}) FROM STDIN WITH (FORMAT csv);\n{out.getvalue()}\\.\n"


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--parquet',type=Path,required=True); p.add_argument('--port',type=int,required=True)
    p.add_argument('--psql',required=True)
    p.add_argument('--database',choices=('b1_test_final','b1_test_repeat'),default='b1_test_final'); args=p.parse_args()
    raw=args.parquet.read_bytes()
    assert hashlib.sha256(raw).hexdigest()=='8ab1c0909f53d1c58ac2b0eeeee142eda7ca2390e4bf107432620a30ae0d3545'
    frame=pd.read_parquet(io.BytesIO(raw)); frame=frame[frame.game_id.eq(GAME)].sort_values('_orig_idx')
    records=frame.to_dict('records')
    stop=next(i for i,r in enumerate(records) if r['inning']==5 and r['half']=='top')
    prefix=records[:stop+1]
    starters={half:[int(r['batter_id']) for r in prefix if r['half']==half][:9] for half in ('top','bot')}
    assert all(len(v)==9 and len(set(v))==9 for v in starters.values())
    player_ids={int(r[k]) for r in prefix for k in ('pitcher_id','batter_id')}
    count={'top':0,'bot':0}; away=home=0; pa=[]; previous={}
    for i,r in enumerate(prefix):
        half=r['half']; slot=count[half]%9+1; count[half]+=1
        assert int(r['batter_id'])==starters[half][slot-1], '선발 타순 확인 필요'
        diff=away-home if half=='top' else home-away
        assert diff==int(r['score_diff_attacker']), '복원 점수차 불일치'
        pitcher=int(r['pitcher_id']); pitch_before=int(r['total_pitch_count'])-1
        assert pitch_before>=0 and int(r['pitches_per_pa'])>=1
        changed=half in previous and previous[half]!=pitcher; previous[half]=pitcher
        pa.append((GAME,i,int(r['inning']),half,int(r['out_count']),str(r['base_state']),home,away,diff,
                   int(r['batter_id']),pitcher,slot,str(r['pa_result']),str(r['pa_result_raw']),
                   int(r['runs_scored']),float(r['we_before']),float(r['we_after']),0.1,pitch_before,
                   int(r['pitches_per_pa']),False,changed,bool(r['inning_ended']),'actual'))
        if i==stop: break
        if half=='top': away+=int(r['runs_scored'])
        else: home+=int(r['runs_scored'])
    assert (away,home,int(prefix[-1]['pitcher_id']),int(prefix[-1]['batter_id']))==(1,2,54354,50704)
    sql='''BEGIN;
ALTER TABLE games ADD COLUMN final_away smallint NOT NULL DEFAULT 0, ADD COLUMN final_home smallint NOT NULL DEFAULT 0,
 ADD COLUMN went_to_extras boolean NOT NULL DEFAULT false, ADD COLUMN is_cold_game boolean NOT NULL DEFAULT false,
 ADD COLUMN is_excluded boolean NOT NULL DEFAULT false, ADD COLUMN available_innings_away smallint[] NOT NULL DEFAULT '{5,6,7,8,9}',
 ADD COLUMN available_innings_home smallint[] NOT NULL DEFAULT '{5,6,7,8,9}',
 ADD COLUMN high_leverage_innings_away smallint[] NOT NULL DEFAULT '{}', ADD COLUMN high_leverage_innings_home smallint[] NOT NULL DEFAULT '{}',
 ADD COLUMN n_pa smallint NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN name text NOT NULL DEFAULT 'fixture', ADD COLUMN throws varchar(1),
 ADD COLUMN hit_type varchar(1), ADD COLUMN photo_url text;
'''
    sql+='\n'.join((Path('etl/schema')/name).read_text(encoding='utf8') for name in
                   ('02_games_pa.sql','03_bullpen_lineup.sql','04_we_li_table.sql','05_li_percentile.sql','06_pitcher_game_stats.sql',
                    '07_batter_game_stats.sql','08_matchups.sql','09_outcome_counts.sql'))+'\n'
    sql+='INSERT INTO players(player_id) SELECT x FROM unnest(ARRAY['+','.join(map(str,sorted(player_ids)))+']) x ON CONFLICT DO NOTHING;\n'
    # Snapshot에는 선발이 없으므로 실제 경기 접두부의 등판 투수도 테스트 명단에 추가한다.
    for team,pitcher in sorted({('KT' if r['half']=='top' else 'HH',int(r['pitcher_id'])) for r in prefix}):
        sql+=f"INSERT INTO game_bullpen(game_id,team_code,pitcher_id,is_listed) VALUES ('{GAME}','{team}',{pitcher},false) ON CONFLICT DO NOTHING;\n"
    order=[(GAME,'HH' if half=='top' else 'KT',i+1,0,player,True,None,0,0)
           for half,players in starters.items() for i,player in enumerate(players)]
    sql+=copy('game_batting_order','game_id,team_code,order_no,seq,batter_id,is_starter,position,first_pa_index,last_pa_index',order)
    sql+=copy('game_pa','game_id,pa_index,inning,half,out_count,base_state,score_home,score_away,score_diff_attacker,batter_id,pitcher_id,batting_order,result,result_raw,runs_scored,we_before,we_after,leverage_index,pitch_count_before,pitches_in_pa,is_partial_pa,is_pitcher_change,inning_ended,bullpen_source',pa)
    sql+="""INSERT INTO we_li_table
SELECT i, h, o, b, d, 0.5, 0.1
FROM generate_series(1,9) i, unnest(ARRAY['top','bot']) h, generate_series(0,2) o,
     unnest(ARRAY['0','1','2','3','12','13','23','123']) b, generate_series(-4,4) d;
INSERT INTO league_outcome_rates VALUES ('2024',56356,0.183,0.042,0.004,0.025,0.104,0.189,0.237,0.216,0.338);
INSERT INTO li_percentile SELECT i / 100.0, LEAST(i / 10,100) FROM generate_series(0,1000) i;
COMMIT;
"""
    env=os.environ.copy()
    for name in ('PGSERVICE','PGSERVICEFILE','PGOPTIONS','PGHOSTADDR'): env.pop(name,None)
    env['PGCLIENTENCODING']='UTF8'
    subprocess.run([args.psql,'-X','-w','-h','127.0.0.1','-p',str(args.port),'-U','b1_test','-d',args.database,
                    '-v','ON_ERROR_STOP=1'],input=sql.encode(),env=env,check=True)
    print(f'실제 {GAME} 경기 시작~5회초 첫 타석 {len(pa)}행; 화면 WE/LI는 테스트용 값')


if __name__=='__main__': main()
