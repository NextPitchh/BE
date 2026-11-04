package com.nemosdream.be.bandit.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nemosdream.be.bandit.dto.BanditRoster;
import com.nemosdream.be.bandit.dto.BanditSnapshot;
import com.nemosdream.be.bandit.repository.BanditRepository;
import com.nemosdream.be.global.error.BusinessException;
import com.nemosdream.be.global.error.ErrorCode;
import com.nemosdream.be.session.domain.*;
import com.nemosdream.be.simulation.domain.Bases;
import com.nemosdream.be.simulation.domain.PaOutcome;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BanditFeatureAssemblerTest {
    private SimState state(Half half, int pitcher, Map<Integer,PitcherDay> days) {
        var away=new TeamSide(List.of(1,2,3,4,5,6,7,8,9),1,200,0,0);
        var home=new TeamSide(List.of(11,12,13,14,15,16,17,18,19),1,pitcher,0,0);
        return new SimState(5,half,0,Bases.EMPTY,2,3,Linescore.empty(),away,home,days,Map.of(),null,null,false,List.of());
    }
    private GameSession session(SimState s) {
        return new GameSession("s","20250322HHKT02025",LocalDate.of(2025,3,22),"KT","HH",false,"default",1,Map.of(),
                new ActualGame("HH","KT",List.of(),List.of(),0,0,"actual"),Set.of(100,101,102),s,Instant.now());
    }
    @Test
    void keepsNullPriorAndExcludesUsedPitchers() {
        var repo=mock(BanditRepository.class);
        when(repo.roster(anyString())).thenReturn(List.of(new BanditRoster("KT",100,false),
                new BanditRoster("KT",101,true),new BanditRoster("KT",102,true)));
        when(repo.snapshots(anyString(),anyString())).thenReturn(List.of(
                new BanditSnapshot(101,0,null,null,null,null,null,null),
                new BanditSnapshot(102,1,0.1,null,0.1,0.2,0.0,4.0)));
        when(repo.trainLi(5,"top",0,"0",-1)).thenReturn(1.25);
        var state=state(Half.TOP,100,Map.of(100,new PitcherDay(1,1,0,0,0),102,new PitcherDay(1,2,0,0,0)));
        var prepared=new BanditFeatureAssembler(repo).assemble(session(state),state);
        assertEquals(java.util.Set.of("101"),prepared.candidateIds());
        var candidates=(List<?>)prepared.request().get("candidates");
        assertNull(((Map<?,?>)candidates.getFirst()).get("prior_bb_rate"));
        var game=(Map<?,?>)prepared.request().get("game_state");
        assertEquals(1,game.get("score_diff_defense")); assertEquals(3,game.get("n_pitchers_used"));
        assertEquals(0.0,(double)game.get("bullpen_available_ratio"),1e-12);
        var exhausted=state(Half.TOP,100,Map.of(100,new PitcherDay(1,1,0,0,0),101,new PitcherDay(1,1,0,0,0),102,new PitcherDay(1,1,0,0,0)));
        assertEquals(ErrorCode.NO_AVAILABLE_PITCHER,assertThrows(BusinessException.class,()->new BanditFeatureAssembler(repo).assemble(session(exhausted),exhausted)).errorCode());
        when(repo.snapshots(anyString(),anyString())).thenReturn(List.of(
                new BanditSnapshot(101,0,null,null,null,null,null,null),
                new BanditSnapshot(102,1,Double.NaN,null,0.1,0.2,0.0,4.0)));
        assertEquals(ErrorCode.INTERNAL_ERROR,assertThrows(BusinessException.class,()->new BanditFeatureAssembler(repo).assemble(session(state),state)).errorCode());
        when(repo.snapshots(anyString(),anyString())).thenReturn(List.of(
                new BanditSnapshot(101,0,null,null,null,null,null,null)));
        assertEquals(ErrorCode.INTERNAL_ERROR,assertThrows(BusinessException.class,()->new BanditFeatureAssembler(repo).assemble(session(state),state)).errorCode());
        var offense=state(Half.BOT,100,Map.of());
        assertEquals(ErrorCode.INVALID_ROLE,assertThrows(BusinessException.class,()->new BanditFeatureAssembler(repo).assemble(session(offense),offense)).errorCode());
        var wrongSeason=new GameSession("s","20240322HHKT02024",LocalDate.of(2024,3,22),"KT","HH",false,"default",1,Map.of(),
                new ActualGame("HH","KT",List.of(),List.of(),0,0,"actual"),Set.of(100,101,102),state,Instant.now());
        assertEquals(ErrorCode.INVALID_REQUEST,assertThrows(BusinessException.class,()->new BanditFeatureAssembler(repo).assemble(wrongSeason,state)).errorCode());
    }

    @Test
    void awayDefenseUsesAwayMinusHomeAndAttackerLookup() {
        var repo=mock(BanditRepository.class);
        when(repo.roster(anyString())).thenReturn(List.of(new BanditRoster("HH",200,false),
                new BanditRoster("HH",201,true)));
        when(repo.snapshots(anyString(),eq("HH"))).thenReturn(List.of(
                new BanditSnapshot(201,0,null,null,null,null,null,null)));
        when(repo.trainLi(5,"bot",0,"0",1)).thenReturn(0.75);
        var s=state(Half.BOT,100,Map.of(200,new PitcherDay(1,1,0,0,0)));
        var session=new GameSession("s","20250322HHKT02025",LocalDate.of(2025,3,22),"HH","KT",true,"default",1,Map.of(),
                new ActualGame("HH","KT",List.of(),List.of(),0,0,"actual"),Set.of(200,201),s,Instant.now());
        var game=(Map<?,?>)new BanditFeatureAssembler(repo).assemble(session,s).request().get("game_state");
        assertEquals(-1,game.get("score_diff_defense")); assertEquals(0,game.get("is_home_defense"));
        assertEquals(0.75,(double)game.get("leverage_index"),1e-12);
    }

    @Test
    void selectedPitcherIsExcludedAfterPaAndRestoredOnUndo() {
        var repo=mock(BanditRepository.class);
        when(repo.roster(anyString())).thenReturn(List.of(new BanditRoster("KT",100,false),
                new BanditRoster("KT",101,true),new BanditRoster("KT",102,true)));
        when(repo.snapshots(anyString(),anyString())).thenReturn(List.of(
                new BanditSnapshot(101,0,null,null,null,null,null,null),
                new BanditSnapshot(102,0,null,null,null,null,null,null)));
        var initial=state(Half.TOP,100,Map.of(100,new PitcherDay(1,4,0,0,0)));
        var game=session(initial);
        var assembler=new BanditFeatureAssembler(repo);
        assertEquals(Set.of("101","102"),assembler.assemble(game,game.current()).candidateIds());
        game.advance(PlateAppearanceEngine.simulate(initial,101,PaOutcome.FLY_OUT,4,null,
                new DivergedAt(5,Half.TOP,0)).next());
        assertEquals(Set.of("102"),assembler.assemble(game,game.current()).candidateIds());
        game.undo();
        assertEquals(Set.of("101","102"),assembler.assemble(game,game.current()).candidateIds());
    }
}
