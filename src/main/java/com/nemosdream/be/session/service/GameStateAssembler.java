package com.nemosdream.be.session.service;

import com.nemosdream.be.batter.dto.BatterCardStats;
import com.nemosdream.be.batter.service.BatterStatsService;
import com.nemosdream.be.matchup.dto.VsCurrentPitcher;
import com.nemosdream.be.matchup.service.MatchupService;
import com.nemosdream.be.pitcher.dto.Fatigue;
import com.nemosdream.be.pitcher.dto.PitcherCardStats;
import com.nemosdream.be.pitcher.service.PitcherStatsService;
import com.nemosdream.be.player.entity.Player;
import com.nemosdream.be.player.repository.PlayerRepository;
import com.nemosdream.be.session.domain.BatterDay;
import com.nemosdream.be.session.domain.GameSession;
import com.nemosdream.be.session.domain.PitcherDay;
import com.nemosdream.be.session.domain.SimState;
import com.nemosdream.be.session.domain.TeamSide;
import com.nemosdream.be.session.dto.BatterResponse;
import com.nemosdream.be.session.dto.GameStateResponse;
import com.nemosdream.be.session.dto.PitcherResponse;
import com.nemosdream.be.weli.dto.WeLi;
import com.nemosdream.be.weli.service.WeLiLookupService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 세션 상태(SimState) → 명세 GameState 응답. 선수 이름·카드·상대 전적·WE/LI 를 붙인다. */
@Component
@Transactional(readOnly = true)
public class GameStateAssembler {

    private static final int UPCOMING = 3;
    private static final Map<String, String> POSITION_CODES = Map.ofEntries(
            Map.entry("투수", "P"), Map.entry("포수", "C"), Map.entry("1루수", "1B"), Map.entry("2루수", "2B"),
            Map.entry("3루수", "3B"), Map.entry("유격수", "SS"), Map.entry("좌익수", "LF"),
            Map.entry("중견수", "CF"), Map.entry("우익수", "RF"), Map.entry("지명타자", "DH"));

    private final PlayerRepository players;
    private final PitcherStatsService pitcherStats;
    private final BatterStatsService batterStats;
    private final MatchupService matchups;
    private final WeLiLookupService weLi;

    public GameStateAssembler(PlayerRepository players, PitcherStatsService pitcherStats,
                              BatterStatsService batterStats, MatchupService matchups, WeLiLookupService weLi) {
        this.players = players;
        this.pitcherStats = pitcherStats;
        this.batterStats = batterStats;
        this.matchups = matchups;
        this.weLi = weLi;
    }

    public GameStateResponse toResponse(GameSession session) {
        SimState s = session.current();
        String gameId = session.gameId();
        int pitcherId = s.currentPitcherId();

        TeamSide batting = s.batting();
        List<Integer> slots = new ArrayList<>();
        for (int i = 0, slot = batting.nextSlot(); i <= UPCOMING; i++, slot = TeamSide.nextOf(slot)) {
            slots.add(slot);
        }
        List<Integer> batterIds = slots.stream().map(batting::batterAt).toList();

        List<Integer> ids = new ArrayList<>(batterIds);
        ids.add(pitcherId);
        Map<Integer, Player> playerById = players.findAllById(ids).stream()
                .collect(Collectors.toMap(Player::getId, Function.identity()));
        Map<Integer, BatterCardStats> batterCards = batterStats.getCardStats(gameId, batterIds);
        Map<Integer, Optional<VsCurrentPitcher>> vs = matchups.vsPitcher(gameId, pitcherId, batterIds);

        List<BatterResponse> batters = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            int id = batterIds.get(i);
            batters.add(batter(session, s, id, slots.get(i), playerById.get(id), batterCards.get(id),
                    vs.getOrDefault(id, Optional.empty()).orElse(null)));
        }

        boolean fielding = session.isMyTeamFielding(s);
        double[] weLiValues = winExpectancyAndLi(session, s, fielding);

        return new GameStateResponse(
                session.id(), gameId, session.date().toString(), s.inning(), s.half(), s.outs(),
                s.bases().baseState(),
                new GameStateResponse.Score(s.scoreHome(), s.scoreAway()),
                s.scoreHome() - s.scoreAway(), s.scoreDiffAttacker(),
                session.myTeam(), session.opponentTeam(), fielding ? "defense" : "offense",
                pitcher(gameId, s, pitcherId, playerById.get(pitcherId)),
                batters.getFirst(), batters.subList(1, batters.size()),
                weLiValues[1], weLiValues[0], session.myTeam(),
                linescore(s),
                s.isDiverged(),
                s.divergedAt() == null ? null : new GameStateResponse.DivergedAtResponse(
                        s.divergedAt().inning(), s.divergedAt().half(), s.divergedAt().paIndex()),
                s.gameOver(), session.canUndo());
    }

    /** [my_team 관점 WE, LI]. 경기가 끝났으면 이기면 1, 지면 0, 동점 0.5 이고 LI 는 0 */
    private double[] winExpectancyAndLi(GameSession session, SimState s, boolean myTeamFielding) {
        if (s.gameOver()) {
            int mine = session.myTeamIsAway() ? s.scoreAway() : s.scoreHome();
            int theirs = session.myTeamIsAway() ? s.scoreHome() : s.scoreAway();
            return new double[]{mine > theirs ? 1.0 : mine < theirs ? 0.0 : 0.5, 0.0};
        }
        WeLi v = weLi.lookup(s.inning(), s.half().code(), s.outs(), s.bases().baseState(), s.scoreDiffAttacker());
        return new double[]{round4(v.weFor(!myTeamFielding)), round4(v.leverageIndex())};
    }

    PitcherResponse pitcher(String gameId, SimState s, int pitcherId, Player p) {
        PitcherCardStats card = pitcherStats.getCardStats(gameId, List.of(pitcherId)).get(pitcherId);
        PitcherDay day = s.pitcherDay(pitcherId);
        Fatigue f = card == null ? null : card.fatigue();
        PitcherResponse.Workload workload = new PitcherResponse.Workload(
                day.paFaced(), (int) Math.round(day.pitches()),
                f == null ? null : f.avgPitchesPerPa(),
                f == null ? null : f.daysRest(),
                f == null ? 0 : f.appearancesLast7d(),
                f == null ? 0 : f.consecutiveDays(),
                f == null ? 0 : f.pitchesLast3d());
        return new PitcherResponse(pitcherId, p == null ? null : p.getName(), p == null ? null : p.getThrows(),
                p == null ? null : p.getPhotoUrl(),
                card == null ? null : card.season(), card == null ? null : card.vsBatterHand(),
                workload, "current", null);
    }

    private BatterResponse batter(GameSession session, SimState s, int batterId, int slot, Player p,
                                  BatterCardStats card, VsCurrentPitcher vs) {
        BatterDay day = s.batterDay(batterId);
        String position = session.positionOf(batterId);
        return new BatterResponse(batterId, p == null ? null : p.getName(), p == null ? null : p.getHitType(),
                p == null ? null : p.getPhotoUrl(), slot,
                position == null ? null : POSITION_CODES.getOrDefault(position, position),
                card == null ? null : card.season(), card == null ? null : card.recentAvg(), vs,
                new BatterResponse.Today(day.ab(), day.h(), day.rbi(), day.log()));
    }

    private static GameStateResponse.LinescoreResponse linescore(SimState s) {
        return new GameStateResponse.LinescoreResponse(s.linescore().away(), s.linescore().home(),
                new GameStateResponse.Totals(
                        new GameStateResponse.TeamTotals(s.scoreAway(), s.away().hits(), null, s.away().walks()),
                        new GameStateResponse.TeamTotals(s.scoreHome(), s.home().hits(), null, s.home().walks())));
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
