package com.luckpermsgui;

import com.luckpermsgui.api.LuckPermsHandler;
import com.luckpermsgui.commands.LPGuiCommand;
import com.luckpermsgui.listeners.GUIListener;
import com.luckpermsgui.listeners.SessionCleanupListener;
import com.luckpermsgui.menu.AbstractMenu;
import com.luckpermsgui.menu.ChatInputManager;
import com.luckpermsgui.menu.MenuSessionManager;
import com.luckpermsgui.modules.auditlog.ActionLogCache;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.List;

/**
 * Main plugin class for LuckpermsGUI.
 *
 * <p>This plugin is a standalone administrative front-end for the LuckPerms API: it has no
 * dependency on, or shared logic with, any other custom plugin. It hard-depends on LuckPerms
 * itself (declared in {@code paper-plugin.yml}) and refuses to start without it.
 */
public final class LuckpermsGUI extends JavaPlugin {

    private LuckPermsHandler luckPermsHandler;
    private MenuSessionManager menuSessionManager;
    private ChatInputManager chatInputManager;
    private ActionLogCache actionLogCache;
    private LPGuiCommand lpGuiCommand;

    @Override
    public void onEnable() {
        LuckPerms luckPerms;
        try {
            luckPerms = LuckPermsProvider.get();
        } catch (IllegalStateException exception) {
            getLogger().severe("Could not hook into LuckPerms - the API is not available.");
            getLogger().severe("Make sure LuckPerms is installed and has started successfully, then restart the server.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info("Hooked into LuckPerms API v" + luckPerms.getPluginMetadata().getVersion() + ".");

        this.luckPermsHandler = new LuckPermsHandler(luckPerms);
        this.menuSessionManager = new MenuSessionManager();
        this.chatInputManager = new ChatInputManager(this);
        this.actionLogCache = new ActionLogCache(luckPermsHandler);
        this.lpGuiCommand = new LPGuiCommand(this, menuSessionManager);

        getServer().getPluginManager().registerEvents(new GUIListener(), this);
        getServer().getPluginManager().registerEvents(new SessionCleanupListener(this, menuSessionManager), this);
        getServer().getPluginManager().registerEvents(chatInputManager, this);
        registerCommand();

        getLogger().info("LuckpermsGUI has been enabled.");
    }

    @Override
    public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof AbstractMenu) {
                player.closeInventory();
            }
        }
        getLogger().info("LuckpermsGUI has been disabled.");
    }

    /**
     * Registers {@code /lpgui} (and its aliases) using Paper's Brigadier-backed lifecycle
     * command API, rather than injecting into the legacy {@code CommandMap} or declaring the
     * command in the plugin manifest. This is the modern, recommended registration path on
     * Paper 1.20.6+ and avoids client-side tab-completion mismatches that can occur when a
     * plain Bukkit command is registered after the server has already built its Brigadier
     * command tree.
     */
    private void registerCommand() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();
            registrar.register(
                    "lpgui",
                    "Open the LuckPerms administrative GUI and manage permissions.",
                    List.of("lpg", "luckpermsgui"),
                    new BasicCommand() {
                        // Note: Paper 1.20.6's BasicCommand has no permission()/canUse() hook
                        // yet (added in later Paper versions) - LPGuiCommand itself already
                        // gates every path on luckpermsgui.admin, so that's where enforcement
                        // and the empty-completions-for-unauthorized-users behavior live.
                        @Override
                        public void execute(CommandSourceStack source, String[] args) {
                            lpGuiCommand.onCommand(source.getSender(), null, "lpgui", args);
                        }

                        @Override
                        public Collection<String> suggest(CommandSourceStack source, String[] args) {
                            List<String> completions = lpGuiCommand.onTabComplete(source.getSender(), null, "lpgui", args);
                            return completions == null ? List.of() : completions;
                        }
                    }
            );
        });
    }

    public LuckPermsHandler getLuckPermsHandler() {
        return luckPermsHandler;
    }

    public MenuSessionManager getMenuSessionManager() {
        return menuSessionManager;
    }

    public ChatInputManager getChatInputManager() {
        return chatInputManager;
    }

    public ActionLogCache getActionLogCache() {
        return actionLogCache;
    }
}
