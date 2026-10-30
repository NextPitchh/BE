package com.nemosdream.be.session.store;

import com.nemosdream.be.session.domain.GameSession;
import java.util.Optional;

/** 세션 저장소. 지금은 서버 메모리, 서버를 여러 대로 늘리면 Redis 구현으로 바꾼다. */
public interface SessionStore {

    void save(GameSession session);

    /** 만료됐거나 없으면 empty. 찾으면 만료 시간을 연장한다 */
    Optional<GameSession> find(String id);
}
