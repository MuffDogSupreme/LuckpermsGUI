package com.luckpermsgui.modules.auditlog;

import com.luckpermsgui.api.LuckPermsHandler;
import net.luckperms.api.actionlog.Action;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Shared, soft-cached view of the LuckPerms action log with a 45-second TTL.
 *
 * <p>{@link net.luckperms.api.actionlog.ActionLogger#getLog()} has no server-side filter or
 * pagination - it returns the entire log every time - so fetching it on every menu open or click
 * would mean re-transferring a potentially huge dataset constantly. This class fetches once,
 * holds the result for 45 seconds, and lets every open {@code ActionLogMenu} share it. One
 * instance lives on the main plugin class for the plugin's whole lifetime.
 *
 * <p>{@link #get} can legitimately be called from the main thread while a previous fetch's
 * completion is still landing on whatever executor LuckPerms uses internally - unlike
 * {@code MenuSession}, this class's state genuinely can be touched from two different threads at
 * once, so its bookkeeping is synchronized rather than relying on caller-side threading
 * discipline.
 */
public final class ActionLogCache {

    private static final Duration TTL = Duration.ofSeconds(45);

    private final LuckPermsHandler handler;
    private List<Action> cachedActions = List.of();
    private Instant cachedAt = Instant.EPOCH;
    private CompletableFuture<List<Action>> inFlight;

    public ActionLogCache(LuckPermsHandler handler) {
        this.handler = handler;
    }

    /**
     * Returns the cached, newest-first action list, refreshing it first if it's older than 45
     * seconds or {@code forceRefresh} is set. A refresh already in progress is shared rather than
     * duplicated if this is called again before it completes.
     */
    public synchronized CompletableFuture<List<Action>> get(boolean forceRefresh) {
        if (!forceRefresh && inFlight == null && Duration.between(cachedAt, Instant.now()).compareTo(TTL) < 0) {
            return CompletableFuture.completedFuture(cachedActions);
        }
        if (inFlight != null) {
            return inFlight;
        }
        CompletableFuture<List<Action>> future = handler.fetchActionLog().thenApply(actionLog -> {
            List<Action> sorted = new ArrayList<>(actionLog.getContent());
            sorted.sort(Comparator.comparing(Action::getTimestamp).reversed());
            return List.copyOf(sorted);
        });
        inFlight = future;
        future.whenComplete(this::onFetchComplete);
        return future;
    }

    private synchronized void onFetchComplete(List<Action> actions, Throwable throwable) {
        inFlight = null;
        if (throwable == null) {
            cachedActions = actions;
            cachedAt = Instant.now();
        }
    }
}
