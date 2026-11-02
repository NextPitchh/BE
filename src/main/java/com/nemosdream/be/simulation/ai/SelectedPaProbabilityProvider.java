package com.nemosdream.be.simulation.ai;

import com.nemosdream.be.session.domain.SimState;
import com.nemosdream.be.simulation.domain.PaContext;
import com.nemosdream.be.simulation.domain.PaOutcome;
import com.nemosdream.be.simulation.service.Log5PaOutcomeModel;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SelectedPaProbabilityProvider implements PaProbabilityProvider {
    private final String selected;
    private final Log5PaOutcomeModel log5;
    private final CatBoostV6FeatureAssembler assembler;
    private final CatBoostV6Client catboost;

    public SelectedPaProbabilityProvider(@Value("${app.simulation.pa-model:log5}") String selected,
            Log5PaOutcomeModel log5, CatBoostV6FeatureAssembler assembler, CatBoostV6Client catboost) {
        if (!"log5".equals(selected) && !"catboost-v6".equals(selected)) {
            throw new IllegalArgumentException("app.simulation.pa-model must be log5 or catboost-v6");
        }
        this.selected = selected;
        this.log5 = log5;
        this.assembler = assembler;
        this.catboost = catboost;
    }

    @Override
    public Map<PaOutcome, Double> probabilities(String gameId, SimState state, CompletedPitchCounts pitches) {
        Objects.requireNonNull(state, "state");
        if (state.gameOver()) throw new IllegalArgumentException("Game is over");
        if (selected.equals("log5")) {
            return log5.probabilities(new PaContext(gameId, state.currentPitcherId(), state.currentBatterId(),
                    state.outs(), state.bases()));
        }
        CatBoostV6FeatureAssembler.Prepared prepared = assembler.assemble(gameId, state, pitches);
        return catboost.probabilities(prepared.state(), prepared.history());
    }

    @Override
    public String modelName() {
        return selected.equals("log5") ? log5.name() : CatBoostV6Client.MODEL_VERSION;
    }
}
