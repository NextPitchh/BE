package com.nemosdream.be.session.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 한 사용자의 시뮬레이션 세션. 상태 변경은 이 객체를 통해서만 하고, 이전 상태는 되돌리기용으로 쌓아 둔다.
 * 한 세션에 요청이 동시에 와도 상태가 꼬이지 않도록 변경 메서드는 synchronized.
 */
public class GameSession {

    private final String id;
    private final String gameId;
    private final LocalDate date;
    private final String myTeam;
    private final String opponentTeam;
    private final boolean myTeamIsAway;
    private final String mode;
    private final long seed;
    private final Map<Integer, String> positions;
    private final ActualGame actualGame;
    private final Set<Integer> myBullpen;
    private final Deque<SimState> history = new ArrayDeque<>();
    private SimState current;
    private volatile Instant lastAccess;

    public GameSession(String id, String gameId, LocalDate date, String myTeam, String opponentTeam,
                       boolean myTeamIsAway, String mode, long seed, Map<Integer, String> positions,
                       ActualGame actualGame, Set<Integer> myBullpen, SimState initial, Instant now) {
        this.id = id;
        this.gameId = gameId;
        this.date = date;
        this.myTeam = myTeam;
        this.opponentTeam = opponentTeam;
        this.myTeamIsAway = myTeamIsAway;
        this.mode = mode;
        this.seed = seed;
        this.positions = Map.copyOf(positions);
        this.actualGame = actualGame;
        this.myBullpen = Set.copyOf(myBullpen);
        this.current = initial;
        this.lastAccess = now;
    }

    public synchronized SimState current() {
        return current;
    }

    /** 새 상태로 진행하고 이전 상태는 되돌리기용으로 쌓는다 */
    public synchronized void advance(SimState next) {
        history.push(current);
        current = next;
    }

    /** 지금까지 진행한 단계 수 (되돌리면 줄어든다). 주사위 seed 를 단계마다 다르게 할 때 쓴다 */
    public synchronized int stepCount() {
        return history.size();
    }

    public synchronized boolean canUndo() {
        return !history.isEmpty();
    }

    /** 직전 상태로 돌아간다. 되돌릴 상태가 없으면 false */
    public synchronized boolean undo() {
        if (history.isEmpty()) {
            return false;
        }
        current = history.pop();
        return true;
    }

    /** 내 팀이 지금 수비 중인가 */
    public boolean isMyTeamFielding(SimState s) {
        return s.half().awayBats() != myTeamIsAway;
    }

    public void touch(Instant now) {
        lastAccess = now;
    }

    public Instant lastAccess() {
        return lastAccess;
    }

    public String id() {
        return id;
    }

    public String gameId() {
        return gameId;
    }

    public LocalDate date() {
        return date;
    }

    public String myTeam() {
        return myTeam;
    }

    public String opponentTeam() {
        return opponentTeam;
    }

    public boolean myTeamIsAway() {
        return myTeamIsAway;
    }

    public String mode() {
        return mode;
    }

    public long seed() {
        return seed;
    }

    /** 그 경기 실제 기록 (타석, 타순) */
    public ActualGame actualGame() {
        return actualGame;
    }

    /** 그 경기 실제 타석 전부 (pa_index 순, index == pa_index) */
    public List<ActualPa> actualPas() {
        return actualGame.pas();
    }

    /** 내 팀이 이 경기에 쓸 수 있었던 투수 (game_bullpen) */
    public Set<Integer> myBullpen() {
        return myBullpen;
    }

    /** 선발 타자의 예고 라인업 포지션 (한글). 교체 선수는 없다 */
    public String positionOf(int batterId) {
        return positions.get(batterId);
    }
}
