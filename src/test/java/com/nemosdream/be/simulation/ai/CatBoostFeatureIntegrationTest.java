package com.nemosdream.be.simulation.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nemosdream.be.session.domain.Half;
import com.nemosdream.be.session.domain.Linescore;
import com.nemosdream.be.session.domain.SimState;
import com.nemosdream.be.session.domain.TeamSide;
import com.nemosdream.be.simulation.domain.Bases;
import com.nemosdream.be.simulation.domain.PaOutcome;
import com.nemosdream.be.simulation.service.Log5PaOutcomeModel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

class CatBoostFeatureIntegrationTest {
    private static final String GAME = "20250322HHKT02025";

    private SimState state(int pitcher, int batter) {
        TeamSide away = new TeamSide(List.of(batter,2,3,4,5,6,7,8,9), 1, 44444, 0, 0);
        TeamSide home = new TeamSide(List.of(11,12,13,14,15,16,17,18,19), 1, pitcher, 0, 0);
        return new SimState(5, Half.TOP, 0, Bases.EMPTY, 1, 2, Linescore.empty(), away, home,
                Map.of(), Map.of(), null, null, false, List.of());
    }

    @Test
    void assemblesCurrentPitcherAndRequiresItsCompletedCounts() {
        CatBoostHistoryRepository repo = mock(CatBoostHistoryRepository.class);
        when(repo.find(54354, 50704)).thenReturn(new CatBoostHistory(293,57,0,0));
        when(repo.find(50030, 50704)).thenReturn(new CatBoostHistory(293,57,4,1));
        when(repo.find(54354, 50165)).thenReturn(new CatBoostHistory(608,168,0,0));
        CatBoostV6FeatureAssembler assembler = new CatBoostV6FeatureAssembler(repo);
        var first = assembler.assemble(GAME, state(54354, 50704), new CompletedPitchCounts(54354,63,0));
        assertEquals(54354, first.state().pitcherId());
        assertEquals(1, first.state().awayScore());
        assertEquals(2, first.state().homeScore());
        assertEquals(new CatBoostHistory(293,57,0,0), first.history());
        assertThrows(IllegalArgumentException.class, () -> assembler.assemble(GAME, state(50030, 50704),
                new CompletedPitchCounts(54354,63,0)));
        assertThrows(IllegalArgumentException.class, () -> assembler.assemble(GAME, state(54354, 50704), null));
        var switched = assembler.assemble(GAME, state(50030, 50704), new CompletedPitchCounts(50030,0,0));
        assertEquals(50030, switched.state().pitcherId());
        assertEquals(0, switched.state().totalPitchCountBefore());
        assertEquals(4, switched.history().matchupAb());
        var nextBatter = assembler.assemble(GAME, state(54354,50165), new CompletedPitchCounts(54354,63,0));
        assertEquals(50165, nextBatter.state().batterId());
        assertEquals(608, nextBatter.history().batterPriorAb());
    }

    @Test
    void selectionCallsOnlyConfiguredModelAndNeverFallsBack() {
        Log5PaOutcomeModel log5 = mock(Log5PaOutcomeModel.class);
        CatBoostV6FeatureAssembler assembler = mock(CatBoostV6FeatureAssembler.class);
        CatBoostV6Client client = mock(CatBoostV6Client.class);
        var sim = state(54354, 50704);
        var counts = new CompletedPitchCounts(54354,63,0);
        var prepared = new CatBoostV6FeatureAssembler.Prepared(CatBoostV6State.from(GAME, sim, 63, 0),
                new CatBoostHistory(293,57,0,0));
        when(assembler.assemble(GAME, sim, counts)).thenReturn(prepared);
        when(client.probabilities(prepared.state(), prepared.history())).thenThrow(new IllegalStateException("offline"));
        PaProbabilityProvider provider = new SelectedPaProbabilityProvider("catboost-v6", log5, assembler, client);
        assertThrows(IllegalStateException.class, () -> provider.probabilities(GAME, sim, counts));
        verifyNoInteractions(log5);
        PaProbabilityProvider log5Provider = new SelectedPaProbabilityProvider("log5", log5, assembler, client);
        log5Provider.probabilities(GAME, sim, null);
        verify(log5).probabilities(any());
        assertThrows(IllegalArgumentException.class, () -> new SelectedPaProbabilityProvider("bad", log5, assembler, client));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "KBO_AI_DB_TEST_URL", matches = ".+")
    void readsActualDatabaseAndHandlesColdStart() {
        CatBoostHistoryRepository repo = realRepository();
        assertEquals(new CatBoostHistory(608,168,3,2), repo.find(50030,50165));
        assertEquals(new CatBoostHistory(0,0,0,0), repo.find(999999999,999999999));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "KBO_AI_DB_TEST_URL", matches = ".+")
    void incompleteSnapshotIsRejectedRatherThanTreatedAsColdStart() {
        DriverManagerDataSource ds = dataSource();
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(ds));
        transaction.executeWithoutResult(status -> {
            jdbc.update("DELETE FROM ai_pa_batter_history WHERE version=? AND batter_id=?",
                    CatBoostHistoryRepository.VERSION, 50165);
            assertThrows(IllegalStateException.class, () -> new CatBoostHistoryRepository(jdbc).find(50030,50165));
            status.setRollbackOnly();
        });
        assertEquals(new CatBoostHistory(608,168,3,2), new CatBoostHistoryRepository(jdbc).find(50030,50165));
        transaction.executeWithoutResult(status -> {
            jdbc.update("UPDATE ai_pa_history_snapshot SET history_sha256=? WHERE version=?",
                    "0".repeat(64), CatBoostHistoryRepository.VERSION);
            assertThrows(IllegalStateException.class, () -> new CatBoostHistoryRepository(jdbc).find(50030,50165));
            status.setRollbackOnly();
        });
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "KBO_AI_DB_TEST_URL", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "KBO_AI_INTEGRATION_URL", matches = ".+")
    void assemblesDatabaseHistoryAndCallsActualFastApi() {
        CatBoostV6FeatureAssembler assembler = new CatBoostV6FeatureAssembler(realRepository());
        CatBoostV6Client client = new CatBoostV6Client(new ObjectMapper(), System.getenv("KBO_AI_INTEGRATION_URL"), 30000);
        var prepared = assembler.assemble(GAME, state(54354, 50704), new CompletedPitchCounts(54354,63,0));
        assertEquals(new CatBoostHistory(293,57,0,0), prepared.history());
        Map<PaOutcome, Double> probabilities = client.probabilities(prepared.state(), prepared.history());
        assertEquals(9, probabilities.size());
        assertEquals(1.0, probabilities.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9);
        assertEquals(0.0, probabilities.get(PaOutcome.DOUBLE_PLAY));
        assertThrows(IllegalStateException.class, () -> client.probabilities(prepared.state(), new CatBoostHistory(0,0,0,0)));
        PaProbabilityProvider provider = new SelectedPaProbabilityProvider("catboost-v6",
                mock(Log5PaOutcomeModel.class), assembler, client);
        assertEquals(probabilities, provider.probabilities(GAME, state(54354,50704), new CompletedPitchCounts(54354,63,0)));
        Map<PaOutcome, Double> cold = provider.probabilities(GAME, state(999999999,999999999),
                new CompletedPitchCounts(999999999,0,0));
        assertEquals(9, cold.size());
        assertEquals(1.0, cold.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9);
    }

    private CatBoostHistoryRepository realRepository() {
        return new CatBoostHistoryRepository(new JdbcTemplate(dataSource()));
    }

    private DriverManagerDataSource dataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl(System.getenv("KBO_AI_DB_TEST_URL"));
        ds.setUsername(System.getenv().getOrDefault("KBO_AI_DB_TEST_USER", "postgres"));
        ds.setPassword(System.getenv().getOrDefault("KBO_AI_DB_TEST_PASSWORD", ""));
        return ds;
    }
}
