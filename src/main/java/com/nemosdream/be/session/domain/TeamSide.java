package com.nemosdream.be.session.domain;

import java.util.List;

/**
 * 한 팀의 진행 상태.
 *
 * @param lineup    타순 1~9 자리의 타자 ID (index 0 = 1번)
 * @param nextSlot  다음에 타석에 설 타순 자리 (1~9)
 * @param pitcherId 이 팀이 수비할 때 마운드에 있는 투수
 * @param hits      안타 수 (스코어보드 H)
 * @param walks     볼넷 수 (스코어보드 B, 사구 포함)
 */
public record TeamSide(List<Integer> lineup, int nextSlot, int pitcherId, int hits, int walks) {

    public TeamSide {
        lineup = List.copyOf(lineup);
        if (lineup.size() != 9) {
            throw new IllegalArgumentException("타순은 9자리여야 합니다: " + lineup.size());
        }
    }

    public int batterAt(int slot) {
        return lineup.get(slot - 1);
    }

    /** slot 다음 자리. 9번 다음은 1번 */
    public static int nextOf(int slot) {
        return slot % 9 + 1;
    }
}
