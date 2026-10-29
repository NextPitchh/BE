package com.nemosdream.be.weli.dto;

/**
 * 한 상황의 승리확률과 LI.
 * we 는 공격팀 관점이다. 화면(my_team 관점)으로 보낼 때는 수비 중이면 1 - we 로 바꾼다.
 */
public record WeLi(double we, double leverageIndex) {

    public double weFor(boolean attacking) {
        return attacking ? we : 1.0 - we;
    }
}
