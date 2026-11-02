package com.nemosdream.be.simulation.ai;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 버전·해시·전체 행 수가 확인된 스냅샷에서만 없는 키를 0으로 취급한다. */
@Repository
public class CatBoostHistoryRepository {
    public static final String VERSION = "pa-service-train-history-2016-2023-v1";
    private final JdbcTemplate jdbc;

    public CatBoostHistoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public CatBoostHistory find(int pitcherId, int batterId) {
        if (pitcherId <= 0 || batterId <= 0) throw new IllegalArgumentException("Invalid player ID");
        String sql = """
            SELECT COALESCE(b.prior_ab,0) batter_ab, COALESCE(b.prior_hits,0) batter_hits,
                   COALESCE(m.prior_ab,0) matchup_ab, COALESCE(m.prior_hits,0) matchup_hits
            FROM ai_pa_history_snapshot s
            LEFT JOIN ai_pa_batter_history b ON b.version=s.version AND b.batter_id=?
            LEFT JOIN ai_pa_matchup_history m ON m.version=s.version AND m.pitcher_id=? AND m.batter_id=?
            WHERE s.version=? AND s.as_of_exclusive=DATE '2024-01-01'
              AND s.batter_rows=656 AND s.matchup_rows=95448
              AND s.history_sha256='63010d94ab60031d5940f8dc1f6688ad5e0088e8986607cd581223071c4584bd'
              AND s.batter_csv_sha256='d3ee73a325954023656cfad61dc3346b8782f6e6fd1d43ed7034e6acaf131345'
              AND s.matchup_csv_sha256='eb2f66989ab20f933e6bf8b897f7fcb2997b2e09079951eff2c778fbf45d371f'
              AND (SELECT COUNT(*) FROM ai_pa_batter_history WHERE version=s.version)=656
              AND (SELECT COUNT(*) FROM ai_pa_matchup_history WHERE version=s.version)=95448
            """;
        List<CatBoostHistory> rows = jdbc.query(sql, (rs, row) -> new CatBoostHistory(
                rs.getInt("batter_ab"), rs.getInt("batter_hits"),
                rs.getInt("matchup_ab"), rs.getInt("matchup_hits")), batterId, pitcherId, batterId, VERSION);
        if (rows.size() != 1) throw new IllegalStateException("CatBoost frozen history is missing or incomplete");
        return rows.getFirst();
    }
}
