package com.luckpermsgui.modules.users;

import com.luckpermsgui.LuckpermsGUI;
import com.luckpermsgui.api.LuckPermsHandler;
import com.luckpermsgui.menu.MenuItems;
import com.luckpermsgui.menu.MenuSession;
import com.luckpermsgui.menu.PaginatedMenu;
import com.luckpermsgui.menu.PlayerResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Paginated directory of every player LuckPerms has ever stored data for.
 *
 * <p>Below 200 total known players, every row gets a real player-skull item. At 200 or above,
 * only "ranked" players (primary group other than {@code default}) get a skull - everyone else
 * renders as a plain name tag - to avoid triggering hundreds of skin-texture resolutions for a
 * screen nobody is going to scroll through in full. Names always show either way; search by
 * name/UUID (the toolbar's Search button) remains the fast path to any specific player regardless
 * of whether they're visible on the current page.
 *
 * <p>Unlike every other paginated menu in this plugin, turning a page here needs *new* data (the
 * name and, above the threshold, primary group of whichever 45 players are now visible) - the
 * full population is loaded once in {@link #load()}, but each page's per-row details are resolved
 * lazily, in {@link #render()} itself, the first time that page is viewed. Rows not yet resolved
 * show a "Loading..." placeholder and trigger a background resolution that repaints the menu
 * again once it completes - a deliberate, documented exception to this framework's usual "render()
 * does no I/O" rule, justified by directory-scale populations being fundamentally different from
 * every other module's fully-preloadable dataset.
 */
public final class UserDirectoryMenu extends PaginatedMenu {

    private static final int HEAD_THRESHOLD = 200;
    private static final int SEARCH_SLOT = 47;
    private static final int PER_PAGE = CONTENT_END - CONTENT_START + 1;

    private final Map<Integer, UUID> slotUuids = new HashMap<>();
    private final Map<UUID, String> resolvedNames = new HashMap<>();
    private final Map<UUID, String> resolvedPrimaryGroups = new HashMap<>();
    private final Set<UUID> pendingResolution = new HashSet<>();

    private List<UUID> orderedUuids = List.of();
    private boolean showHeadsForEveryone = true;

    public UserDirectoryMenu(LuckpermsGUI plugin, MenuSession session, Player viewer) {
        super(plugin, session, viewer, MenuItems.render("<dark_purple><bold>User Directory</bold></dark_purple>"));
    }

    @Override
    protected CompletableFuture<Void> load() {
        return plugin.getLuckPermsHandler().getUniqueUsers().thenAccept(uuids -> {
            showHeadsForEveryone = uuids.size() < HEAD_THRESHOLD;

            List<UUID> online = new ArrayList<>();
            List<UUID> offline = new ArrayList<>();
            for (UUID uuid : uuids) {
                if (Bukkit.getPlayer(uuid) != null) {
                    online.add(uuid);
                } else {
                    offline.add(uuid);
                }
            }
            online.sort(Comparator.comparing(uuid -> Bukkit.getPlayer(uuid).getName(), String.CASE_INSENSITIVE_ORDER));
            offline.sort(Comparator.comparing(UUID::toString));

            List<UUID> combined = new ArrayList<>(online.size() + offline.size());
            combined.addAll(online);
            combined.addAll(offline);
            this.orderedUuids = combined;
        });
    }

    @Override
    protected void render() {
        for (int i = 0; i < PaginatedMenu.SIZE; i++) {
            getInventory().setItem(i, MenuItems.filler());
        }
        slotUuids.clear();

        setTotalPages((int) Math.ceil(orderedUuids.size() / (double) PER_PAGE));
        int from = getPage() * PER_PAGE;
        int to = Math.min(from + PER_PAGE, orderedUuids.size());
        List<UUID> pageUuids = orderedUuids.subList(from, to);

        List<UUID> needsResolution = new ArrayList<>();
        for (UUID uuid : pageUuids) {
            if (!resolvedNames.containsKey(uuid) && !pendingResolution.contains(uuid)) {
                needsResolution.add(uuid);
            }
        }
        if (!needsResolution.isEmpty()) {
            pendingResolution.addAll(needsResolution);
            resolvePage(needsResolution);
        }

        int slot = CONTENT_START;
        for (UUID uuid : pageUuids) {
            getInventory().setItem(slot, userItem(uuid));
            slotUuids.put(slot, uuid);
            slot++;
        }

        renderToolbar();
        renderPageControls();
    }

    private void resolvePage(List<UUID> uuids) {
        LuckPermsHandler handler = plugin.getLuckPermsHandler();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (UUID uuid : uuids) {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                resolvedNames.put(uuid, online.getName());
                if (!showHeadsForEveryone) {
                    futures.add(resolvePrimaryGroup(uuid));
                }
                continue;
            }
            if (showHeadsForEveryone) {
                futures.add(handler.lookupUsername(uuid).thenAccept(name ->
                        resolvedNames.put(uuid, name != null ? name : shortUuid(uuid))));
            } else {
                // One loadUser call gives both the stored username and the primary group.
                futures.add(handler.loadUser(uuid).thenAccept(user -> {
                    String name = user.getUsername();
                    resolvedNames.put(uuid, name != null ? name : shortUuid(uuid));
                    resolvedPrimaryGroups.put(uuid, user.getPrimaryGroup());
                }));
            }
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).whenComplete((ignored, throwable) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    pendingResolution.removeAll(uuids);
                    if (session.current() == this) {
                        render();
                    }
                }));
    }

    private CompletableFuture<Void> resolvePrimaryGroup(UUID uuid) {
        User cached = plugin.getLuckPermsHandler().getLuckPerms().getUserManager().getUser(uuid);
        if (cached != null) {
            resolvedPrimaryGroups.put(uuid, cached.getPrimaryGroup());
            return CompletableFuture.completedFuture(null);
        }
        return plugin.getLuckPermsHandler().loadUser(uuid).thenAccept(user -> resolvedPrimaryGroups.put(uuid, user.getPrimaryGroup()));
    }

    private String shortUuid(UUID uuid) {
        return uuid.toString().substring(0, 8);
    }

    private ItemStack userItem(UUID uuid) {
        String name = resolvedNames.getOrDefault(uuid, "Loading...");
        boolean online = Bukkit.getPlayer(uuid) != null;
        String primaryGroup = resolvedPrimaryGroups.get(uuid);
        boolean ranked = primaryGroup != null && !primaryGroup.equalsIgnoreCase("default");
        boolean showHead = showHeadsForEveryone || ranked;

        List<Component> lore = new ArrayList<>();
        lore.add(MenuItems.render(online ? "<green>Online" : "<gray>Offline"));
        if (primaryGroup != null) {
            lore.add(MenuItems.render("<gray>Primary Group: <aqua><group>", Placeholder.unparsed("group", primaryGroup)));
        }
        lore.add(MenuItems.render("<yellow>Click to manage"));

        Component displayName = MenuItems.render("<gold><name>", Placeholder.unparsed("name", name));

        if (showHead) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
            meta.displayName(displayName);
            meta.lore(lore);
            head.setItemMeta(meta);
            return head;
        }
        return MenuItems.itemWithLore(Material.NAME_TAG, displayName, lore);
    }

    private void renderToolbar() {
        getInventory().setItem(SEARCH_SLOT, MenuItems.item(Material.SPYGLASS,
                "<gold><bold>Search</bold></gold>",
                List.of("<gray>Type a player's name to jump", "<gray>straight to their dashboard.", "", "<yellow>Click to search")));
    }

    @Override
    protected void onContentClick(int slot, ClickType clickType, Player player) {
        UUID uuid = slotUuids.get(slot);
        if (uuid == null) {
            return;
        }
        String name = resolvedNames.getOrDefault(uuid, uuid.toString());
        new UserDashboardMenu(plugin, session, viewer, uuid, name).open();
    }

    @Override
    protected void onToolbarClick(int slot, ClickType clickType, Player player) {
        if (slot != SEARCH_SLOT) {
            return;
        }
        promptChatInput(
                MenuItems.info("Type a player's name to open their dashboard."),
                text -> !text.isBlank(),
                MenuItems.error("Please enter a player name, or type 'cancel'."),
                name -> PlayerResolver.resolve(plugin, viewer, name, uuid -> {
                    String displayName = PlayerResolver.displayName(name, uuid);
                    new UserDashboardMenu(plugin, session, viewer, uuid, displayName).open();
                })
        );
    }
}
