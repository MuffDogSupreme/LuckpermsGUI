package com.luckpermsgui.menu;

import java.time.Duration;
import java.time.Instant;

/**
 * Tracks a "click again to confirm" arming window for one destructive action in a menu (deleting
 * a group or track). {@link #arm()} starts a 10-second window; a second read via {@link #isArmed()}
 * during that window means the next click should be treated as confirmation instead of the first
 * warning click. Used instead of performing something hard-to-reverse on a single click.
 */
public final class ConfirmGuard {

    private static final Duration WINDOW = Duration.ofSeconds(10);

    private Instant armedUntil;

    public void arm() {
        armedUntil = Instant.now().plus(WINDOW);
    }

    public boolean isArmed() {
        return armedUntil != null && Instant.now().isBefore(armedUntil);
    }

    public void reset() {
        armedUntil = null;
    }
}
