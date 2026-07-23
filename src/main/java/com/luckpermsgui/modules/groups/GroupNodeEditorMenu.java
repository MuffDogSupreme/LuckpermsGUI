package com.luckpermsgui.modules.groups;

import com.luckpermsgui.LuckpermsGUI;
import com.luckpermsgui.api.LuckPermsHandler;
import com.luckpermsgui.menu.InputParsers;
import com.luckpermsgui.menu.MenuItems;
import com.luckpermsgui.menu.MenuSession;
import com.luckpermsgui.menu.PaginatedMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.luckperms.api.model.data.DataMutateResult;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Paginated raw-permission-node editor for a single group: the group-side equivalent of
 * {@link com.luckpermsgui.modules.users.UserEditorMenu}. Same True/Negated/Unset tri-state model,
 * plus support for adding temporary (expiring) nodes and a single context restriction via the
 * compact chat-input syntax in {@link InputParsers#parseNodeInput}.
 */
public final class GroupNodeEditorMenu extends PaginatedMenu {

    private static final DateTimeFormatter EXPIRY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z").withZone(ZoneId.systemDefault());
    private static final int ADD_SLOT = 46;
    private static final int PER_PAGE = CONTENT_END - CONTENT_START + 1;

    private final String groupName;
    private final Map<Integer, Node> nodeSlots = new HashMap<>();

    private Group cachedGroup;

    public GroupNodeEditorMenu(LuckpermsGUI plugin, MenuSession session, Player viewer, String groupName) {
        super(plugin, session, viewer, MenuItems.render("<dark_purple>Nodes • <name>", Placeholder.unparsed("name", groupName)));
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
        for (int i = 0; i < PaginatedMenu.SIZE; i++) {
            getInventory().setItem(i, MenuItems.filler());
        }
        nodeSlots.clear();

        List<Node> permissionNodes = new ArrayList<>(group.getNodes(NodeType.PERMISSION));
        setTotalPages((int) Math.ceil(permissionNodes.size() / (double) PER_PAGE));

        int from = getPage() * PER_PAGE;
        int to = Math.min(from + PER_PAGE, permissionNodes.size());
        int slot = CONTENT_START;
        for (int i = from; i < to; i++) {
            Node node = permissionNodes.get(i);
            getInventory().setItem(slot, nodeItem(node));
            nodeSlots.put(slot, node);
            slot++;
        }

        renderToolbar();
        renderPageControls();
    }

    private ItemStack nodeItem(Node node) {
        boolean value = node.getValue();
        List<Component> lore = new ArrayList<>();
        lore.add(MenuItems.render(value ? "<gray>Value: <green>True</green>" : "<gray>Value: <red>Negated (False)</red>"));
        if (node.hasExpiry()) {
            String expiry = EXPIRY_FORMAT.format(node.getExpiry());
            lore.add(MenuItems.render("<light_purple>Expires: <expiry>", Placeholder.unparsed("expiry", expiry)));
        }
        if (!node.getContexts().isEmpty()) {
            lore.add(MenuItems.render("<light_purple>Contextual"));
        }
        lore.add(MenuItems.render("<yellow>Left-click: toggle True/Negated"));
        lore.add(MenuItems.render("<red>Right-click: unset (remove)"));

        Component name = MenuItems.render((value ? "<white>" : "<dark_gray>") + "<key>", Placeholder.unparsed("key", node.getKey()));
        return MenuItems.itemWithLore(value ? Material.PAPER : Material.GUNPOWDER, name, lore);
    }

    private void renderToolbar() {
        getInventory().setItem(ADD_SLOT, MenuItems.item(Material.EMERALD,
                "<green><bold>Add Permission</bold></green>",
                List.of(
                        "<gray>Type: key [true|false]",
                        "<gray>[duration] [context=value]",
                        "<gray>Only the key is required.",
                        "",
                        "<yellow>Click to add"
                )));
    }

    @Override
    protected void onContentClick(int slot, ClickType clickType, Player player) {
        Node node = nodeSlots.get(slot);
        if (node == null) {
            return;
        }
        LuckPermsHandler handler = plugin.getLuckPermsHandler();
        beginMutation();
        if (clickType.isRightClick()) {
            handler.removeGroupPermission(viewer, groupName, node.getKey()).whenComplete((result, throwable) ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (throwable != null) {
                            viewer.sendMessage(MenuItems.error("Failed to remove permission: <cause>",
                                    Placeholder.unparsed("cause", MenuItems.rootCause(throwable).getMessage())));
                        } else if (result == DataMutateResult.SUCCESS) {
                            viewer.sendMessage(MenuItems.success("Removed permission <perm>.", Placeholder.unparsed("perm", node.getKey())));
                        } else {
                            viewer.sendMessage(MenuItems.info("Could not remove that permission."));
                        }
                        refresh();
                    }));
        } else {
            handler.toggleGroupNode(viewer, groupName, node).whenComplete((ignored, throwable) ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (throwable != null) {
                            viewer.sendMessage(MenuItems.error("Failed to toggle permission: <cause>",
                                    Placeholder.unparsed("cause", MenuItems.rootCause(throwable).getMessage())));
                        } else {
                            viewer.sendMessage(MenuItems.success("Toggled <perm> to <value>.",
                                    Placeholder.unparsed("perm", node.getKey()),
                                    Placeholder.unparsed("value", String.valueOf(!node.getValue()))));
                        }
                        refresh();
                    }));
        }
    }

    @Override
    protected void onToolbarClick(int slot, ClickType clickType, Player player) {
        if (slot != ADD_SLOT) {
            return;
        }
        promptChatInput(
                MenuItems.info("Type: permission.key [true|false] [duration] [context=value]"),
                text -> InputParsers.parseNodeInput(text).isPresent(),
                MenuItems.error("Could not parse that - the permission key is required, everything else is optional."),
                text -> {
                    InputParsers.ParsedNode parsed = InputParsers.parseNodeInput(text).orElseThrow();
                    beginMutation();
                    plugin.getLuckPermsHandler().addGroupPermission(viewer, groupName, parsed.key(), parsed.value(),
                                    parsed.expiry(), parsed.contextKey(), parsed.contextValue())
                            .whenComplete((result, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                                if (throwable != null) {
                                    viewer.sendMessage(MenuItems.error("Failed to add permission: <cause>",
                                            Placeholder.unparsed("cause", MenuItems.rootCause(throwable).getMessage())));
                                } else if (result == DataMutateResult.SUCCESS) {
                                    viewer.sendMessage(MenuItems.success("Added permission <perm>.", Placeholder.unparsed("perm", parsed.key())));
                                } else {
                                    viewer.sendMessage(MenuItems.info("This group already has that exact permission."));
                                }
                                refresh();
                            }));
                }
        );
    }
}
