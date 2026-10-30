package com.nemosdream.be.session.service;

import com.nemosdream.be.game.entity.Game;
import com.nemosdream.be.game.entity.GameBattingOrder;
import com.nemosdream.be.game.entity.GameBullpen;
import com.nemosdream.be.game.repository.GameBattingOrderRepository;
import com.nemosdream.be.game.repository.GameBullpenRepository;
import com.nemosdream.be.game.repository.GamePaRepository;
import com.nemosdream.be.game.repository.GameRepository;
import com.nemosdream.be.global.error.BusinessException;
import com.nemosdream.be.global.error.ErrorCode;
import com.nemosdream.be.session.domain.ActualGame;
import com.nemosdream.be.session.domain.ActualPa;
import com.nemosdream.be.session.domain.GameSession;
import com.nemosdream.be.session.domain.LineupEntry;
import com.nemosdream.be.session.domain.SimState;
import com.nemosdream.be.session.dto.CreateSessionRequest;
import com.nemosdream.be.session.dto.SessionStateResponse;
import com.nemosdream.be.session.store.SessionStore;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SessionService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final GameRepository games;
    private final GamePaRepository gamePas;
    private final GameBattingOrderRepository battingOrders;
    private final GameBullpenRepository bullpens;
    private final SessionStore store;
    private final GameStateAssembler assembler;

    public SessionService(GameRepository games, GamePaRepository gamePas, GameBattingOrderRepository battingOrders,
                          GameBullpenRepository bullpens, SessionStore store, GameStateAssembler assembler) {
        this.games = games;
        this.gamePas = gamePas;
        this.battingOrders = battingOrders;
        this.bullpens = bullpens;
        this.store = store;
        this.assembler = assembler;
    }

    /** 명세 §3-4. 시작 이닝의 내 팀 수비 반이닝 첫 타석 직전 상태로 세션을 만든다. */
    public SessionStateResponse create(CreateSessionRequest request) {
        Game game = games.findById(request.gameId())
                .filter(g -> !g.isExcluded())
                .orElseThrow(() -> new BusinessException(ErrorCode.GAME_NOT_FOUND));
        String myTeam = request.myTeam();
        boolean myTeamIsAway = myTeam.equals(game.getAwayTeam());
        if (!myTeamIsAway && !myTeam.equals(game.getHomeTeam())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, myTeam + " 은 이 경기 팀이 아닙니다.");
        }
        int startInning = request.startInning();
        if (Arrays.stream(game.availableInningsOf(myTeam)).noneMatch(i -> i == startInning)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    startInning + "회는 시작할 수 없습니다. 가능한 이닝: " + Arrays.toString(game.availableInningsOf(myTeam)));
        }

        List<GameBattingOrder> orders = battingOrders.findByGame(game.getId());
        ActualGame actual = new ActualGame(game.getAwayTeam(), game.getHomeTeam(),
                gamePas.findByGame(game.getId()).stream().map(ActualPa::of).toList(),
                orders.stream().map(LineupEntry::of).toList());
        SimState initial = InitialStateFactory.build(actual, myTeamIsAway, startInning);
        Set<Integer> myBullpen = bullpens.findByGameAndTeam(game.getId(), myTeam).stream()
                .map(GameBullpen::getPitcherId)
                .collect(Collectors.toSet());

        GameSession session = new GameSession(newSessionId(), game.getId(), game.getDate(), myTeam,
                myTeamIsAway ? game.getHomeTeam() : game.getAwayTeam(), myTeamIsAway, request.modeOrDefault(),
                RANDOM.nextLong(), starterPositions(orders), actual, myBullpen, initial, Instant.now());
        store.save(session);
        return new SessionStateResponse(assembler.toResponse(session));
    }

    /** 세션 조회. 없거나 만료됐으면 SESSION_EXPIRED */
    public GameSession get(String sessionId) {
        return store.find(sessionId).orElseThrow(() -> new BusinessException(ErrorCode.SESSION_EXPIRED));
    }

    private static Map<Integer, String> starterPositions(List<GameBattingOrder> orders) {
        Map<Integer, String> positions = new HashMap<>();
        for (GameBattingOrder o : orders) {
            if (o.getPosition() != null) {
                positions.putIfAbsent(o.getBatterId(), o.getPosition());
            }
        }
        return positions;
    }

    private static String newSessionId() {
        byte[] bytes = new byte[12];
        RANDOM.nextBytes(bytes);
        return "s_" + HexFormat.of().formatHex(bytes);
    }
}
