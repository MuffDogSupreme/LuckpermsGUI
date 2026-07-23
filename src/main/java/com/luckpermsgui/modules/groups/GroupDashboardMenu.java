package com.luckpermsgui.modules.groups;

import com.luckpermsgui.LuckpermsGUI;
import com.luckpermsgui.api.LuckPermsHandler;
import com.luckpermsgui.menu.AbstractMenu;
import com.luckpermsgui.menu.ConfirmGuard;
import com.luckpermsgui.menu.InputParsers;
import com.luckpermsgui.menu.MenuItems;
import com.luckpermsgui.menu.MenuSession;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.luckperms.api.model.data.DataMutateResult;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.ChatMetaNode;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The identity and settings screen for a single group: weight, display name, prefix, suffix,
 * parent groups, and a link into {@link GroupNodeEditorMenu} for its raw permission nodes.
 */
public final class GroupDashboardMenu extends AbstractMenu {

    private static final int IDENTITY_SLOT = 4;
    private static final int PARENT_ROW_START = 9;
    private static final int PARENT_ROW_END = 16;
    private static final int ADD_PARENT_SLOT = 17;
    private static final int WEIGHT_SLOT = 18;
    private static final int PREFIX_SLOT = 19;
    private static final int SUFFIX_SLOT = 20;
    private static final int DISPLAY_NAME_SLOT = 21;
    private static final int NODE_EDITOR_SLOT = 22;
    private static final int DELETE_SLOT = 24;

    private final String groupName;
    private final Map<Integer, String> parentSlots = new HashMap<>();
    private final ConfirmGuard deleteGuard = new ConfirmGuard();

    private Group cachedGroup;

    public GroupDashboardMenu(LuckpermsGUI plugin, MenuSession session, Player viewer, String groupName) {
        super(plugin, session, viewer, 27, MenuItems.render("<dark_purple><bold>Group • <name></bold></dark_purple>",
                Placeholder.unparsed("name", groupName)));
        this.groupName = groupName;
    }

    @Override
    protected CompletableFuture<Void> load() {
        return plugin.getLuckPermsHandler().groupExists(groupName).thenCompose(exists -> {
            if (!Boolean.TRUE.equals(exists)) {
                return CompletableFuture.failedFuture(new IllegalStateException("Group '" + groupName + "' no longer exists."));
            }
            this.cachedGroup = plugin.getLuckPermsHandler().getLuckPerms().getGroupManager().getGroup(groupName);
            return CompletableFuture.completedFuture(null);
        });
    }

    @Override
    protected void render() {
        Group group = cachedGroup;
        if (group == null) {
            return;
        }
        for (int i = 0; i < 27; i++) {
            getInventory().setItem(i, MenuItems.filler());
        }
        parentSlots.clear();

        placeIdentity(group);
        placeParents(group);
        placeActions(group);
        renderBackOrExitButton();
    }

    private void placeIdentity(Group group) {
        int weight = group.getWeight().orElse(0);
        String prefix = highestPriority(group.getNodes(NodeType.PREFIX)).map(ChatMetaNode::getMetaValue).orElse("(none)");
        String suffix = highestPriority(group.getNodes(NodeType.SUFFIX)).map(ChatMetaNode::getMetaValue).orElse("(none)");
        List<String> tracks = plugin.getLuckPermsHandler().getTracksContainingGroup(groupName);

        getInventory().setItem(IDENTITY_SLOT, MenuItems.itemWithLore(Material.WRITTEN_BOOK,
                MenuItems.render("<gold><bold><name></bold></gold>", Placeholder.unparsed("name", group.getDisplayName())),
                List.of(
                        MenuItems.render("<gray>Weight: <aqua><weight>", Placeholder.unparsed("weight", String.valueOf(weight))),
                        MenuItems.render("<gray>Prefix: <white><prefix>", Placeholder.unparsed("prefix", prefix)),
                        MenuItems.render("<gray>Suffix: <white><suffix>", Placeholder.unparsed("suffix", suffix)),
                        tracks.isEmpty()
                                ? MenuItems.render("<dark_gray>Not on any track")
                                : MenuItems.render("<gray>Tracks: <aqua><tracks>", Placeholder.unparsed("tracks", String.join(", ", tracks)))
                )));
    }

    private Optional<? extends ChatMetaNode<?, ?>> highestPriority(java.util.Collection<? extends ChatMetaNode<?, ?>> nodes) {
        return nodes.stream().max(Comparator.comparingInt(ChatMetaNode::getPriority));
    }

    private void placeParents(Group group) {
        LinkedHashSet<String> parents = new LinkedHashSet<>();
        for (InheritanceNode node : group.getNodes(NodeType.INHERITANCE)) {
            parents.add(node.getGroupName());
        }

        int slot = PARENT_ROW_START;
        for (String parent : parents) {
            if (slot > PARENT_ROW_END) {
                break;
            }
            getInventory().setItem(slot, MenuItems.item(Material.NAME_TAG,
                    "<yellow><name>", List.of("<red>Right-click to remove parent"),
                    Placeholder.unparsed("name", parent)));
            parentSlots.put(slot, parent);
            slot++;
        }

        getInventory().setItem(ADD_PARENT_SLOT, MenuItems.item(Material.LEAD,
                "<green><bold>Add Parent</bold></green>",
                List.of("<gray>Type: parent [duration]", "", "<yellow>Click to add")));
    }

    private void placeActions(Group group) {
        getInventory().setItem(WEIGHT_SLOT, MenuItems.item(Material.ANVIL,
                "<gold><bold>Set Weight</bold></gold>",
                List.of("<gray>Current: <white>" + group.getWeight().orElse(0), "", "<yellow>Click to change")));

        getInventory().setItem(PREFIX_SLOT, MenuItems.item(Material.NAME_TAG,
                "<gold><bold>Set Prefix</bold></gold>",
                List.of("<gray>Replaces any existing prefix.", "", "<yellow>Click to change")));

        getInventory().setItem(SUFFIX_SLOT, MenuItems.item(Material.NAME_TAG,
                "<gold><bold>Set Suffix</bold></gold>",
                List.of("<gray>Replaces any existing suffix.", "", "<yellow>Click to change")));

        getInventory().setItem(DISPLAY_NAME_SLOT, MenuItems.item(Material.OAK_SIGN,
                "<gold><bold>Set Display Name</bold></gold>",
                List.of("<gray>Cosmetic name shown in menus.", "", "<yellow>Click to change")));

        getInventory().setItem(NODE_EDITOR_SLOT, MenuItems.item(Material.PAPER,
                "<gold><bold>Permission Nodes</bold></gold>",
                List.of("<gray>Browse, toggle and add", "<gray>this group's raw nodes.", "", "<yellow>Click to open")));

        getInventory().setItem(DELETE_SLOT, deleteGuard.isArmed()
                ? MenuItems.item(Material.TNT, "<red><bold>Click again to confirm</bold></red>",
                        List.of("<gray>This cannot be undone."))
                : MenuItems.item(Material.LAVA_BUCKET, "<red><bold>Delete Group</bold></red>",
                        List.of("<gray>Click twice within 10 seconds", "<gray>to permanently delete this group.")));
    }

    @Override
    protected void onClick(int slot, ClickType clickType, Player player) {
        if (slot != DELETE_SLOT) {
            deleteGuard.reset();
        }

        String parent = parentSlots.get(slot);
        if (parent != null && clickType.isRightClick()) {
            removeParent(parent);
            return;
        }

        if (slot == ADD_PARENT_SLOT) {
            addParent();
            return;
        }
        if (slot == WEIGHT_SLOT) {
            changeWeight();
            return;
        }
        if (slot == PREFIX_SLOT) {
            changePrefix();
            return;
        }
        if (slot == SUFFIX_SLOT) {
            changeSuffix();
            return;
        }
        if (slot == DISPLAY_NAME_SLOT) {
            changeDisplayName();
            return;
        }
        if (slot == NODE_EDITOR_SLOT) {
            new GroupNodeEditorMenu(plugin, session, viewer, groupName).open();
            return;
        }
        if (slot == DELETE_SLOT) {
            handleDeleteClick();
        }
    }

    private void removeParent(String parent) {
        beginMutation();
        plugin.getLuckPermsHandler().removeGroupParent(viewer, groupName, parent).whenComplete((result, throwable) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (throwable != null) {
                        viewer.sendMessage(MenuItems.error("Failed to remove parent: <cause>",
                                Placeholder.unparsed("cause", MenuItems.rootCause(throwable).getMessage())));
                    } else if (result == DataMutateResult.SUCCESS) {
                        viewer.sendMessage(MenuItems.success("Removed parent <parent>.", Placeholder.unparsed("parent", parent)));
                    } else {
                        viewer.sendMessage(MenuItems.info("This group does not have that parent."));
                    }
                    refresh();
                }));
    }

    private void addParent() {
        promptChatInput(
                MenuItems.info("Type: parent-group-name [duration] (e.g. 'default' or 'default 30d')."),
                text -> !text.isBlank() && !text.trim().split("\\s+", 2)[0].equalsIgnoreCase(groupName),
                MenuItems.error("Please enter a different group's name - a group cannot be its own parent."),
                text -> {
                    String[] parts = text.trim().split("\\s+", 2);
                    String parentName = parts[0];
                    Duration expiry = parts.length > 1 ? InputParsers.parseDuration(parts[1]).orElse(null) : null;
                    beginMutation();
                    plugin.getLuckPermsHandler().addGroupParent(viewer, groupName, parentName, expiry).whenComplete((result, throwable) ->
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (throwable != null) {
                                    viewer.sendMessage(MenuItems.error("Failed to add parent: <cause>",
                                            Placeholder.unparsed("cause", MenuItems.rootCause(throwable).getMessage())));
                                } else if (result == DataMutateResult.SUCCESS) {
                                    viewer.sendMessage(MenuItems.success("Added parent <parent>.", Placeholder.unparsed("parent", parentName)));
                                } else {
                                    viewer.sendMessage(MenuItems.info("This group already has that parent."));
                                }
                                refresh();
                            }));
                }
        );
    }

    private void changeWeight() {
        promptChatInput(
                MenuItems.info("Type the new weight (a whole number)."),
                text -> text.matches("-?\\d+"),
                MenuItems.error("Weight must be a whole number, or type 'cancel'."),
                text -> {
                    int weight = Integer.parseInt(text.trim());
                    beginMutation();
                    plugin.getLuckPermsHandler().setGroupWeight(viewer, groupName, weight).whenComplete((ignored, throwable) ->
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (throwable != null) {
                                    viewer.sendMessage(MenuItems.error("Failed to set weight: <cause>",
                                            Placeholder.unparsed("cause", MenuItems.rootCause(throwable).getMessage())));
                                } else {
                                    viewer.sendMessage(MenuItems.success("Set weight to <weight>.", Placeholder.unparsed("weight", String.valueOf(weight))));
                                }
                                refresh();
                            }));
                }
        );
    }

    private void changePrefix() {
        promptChatInput(
                MenuItems.info("Type the new prefix (MiniMessage formatting allowed)."),
                text -> !text.isBlank(),
                MenuItems.error("Prefix cannot be empty, or type 'cancel'."),
                text -> {
                    beginMutation();
                    plugin.getLuckPermsHandler().setGroupPrefix(viewer, groupName, text).whenComplete((ignored, throwable) ->
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (throwable != null) {
                                    viewer.sendMessage(MenuItems.error("Failed to set prefix: <cause>",
                                            Placeholder.unparsed("cause", MenuItems.rootCause(throwable).getMessage())));
                                } else {
                                    viewer.sendMessage(MenuItems.success("Prefix updated."));
                                }
                                refresh();
                            }));
                }
        );
    }

    private void changeSuffix() {
        promptChatInput(
                MenuItems.info("Type the new suffix (MiniMessage formatting allowed)."),
                text -> !text.isBlank(),
                MenuItems.error("Suffix cannot be empty, or type 'cancel'."),
                text -> {
                    beginMutation();
                    plugin.getLuckPermsHandler().setGroupSuffix(viewer, groupName, text).whenComplete((ignored, throwable) ->
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (throwable != null) {
                                    viewer.sendMessage(MenuItems.error("Failed to set suffix: <cause>",
                                            Placeholder.unparsed("cause", MenuItems.rootCause(throwable).getMessage())));
                                } else {
                                    viewer.sendMessage(MenuItems.success("Suffix updated."));
                                }
                                refresh();
                            }));
                }
        );
    }

    private void changeDisplayName() {
        promptChatInput(
                MenuItems.info("Type the new display name."),
                text -> !text.isBlank(),
                MenuItems.error("Display name cannot be empty, or type 'cancel'."),
                text -> {
                    beginMutation();
                    plugin.getLuckPermsHandler().setGroupDisplayName(viewer, groupName, text).whenComplete((ignored, throwable) ->
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (throwable != null) {
                                    viewer.sendMessage(MenuItems.error("Failed to set display name: <cause>",
                                            Placeholder.unparsed("cause", MenuItems.rootCause(throwable).getMessage())));
                                } else {
                                    viewer.sendMessage(MenuItems.success("Display name updated."));
                                }
                                refresh();
                            }));
                }
        );
    }

    private void handleDeleteClick() {
        if (!deleteGuard.isArmed()) {
            deleteGuard.arm();
            render();
            return;
        }
        deleteGuard.reset();
        beginMutation();
        plugin.getLuckPermsHandler().deleteGroup(viewer, groupName).whenComplete((ignored, throwable) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (throwable != null) {
                        viewer.sendMessage(MenuItems.error("Failed to delete group: <cause>",
                                Placeholder.unparsed("cause", MenuItems.rootCause(throwable).getMessage())));
                        session.setMutationInFlight(false);
                        render();
                        return;
                    }
                    viewer.sendMessage(MenuItems.success("Deleted group <name>.", Placeholder.unparsed("name", groupName)));
                    session.goBack(viewer); // pops this now-deleted dashboard and clears the latch itself
                }));
    }
}
