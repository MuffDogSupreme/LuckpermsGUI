package com.luckpermsgui.commands;

import com.luckpermsgui.LuckpermsGUI;
import com.luckpermsgui.api.LuckPermsHandler;
import com.luckpermsgui.menu.MenuItems;
import com.luckpermsgui.menu.MenuSession;
import com.luckpermsgui.menu.MenuSessionManager;
import com.luckpermsgui.menu.PlayerResolver;
import com.luckpermsgui.modules.launcher.LauncherMenu;
import com.luckpermsgui.modules.users.UserDashboardMenu;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.luckperms.api.model.data.DataMutateResult;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Implements every {@code /lpgui} subcommand. This class is a plain
 * {@link CommandExecutor}/{@link TabCompleter} - it is registered with the server through a thin
 * {@code BasicCommand} adapter (see {@link LuckpermsGUI}) that bridges Paper's Brigadier-based
 * command registration to this class's {@code onCommand}/{@code onTabComplete} methods, so the
 * logic here stays framework-agnostic.
 */
public final class LPGuiCommand implements CommandExecutor, TabCompleter {

    public static final String PERMISSION = "luckpermsgui.admin";
    private static final List<String> SUBCOMMANDS = List.of("menu", "open", "setgroup", "addperm", "removeperm", "help");

    private final LuckpermsGUI plugin;
    private final MenuSessionManager sessionManager;

    public LPGuiCommand(LuckpermsGUI plugin, MenuSessionManager sessionManager) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(MenuItems.error("You do not have permission to use this command."));
            return true;
        }

        if (args.length == 0) {
            handleMenu(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "menu" -> handleMenu(sender);
            case "help" -> sendUsage(sender, label);
            case "open" -> handleOpen(sender, args);
            case "setgroup" -> handleSetGroup(sender, args);
            case "addperm" -> handleAddPerm(sender, args);
            case "removeperm" -> handleRemovePerm(sender, args);
            default -> sendUsage(sender, label);
        }
        return true;
    }

    private void handleMenu(CommandSender sender) {
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(MenuItems.error("Only players can open the GUI. Use /lpgui help for command usage."));
            return;
        }
        MenuSession session = sessionManager.startSession(viewer);
        new LauncherMenu(plugin, session, viewer).open();
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(MenuItems.render("<gold>--- LuckpermsGUI ---"));
        sender.sendMessage(MenuItems.render("<yellow>/<label></yellow> <gray>- Open the Launcher GUI (players only).",
                Placeholder.unparsed("label", label)));
        sender.sendMessage(MenuItems.render("<yellow>/<label> open [player]</yellow> <gray>- Open a player's dashboard directly.",
                Placeholder.unparsed("label", label)));
        sender.sendMessage(MenuItems.render("<yellow>/<label> setgroup <player> <group></yellow> <gray>- Set a player's primary group.",
                Placeholder.unparsed("label", label)));
        sender.sendMessage(MenuItems.render("<yellow>/<label> addperm <player> <permission></yellow> <gray>- Add a permission node.",
                Placeholder.unparsed("label", label)));
        sender.sendMessage(MenuItems.render("<yellow>/<label> removeperm <player> <permission></yellow> <gray>- Remove a permission node.",
                Placeholder.unparsed("label", label)));
    }

    private void handleOpen(CommandSender sender, String[] args) {
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(MenuItems.error("Only players can open the GUI."));
            return;
        }

        String targetName = args.length >= 2 ? args[1] : viewer.getName();

        PlayerResolver.resolve(plugin, sender, targetName, uuid -> {
            String displayName = PlayerResolver.displayName(targetName, uuid);
            MenuSession session = sessionManager.startSession(viewer);
            new UserDashboardMenu(plugin, session, viewer, uuid, displayName).open();
        });
    }

    private void handleSetGroup(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MenuItems.error("Usage: /lpgui setgroup <player> <group>"));
            return;
        }
        String targetName = args[1];
        String groupName = args[2];
        LuckPermsHandler handler = plugin.getLuckPermsHandler();

        handler.groupExists(groupName).whenComplete((exists, groupThrowable) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (groupThrowable != null) {
                        sender.sendMessage(MenuItems.error("Failed to look up group: <cause>",
                                Placeholder.unparsed("cause", MenuItems.rootCause(groupThrowable).getMessage())));
                        return;
                    }
                    if (!Boolean.TRUE.equals(exists)) {
                        sender.sendMessage(MenuItems.error("Group <group> does not exist.",
                                Placeholder.unparsed("group", groupName)));
                        return;
                    }
                    PlayerResolver.resolve(plugin, sender, targetName, uuid -> handler.setPrimaryGroup(sender, uuid, targetName, groupName)
                            .whenComplete((ignored, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                                if (throwable != null) {
                                    sender.sendMessage(MenuItems.error("Failed to set primary group: <cause>",
                                            Placeholder.unparsed("cause", MenuItems.rootCause(throwable).getMessage())));
                                    return;
                                }
                                sender.sendMessage(MenuItems.success("Set <name>'s primary group to <group>.",
                                        Placeholder.unparsed("name", targetName), Placeholder.unparsed("group", groupName)));
                            })));
                }));
    }

    private void handleAddPerm(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MenuItems.error("Usage: /lpgui addperm <player> <permission>"));
            return;
        }
        String targetName = args[1];
        String permission = args[2];
        LuckPermsHandler handler = plugin.getLuckPermsHandler();

        PlayerResolver.resolve(plugin, sender, targetName, uuid -> handler.addPermission(sender, uuid, targetName, permission, true)
                .whenComplete((result, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (throwable != null) {
                        sender.sendMessage(MenuItems.error("Failed to add permission: <cause>",
                                Placeholder.unparsed("cause", MenuItems.rootCause(throwable).getMessage())));
                        return;
                    }
                    if (result == DataMutateResult.SUCCESS) {
                        sender.sendMessage(MenuItems.success("Added permission <perm> to <name>.",
                                Placeholder.unparsed("perm", permission), Placeholder.unparsed("name", targetName)));
                    } else {
                        sender.sendMessage(MenuItems.info("<name> already has permission <perm>.",
                                Placeholder.unparsed("name", targetName), Placeholder.unparsed("perm", permission)));
                    }
                })));
    }

    private void handleRemovePerm(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MenuItems.error("Usage: /lpgui removeperm <player> <permission>"));
            return;
        }
        String targetName = args[1];
        String permission = args[2];
        LuckPermsHandler handler = plugin.getLuckPermsHandler();

        PlayerResolver.resolve(plugin, sender, targetName, uuid -> handler.removePermission(sender, uuid, targetName, permission)
                .whenComplete((result, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (throwable != null) {
                        sender.sendMessage(MenuItems.error("Failed to remove permission: <cause>",
                                Placeholder.unparsed("cause", MenuItems.rootCause(throwable).getMessage())));
                        return;
                    }
                    if (result == DataMutateResult.SUCCESS) {
                        sender.sendMessage(MenuItems.success("Removed permission <perm> from <name>.",
                                Placeholder.unparsed("perm", permission), Placeholder.unparsed("name", targetName)));
                    } else {
                        sender.sendMessage(MenuItems.info("<name> does not have permission <perm>.",
                                Placeholder.unparsed("name", targetName), Placeholder.unparsed("perm", permission)));
                    }
                })));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }

        if (args.length == 1) {
            return filterPrefix(SUBCOMMANDS, args[0]);
        }

        if (args.length == 2) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return filterPrefix(names, args[1]);
        }

        if (args.length == 3) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("setgroup")) {
                return filterPrefix(new ArrayList<>(plugin.getLuckPermsHandler().getLoadedGroupNames()), args[2]);
            }
            if (sub.equals("removeperm")) {
                return filterPrefix(currentlyLoadedPermissionKeys(args[1]), args[2]);
            }
        }

        return List.of();
    }

    /**
     * Best-effort, non-blocking permission suggestions for tab completion: only inspects a user
     * that LuckPerms already has resident in memory (online, or recently touched). Never triggers
     * a storage lookup, since tab completion must not block or run off-thread.
     */
    private List<String> currentlyLoadedPermissionKeys(String targetName) {
        Player online = Bukkit.getPlayerExact(targetName);
        if (online == null) {
            return List.of();
        }
        User user = plugin.getLuckPermsHandler().getLuckPerms().getUserManager().getUser(online.getUniqueId());
        if (user == null) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        user.getNodes(NodeType.PERMISSION).forEach(node -> keys.add(node.getKey()));
        return keys;
    }

    private List<String> filterPrefix(List<String> options, String prefix) {
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) {
                result.add(option);
            }
        }
        return result;
    }
}
