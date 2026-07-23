package com.luckpermsgui.menu;

import com.luckpermsgui.LuckpermsGUI;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Shared player-name-to-UUID resolution used by both commands and menus: online players resolve
 * instantly, offline players go through LuckPerms' async lookup service. Factored out here once a
 * second caller (the Launcher's Users tile) needed the exact same logic {@code LPGuiCommand}
 * already had.
 */
public final class PlayerResolver {

    private PlayerResolver() {
    }

    /** Resolves {@code name} to a UUID, invoking {@code onResolved} on the main thread. Sends an error to {@code requester} if not found. */
    public static void resolve(LuckpermsGUI plugin, CommandSender requester, String name, Consumer<UUID> onResolved) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            onResolved.accept(online.getUniqueId());
            return;
        }

        plugin.getLuckPermsHandler().lookupUniqueId(name).whenComplete((uuid, throwable) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (throwable != null || uuid == null) {
                        requester.sendMessage(MenuItems.error("Could not find a player named <name>.",
                                Placeholder.unparsed("name", name)));
                        return;
                    }
                    onResolved.accept(uuid);
                }));
    }

    /** The best display name available for a resolved UUID: their current online name if connected, otherwise whatever was looked up. */
    public static String displayName(String requestedName, UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        return online != null ? online.getName() : requestedName;
    }
}
