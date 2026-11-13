package com.nemosdream.be.bandit.repository;

import com.nemosdream.be.bandit.dto.BanditRoster;
import com.nemosdream.be.bandit.dto.BanditSnapshot;
import com.nemosdream.be.global.error.BusinessException;
import com.nemosdream.be.global.error.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BanditRepository {
    public static final String VERSION = "b1-2025-game-start-asof-v1";
    public static final String DEMO_VERSION = "b1-2026ks-demo-v1";
    public static final String DEMO_GAME_ID = "77771104SSKT02026";
    public static final String MODEL = "b1-optionb-chronology67360-v3";
    private static final String GAME_2025 = "2025[0-9]{4}[A-Z]{4}[012]2025";
    private static final String LI_SHA = "0e0b1dd20592903c9bbde710ccbe66e5ff601997c214ad9ba3422b7f10924757";
    private static final Dataset FROZEN_2025 = new Dataset(VERSION,
            "80050207db66b5c9e9444f7115bd9ff6fe9979fbbc1c17e052930ab26ae8ff4d",18155);
    private static final Dataset DEMO_2026_KS = new Dataset(DEMO_VERSION,
            "47b0b8450a969dd1573ebff797683856791bb103ec39dd5e33d53286c573f54d",24);
    private final JdbcTemplate jdbc;

    private record Dataset(String version, String snapshotSha, int snapshotRows) {}

    public BanditRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public static boolean supports(String gameId) { return dataset(gameId).isPresent(); }

    private static Optional<Dataset> dataset(String gameId) {
        if (gameId==null) return Optional.empty();
        if (gameId.matches(GAME_2025)) return Optional.of(FROZEN_2025);
        if (gameId.equals(DEMO_GAME_ID)) return Optional.of(DEMO_2026_KS);
        return Optional.empty();
    }

    private static Dataset require(String gameId) {
        return dataset(gameId).orElseThrow(() -> dataError("B1 동결 데이터가 없는 경기입니다."));
    }

    public void verifyDataset(String gameId) {
        Dataset d = require(gameId);
        Integer count = jdbc.queryForObject("""
            SELECT count(*) FROM bandit_dataset_manifest m WHERE version=? AND model_version=?
              AND snapshot_sha256=? AND li_sha256=? AND snapshot_rows=? AND li_rows=3888
              AND (SELECT count(*) FROM bandit_pitcher_snapshots WHERE version=m.version)=?
              AND (SELECT count(*) FROM bandit_train_li_lookup WHERE version=m.version)=3888
            """, Integer.class, d.version(), MODEL, d.snapshotSha(), LI_SHA, d.snapshotRows(), d.snapshotRows());
        if (count == null || count != 1) throw dataError("B1 동결 데이터가 없거나 불완전합니다.");
    }

    public List<BanditRoster> roster(String gameId) {
        return jdbc.query("SELECT team_code,pitcher_id,is_listed FROM game_bullpen WHERE game_id=?",
                (r,n) -> new BanditRoster(r.getString(1),r.getInt(2),r.getBoolean(3)), gameId);
    }

    public List<BanditSnapshot> snapshots(String gameId, String team) {
        return jdbc.query("""
            SELECT s.pitcher_id, s.prior_n_apps, s.prior_pitcher_reward_mean, s.prior_wpa_std, s.prior_bb_rate,
                   s.prior_so_rate, s.prior_hr_rate, s.prior_avg_pa_per_app
            FROM bandit_pitcher_snapshots s JOIN games g ON g.game_id=s.game_id
            WHERE s.version=? AND s.game_id=? AND s.team_code=? AND s.feature_schema_version='b1-li-88-v1'
              AND s.profile_asof_date=g.game_date
            """, (r,n) -> new BanditSnapshot(r.getInt(1),r.getDouble(2),r.getObject(3,Double.class),
                    r.getObject(4,Double.class),r.getObject(5,Double.class),r.getObject(6,Double.class),
                    r.getObject(7,Double.class),r.getObject(8,Double.class)), require(gameId).version(),gameId,team);
    }

    public double trainLi(String gameId, int inning, String half, int outs, String bases, int attackerDiff) {
        int clipped=Math.max(-4,Math.min(4,attackerDiff));
        List<Double> result=jdbc.query("""
            SELECT leverage_index FROM bandit_train_li_lookup
            WHERE version=? AND inning=? AND half=? AND out_count=? AND base_state=? AND score_diff_attacker=?
            """, (r,n)->r.getDouble(1),require(gameId).version(),inning,half,outs,bases,clipped);
        if (result.size()!=1 || !Double.isFinite(result.getFirst()) || result.getFirst()<0)
            throw dataError("B1 TRAIN LI를 찾을 수 없습니다.");
        return result.getFirst();
    }

    public static BusinessException dataError(String message) {
        return new BusinessException(ErrorCode.INTERNAL_ERROR,message);
    }
}
