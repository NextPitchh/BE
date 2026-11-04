package com.nemosdream.be.session.service;

import com.nemosdream.be.appconfig.properties.AppConfigProperties;
import com.nemosdream.be.game.entity.GameBullpen;
import com.nemosdream.be.game.repository.GameBullpenRepository;
import com.nemosdream.be.global.error.BusinessException;
import com.nemosdream.be.global.error.ErrorCode;
import com.nemosdream.be.matchup.dto.MatchupLabel;
import com.nemosdream.be.matchup.service.MatchupService;
import com.nemosdream.be.pitcher.dto.PitcherCardStats;
import com.nemosdream.be.pitcher.service.PitcherStatsService;
import com.nemosdream.be.player.entity.Player;
import com.nemosdream.be.player.repository.PlayerRepository;
import com.nemosdream.be.session.domain.GameSession;
import com.nemosdream.be.session.domain.PitcherDay;
import com.nemosdream.be.session.domain.PlateAppearanceEngine;
import com.nemosdream.be.session.domain.SimState;
import com.nemosdream.be.session.dto.RecommendResponse;
import com.nemosdream.be.session.dto.RecommendResponse.CandidatePitcher;
import com.nemosdream.be.weli.service.LiPercentileService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 명세 §3-5 POST /sessions/{id}/recommend — 지금 상황의 LI 와 교체 후보.
 *
 * <pre>
 * 후보   = 내 팀 경기 전 불펜 명단(is_listed) 중 현재 투수가 아닌 투수
 * used   = 그중 이 경기에 이미 등판한 투수 (다시 못 나옴, 목록에는 남김, 점수 없음)
 * 순서   = model_score 내림차순 (밴딧 연결 전에는 quality 내림차순, 순위 null)
 * </pre>
 */
@Service
public class RecommendService {

    private final SessionService sessions;
    private final GameBullpenRepository bullpens;
    private final PlayerRepository players;
    private final PitcherStatsService pitcherStats;
    private final MatchupService matchups;
    private final LiPercentileService liPercentile;
    private final WinExpectancyCalculator winExpectancy;
    private final GameStateAssembler assembler;
    private final CandidateScorer scorer;
    private final AppConfigProperties config;

    public RecommendService(SessionService sessions, GameBullpenRepository bullpens, PlayerRepository players,
                            PitcherStatsService pitcherStats, MatchupService matchups,
                            LiPercentileService liPercentile, WinExpectancyCalculator winExpectancy,
                            GameStateAssembler assembler, CandidateScorer scorer, AppConfigProperties config) {
        this.sessions = sessions;
        this.bullpens = bullpens;
        this.players = players;
        this.pitcherStats = pitcherStats;
        this.matchups = matchups;
        this.liPercentile = liPercentile;
        this.winExpectancy = winExpectancy;
        this.assembler = assembler;
        this.scorer = scorer;
        this.config = config;
    }

    public RecommendResponse recommend(String sessionId) {
        GameSession session = sessions.get(sessionId);
        SimState s = session.current();
        if (s.gameOver()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "경기가 끝났습니다.");
        }
        if (!session.isMyTeamFielding(s)) {
            throw new BusinessException(ErrorCode.INVALID_ROLE);
        }
        String gameId = session.gameId();
        int currentId = s.currentPitcherId();
        int batterId = s.currentBatterId();

        List<GameBullpen> listed = bullpens.findByGameAndTeam(gameId, session.myTeam()).stream()
                .filter(b -> b.isListed() && b.getPitcherId() != currentId)
                .toList();
        List<Integer> ids = new ArrayList<>(listed.stream().map(GameBullpen::getPitcherId).toList());
        List<Integer> available = ids.stream().filter(id -> !s.pitcherDays().containsKey(id)).toList();
        if (available.isEmpty()) {
            throw new BusinessException(ErrorCode.NO_AVAILABLE_PITCHER);
        }

        Set<Integer> all = new HashSet<>(ids);
        all.add(currentId);
        Map<Integer, Player> playerById = players.findAllById(all).stream()
                .collect(Collectors.toMap(Player::getId, Function.identity()));
        Map<Integer, PitcherCardStats> cards = pitcherStats.getCardStats(gameId, all);
        Map<Integer, Optional<MatchupLabel>> labels = matchups.labelsVsBatter(gameId, all, batterId);
        Optional<Map<Integer, Double>> scores = scorer.score(session, s, available);

        List<CandidatePitcher> availableRows = new ArrayList<>();
        List<CandidatePitcher> usedRows = new ArrayList<>();
        for (GameBullpen b : listed) {
            int id = b.getPitcherId();
            boolean used = s.pitcherDays().containsKey(id);
            Double score = used ? null : scores.map(m -> m.get(id)).orElse(null);
            CandidatePitcher row = candidate(id, b.getRole(), playerById.get(id), cards.get(id),
                    s.pitcherDay(id), used, score, label(labels, id));
            (used ? usedRows : availableRows).add(row);
        }
        availableRows = ranked(availableRows, scores.isPresent());
        usedRows.sort(Comparator.comparingInt(CandidatePitcher::pitcherId));

        List<RecommendResponse.Candidate> candidates = new ArrayList<>();
        availableRows.forEach(c -> candidates.add(new RecommendResponse.Candidate(c)));
        usedRows.forEach(c -> candidates.add(new RecommendResponse.Candidate(c)));

        double li = winExpectancy.leverageIndex(s);
        PitcherDay today = s.pitcherDay(currentId);
        RecommendResponse.CurrentPitcher current = new RecommendResponse.CurrentPitcher(
                assembler.pitcher(gameId, s, currentId, playerById.get(currentId)),
                new RecommendResponse.ThisOuting((int) Math.round(today.pitches()), today.outs(),
                        today.hitsAllowed(), today.walks(), label(labels, currentId)));

        return new RecommendResponse(round4(li), band(li), liPercentile.percentileOf(li),
                config.leverage().max(), scorer.source(s), current, candidates,
                new RecommendResponse.BullpenSummary(availableRows.size(), usedRows.size(),
                        availableRows.size() + usedRows.size()),
                session.actualGame().bullpenSource(), actualDecision(session, s, playerById));
    }

    /** 점수가 있으면 점수 내림차순 + 순위, 없으면 quality 내림차순 (순위 null) */
    private static List<CandidatePitcher> ranked(List<CandidatePitcher> rows, boolean scored) {
        Comparator<CandidatePitcher> order = scored
                ? Comparator.comparing(CandidatePitcher::modelScore, Comparator.nullsLast(Comparator.reverseOrder()))
                : Comparator.comparing(CandidatePitcher::qualityPercentile, Comparator.nullsLast(Comparator.reverseOrder()));
        List<CandidatePitcher> sorted = rows.stream()
                .sorted(order.thenComparingInt(CandidatePitcher::pitcherId)).toList();
        if (!scored) {
            return sorted;
        }
        List<CandidatePitcher> out = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            CandidatePitcher c = sorted.get(i);
            out.add(new CandidatePitcher(c.pitcherId(), c.name(), c.throwingHand(), c.photoUrl(), c.season(),
                    c.vsBatterHand(), c.workload(), c.availability(), c.unavailableReason(), c.modelScore(),
                    c.modelScore() == null ? null : i + 1, c.qualityPercentile(), c.matchupVsBatter(), c.role()));
        }
        return out;
    }

    private static CandidatePitcher candidate(int id, String role, Player p, PitcherCardStats card, PitcherDay day,
                                              boolean used, Double score, MatchupLabel label) {
        return new CandidatePitcher(id, p == null ? null : p.getName(), p == null ? null : p.getThrows(),
                p == null ? null : p.getPhotoUrl(),
                card == null ? null : card.season(), card == null ? null : card.vsBatterHand(),
                GameStateAssembler.workload(day, card),
                used ? "used" : "available", used ? "이미 등판" : null,
                score, null, card == null ? null : card.qualityPercentile(), label, role);
    }

    /** 분기 전에만: 이번 타석 실제 감독이 올린 투수 */
    private static RecommendResponse.ActualDecision actualDecision(GameSession session, SimState s,
                                                                  Map<Integer, Player> playerById) {
        if (s.isDiverged()) {
            return null;
        }
        int actualId = PlateAppearanceEngine.actualUnit(session.actualPas(), s.nextActualPaIndex())
                .map(PlateAppearanceEngine.ActualUnit::startingPitcherId)
                .orElse(session.actualPas().get(s.nextActualPaIndex()).pitcherId());
        Player p = playerById.get(actualId);
        return new RecommendResponse.ActualDecision(actualId, p == null ? null : p.getName(),
                actualId != s.currentPitcherId());
    }

    /** /config 의 레버리지 밴드 라벨. 마지막 밴드 상한 이상이면 마지막 밴드 */
    private String band(double li) {
        List<AppConfigProperties.Band> bands = config.leverage().bands();
        for (AppConfigProperties.Band b : bands) {
            if (li >= b.min() && li < b.max()) {
                return b.label();
            }
        }
        return bands.getLast().label();
    }

    private static MatchupLabel label(Map<Integer, Optional<MatchupLabel>> labels, int id) {
        return labels.getOrDefault(id, Optional.empty()).orElse(null);
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
