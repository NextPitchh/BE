package com.nemosdream.be.weli.service;

import com.nemosdream.be.weli.dto.WeLi;
import com.nemosdream.be.weli.entity.WeLiEntry;
import com.nemosdream.be.weli.entity.WeLiKey;
import com.nemosdream.be.weli.repository.WeLiRepository;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 상황 → WE·LI 조회. 값은 파이썬 원본(kbo-pitcher-change-ai-)으로 미리 계산한 we_li_table 에서만 가져온다.
 * 3,888행이라 처음 조회할 때 전부 메모리에 올려 두고 이후에는 DB 를 다시 읽지 않는다
 * (/repeat 처럼 한 요청에 수천 번 조회해도 빠르게).
 */
@Service
public class WeLiLookupService {

    public static final int SCORE_DIFF_MIN = -4;
    public static final int SCORE_DIFF_MAX = 4;
    private static final Set<String> HALVES = Set.of("top", "bot");
    private static final Set<String> BASE_STATES = Set.of("0", "1", "2", "3", "12", "13", "23", "123");

    private final WeLiRepository repository;
    private volatile Map<WeLiKey, WeLi> table;

    public WeLiLookupService(WeLiRepository repository) {
        this.repository = repository;
    }

    /**
     * @param scoreDiffAttacker 공격팀 관점 점수차 (공격팀 - 수비팀). ±4 를 넘으면 ±4 로 잘라서 찾는다 (원본과 동일)
     */
    public WeLi lookup(int inning, String half, int outCount, String baseState, int scoreDiffAttacker) {
        if (inning < 1 || inning > 9 || !HALVES.contains(half) || outCount < 0 || outCount > 2
                || !BASE_STATES.contains(baseState)) {
            throw new IllegalArgumentException("we_li_table 에 없는 상황: inning=%d half=%s out=%d base=%s"
                    .formatted(inning, half, outCount, baseState));
        }
        int clipped = Math.clamp(scoreDiffAttacker, SCORE_DIFF_MIN, SCORE_DIFF_MAX);
        WeLi value = table().get(new WeLiKey((short) inning, half, (short) outCount, baseState, (short) clipped));
        if (value == null) {
            throw new IllegalStateException("we_li_table 이 비어 있거나 불완전합니다 (etl/load.sh 확인)");
        }
        return value;
    }

    private Map<WeLiKey, WeLi> table() {
        Map<WeLiKey, WeLi> loaded = table;
        if (loaded == null) {
            synchronized (this) {
                if (table == null) {
                    table = repository.findAll().stream().collect(Collectors.toUnmodifiableMap(
                            WeLiEntry::getKey, e -> new WeLi(e.getWe(), e.getLeverageIndex())));
                }
                loaded = table;
            }
        }
        return loaded;
    }
}
