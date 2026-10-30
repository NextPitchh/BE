package com.nemosdream.be.global.util;

public final class Decimals {

    private Decimals() {
    }

    /** DB REAL → 소수 3자리 double. 0.241 이 0.24099999 로 나가지 않게 한다. null 은 그대로. */
    public static Double round3(Float value) {
        return value == null ? null : Math.round(value * 1000.0) / 1000.0;
    }
}
