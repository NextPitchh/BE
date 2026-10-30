package com.nemosdream.be.simulation.service;

import com.nemosdream.be.simulation.domain.PaContext;
import com.nemosdream.be.simulation.domain.PaOutcome;
import java.util.Map;

/**
 * 타석 결과 9종의 확률. 지금은 log5, 이후 CatBoost(Flask /predict-pa) 구현으로 바꿔 끼운다.
 * 반환값의 합은 1 이고, 병살이 불가능한 상황에서는 병살 확률이 0 이다.
 */
public interface PaOutcomeModel {

    Map<PaOutcome, Double> probabilities(PaContext context);

    /** 응답의 model_version 등에 쓰는 이름 */
    String name();
}
