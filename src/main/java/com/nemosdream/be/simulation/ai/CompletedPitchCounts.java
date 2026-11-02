package com.nemosdream.be.simulation.ai;

/** 이 구수가 어느 투수의 것인지 명시하여 투수 교체 후 이전 투수 값을 재사용하지 않는다. */
public record CompletedPitchCounts(int pitcherId, int total, int inning) {
    public CompletedPitchCounts {
        if (pitcherId <= 0 || total < 0 || inning < 0 || inning > total) {
            throw new IllegalArgumentException("Invalid completed pitch counts");
        }
    }
}
