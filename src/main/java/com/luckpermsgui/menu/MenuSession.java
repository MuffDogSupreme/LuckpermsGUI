package com.luckpermsgui.menu;

import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/**
 * Tracks one player's navigation stack through the plugin's menus, plus a single in-flight
 * mutation latch shared by whichever menu is currently on top of that stack.
 *
 * <p>Every field here is read and written exclusively on the main server thread: clicks are
 * handled on the main thread, and every async LuckPerms callback that touches this class is
 * hopped back onto the main thread (via the Bukkit scheduler) before it does so. That invariant
 * is what makes plain, non-atomic fields safe here - there is never a concurrent reader.
 */
public final class MenuSession {

    private final UUID playerId;
    private final Deque<AbstractMenu> stack = new ArrayDeque<>();
    private boolean mutationInFlight = false;
    private boolean awaitingChatInput = false;
    private boolean active = true;

    public MenuSession(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public AbstractMenu current() {
        return stack.peek();
    }

    public boolean hasParent() {
        return stack.size() > 1;
    }

    public boolean isMutationInFlight() {
        return mutationInFlight;
    }

    public void setMutationInFlight(boolean mutationInFlight) {
        this.mutationInFlight = mutationInFlight;
    }

    /**
     * True while a menu in this session has closed the player's inventory to prompt them for
     * chat input (see {@link AbstractMenu#promptChatInput}). {@link
     * com.luckpermsgui.listeners.SessionCleanupListener} checks this before treating the
     * resulting {@code InventoryCloseEvent} as "the player left our menus" - without it, every
     * chat prompt would look identical to the player quitting the menu chain entirely, and the
     * navigation stack would be wiped out from under them while they're still mid-conversation.
     */
    public boolean isAwaitingChatInput() {
        return awaitingChatInput;
    }

    public void setAwaitingChatInput(boolean awaitingChatInput) {
        this.awaitingChatInput = awaitingChatInput;
    }

    /**
     * Pushes a menu onto this session, unless the session has already been ended (see
     * {@link #clear()}). That guard matters for exactly one scenario: a menu closes the player's
     * inventory to prompt them in chat (see {@link AbstractMenu#promptChatInput}), and while that
     * prompt is still pending, something else replaces this player's session outright (they run
     * {@code /lpgui menu} again, starting a brand-new stack). When the original prompt is finally
     * answered, its callback still holds a direct reference to *this* now-superseded session and
     * would otherwise happily push a freshly-constructed menu onto it and open it for the player -
     * yanking them out of whatever they're actually looking at now. Refusing to push once
     * {@code clear()} has run makes every such stale callback a safe no-op instead.
     */
    void push(AbstractMenu menu) {
        if (!active) {
            return;
        }
        stack.push(menu);
    }

    /** Removes the current menu without opening anything - used when a freshly-opened menu's first load fails. */
    void discardCurrent() {
        if (!stack.isEmpty()) {
            stack.pop();
        }
    }

    /**
     * Pops the current menu and reopens whatever is now on top, or closes the player's inventory
     * and clears the session if the stack has nothing left beneath it.
     */
    public void goBack(Player player) {
        if (stack.size() <= 1) {
            clear();
            player.closeInventory();
            return;
        }
        stack.pop();
        mutationInFlight = false;
        AbstractMenu parent = stack.peek();
        parent.reopen();
    }

    /**
     * Fully and permanently ends this session - called when the player leaves the menu chain
     * entirely, disconnects, or has a fresh session started in its place. Once called, this
     * session object is inert forever (see {@link #push}); a new {@link MenuSession} is created
     * for whatever comes next.
     */
    public void clear() {
        stack.clear();
        mutationInFlight = false;
        awaitingChatInput = false;
        active = false;
    }
}
