package com.luckpermsgui.modules.system;

import com.luckpermsgui.LuckpermsGUI;
import com.luckpermsgui.api.LuckPermsHandler;
import com.luckpermsgui.menu.AbstractMenu;
import com.luckpermsgui.menu.MenuItems;
import com.luckpermsgui.menu.MenuSession;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Administrative operations that don't belong to any one user/group/track: network sync, the
 * LuckPerms web editor bridge, and local cache invalidation.
 *
 * <p>The original spec for this module asked for "storage flushing" - there is no such operation
 * in the LuckPerms API (it persists on every mutation already; there is nothing buffered to
 * flush). Rather than fake a button that does nothing real, that tile is replaced with
 * "Purge Local Cache", which wraps a real, verified API call
 * ({@link net.luckperms.api.cacheddata.CachedDataManager#invalidate()} across every loaded user
 * and group) and does something genuinely useful in the same spirit.
 */
public final class SystemToolsMenu extends AbstractMenu {

    private static final int STATUS_SLOT = 4;
    private static final int SYNC_SLOT = 10;
    private static final int EDITOR_SLOT = 12;
    private static final int PURGE_SLOT = 14;

    public SystemToolsMenu(LuckpermsGUI plugin, MenuSession session, Player viewer) {
        super(plugin, session, viewer, 27, MenuItems.render("<dark_purple><bold>System Tools</bold></dark_purple>"));
    }

    @Override
    protected CompletableFuture<Void> load() {
        // Every tile here reads live, synchronous, in-memory LuckPerms state - no storage I/O
        // until a tile is actually clicked.
        return CompletableFuture.completedFuture(null);
    }

    @Override
    protected void render() {
        for (int i = 0; i < getInventory().getSize(); i++) {
            getInventory().setItem(i, MenuItems.filler());
        }

        LuckPermsHandler handler = plugin.getLuckPermsHandler();
        boolean hasMessaging = handler.hasMessagingService();
        int loadedUsers = handler.getLuckPerms().getUserManager().getLoadedUsers().size();
        int loadedGroups = handler.getLuckPerms().getGroupManager().getLoadedGroups().size();

        getInventory().setItem(STATUS_SLOT, MenuItems.item(Material.BOOK,
                "<gold><bold>Status</bold></gold>",
                List.of(
                        "<gray>Loaded users: <aqua><users>",
                        "<gray>Loaded groups: <aqua><groups>",
                        hasMessaging ? "<green>Messaging service connected</green>" : "<red>No messaging service configured</red>"
                ),
                Placeholder.unparsed("users", String.valueOf(loadedUsers)),
                Placeholder.unparsed("groups", String.valueOf(loadedGroups))
        ));

        getInventory().setItem(SYNC_SLOT, MenuItems.item(Material.BEACON,
                "<gold><bold>Sync Network</bold></gold>",
                List.of(
                        "<gray>Runs a full permission update",
                        "<gray>task and, if configured, pushes",
                        "<gray>it across the network.",
                        "",
                        "<yellow>Click to sync"
                )));

        getInventory().setItem(EDITOR_SLOT, MenuItems.item(Material.WRITABLE_BOOK,
                "<gold><bold>Open Web Editor</bold></gold>",
                List.of(
                        "<gray>Runs LuckPerms' own",
                        "<gray>'/lp editor' command for you.",
                        "<gray>Requires LuckPerms editor access.",
                        "",
                        "<yellow>Click to open"
                )));

        getInventory().setItem(PURGE_SLOT, MenuItems.item(Material.LAVA_BUCKET,
                "<gold><bold>Purge Local Cache</bold></gold>",
                List.of(
                        "<gray>Invalidates cached permission",
                        "<gray>and meta data for every user",
                        "<gray>and group currently loaded",
                        "<gray>in memory on this server.",
                        "",
                        "<yellow>Click to purge"
                )));

        renderBackOrExitButton();
    }

    @Override
    protected void onClick(int slot, ClickType clickType, Player player) {
        LuckPermsHandler handler = plugin.getLuckPermsHandler();

        if (slot == SYNC_SLOT) {
            beginMutation();
            handler.synchronizeNetwork().whenComplete((ignored, throwable) ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (throwable != null) {
                            viewer.sendMessage(MenuItems.error("Sync failed: <cause>",
                                    Placeholder.unparsed("cause", MenuItems.rootCause(throwable).getMessage())));
                        } else if (!handler.hasMessagingService()) {
                            viewer.sendMessage(MenuItems.success("Local update task complete."));
                            viewer.sendMessage(MenuItems.info("No messaging service is configured - other servers on the network were not notified."));
                        } else {
                            viewer.sendMessage(MenuItems.success("Network sync complete."));
                        }
                        refresh();
                    }));
            return;
        }

        if (slot == EDITOR_SLOT) {
            player.performCommand("lp editor");
            return;
        }

        if (slot == PURGE_SLOT) {
            int purged = handler.purgeLocalCaches();
            viewer.sendMessage(MenuItems.success("Invalidated cached data for <count> loaded holders.",
                    Placeholder.unparsed("count", String.valueOf(purged))));
            render();
        }
    }
}
