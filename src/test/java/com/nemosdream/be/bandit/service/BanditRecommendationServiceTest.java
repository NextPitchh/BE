package com.nemosdream.be.bandit.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nemosdream.be.bandit.client.BanditClient;
import com.nemosdream.be.bandit.dto.BanditPreparedRequest;
import com.nemosdream.be.bandit.dto.BanditRanking;
import com.nemosdream.be.bandit.dto.BanditRecommendationResponse;
import com.nemosdream.be.bandit.repository.BanditRepository;
import com.nemosdream.be.session.domain.*;
import com.nemosdream.be.session.service.SessionService;
import com.nemosdream.be.simulation.domain.Bases;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BanditRecommendationServiceTest {
    private SimState state(int outs) {
        var away=new TeamSide(List.of(1,2,3,4,5,6,7,8,9),1,200,0,0);
        var home=new TeamSide(List.of(11,12,13,14,15,16,17,18,19),1,100,0,0);
        return new SimState(5,Half.TOP,outs,Bases.EMPTY,2,3,Linescore.empty(),away,home,Map.of(),Map.of(),null,null,false,List.of());
    }
    @Test
    void scoresPastStateForResultScreen() {
        var past=state(0);
        var session=new GameSession("s","20250322HHKT02025",LocalDate.of(2025,3,22),"KT","HH",false,"default",1,Map.of(),
                new ActualGame("HH","KT",List.of(),List.of(),0,0,"actual"),Set.of(100,101,102),past,Instant.now());
        session.advance(state(1));
        var assembler=mock(BanditFeatureAssembler.class);
        var client=mock(BanditClient.class);
        var prepared=new BanditPreparedRequest(Map.of(),Set.of("101","102"));
        when(assembler.assemble(session,past)).thenReturn(prepared);
        when(client.recommend(prepared)).thenReturn(new BanditRecommendationResponse(BanditRepository.MODEL,
                List.of(new BanditRanking(101,0.2,1),new BanditRanking(102,0.1,2))));
        var service=new BanditRecommendationService(mock(SessionService.class),assembler,client);
        assertEquals(Optional.of(Map.of(101,0.2,102,0.1)),service.score(session,past,List.of(101,102)));
    }
}
