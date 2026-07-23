package com.luckpermsgui.modules.launcher;

import com.luckpermsgui.LuckpermsGUI;
import com.luckpermsgui.menu.AbstractMenu;
import com.luckpermsgui.menu.MenuItems;
import com.luckpermsgui.menu.MenuSession;
import com.luckpermsgui.modules.auditlog.ActionLogMenu;
import com.luckpermsgui.modules.contexts.ContextsMenu;
import com.luckpermsgui.modules.groups.GroupDirectoryMenu;
import com.luckpermsgui.modules.system.SystemToolsMenu;
import com.luckpermsgui.modules.tracks.TrackDirectoryMenu;
import com.luckpermsgui.modules.users.UserDirectoryMenu;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The root hub of the plugin's GUI: 27 slots, no pagination, tiles linking out to each module.
 * Always the bottom of a fresh {@link MenuSession} navigation stack (opened via {@code /lpgui} or
 * {@code /lpgui menu}), so its back/exit slot always renders as Exit.
 */
public final class LauncherMenu extends AbstractMenu {

    private static final int USERS_SLOT = 10;
    private static final int GROUPS_SLOT = 11;
    private static final int TRACKS_SLOT = 12;
    private static final int CONTEXTS_SLOT = 14;
    private static final int ACTION_LOG_SLOT = 15;
    private static final int SYSTEM_TOOLS_SLOT = 16;

    public LauncherMenu(LuckpermsGUI plugin, MenuSession session, Player viewer) {
        super(plugin, session, viewer, 27, MenuItems.render("<dark_purple><bold>LuckPerms Launcher</bold></dark_purple>"));
    }

    @Override
    protected CompletableFuture<Void> load() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    protected void render() {
        for (int i = 0; i < getInventory().getSize(); i++) {
            getInventory().setItem(i, MenuItems.filler());
        }

        getInventory().setItem(USERS_SLOT, MenuItems.item(Material.PLAYER_HEAD,
                "<gold><bold>Users</bold></gold>",
                List.of(
                        "<gray>Browse or search every",
                        "<gray>player's groups and nodes.",
                        "",
                        "<yellow>Click to open"
                )));

        getInventory().setItem(GROUPS_SLOT, MenuItems.item(Material.NAME_TAG,
                "<gold><bold>Groups</bold></gold>",
                List.of(
                        "<gray>Manage permission groups,",
                        "<gray>inheritance, and metadata.",
                        "",
                        "<yellow>Click to open"
                )));

        getInventory().setItem(TRACKS_SLOT, MenuItems.item(Material.LADDER,
                "<gold><bold>Tracks</bold></gold>",
                List.of(
                        "<gray>Manage promotion ladders",
                        "<gray>and group sequences.",
                        "",
                        "<yellow>Click to open"
                )));

        getInventory().setItem(CONTEXTS_SLOT, MenuItems.item(Material.COMPASS,
                "<gold><bold>Contexts</bold></gold>",
                List.of(
                        "<gray>View active server and",
                        "<gray>per-player context data.",
                        "",
                        "<yellow>Click to open"
                )));

        getInventory().setItem(ACTION_LOG_SLOT, MenuItems.item(Material.WRITABLE_BOOK,
                "<gold><bold>Action Log</bold></gold>",
                List.of(
                        "<gray>Browse the LuckPerms",
                        "<gray>audit trail.",
                        "",
                        "<yellow>Click to open"
                )));

        getInventory().setItem(SYSTEM_TOOLS_SLOT, MenuItems.item(Material.COMMAND_BLOCK,
                "<gold><bold>System Tools</bold></gold>",
                List.of(
                        "<gray>Sync, cache and health",
                        "<gray>operations.",
                        "",
                        "<yellow>Click to open"
                )));

        renderBackOrExitButton();
    }

    @Override
    protected void onClick(int slot, ClickType clickType, Player player) {
        switch (slot) {
            case USERS_SLOT -> new UserDirectoryMenu(plugin, session, viewer).open();
            case GROUPS_SLOT -> new GroupDirectoryMenu(plugin, session, viewer).open();
            case TRACKS_SLOT -> new TrackDirectoryMenu(plugin, session, viewer).open();
            case CONTEXTS_SLOT -> new ContextsMenu(plugin, session, viewer).open();
            case ACTION_LOG_SLOT -> new ActionLogMenu(plugin, session, viewer).open();
            case SYSTEM_TOOLS_SLOT -> new SystemToolsMenu(plugin, session, viewer).open();
            default -> {
            }
        }
    }
}
