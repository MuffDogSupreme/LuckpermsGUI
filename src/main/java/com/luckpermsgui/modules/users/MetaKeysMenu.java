package com.luckpermsgui.modules.users;

import com.luckpermsgui.LuckpermsGUI;
import com.luckpermsgui.menu.MenuItems;
import com.luckpermsgui.menu.MenuSession;
import com.luckpermsgui.menu.PaginatedMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.luckperms.api.model.data.DataMutateResult;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.MetaNode;
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
 * Paginated list of a user's meta key/value pairs. Left-click a key to replace its value;
 * right-click to remove it entirely; the toolbar's Add Meta button accepts new pairs as
 * {@code key value} (everything after the first space is the value, so values may contain
 * spaces).
 */
public final class MetaKeysMenu extends PaginatedMenu {

    private static final DateTimeFormatter EXPIRY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z").withZone(ZoneId.systemDefault());
    private static final int ADD_SLOT = 46;
    private static final int PER_PAGE = CONTENT_END - CONTENT_START + 1;

    private final UUID targetUniqueId;
    private final String targetName;
    private final Map<Integer, MetaNode> metaSlots = new HashMap<>();

    private User cachedUser;

    public MetaKeysMenu(LuckpermsGUI plugin, MenuSession session, Player viewer, UUID targetUniqueId, String targetName) {
        super(plugin, session, viewer, MenuItems.render("<dark_purple>Meta • <name>", Placeholder.unparsed("name", targetName)));
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
        metaSlots.clear();

        List<MetaNode> metaNodes = new ArrayList<>(user.getNodes(NodeType.META));
        setTotalPages((int) Math.ceil(metaNodes.size() / (double) PER_PAGE));

        int from = getPage() * PER_PAGE;
        int to = Math.min(from + PER_PAGE, metaNodes.size());
        int slot = CONTENT_START;
        for (int i = from; i < to; i++) {
            MetaNode node = metaNodes.get(i);
            getInventory().setItem(slot, metaItem(node));
            metaSlots.put(slot, node);
            slot++;
        }

        renderToolbar();
        renderPageControls();
    }

    private ItemStack metaItem(MetaNode node) {
        List<Component> lore = new ArrayList<>();
        lore.add(MenuItems.render("<gray>Value: <white><value>", Placeholder.unparsed("value", node.getMetaValue())));
        if (node.hasExpiry()) {
            lore.add(MenuItems.render("<light_purple>Expires: <expiry>", Placeholder.unparsed("expiry", EXPIRY_FORMAT.format(node.getExpiry()))));
        }
        lore.add(MenuItems.render("<yellow>Left-click: replace value"));
        lore.add(MenuItems.render("<red>Right-click: remove"));

        Component name = MenuItems.render("<white><key>", Placeholder.unparsed("key", node.getMetaKey()));
        return MenuItems.itemWithLore(Material.PAPER, name, lore);
    }

    private void renderToolbar() {
        getInventory().setItem(ADD_SLOT, MenuItems.item(Material.EMERALD,
                "<green><bold>Add Meta Key</bold></green>",
                List.of("<gray>Type: key value", "<gray>(everything after the first", "<gray>space is the value)", "", "<yellow>Click to add")));
    }

    @Override
    protected void onContentClick(int slot, ClickType clickType, Player player) {
        MetaNode node = metaSlots.get(slot);
        if (node == null) {
            return;
        }
        if (clickType.isRightClick()) {
            removeMeta(node.getMetaKey());
        } else {
            replaceValue(node.getMetaKey());
        }
    }

    private void replaceValue(String key) {
        promptChatInput(
                MenuItems.info("Type the new value for <key>.", Placeholder.unparsed("key", key)),
                text -> !text.isBlank(),
                MenuItems.error("Value cannot be empty, or type 'cancel'."),
                value -> {
                    beginMutation();
                    plugin.getLuckPermsHandler().addUserMeta(viewer, targetUniqueId, targetName, key, value, null)
                            .whenComplete((result, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                                if (throwable != null) {
                                    viewer.sendMessage(MenuItems.error("Failed to update meta: <cause>",
                                            Placeholder.unparsed("cause", MenuItems.rootCause(throwable).getMessage())));
                                } else {
                                    viewer.sendMessage(MenuItems.success("Set <key> = <value>.",
                                            Placeholder.unparsed("key", key), Placeholder.unparsed("value", value)));
                                }
                                refresh();
                            }));
                }
        );
    }

    private void removeMeta(String key) {
        beginMutation();
        plugin.getLuckPermsHandler().removeUserMeta(viewer, targetUniqueId, targetName, key).whenComplete((removed, throwable) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (throwable != null) {
                        viewer.sendMessage(MenuItems.error("Failed to remove meta: <cause>",
                                Placeholder.unparsed("cause", MenuItems.rootCause(throwable).getMessage())));
                    } else if (Boolean.TRUE.equals(removed)) {
                        viewer.sendMessage(MenuItems.success("Removed meta key <key>.", Placeholder.unparsed("key", key)));
                    } else {
                        viewer.sendMessage(MenuItems.info("That meta key no longer exists."));
                    }
                    refresh();
                }));
    }

    @Override
    protected void onToolbarClick(int slot, ClickType clickType, Player player) {
        if (slot != ADD_SLOT) {
            return;
        }
        promptChatInput(
                MenuItems.info("Type: key value"),
                text -> text.trim().split("\\s+", 2).length == 2,
                MenuItems.error("Please provide both a key and a value, separated by a space."),
                text -> {
                    String[] parts = text.trim().split("\\s+", 2);
                    String key = parts[0];
                    String value = parts[1];
                    beginMutation();
                    plugin.getLuckPermsHandler().addUserMeta(viewer, targetUniqueId, targetName, key, value, null)
                            .whenComplete((result, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                                if (throwable != null) {
                                    viewer.sendMessage(MenuItems.error("Failed to add meta: <cause>",
                                            Placeholder.unparsed("cause", MenuItems.rootCause(throwable).getMessage())));
                                } else if (result == DataMutateResult.SUCCESS) {
                                    viewer.sendMessage(MenuItems.success("Set <key> = <value>.",
                                            Placeholder.unparsed("key", key), Placeholder.unparsed("value", value)));
                                } else {
                                    viewer.sendMessage(MenuItems.info("Could not add that meta key."));
                                }
                                refresh();
                            }));
                }
        );
    }
}
