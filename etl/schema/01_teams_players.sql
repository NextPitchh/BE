-- teams, players (명세 v0.3 §2-2, §2-3, §3-2)
-- 팀은 코드로만 식별한다. 이름은 과거 경기에도 현재 명칭이 소급되므로 키로 쓰지 않는다.

CREATE TABLE IF NOT EXISTS teams (
    team_code   VARCHAR(4)  PRIMARY KEY,      -- "HH", "SK" ...
    name        VARCHAR(20) NOT NULL,         -- "한화"
    full_name   VARCHAR(40),                  -- "한화 이글스"
    logo_url    VARCHAR(200)
);

-- player_id 는 데이터셋 pitcher_id / batter_id, 네이버 playerCode 와 같은
CREATE TABLE IF NOT EXISTS players (
    player_id    INTEGER     PRIMARY KEY,
    name         VARCHAR(30) NOT NULL,
    throws       CHAR(1),                     -- 'L' | 'R' | 'U'(언더핸드). 데이터셋 pitcher_throws 와 같은 컨벤션
    hit_type     CHAR(1),                     -- 'L' | 'R' | 'S' (타자만, 투수 전용 선수는 NULL)
    back_number  VARCHAR(4),
    birth_date   DATE,
    height_cm    NUMERIC(5,1),
    weight_kg    NUMERIC(5,1),
    photo_url    VARCHAR(200)                 -- 없으면 NULL → 프론트가 이니셜 아바타로 폴백
);
