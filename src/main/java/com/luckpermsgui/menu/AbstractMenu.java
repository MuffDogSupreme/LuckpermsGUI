package com.luckpermsgui.menu;

import com.luckpermsgui.LuckpermsGUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Base class for every GUI screen in the plugin.
 *
 * <p>Each instance is single-use and single-viewer: it is constructed for exactly one player,
 * pushed onto that player's {@link MenuSession} navigation stack via {@link #open()}, and never
 * shown to anyone else. Subclasses implement:
 * <ul>
 *   <li>{@link #load()} - asynchronously (re)fetch backing data (LuckPerms calls). No Bukkit API.</li>
 *   <li>{@link #render()} - synchronously paint {@link #getInventory()} from whatever {@link #load()}
 *       last populated. Pure UI work, no I/O.</li>
 *   <li>{@link #onClick(int, ClickType, Player)} - handle a click on any slot other than the
 *       universal back/exit slot, which this class reserves and handles itself.</li>
 * </ul>
 *
 * <p>{@code getInventory().getHolder() == this} is how {@code GUIListener} recognises a click as
 * belonging to this framework at all, which is why the inventory is created with {@code this} as
 * its holder below rather than left to subclasses.
 */
public abstract class AbstractMenu implements InventoryHolder {

    protected final LuckpermsGUI plugin;
    protected final MenuSession session;
    protected final Player viewer;

    private final Inventory inventory;
    private boolean everRendered = false;

    protected AbstractMenu(LuckpermsGUI plugin, MenuSession session, Player viewer, int size, Component title) {
        this.plugin = plugin;
        this.session = session;
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    @Override
    public final Inventory getInventory() {
        return inventory;
    }

    public final Player getViewer() {
        return viewer;
    }

    /** Asynchronously (re)fetches this menu's backing data. Must not touch any Bukkit API. */
    protected abstract CompletableFuture<Void> load();

    /** Synchronously paints {@link #getInventory()} from data already fetched by {@link #load()}. No I/O. */
    protected abstract void render();

    /** Handles a click on any slot other than the universal back/exit slot ({@link #getBackSlot()}). */
    protected abstract void onClick(int slot, ClickType clickType, Player player);

    /** The last slot of this inventory - reserved on every menu for Back (if nested) or Exit (if a root menu). */
    protected final int getBackSlot() {
        return inventory.getSize() - 1;
    }

    /** Paints the universal Back/Exit button into {@link #getBackSlot()}. Call this from {@link #render()}. */
    protected final void renderBackOrExitButton() {
        inventory.setItem(getBackSlot(), session.hasParent() ? MenuItems.backButton() : MenuItems.exitButton());
    }

    /**
     * Entry point called by {@code GUIListener} for every click inside this menu's own inventory.
     * Clicks are ignored outright while a mutation dispatched by a previous click is still saving
     * (see {@link #beginMutation()}), which prevents double-fires and clicks against slots whose
     * backing node is already stale.
     */
    public final void handleClick(int slot, ClickType clickType, Player player) {
        if (session.isMutationInFlight()) {
            return;
        }
        if (slot == getBackSlot()) {
            session.goBack(player);
            return;
        }
        onClick(slot, clickType, player);
    }

    /** Pushes this menu onto the viewer's session stack and performs the initial load + open. */
    public final void open() {
        session.push(this);
        session.setMutationInFlight(false);
        reopen();
    }

    /**
     * Marks that a mutating action has been dispatched to LuckPerms; further clicks are ignored
     * until a subsequent {@link #refresh()} call completes and clears the latch. Call this
     * synchronously, before kicking off the async mutation.
     */
    protected final void beginMutation() {
        session.setMutationInFlight(true);
    }

    /**
     * Prompts the viewer for a line of chat input, correctly bridging it into this menu's
     * lifecycle: marks the session as expecting a chat-driven close (see
     * {@link MenuSession#isAwaitingChatInput()}) so {@code SessionCleanupListener} doesn't tear
     * down the navigation stack while the player is typing, and reopens this menu unchanged if
     * they cancel or time out. On successful input, {@code onInput} is responsible for whatever
     * happens next (reopening this menu, navigating to another one, etc).
     */
    protected final void promptChatInput(Component promptMessage, Predicate<String> validator,
                                          Component invalidMessage, Consumer<String> onInput) {
        session.setAwaitingChatInput(true);
        plugin.getChatInputManager().prompt(viewer, promptMessage, validator, invalidMessage,
                text -> {
                    session.setAwaitingChatInput(false);
                    onInput.accept(text);
                },
                () -> {
                    session.setAwaitingChatInput(false);
                    if (viewer.isOnline() && session.current() == this) {
                        reopen();
                    }
                });
    }

    /**
     * Re-fetches data and re-paints this menu in place - call after a mutation completes. Does
     * not touch navigation or reopen the inventory; the menu must already be on screen.
     */
    protected final CompletableFuture<Void> refresh() {
        CompletableFuture<Void> done = new CompletableFuture<>();
        load().handle((ignored, throwable) -> throwable).thenAccept(throwable ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    session.setMutationInFlight(false);
                    if (session.current() == this) {
                        if (throwable != null) {
                            viewer.sendMessage(MenuItems.error("Failed to refresh menu: <cause>",
                                    Placeholder.unparsed("cause", MenuItems.rootCause(throwable).getMessage())));
                        } else {
                            render();
                        }
                    }
                    done.complete(null);
                }));
        return done;
    }

    /**
     * (Re)loads and (re)paints this menu, then opens it for the viewer. Used both for the very
     * first open (via {@link #open()}) and when {@link MenuSession#goBack(Player)} returns to a
     * menu already on the stack - in both cases the data is re-queried rather than reusing a
     * possibly-stale snapshot from before the player navigated away.
     */
    protected final void reopen() {
        load().handle((ignored, throwable) -> throwable).thenAccept(throwable ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (session.current() != this) {
                        return; // superseded by further navigation while this was loading
                    }
                    if (throwable != null) {
                        viewer.sendMessage(MenuItems.error("Failed to open menu: <cause>",
                                Placeholder.unparsed("cause", MenuItems.rootCause(throwable).getMessage())));
                        if (!everRendered) {
                            session.discardCurrent();
                            if (viewer.isOnline()) {
                                viewer.closeInventory();
                            }
                        }
                        return;
                    }
                    render();
                    everRendered = true;
                    if (viewer.isOnline()) {
                        viewer.openInventory(inventory);
                    }
                }));
    }
}
