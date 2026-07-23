package com.luckpermsgui.modules.contexts;

import com.luckpermsgui.LuckpermsGUI;
import com.luckpermsgui.menu.AbstractMenu;
import com.luckpermsgui.menu.MenuItems;
import com.luckpermsgui.menu.MenuSession;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.luckperms.api.context.ImmutableContextSet;
import net.luckperms.api.platform.Platform;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Read-only visualization of LuckPerms' context state: platform info, the server-wide static
 * context, and (on request) an online player's currently active context set.
 *
 * <p>The original spec for this module asked for {@code getPotentialContexts()} - that method
 * does not exist anywhere in the LuckPerms API (confirmed against the real jar; there is no
 * registry of "every context value that could ever apply" since context calculators are dynamic
 * and open-ended). What the API actually exposes is what's built here: a subject's *currently
 * active* contexts via {@link net.luckperms.api.context.ContextManager#getContext(Object)}, and
 * the platform-wide static context via {@code getStaticContext()}.
 *
 * <p>Active contexts require a live subject to evaluate against (world, gamemode, etc.), so
 * player-context inspection only works for currently online players - there is no meaningful
 * "active context" for someone who isn't connected.
 */
public final class ContextsMenu extends AbstractMenu {

    private static final int PLATFORM_SLOT = 4;
    private static final int STATIC_CONTEXT_SLOT = 11;
    private static final int PLAYER_CONTEXT_SLOT = 13;
    private static final int INSPECT_SLOT = 15;

    private UUID focusPlayerId;
    private String focusPlayerName;

    public ContextsMenu(LuckpermsGUI plugin, MenuSession session, Player viewer) {
        super(plugin, session, viewer, 27, MenuItems.render("<dark_purple><bold>Contexts & Scope</bold></dark_purple>"));
    }

    @Override
    protected CompletableFuture<Void> load() {
        // Everything shown here is live, in-memory LuckPerms/Bukkit state - no storage I/O.
        return CompletableFuture.completedFuture(null);
    }

    @Override
    protected void render() {
        for (int i = 0; i < getInventory().getSize(); i++) {
            getInventory().setItem(i, MenuItems.filler());
        }
        renderPlatformInfo();
        renderStaticContext();
        renderPlayerContext();
        renderInspectButton();
        renderBackOrExitButton();
    }

    private void renderPlatformInfo() {
        Platform platform = plugin.getLuckPermsHandler().getLuckPerms().getPlatform();
        getInventory().setItem(PLATFORM_SLOT, MenuItems.item(Material.BEACON,
                "<gold><bold>Platform</bold></gold>",
                List.of(
                        "<gray>Type: <aqua><type>",
                        "<gray>Known connections: <aqua><connections>",
                        "<gray>Started: <aqua><started>"
                ),
                Placeholder.unparsed("type", platform.getType().getFriendlyName()),
                Placeholder.unparsed("connections", String.valueOf(platform.getUniqueConnections().size())),
                Placeholder.unparsed("started", platform.getStartTime().toString())
        ));
    }

    private void renderStaticContext() {
        ImmutableContextSet staticContext = plugin.getLuckPermsHandler().getLuckPerms().getContextManager().getStaticContext();
        getInventory().setItem(STATIC_CONTEXT_SLOT,
                contextItem(Material.GRASS_BLOCK, "<green><bold>Server (Static) Context</bold></green>", staticContext));
    }

    private void renderPlayerContext() {
        if (focusPlayerId == null) {
            getInventory().setItem(PLAYER_CONTEXT_SLOT, MenuItems.item(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                    "<gray>No player focused</gray>",
                    List.of("<gray>Click 'Inspect a Player' to view", "<gray>someone's active contexts.")));
            return;
        }
        Player online = Bukkit.getPlayer(focusPlayerId);
        if (online == null) {
            getInventory().setItem(PLAYER_CONTEXT_SLOT, MenuItems.item(Material.RED_STAINED_GLASS_PANE,
                    "<red><name> is now offline</red>",
                    List.of("<gray>Active contexts require the", "<gray>player to be online."),
                    Placeholder.unparsed("name", focusPlayerName)));
            return;
        }
        ImmutableContextSet playerContext = plugin.getLuckPermsHandler().getLuckPerms().getContextManager().getContext(online);
        getInventory().setItem(PLAYER_CONTEXT_SLOT, contextItem(Material.PLAYER_HEAD,
                "<aqua><bold><name>'s Context</bold></aqua>", playerContext,
                Placeholder.unparsed("name", focusPlayerName)));
    }

    private ItemStack contextItem(Material material, String nameTemplate, ImmutableContextSet contextSet, TagResolver... nameResolvers) {
        Component name = MenuItems.render(nameTemplate, nameResolvers);
        Map<String, Set<String>> map = contextSet.toMap();
        List<Component> lore = new ArrayList<>();
        if (map.isEmpty()) {
            lore.add(MenuItems.render("<gray>(no context keys)"));
        } else {
            for (Map.Entry<String, Set<String>> entry : map.entrySet()) {
                lore.add(MenuItems.render("<gray><key>: <aqua><value>",
                        Placeholder.unparsed("key", entry.getKey()),
                        Placeholder.unparsed("value", String.join(", ", entry.getValue()))));
            }
        }
        return MenuItems.itemWithLore(material, name, lore);
    }

    private void renderInspectButton() {
        getInventory().setItem(INSPECT_SLOT, MenuItems.item(Material.SPYGLASS,
                "<gold><bold>Inspect a Player</bold></gold>",
                List.of(
                        "<gray>Type an online player's name",
                        "<gray>to view their active contexts.",
                        "",
                        "<yellow>Click to search"
                )));
    }

    @Override
    protected void onClick(int slot, ClickType clickType, Player player) {
        if (slot != INSPECT_SLOT) {
            return;
        }
        promptChatInput(
                MenuItems.info("Type an ONLINE player's name in chat to inspect their active contexts."),
                text -> !text.isBlank(),
                MenuItems.error("Please enter a non-empty player name, or type 'cancel'."),
                name -> {
                    Player online = Bukkit.getPlayerExact(name);
                    if (online == null) {
                        viewer.sendMessage(MenuItems.error("<name> must be online to inspect their active contexts.",
                                Placeholder.unparsed("name", name)));
                        reopen();
                        return;
                    }
                    focusPlayerId = online.getUniqueId();
                    focusPlayerName = online.getName();
                    reopen();
                }
        );
    }
}
