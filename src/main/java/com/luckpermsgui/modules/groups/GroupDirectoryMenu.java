package com.luckpermsgui.modules.groups;

import com.luckpermsgui.LuckpermsGUI;
import com.luckpermsgui.api.LuckPermsHandler;
import com.luckpermsgui.menu.MenuItems;
import com.luckpermsgui.menu.MenuSession;
import com.luckpermsgui.menu.PaginatedMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.node.NodeType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Paginated directory of every LuckPerms group, with a toolbar action to create a new one.
 * Groups are relatively few in number compared to users (dozens, not thousands), so unlike the
 * user directory this simply loads and lists all of them - no scale-driven rendering shortcuts
 * are needed here.
 */
public final class GroupDirectoryMenu extends PaginatedMenu {

    private static final Pattern VALID_NAME = Pattern.compile("^[a-zA-Z0-9_-]{1,36}$");
    private static final int CREATE_SLOT = 47;
    private static final int PER_PAGE = CONTENT_END - CONTENT_START + 1;

    private List<Group> groups = List.of();
    private final Map<Integer, String> groupSlots = new HashMap<>();

    public GroupDirectoryMenu(LuckpermsGUI plugin, MenuSession session, Player viewer) {
        super(plugin, session, viewer, MenuItems.render("<dark_purple><bold>Groups Directory</bold></dark_purple>"));
    }

    @Override
    protected CompletableFuture<Void> load() {
        LuckPermsHandler handler = plugin.getLuckPermsHandler();
        return handler.loadAllGroups().thenAccept(ignored -> {
            List<Group> sorted = new ArrayList<>(handler.getLuckPerms().getGroupManager().getLoadedGroups());
            sorted.sort(Comparator.comparing(Group::getName, String.CASE_INSENSITIVE_ORDER));
            this.groups = sorted;
        });
    }

    @Override
    protected void render() {
        for (int i = 0; i < PaginatedMenu.SIZE; i++) {
            getInventory().setItem(i, MenuItems.filler());
        }
        groupSlots.clear();

        setTotalPages((int) Math.ceil(groups.size() / (double) PER_PAGE));
        int from = getPage() * PER_PAGE;
        int to = Math.min(from + PER_PAGE, groups.size());

        int slot = CONTENT_START;
        for (int i = from; i < to; i++) {
            Group group = groups.get(i);
            getInventory().setItem(slot, groupItem(group));
            groupSlots.put(slot, group.getName());
            slot++;
        }

        renderToolbar();
        renderPageControls();
    }

    private ItemStack groupItem(Group group) {
        int weight = group.getWeight().orElse(0);
        int parentCount = group.getNodes(NodeType.INHERITANCE).size();
        List<String> tracks = plugin.getLuckPermsHandler().getTracksContainingGroup(group.getName());

        List<Component> lore = new ArrayList<>();
        lore.add(MenuItems.render("<gray>Weight: <aqua><weight>", Placeholder.unparsed("weight", String.valueOf(weight))));
        lore.add(MenuItems.render("<gray>Parents: <aqua><count>", Placeholder.unparsed("count", String.valueOf(parentCount))));
        lore.add(tracks.isEmpty()
                ? MenuItems.render("<dark_gray>Not on any track")
                : MenuItems.render("<gray>Tracks: <aqua><tracks>", Placeholder.unparsed("tracks", String.join(", ", tracks))));
        lore.add(MenuItems.render("<yellow>Click to manage"));

        Component name = MenuItems.render("<gold><name>", Placeholder.unparsed("name", group.getDisplayName()));
        return MenuItems.itemWithLore(Material.NAME_TAG, name, lore);
    }

    private void renderToolbar() {
        getInventory().setItem(CREATE_SLOT, MenuItems.item(Material.EMERALD,
                "<green><bold>Create Group</bold></green>",
                List.of("<gray>Type a name in chat.", "", "<yellow>Click to create")));
    }

    @Override
    protected void onContentClick(int slot, ClickType clickType, Player player) {
        String groupName = groupSlots.get(slot);
        if (groupName != null) {
            new GroupDashboardMenu(plugin, session, viewer, groupName).open();
        }
    }

    @Override
    protected void onToolbarClick(int slot, ClickType clickType, Player player) {
        if (slot != CREATE_SLOT) {
            return;
        }
        promptChatInput(
                MenuItems.info("Type the new group's name in chat (letters, numbers, - and _ only)."),
                text -> VALID_NAME.matcher(text.trim()).matches(),
                MenuItems.error("Group names may only contain letters, numbers, hyphens and underscores, up to 36 characters."),
                name -> plugin.getLuckPermsHandler().groupExists(name).whenComplete((exists, throwable) ->
                        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                            if (throwable != null) {
                                viewer.sendMessage(MenuItems.error("Failed to check group existence: <cause>",
                                        Placeholder.unparsed("cause", MenuItems.rootCause(throwable).getMessage())));
                                reopen();
                                return;
                            }
                            if (Boolean.TRUE.equals(exists)) {
                                viewer.sendMessage(MenuItems.error("A group named <name> already exists.", Placeholder.unparsed("name", name)));
                                reopen();
                                return;
                            }
                            plugin.getLuckPermsHandler().createGroup(viewer, name).whenComplete((group, createThrowable) ->
                                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                                        if (createThrowable != null) {
                                            viewer.sendMessage(MenuItems.error("Failed to create group: <cause>",
                                                    Placeholder.unparsed("cause", MenuItems.rootCause(createThrowable).getMessage())));
                                        } else {
                                            viewer.sendMessage(MenuItems.success("Created group <name>.", Placeholder.unparsed("name", name)));
                                        }
                                        refresh();
                                    }));
                        }))
        );
    }
}
