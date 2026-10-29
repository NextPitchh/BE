package com.nemosdream.be.weli.service;

import com.nemosdream.be.weli.entity.LiPercentile;
import com.nemosdream.be.weli.repository.LiPercentileRepository;
import org.springframework.stereotype.Service;

/**
 * LI → 리그 백분위 (0~100). 화면의 "리그 상위 X%" 는 100 - 백분위.
 * 기준은 bandit train 구간 타석의 LI 분포이고, 값은 li_percentile 표(0.01 간격 1,001행)에서만 가져온다.
 * 표가 작아 처음 조회할 때 배열로 올려 두고 이후에는 DB 를 다시 읽지 않는다.
 */
@Service
public class LiPercentileService {

    private static final int STEPS_PER_UNIT = 100;      // 0.01 간격
    private static final int GRID_SIZE = 1001;          // 0.00 ~ 10.00

    private final LiPercentileRepository repository;
    private volatile int[] table;

    public LiPercentileService(LiPercentileRepository repository) {
        this.repository = repository;
    }

    /** LI 를 소수 둘째 자리로 반올림해 찾는다. 0 미만은 0, 10 초과는 10 으로 본다. */
    public int percentileOf(double leverageIndex) {
        if (Double.isNaN(leverageIndex)) {
            throw new IllegalArgumentException("LI 가 NaN 입니다");
        }
        int index = (int) Math.round(Math.clamp(leverageIndex * STEPS_PER_UNIT, 0.0, GRID_SIZE - 1.0));
        return table()[index];
    }

    private int[] table() {
        int[] loaded = table;
        if (loaded == null) {
            synchronized (this) {
                if (table == null) {
                    table = load();
                }
                loaded = table;
            }
        }
        return loaded;
    }

    private int[] load() {
        int[] values = new int[GRID_SIZE];
        boolean[] filled = new boolean[GRID_SIZE];
        for (LiPercentile row : repository.findAll()) {
            int index = row.getLiValue().movePointRight(2).intValueExact();
            values[index] = row.getPercentile();
            filled[index] = true;
        }
        for (boolean f : filled) {
            if (!f) {
                throw new IllegalStateException("li_percentile 이 비어 있거나 불완전합니다 (etl/load.sh 확인)");
            }
        }
        return values;
    }
}
