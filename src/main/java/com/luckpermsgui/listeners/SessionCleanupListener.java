package com.luckpermsgui.listeners;

import com.luckpermsgui.LuckpermsGUI;
import com.luckpermsgui.menu.AbstractMenu;
import com.luckpermsgui.menu.MenuSession;
import com.luckpermsgui.menu.MenuSessionManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Optional;

/**
 * Ends a player's {@link com.luckpermsgui.menu.MenuSession} once they are truly done with our
 * menus - not merely navigating between two of them, and not merely answering a chat prompt.
 *
 * <p>Bukkit fires {@link InventoryCloseEvent} for the old view every time a new inventory is
 * opened over it, including our own Back/forward navigation (which closes the child menu and
 * opens the parent in the same tick) and {@link com.luckpermsgui.menu.ChatInputManager} prompts
 * (which close the inventory entirely while the player types in chat). A close event alone can't
 * tell any of these apart from "left our menus for good", so this listener defers the check by
 * one tick - by which point navigation has already reopened the next menu - and additionally
 * skips ending the session outright while {@link MenuSession#isAwaitingChatInput()} is set.
 */
public final class SessionCleanupListener implements Listener {

    private final LuckpermsGUI plugin;
    private final MenuSessionManager sessionManager;

    public SessionCleanupListener(LuckpermsGUI plugin, MenuSessionManager sessionManager) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof AbstractMenu)) {
            return;
        }
        HumanEntity closer = event.getPlayer();
        if (!(closer instanceof Player player)) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return; // PlayerQuitEvent below already handled cleanup
            }
            Optional<MenuSession> session = sessionManager.getSession(player.getUniqueId());
            if (session.isPresent() && session.get().isAwaitingChatInput()) {
                return; // expected close - the player is being prompted in chat, not leaving
            }
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof AbstractMenu)) {
                sessionManager.endSession(player.getUniqueId());
            }
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sessionManager.endSession(event.getPlayer().getUniqueId());
    }
}
