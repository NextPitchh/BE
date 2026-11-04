"""분리된 b1_test DB 전용 합성 카탈로그. Snapshot 키를 이용하며 실제 운영 DB 검증이 아니다."""
import argparse
import csv
import hashlib
import io
import os
import subprocess
from pathlib import Path


def copy(table, columns, rows):
    out=io.StringIO(newline='')
    writer=csv.writer(out,lineterminator='\n')
    writer.writerows(rows)
    return f"COPY {table} ({columns}) FROM STDIN WITH (FORMAT csv);\n{out.getvalue()}\\.\n"


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--source',type=Path,required=True)
    p.add_argument('--port',type=int,required=True)
    p.add_argument('--psql',required=True)
    p.add_argument('--database',choices=('b1_test','b1_test_final','b1_test_repeat'),default='b1_test')
    args=p.parse_args()
    raw=(args.source/'pitcher_snapshots.csv').read_bytes()
    assert hashlib.sha256(raw).hexdigest()=='80050207db66b5c9e9444f7115bd9ff6fe9979fbbc1c17e052930ab26ae8ff4d'
    rows=list(csv.DictReader(io.StringIO(raw.decode('utf-8'),newline='')))
    games=sorted({r['game_id'] for r in rows})
    teams=sorted({x for g in games for x in (g[8:10],g[10:12])})
    players=sorted({int(r['pitcher_id']) for r in rows} | {54354,60002})
    roster={(r['game_id'],r['team_code'],int(r['pitcher_id']),True) for r in rows}
    assert ('20250322HHKT02025','KT',54354,True) not in roster
    roster.add(('20250322HHKT02025','KT',54354,False))
    roster.add(('20250322HHKT02025','HH',60002,False))
    ddl='''BEGIN;
CREATE TABLE teams (team_code varchar(4) PRIMARY KEY);
CREATE TABLE players (player_id integer PRIMARY KEY);
CREATE TABLE games (game_id varchar(20) PRIMARY KEY, game_date date NOT NULL,
                    away_team varchar(4) NOT NULL REFERENCES teams(team_code), home_team varchar(4) NOT NULL REFERENCES teams(team_code));
CREATE TABLE game_bullpen (game_id varchar(20) NOT NULL REFERENCES games(game_id), team_code varchar(4) NOT NULL REFERENCES teams(team_code),
  pitcher_id integer NOT NULL REFERENCES players(player_id), role varchar(10) NOT NULL DEFAULT 'bullpen',
  is_listed boolean NOT NULL, first_pa_index smallint, n_pa smallint NOT NULL DEFAULT 0, PRIMARY KEY(game_id,team_code,pitcher_id));
CREATE TABLE b1_test_existing_data (value text NOT NULL);
INSERT INTO b1_test_existing_data VALUES ('unchanged');
'''
    game_rows=[(g,f'{g[:4]}-{g[4:6]}-{g[6:8]}',g[8:10],g[10:12]) for g in games]
    script=ddl+copy('teams','team_code',[(t,) for t in teams])+copy('players','player_id',[(v,) for v in players])
    script+=copy('games','game_id,game_date,away_team,home_team',game_rows)
    script+=copy('game_bullpen','game_id,team_code,pitcher_id,is_listed',sorted(roster))+'COMMIT;\n'
    env=os.environ.copy()
    for name in ('PGSERVICE','PGSERVICEFILE','PGOPTIONS','PGHOSTADDR'): env.pop(name,None)
    env['PGCLIENTENCODING']='UTF8'
    subprocess.run([args.psql,'-X','-w','-h','127.0.0.1','-p',str(args.port),'-U','b1_test','-d',args.database,
                    '-v','ON_ERROR_STOP=1'],input=script.encode(),env=env,check=True)
    print(f'합성 카탈로그: 경기 {len(games)}, 선수 {len(players)}, 불펜 {len(roster)}')


if __name__=='__main__': main()
