package com.luckpermsgui.menu;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of active {@link MenuSession}s, keyed by player. Only ever accessed from the main
 * thread; backed by a {@link ConcurrentHashMap} as cheap insurance rather than because concurrent
 * access is expected.
 */
public final class MenuSessionManager {

    private final Map<UUID, MenuSession> sessions = new ConcurrentHashMap<>();

    /**
     * Starts a brand-new navigation stack for this player, ending any previous one first (see
     * {@link MenuSession#clear()}) so a chat-input callback still pending against the old session
     * can't resurrect it later - see {@link MenuSession#push} for the exact scenario this guards.
     */
    public MenuSession startSession(Player player) {
        endSession(player.getUniqueId());
        MenuSession session = new MenuSession(player.getUniqueId());
        sessions.put(player.getUniqueId(), session);
        return session;
    }

    public Optional<MenuSession> getSession(UUID playerId) {
        return Optional.ofNullable(sessions.get(playerId));
    }

    /** Ends and forgets a player's session. Safe to call even if they have none. */
    public void endSession(UUID playerId) {
        MenuSession session = sessions.remove(playerId);
        if (session != null) {
            session.clear();
        }
    }
}
