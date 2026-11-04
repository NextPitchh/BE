package com.nemosdream.be.bandit.repository;

import com.nemosdream.be.bandit.dto.BanditRoster;
import com.nemosdream.be.bandit.dto.BanditSnapshot;
import com.nemosdream.be.global.error.BusinessException;
import com.nemosdream.be.global.error.ErrorCode;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BanditRepository {
    public static final String VERSION = "b1-2025-game-start-asof-v1";
    public static final String MODEL = "b1-optionb-chronology67360-v3";
    private final JdbcTemplate jdbc;

    public BanditRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void verifyDataset() {
        Integer count = jdbc.queryForObject("""
            SELECT count(*) FROM bandit_dataset_manifest m WHERE version=? AND model_version=?
              AND snapshot_sha256='80050207db66b5c9e9444f7115bd9ff6fe9979fbbc1c17e052930ab26ae8ff4d'
              AND li_sha256='0e0b1dd20592903c9bbde710ccbe66e5ff601997c214ad9ba3422b7f10924757'
              AND snapshot_rows=18155 AND li_rows=3888
              AND (SELECT count(*) FROM bandit_pitcher_snapshots WHERE version=m.version)=18155
              AND (SELECT count(*) FROM bandit_train_li_lookup WHERE version=m.version)=3888
            """, Integer.class, VERSION, MODEL);
        if (count == null || count != 1) throw dataError("B1 동결 데이터가 없거나 불완전합니다.");
    }

    public List<BanditRoster> roster(String gameId) {
        return jdbc.query("SELECT team_code,pitcher_id,is_listed FROM game_bullpen WHERE game_id=?",
                (r,n) -> new BanditRoster(r.getString(1),r.getInt(2),r.getBoolean(3)), gameId);
    }

    public List<BanditSnapshot> snapshots(String gameId, String team) {
        return jdbc.query("""
            SELECT pitcher_id, prior_n_apps, prior_pitcher_reward_mean, prior_wpa_std, prior_bb_rate,
                   prior_so_rate, prior_hr_rate, prior_avg_pa_per_app
            FROM bandit_pitcher_snapshots
            WHERE version=? AND game_id=? AND team_code=? AND feature_schema_version='b1-li-88-v1'
              AND profile_asof_date=to_date(substring(game_id,1,8),'YYYYMMDD')
            """, (r,n) -> new BanditSnapshot(r.getInt(1),r.getDouble(2),r.getObject(3,Double.class),
                    r.getObject(4,Double.class),r.getObject(5,Double.class),r.getObject(6,Double.class),
                    r.getObject(7,Double.class),r.getObject(8,Double.class)), VERSION,gameId,team);
    }

    public double trainLi(int inning, String half, int outs, String bases, int attackerDiff) {
        int clipped=Math.max(-4,Math.min(4,attackerDiff));
        List<Double> result=jdbc.query("""
            SELECT leverage_index FROM bandit_train_li_lookup
            WHERE version=? AND inning=? AND half=? AND out_count=? AND base_state=? AND score_diff_attacker=?
            """, (r,n)->r.getDouble(1),VERSION,inning,half,outs,bases,clipped);
        if (result.size()!=1 || !Double.isFinite(result.getFirst()) || result.getFirst()<0)
            throw dataError("B1 TRAIN LI를 찾을 수 없습니다.");
        return result.getFirst();
    }

    public static BusinessException dataError(String message) {
        return new BusinessException(ErrorCode.INTERNAL_ERROR,message);
    }
}
