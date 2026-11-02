package com.nemosdream.be.simulation.ai;

import com.nemosdream.be.session.domain.SimState;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class CatBoostV6FeatureAssembler {
    private final CatBoostHistoryRepository history;

    public CatBoostV6FeatureAssembler(CatBoostHistoryRepository history) {
        this.history = history;
    }

    public record Prepared(CatBoostV6State state, CatBoostHistory history) {}

    public Prepared assemble(String gameId, SimState state, CompletedPitchCounts pitches) {
        Objects.requireNonNull(state, "state");
        if (pitches == null || pitches.pitcherId() != state.currentPitcherId()) {
            throw new IllegalArgumentException("Completed counts for the current pitcher are required");
        }
        CatBoostV6State request = CatBoostV6State.from(gameId, state, pitches.total(), pitches.inning());
        return new Prepared(request, history.find(request.pitcherId(), request.batterId()));
    }
}
