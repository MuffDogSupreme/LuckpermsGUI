package com.luckpermsgui.modules.auditlog;

import com.luckpermsgui.LuckpermsGUI;
import com.luckpermsgui.menu.MenuItems;
import com.luckpermsgui.menu.MenuSession;
import com.luckpermsgui.menu.PaginatedMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.luckperms.api.actionlog.Action;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Browses the LuckPerms action log via the shared {@link ActionLogCache} (45s TTL - see that
 * class for why a per-open fetch isn't viable). Supports an actor/target name filter and a
 * force-refresh button that bypasses the cache early.
 *
 * <p>Entries are read-only: {@link #onContentClick} intentionally does nothing.
 */
public final class ActionLogMenu extends PaginatedMenu {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private static final int FORCE_REFRESH_SLOT = 45;
    private static final int FILTER_SLOT = 46;
    private static final int CLEAR_FILTER_SLOT = 47;
    private static final int PER_PAGE = CONTENT_END - CONTENT_START + 1;

    private List<Action> allActions = List.of();
    private String filterText;
    private boolean forceNextLoad = false;

    public ActionLogMenu(LuckpermsGUI plugin, MenuSession session, Player viewer) {
        super(plugin, session, viewer, MenuItems.render("<dark_purple><bold>Global Action Log</bold></dark_purple>"));
    }

    @Override
    protected CompletableFuture<Void> load() {
        boolean force = forceNextLoad;
        forceNextLoad = false;
        return plugin.getActionLogCache().get(force).thenAccept(actions -> this.allActions = actions);
    }

    @Override
    protected void render() {
        for (int i = 0; i < PaginatedMenu.SIZE; i++) {
            getInventory().setItem(i, MenuItems.filler());
        }

        List<Action> visible = filterText == null
                ? allActions
                : allActions.stream().filter(this::matchesFilter).toList();

        setTotalPages((int) Math.ceil(visible.size() / (double) PER_PAGE));

        int from = getPage() * PER_PAGE;
        int to = Math.min(from + PER_PAGE, visible.size());

        int slot = CONTENT_START;
        for (int i = from; i < to; i++) {
            getInventory().setItem(slot, actionItem(visible.get(i)));
            slot++;
        }

        renderToolbar();
        renderPageControls();
    }

    private boolean matchesFilter(Action action) {
        String needle = filterText.toLowerCase(Locale.ROOT);
        return action.getSource().getName().toLowerCase(Locale.ROOT).contains(needle)
                || action.getTarget().getName().toLowerCase(Locale.ROOT).contains(needle);
    }

    private ItemStack actionItem(Action action) {
        String timestamp = TIMESTAMP_FORMAT.format(action.getTimestamp());
        Component name = MenuItems.render("<yellow><timestamp>", Placeholder.unparsed("timestamp", timestamp));
        List<Component> lore = List.of(
                MenuItems.render("<gray>Actor: <aqua><actor>", Placeholder.unparsed("actor", action.getSource().getName())),
                MenuItems.render("<gray>Target: <aqua><target> <dark_gray>(<type>)</dark_gray>",
                        Placeholder.unparsed("target", action.getTarget().getName()),
                        Placeholder.unparsed("type", action.getTarget().getType().name())),
                MenuItems.render("<white><description>", Placeholder.unparsed("description", action.getDescription()))
        );
        return MenuItems.itemWithLore(Material.WRITABLE_BOOK, name, lore);
    }

    private void renderToolbar() {
        getInventory().setItem(FORCE_REFRESH_SLOT, MenuItems.item(Material.SUNFLOWER,
                "<green><bold>Force Refresh</bold></green>",
                List.of("<gray>Bypass the 45-second cache", "<gray>and reload from storage now.")));

        getInventory().setItem(FILTER_SLOT, filterText == null
                ? MenuItems.item(Material.NAME_TAG, "<yellow>Filter by Name</yellow>",
                        List.of("<gray>Search by actor or target name.", "", "<yellow>Click to set a filter"))
                : MenuItems.item(Material.NAME_TAG, "<yellow>Filtering: <filter></yellow>",
                        List.of("<gray>Click to change the filter."),
                        Placeholder.unparsed("filter", filterText)));

        getInventory().setItem(CLEAR_FILTER_SLOT, filterText == null
                ? MenuItems.filler()
                : MenuItems.item(Material.BARRIER, "<red>Clear Filter</red>", List.of()));
    }

    @Override
    protected void onContentClick(int slot, ClickType clickType, Player player) {
        // Log entries are read-only.
    }

    @Override
    protected void onToolbarClick(int slot, ClickType clickType, Player player) {
        if (slot == FORCE_REFRESH_SLOT) {
            forceNextLoad = true;
            refresh();
            return;
        }
        if (slot == FILTER_SLOT) {
            promptChatInput(
                    MenuItems.info("Type a name to filter by actor or target, or 'cancel' to abort."),
                    text -> !text.isBlank(),
                    MenuItems.error("Please enter a non-empty name, or type 'cancel'."),
                    text -> {
                        filterText = text;
                        render();
                    }
            );
            return;
        }
        if (slot == CLEAR_FILTER_SLOT && filterText != null) {
            filterText = null;
            render();
        }
    }
}
