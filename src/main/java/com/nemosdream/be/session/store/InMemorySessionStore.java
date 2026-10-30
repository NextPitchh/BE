package com.nemosdream.be.session.store;

import com.nemosdream.be.session.domain.GameSession;
import com.nemosdream.be.session.properties.SessionProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 서버 메모리 세션 저장소. 마지막 요청 후 ttl 이 지나면 만료된다 (요청마다 연장).
 * 서버가 재시작되면 세션은 사라진다 → 프론트는 SESSION_EXPIRED 로 처음부터 다시 시작한다.
 */
@Component
@EnableConfigurationProperties(SessionProperties.class)
public class InMemorySessionStore implements SessionStore {

    private final Map<String, GameSession> sessions = new ConcurrentHashMap<>();
    private final SessionProperties properties;
    private final Clock clock;

    @Autowired
    public InMemorySessionStore(SessionProperties properties) {
        this(properties, Clock.systemUTC());
    }

    InMemorySessionStore(SessionProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void save(GameSession session) {
        removeExpired();
        sessions.put(session.id(), session);
    }

    @Override
    public Optional<GameSession> find(String id) {
        GameSession session = sessions.get(id);
        if (session == null) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        if (isExpired(session, now)) {
            sessions.remove(id);
            return Optional.empty();
        }
        session.touch(now);
        return Optional.of(session);
    }

    private void removeExpired() {
        Instant now = clock.instant();
        sessions.values().removeIf(s -> isExpired(s, now));
    }

    private boolean isExpired(GameSession s, Instant now) {
        return s.lastAccess().plus(properties.ttl()).isBefore(now);
    }
}
