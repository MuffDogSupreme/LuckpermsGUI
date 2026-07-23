package com.luckpermsgui.modules.users;

import com.luckpermsgui.LuckpermsGUI;
import com.luckpermsgui.api.LuckPermsHandler;
import com.luckpermsgui.menu.AbstractMenu;
import com.luckpermsgui.menu.InputParsers;
import com.luckpermsgui.menu.MenuItems;
import com.luckpermsgui.menu.MenuSession;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.luckperms.api.model.data.DataMutateResult;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.track.DemotionResult;
import net.luckperms.api.track.PromotionResult;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The identity and settings hub for a single user: primary group, group membership, and links out
 * to {@link UserEditorMenu} (raw permission nodes) and {@link MetaKeysMenu} (meta key/value
 * pairs). Track promotion/demotion is handled directly here since it's a single action rather
 * than something worth its own screen.
 */
public final class UserDashboardMenu extends AbstractMenu {

    private static final int HEAD_SLOT = 4;
    private static final int GROUP_ROW_START = 9;
    private static final int GROUP_ROW_END = 16;
    private static final int ADD_GROUP_SLOT = 17;
    private static final int SET_PRIMARY_SLOT = 18;
    private static final int PERMISSION_NODES_SLOT = 19;
    private static final int META_KEYS_SLOT = 20;
    private static final int PROMOTE_SLOT = 21;
    private static final int DEMOTE_SLOT = 22;

    private final UUID targetUniqueId;
    private final String targetName;
    private final Map<Integer, String> groupSlots = new HashMap<>();

    private User cachedUser;

    public UserDashboardMenu(LuckpermsGUI plugin, MenuSession session, Player viewer, UUID targetUniqueId, String targetName) {
        super(plugin, session, viewer, 27, MenuItems.render("<dark_purple><bold>User • <name></bold></dark_purple>",
                Placeholder.unparsed("name", targetName)));
        this.targetUniqueId = targetUniqueId;
        this.targetName = targetName;
    }

    @Override
    protected CompletableFuture<Void> load() {
        return plugin.getLuckPermsHandler().loadUser(targetUniqueId).thenAccept(user -> this.cachedUser = user);
    }

    @Override
    protected void render() {
        User user = cachedUser;
        if (user == null) {
            return;
        }
        for (int i = 0; i < 27; i++) {
            getInventory().setItem(i, MenuItems.filler());
        }
        groupSlots.clear();

        placeIdentity(user);
        placeGroups(user);
        placeActions();
        renderBackOrExitButton();
    }

    private void placeIdentity(User user) {
        int groupCount = user.getNodes(NodeType.INHERITANCE).size();
        int permissionCount = user.getNodes(NodeType.PERMISSION).size();

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetUniqueId);
        meta.setOwningPlayer(offlinePlayer);
        meta.displayName(MenuItems.render("<gold><bold><name></bold></gold>", Placeholder.unparsed("name", targetName)));
        meta.lore(List.of(
                MenuItems.render("<gray>Primary Group: <aqua><group>", Placeholder.unparsed("group", user.getPrimaryGroup())),
                MenuItems.render("<gray>Groups: <aqua><count>", Placeholder.unparsed("count", String.valueOf(groupCount))),
                MenuItems.render("<gray>Permission Nodes: <aqua><count>", Placeholder.unparsed("count", String.valueOf(permissionCount))),
                MenuItems.render("<dark_gray>UUID: <uuid>", Placeholder.unparsed("uuid", targetUniqueId.toString()))
        ));
        head.setItemMeta(meta);
        getInventory().setItem(HEAD_SLOT, head);
    }

    private void placeGroups(User user) {
        LinkedHashSet<String> distinctGroups = new LinkedHashSet<>();
        for (InheritanceNode node : user.getNodes(NodeType.INHERITANCE)) {
            distinctGroups.add(node.getGroupName());
        }

        int slot = GROUP_ROW_START;
        for (String groupName : distinctGroups) {
            if (slot > GROUP_ROW_END) {
                break;
            }
            boolean isPrimary = groupName.equalsIgnoreCase(user.getPrimaryGroup());
            List<String> lore = isPrimary
                    ? List.of("<green>Primary group", "<dark_gray>Cannot be removed while primary")
                    : List.of("<red>Right-click to remove group");

            getInventory().setItem(slot, MenuItems.item(
                    Material.NAME_TAG,
                    (isPrimary ? "<green>" : "<yellow>") + "<name>",
                    lore,
                    Placeholder.unparsed("name", groupName)
            ));
            groupSlots.put(slot, groupName);
            slot++;
        }

        getInventory().setItem(ADD_GROUP_SLOT, MenuItems.item(Material.LEAD,
                "<green><bold>Add Group</bold></green>",
                List.of("<gray>Type: group [duration]", "", "<yellow>Click to add")));
    }

    private void placeActions() {
        getInventory().setItem(SET_PRIMARY_SLOT, MenuItems.item(Material.BEACON,
                "<gold><bold>Set Primary Group</bold></gold>",
                List.of("<gray>Also grants membership if needed.", "", "<yellow>Click to change")));

        getInventory().setItem(PERMISSION_NODES_SLOT, MenuItems.item(Material.PAPER,
                "<gold><bold>Permission Nodes</bold></gold>",
                List.of("<gray>Browse, toggle and add", "<gray>this player's raw nodes.", "", "<yellow>Click to open")));

        getInventory().setItem(META_KEYS_SLOT, MenuItems.item(Material.WRITABLE_BOOK,
                "<gold><bold>Meta Keys</bold></gold>",
                List.of("<gray>Custom key/value data", "<gray>(placeholders, ranks, etc).", "", "<yellow>Click to open")));

        getInventory().setItem(PROMOTE_SLOT, MenuItems.item(Material.EXPERIENCE_BOTTLE,
                "<green><bold>Promote on Track</bold></green>",
                List.of("<gray>Type a track name in chat.", "", "<yellow>Click to promote")));

        getInventory().setItem(DEMOTE_SLOT, MenuItems.item(Material.GLASS_BOTTLE,
                "<red><bold>Demote on Track</bold></red>",
                List.of("<gray>Type a track name in chat.", "", "<yellow>Click to demote")));
    }

    @Override
    protected void onClick(int slot, ClickType clickType, Player player) {
        String groupName = groupSlots.get(slot);
        if (groupName != null && clickType.isRightClick()) {
            removeGroup(groupName);
            return;
        }

        if (slot == ADD_GROUP_SLOT) {
            addGroup();
            return;
        }
        if (slot == SET_PRIMARY_SLOT) {
            setPrimaryGroup();
            return;
        }
        if (slot == PERMISSION_NODES_SLOT) {
            new UserEditorMenu(plugin, session, viewer, targetUniqueId, targetName).open();
            return;
        }
        if (slot == META_KEYS_SLOT) {
            new MetaKeysMenu(plugin, session, viewer, targetUniqueId, targetName).open();
            return;
        }
        if (slot == PROMOTE_SLOT) {
            promote();
            return;
        }
        if (slot == DEMOTE_SLOT) {
            demote();
        }
    }

    private void removeGroup(String groupName) {
        if (cachedUser != null && groupName.equalsIgnoreCase(cachedUser.getPrimaryGroup())) {
            viewer.sendMessage(MenuItems.error("You cannot remove a player's primary group. Set a different primary group first."));
            return;
        }
        beginMutation();
        LuckPermsHandler handler = plugin.getLuckPermsHandler();
        handler.removeGroup(viewer, targetUniqueId, targetName, groupName).whenComplete((result, throwable) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (throwable != null) {
                        viewer.sendMessage(MenuItems.error("Failed to remove group: <cause>",
                                Placeholder.unparsed("cause", MenuItems.rootCause(throwable).getMessage())));
                    } else if (result == DataMutateResult.SUCCESS) {
                        viewer.sendMessage(MenuItems.success("Removed group <group> from <name>.",
                                Placeholder.unparsed("group", groupName), Placeholder.unparsed("name", targetName)));
                    } else {
                        viewer.sendMessage(MenuItems.info("<name> does not have group <group>.",
                                Placeholder.unparsed("name", targetName), Placeholder.unparsed("group", groupName)));
                    }
                    refresh();
                }));
    }

    private void addGroup() {
        promptChatInput(
                MenuItems.info("Type: group-name [duration] (e.g. 'vip' or 'vip 30d')."),
                text -> !text.isBlank(),
                MenuItems.error("Please enter a group name, or type 'cancel'."),
                text -> {
                    String[] parts = text.trim().split("\\s+", 2);
                    String groupName = parts[0];
                    Duration expiry = parts.length > 1 ? InputParsers.parseDuration(parts[1]).orElse(null) : null;
                    beginMutation();
                    plugin.getLuckPermsHandler().addGroupMembership(viewer, targetUniqueId, targetName, groupName, expiry)
                            .whenComplete((result, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                                if (throwable != null) {
                                    viewer.sendMessage(MenuItems.error("Failed to add group: <cause>",
                                            Placeholder.unparsed("cause", MenuItems.rootCause(throwable).getMessage())));
                                } else if (result == DataMutateResult.SUCCESS) {
                                    viewer.sendMessage(MenuItems.success("Added group <group> to <name>.",
                                            Placeholder.unparsed("group", groupName), Placeholder.unparsed("name", targetName)));
                                } else {
                                    viewer.sendMessage(MenuItems.info("<name> already has that group.",
                                            Placeholder.unparsed("name", targetName)));
                                }
                                refresh();
                            }));
                }
        );
    }

    private void setPrimaryGroup() {
        promptChatInput(
                MenuItems.info("Type the group name to make primary."),
                text -> !text.isBlank(),
                MenuItems.error("Please enter a group name, or type 'cancel'."),
                groupName -> {
                    beginMutation();
                    plugin.getLuckPermsHandler().setPrimaryGroup(viewer, targetUniqueId, targetName, groupName).whenComplete((ignored, throwable) ->
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (throwable != null) {
                                    viewer.sendMessage(MenuItems.error("Failed to set primary group: <cause>",
                                            Placeholder.unparsed("cause", MenuItems.rootCause(throwable).getMessage())));
                                } else {
                                    viewer.sendMessage(MenuItems.success("Set <name>'s primary group to <group>.",
                                            Placeholder.unparsed("name", targetName), Placeholder.unparsed("group", groupName)));
                                }
                                refresh();
                            }));
                }
        );
    }

    private void promote() {
        promptChatInput(
                MenuItems.info("Type the track name to promote along."),
                text -> !text.isBlank(),
                MenuItems.error("Please enter a track name, or type 'cancel'."),
                trackName -> {
                    beginMutation();
                    plugin.getLuckPermsHandler().promoteOnTrack(viewer, targetUniqueId, targetName, trackName).whenComplete((result, throwable) ->
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (throwable != null) {
                                    viewer.sendMessage(MenuItems.error("Failed to promote: <cause>",
                                            Placeholder.unparsed("cause", MenuItems.rootCause(throwable).getMessage())));
                                } else {
                                    viewer.sendMessage(describePromotion(result));
                                }
                                refresh();
                            }));
                }
        );
    }

    private void demote() {
        promptChatInput(
                MenuItems.info("Type the track name to demote along."),
                text -> !text.isBlank(),
                MenuItems.error("Please enter a track name, or type 'cancel'."),
                trackName -> {
                    beginMutation();
                    plugin.getLuckPermsHandler().demoteOnTrack(viewer, targetUniqueId, targetName, trackName).whenComplete((result, throwable) ->
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (throwable != null) {
                                    viewer.sendMessage(MenuItems.error("Failed to demote: <cause>",
                                            Placeholder.unparsed("cause", MenuItems.rootCause(throwable).getMessage())));
                                } else {
                                    viewer.sendMessage(describeDemotion(result));
                                }
                                refresh();
                            }));
                }
        );
    }

    private net.kyori.adventure.text.Component describePromotion(PromotionResult result) {
        String from = result.getGroupFrom().orElse(null);
        String to = result.getGroupTo().orElse(null);
        return switch (result.getStatus()) {
            case SUCCESS -> MenuItems.success("Promoted <name> from <from> to <to>.",
                    Placeholder.unparsed("name", targetName), Placeholder.unparsed("from", String.valueOf(from)), Placeholder.unparsed("to", String.valueOf(to)));
            case ADDED_TO_FIRST_GROUP -> MenuItems.success("<name> was not on the track - added to the first group, <to>.",
                    Placeholder.unparsed("name", targetName), Placeholder.unparsed("to", String.valueOf(to)));
            case END_OF_TRACK -> MenuItems.info("<name> is already at the end of that track.", Placeholder.unparsed("name", targetName));
            case MALFORMED_TRACK -> MenuItems.error("That track has fewer than two groups configured.");
            case AMBIGUOUS_CALL -> MenuItems.error("<name> is in multiple groups from that track - unclear how to promote.", Placeholder.unparsed("name", targetName));
            default -> MenuItems.error("Promotion failed for an unknown reason.");
        };
    }

    private net.kyori.adventure.text.Component describeDemotion(DemotionResult result) {
        String from = result.getGroupFrom().orElse(null);
        String to = result.getGroupTo().orElse(null);
        return switch (result.getStatus()) {
            case SUCCESS -> MenuItems.success("Demoted <name> from <from> to <to>.",
                    Placeholder.unparsed("name", targetName), Placeholder.unparsed("from", String.valueOf(from)), Placeholder.unparsed("to", String.valueOf(to)));
            case REMOVED_FROM_FIRST_GROUP -> MenuItems.success("<name> was demoted out of the track entirely (removed from <from>).",
                    Placeholder.unparsed("name", targetName), Placeholder.unparsed("from", String.valueOf(from)));
            case NOT_ON_TRACK -> MenuItems.info("<name> is not on that track.", Placeholder.unparsed("name", targetName));
            case MALFORMED_TRACK -> MenuItems.error("That track has fewer than two groups configured.");
            case AMBIGUOUS_CALL -> MenuItems.error("<name> is in multiple groups from that track - unclear how to demote.", Placeholder.unparsed("name", targetName));
            default -> MenuItems.error("Demotion failed for an unknown reason.");
        };
    }
}
