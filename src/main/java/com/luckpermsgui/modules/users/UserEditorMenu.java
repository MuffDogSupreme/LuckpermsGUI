package com.luckpermsgui.modules.users;

import com.luckpermsgui.LuckpermsGUI;
import com.luckpermsgui.api.LuckPermsHandler;
import com.luckpermsgui.menu.InputParsers;
import com.luckpermsgui.menu.MenuItems;
import com.luckpermsgui.menu.MenuSession;
import com.luckpermsgui.menu.PaginatedMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.luckperms.api.model.data.DataMutateResult;
import net.luckperms.api.model.user.User;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Paginated raw-permission-node editor for a single user: the user-side equivalent of
 * {@link com.luckpermsgui.modules.groups.GroupNodeEditorMenu}. Uses the full 0-44 content grid -
 * identity, groups and primary-group switching moved to {@link UserDashboardMenu} once that
 * screen existed, so this one screen is purely about permission nodes.
 *
 * <p>Permission nodes are modelled as a strict three-state cycle: True, Negated (a stored node
 * with {@code value=false}), or Unset (no node at all, i.e. no item rendered for that key).
 * Left-click toggles True/Negated; right-click unsets entirely; the toolbar's Add Permission
 * button accepts an optional expiry and a single context restriction via the same compact syntax
 * as the group node editor.
 */
public final class UserEditorMenu extends PaginatedMenu {

    private static final DateTimeFormatter EXPIRY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z").withZone(ZoneId.systemDefault());
    private static final int ADD_SLOT = 46;
    private static final int PER_PAGE = CONTENT_END - CONTENT_START + 1;

    private final UUID targetUniqueId;
    private final String targetName;
    private final Map<Integer, Node> permissionSlots = new HashMap<>();

    private User cachedUser;

    public UserEditorMenu(LuckpermsGUI plugin, MenuSession session, Player viewer, UUID targetUniqueId, String targetName) {
        super(plugin, session, viewer, MenuItems.render("<dark_purple>Nodes • <name>", Placeholder.unparsed("name", targetName)));
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
        for (int i = 0; i < PaginatedMenu.SIZE; i++) {
            getInventory().setItem(i, MenuItems.filler());
        }
        permissionSlots.clear();

        List<Node> permissionNodes = new ArrayList<>(user.getNodes(NodeType.PERMISSION));
        setTotalPages((int) Math.ceil(permissionNodes.size() / (double) PER_PAGE));

        int from = getPage() * PER_PAGE;
        int to = Math.min(from + PER_PAGE, permissionNodes.size());
        int slot = CONTENT_START;
        for (int i = from; i < to; i++) {
            Node node = permissionNodes.get(i);
            getInventory().setItem(slot, nodeItem(node));
            permissionSlots.put(slot, node);
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
        Node node = permissionSlots.get(slot);
        if (node == null) {
            return;
        }
        LuckPermsHandler handler = plugin.getLuckPermsHandler();
        beginMutation();
        if (clickType.isRightClick()) {
            handler.removePermission(viewer, targetUniqueId, targetName, node.getKey()).whenComplete((result, throwable) ->
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
            handler.toggleNode(viewer, targetUniqueId, targetName, node).whenComplete((ignored, throwable) ->
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
                    plugin.getLuckPermsHandler().addPermission(viewer, targetUniqueId, targetName, parsed.key(), parsed.value(),
                                    parsed.expiry(), parsed.contextKey(), parsed.contextValue())
                            .whenComplete((result, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                                if (throwable != null) {
                                    viewer.sendMessage(MenuItems.error("Failed to add permission: <cause>",
                                            Placeholder.unparsed("cause", MenuItems.rootCause(throwable).getMessage())));
                                } else if (result == DataMutateResult.SUCCESS) {
                                    viewer.sendMessage(MenuItems.success("Added permission <perm>.", Placeholder.unparsed("perm", parsed.key())));
                                } else {
                                    viewer.sendMessage(MenuItems.info("<name> already has that exact permission.",
                                            Placeholder.unparsed("name", targetName)));
                                }
                                refresh();
                            }));
                }
        );
    }
}
