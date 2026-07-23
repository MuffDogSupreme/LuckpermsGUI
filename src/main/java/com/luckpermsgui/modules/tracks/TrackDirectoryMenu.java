package com.luckpermsgui.modules.tracks;

import com.luckpermsgui.LuckpermsGUI;
import com.luckpermsgui.api.LuckPermsHandler;
import com.luckpermsgui.menu.MenuItems;
import com.luckpermsgui.menu.MenuSession;
import com.luckpermsgui.menu.PaginatedMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.luckperms.api.track.Track;
import org.bukkit.Bukkit;
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

/** Paginated directory of every LuckPerms track, with a toolbar action to create a new one. */
public final class TrackDirectoryMenu extends PaginatedMenu {

    private static final Pattern VALID_NAME = Pattern.compile("^[a-zA-Z0-9_-]{1,36}$");
    private static final int CREATE_SLOT = 47;
    private static final int PER_PAGE = CONTENT_END - CONTENT_START + 1;

    private List<Track> tracks = List.of();
    private final Map<Integer, String> trackSlots = new HashMap<>();

    public TrackDirectoryMenu(LuckpermsGUI plugin, MenuSession session, Player viewer) {
        super(plugin, session, viewer, MenuItems.render("<dark_purple><bold>Tracks Directory</bold></dark_purple>"));
    }

    @Override
    protected CompletableFuture<Void> load() {
        LuckPermsHandler handler = plugin.getLuckPermsHandler();
        return handler.loadAllTracks().thenAccept(ignored -> {
            List<Track> sorted = new ArrayList<>(handler.getLuckPerms().getTrackManager().getLoadedTracks());
            sorted.sort(Comparator.comparing(Track::getName, String.CASE_INSENSITIVE_ORDER));
            this.tracks = sorted;
        });
    }

    @Override
    protected void render() {
        for (int i = 0; i < PaginatedMenu.SIZE; i++) {
            getInventory().setItem(i, MenuItems.filler());
        }
        trackSlots.clear();

        setTotalPages((int) Math.ceil(tracks.size() / (double) PER_PAGE));
        int from = getPage() * PER_PAGE;
        int to = Math.min(from + PER_PAGE, tracks.size());

        int slot = CONTENT_START;
        for (int i = from; i < to; i++) {
            Track track = tracks.get(i);
            getInventory().setItem(slot, trackItem(track));
            trackSlots.put(slot, track.getName());
            slot++;
        }

        renderToolbar();
        renderPageControls();
    }

    private ItemStack trackItem(Track track) {
        List<String> groups = track.getGroups();
        List<Component> lore = new ArrayList<>();
        lore.add(MenuItems.render("<gray>Groups: <aqua><count>", Placeholder.unparsed("count", String.valueOf(groups.size()))));
        lore.add(groups.isEmpty()
                ? MenuItems.render("<dark_gray>(empty)")
                : MenuItems.render("<gray>Sequence: <white><sequence>", Placeholder.unparsed("sequence", String.join(" -> ", groups))));
        lore.add(MenuItems.render("<yellow>Click to manage"));

        Component name = MenuItems.render("<gold><name>", Placeholder.unparsed("name", track.getName()));
        return MenuItems.itemWithLore(Material.LADDER, name, lore);
    }

    private void renderToolbar() {
        getInventory().setItem(CREATE_SLOT, MenuItems.item(Material.EMERALD,
                "<green><bold>Create Track</bold></green>",
                List.of("<gray>Type a name in chat.", "", "<yellow>Click to create")));
    }

    @Override
    protected void onContentClick(int slot, ClickType clickType, Player player) {
        String trackName = trackSlots.get(slot);
        if (trackName != null) {
            new TrackSequenceEditorMenu(plugin, session, viewer, trackName).open();
        }
    }

    @Override
    protected void onToolbarClick(int slot, ClickType clickType, Player player) {
        if (slot != CREATE_SLOT) {
            return;
        }
        promptChatInput(
                MenuItems.info("Type the new track's name in chat (letters, numbers, - and _ only)."),
                text -> VALID_NAME.matcher(text.trim()).matches(),
                MenuItems.error("Track names may only contain letters, numbers, hyphens and underscores, up to 36 characters."),
                name -> plugin.getLuckPermsHandler().createTrack(viewer, name).whenComplete((track, throwable) ->
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (throwable != null) {
                                viewer.sendMessage(MenuItems.error("Failed to create track: <cause>",
                                        Placeholder.unparsed("cause", MenuItems.rootCause(throwable).getMessage())));
                            } else {
                                viewer.sendMessage(MenuItems.success("Created track <name>.", Placeholder.unparsed("name", name)));
                            }
                            refresh();
                        }))
        );
    }
}
