package com.nemosdream.be.session.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.nemosdream.be.session.domain.GameSession;
import com.nemosdream.be.session.domain.Half;
import com.nemosdream.be.session.domain.Linescore;
import com.nemosdream.be.session.domain.SimState;
import com.nemosdream.be.session.domain.TeamSide;
import com.nemosdream.be.session.properties.SessionProperties;
import com.nemosdream.be.simulation.domain.Bases;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InMemorySessionStoreTest {

    static final Instant T0 = Instant.parse("2026-10-30T00:00:00Z");

    @Test
    void 마지막_요청_후_30분이_지나면_만료되고_요청마다_연장된다() {
        MutableClock clock = new MutableClock(T0);
        InMemorySessionStore store = new InMemorySessionStore(new SessionProperties(Duration.ofMinutes(30)), clock);
        store.save(session("s_1"));

        clock.now = T0.plus(Duration.ofMinutes(29));
        assertThat(store.find("s_1")).isPresent();          // 여기서 연장
        clock.now = T0.plus(Duration.ofMinutes(58));
        assertThat(store.find("s_1")).isPresent();
        clock.now = T0.plus(Duration.ofMinutes(89));
        assertThat(store.find("s_1")).isEmpty();
        assertThat(store.find("없는_세션")).isEmpty();
    }

    @Test
    void 되돌리기는_직전_상태로_돌아간다() {
        GameSession s = session("s_2");
        SimState first = s.current();
        SimState next = new SimState(5, Half.TOP, 1, Bases.EMPTY, 0, 0, Linescore.empty(), first.away(), first.home(),
                Map.of(), Map.of(), 7, null, false);

        assertThat(s.canUndo()).isFalse();
        s.advance(next);
        assertThat(s.canUndo()).isTrue();
        assertThat(s.undo()).isTrue();
        assertThat(s.current()).isEqualTo(first);
        assertThat(s.undo()).isFalse();
    }

    private static GameSession session(String id) {
        TeamSide side = new TeamSide(Collections.nCopies(9, 1), 1, 100, 0, 0);
        SimState state = new SimState(5, Half.TOP, 0, Bases.EMPTY, 0, 0, Linescore.empty(), side, side,
                Map.of(), Map.of(), 6, null, false);
        return new GameSession(id, "g", LocalDate.of(2025, 6, 1), "KT", "HH", false, "play", 1L, Map.of(), state, T0);
    }

    static class MutableClock extends Clock {
        Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
