package com.nemosdream.be.bandit.service;

import com.nemosdream.be.bandit.dto.BanditPreparedRequest;
import com.nemosdream.be.bandit.dto.BanditRoster;
import com.nemosdream.be.bandit.dto.BanditSnapshot;
import com.nemosdream.be.bandit.repository.BanditRepository;
import com.nemosdream.be.global.error.BusinessException;
import com.nemosdream.be.global.error.ErrorCode;
import com.nemosdream.be.session.domain.GameSession;
import com.nemosdream.be.session.domain.SimState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BanditFeatureAssembler {
    private final BanditRepository repository;

    public BanditFeatureAssembler(BanditRepository repository) { this.repository=repository; }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public BanditPreparedRequest assemble(GameSession session, SimState state) {
        if (state.gameOver()) throw new BusinessException(ErrorCode.INVALID_REQUEST,"종료된 경기입니다.");
        if (!session.isMyTeamFielding(state)) throw new BusinessException(ErrorCode.INVALID_ROLE);
        if (session.gameId()==null || !session.gameId().matches("2025[0-9]{4}[A-Z]{4}[012]2025"))
            throw new BusinessException(ErrorCode.INVALID_REQUEST,"B1은 2025 시즌 동결 데이터만 지원합니다.");
        if (state.inning()<1 || state.inning()>9) throw new BusinessException(ErrorCode.INVALID_REQUEST,"B1은 1~9회만 지원합니다.");
        repository.verifyDataset();
        List<BanditRoster> roster=repository.roster(session.gameId());
        Set<Integer> teamAll=new HashSet<>(), known=new HashSet<>(), listed=new HashSet<>();
        for (var r:roster) {
            if (!r.team().equals(session.myTeam()) && !r.team().equals(session.opponentTeam()))
                throw BanditRepository.dataError("불펜 명단의 팀이 경기와 다릅니다.");
            if (!known.add(r.pitcherId())) throw BanditRepository.dataError("경기 내 투수 소속이 중복됩니다.");
            if (r.team().equals(session.myTeam())) {
                teamAll.add(r.pitcherId());
                if (r.listed()) listed.add(r.pitcherId());
            }
        }
        if (!teamAll.contains(state.currentPitcherId()) || !known.containsAll(state.pitcherDays().keySet()))
            throw BanditRepository.dataError("현재 또는 사용 투수의 경기 명단이 없습니다.");
        Set<Integer> used=new HashSet<>(state.pitcherDays().keySet());
        used.retainAll(teamAll);
        used.add(state.currentPitcherId());
        Map<Integer,BanditSnapshot> byId=new HashMap<>();
        for (var snapshot:repository.snapshots(session.gameId(),session.myTeam())) {
            validateSnapshot(snapshot);
            if (byId.put(snapshot.pitcherId(),snapshot)!=null) throw BanditRepository.dataError("중복 Snapshot입니다.");
        }
        if (!byId.keySet().equals(listed)) throw BanditRepository.dataError("불펜 명단과 B1 Snapshot이 일치하지 않습니다.");
        List<Integer> available=listed.stream().filter(id->!used.contains(id)).sorted().toList();
        if (available.isEmpty()) throw new BusinessException(ErrorCode.NO_AVAILABLE_PITCHER);
        List<Map<String,Object>> candidates=new ArrayList<>();
        Set<String> ids=new HashSet<>();
        for (int id:available) {
            var s=byId.get(id);
            Map<String,Object> c=new LinkedHashMap<>();
            c.put("pitcher_id",Integer.toString(id)); c.put("prior_n_apps",s.priorNApps());
            c.put("prior_pitcher_reward_mean",s.priorReward()); c.put("prior_wpa_std",s.priorWpaStd());
            c.put("prior_bb_rate",s.priorBbRate()); c.put("prior_so_rate",s.priorSoRate());
            c.put("prior_hr_rate",s.priorHrRate()); c.put("prior_avg_pa_per_app",s.priorAvgPa());
            candidates.add(c); ids.add(Integer.toString(id));
        }
        Map<String,Object> game=new LinkedHashMap<>();
        game.put("inning",state.inning()); game.put("out_count",state.outs());
        game.put("score_diff_defense",-state.scoreDiffAttacker());
        game.put("is_base1",state.bases().first()?1:0); game.put("is_base2",state.bases().second()?1:0);
        game.put("is_base3",state.bases().third()?1:0); game.put("is_home_defense",state.half().awayBats()?1:0);
        game.put("bullpen_available_ratio",(available.size()-1.0)/listed.size());
        game.put("n_pitchers_used",used.size()+1);
        game.put("leverage_index",repository.trainLi(state.inning(),state.half().code(),state.outs(),
                state.bases().baseState(),state.scoreDiffAttacker()));
        return new BanditPreparedRequest(Map.of("game_state",game,"candidates",candidates),Set.copyOf(ids));
    }

    private static void validateSnapshot(BanditSnapshot s) {
        if (s.pitcherId()<=0 || !Double.isFinite(s.priorNApps()) || s.priorNApps()<0
                || s.priorNApps()!=Math.rint(s.priorNApps()) || !finite(s.priorReward())
                || !nonnegative(s.priorWpaStd()) || !rate(s.priorBbRate()) || !rate(s.priorSoRate())
                || !rate(s.priorHrRate()) || !nonnegative(s.priorAvgPa())
                || (s.priorNApps()==0 && (s.priorReward()!=null || s.priorWpaStd()!=null || s.priorBbRate()!=null
                    || s.priorSoRate()!=null || s.priorHrRate()!=null || s.priorAvgPa()!=null))) {
            throw BanditRepository.dataError("B1 Snapshot 값이 올바르지 않습니다.");
        }
    }
    private static boolean finite(Double v) { return v==null || Double.isFinite(v); }
    private static boolean nonnegative(Double v) { return finite(v) && (v==null || v>=0); }
    private static boolean rate(Double v) { return nonnegative(v) && (v==null || v<=1); }
}
