package com.luckpermsgui.modules.tracks;

import com.luckpermsgui.LuckpermsGUI;
import com.luckpermsgui.api.LuckPermsHandler;
import com.luckpermsgui.menu.AbstractMenu;
import com.luckpermsgui.menu.ConfirmGuard;
import com.luckpermsgui.menu.MenuItems;
import com.luckpermsgui.menu.MenuSession;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.luckperms.api.model.data.DataMutateResult;
import net.luckperms.api.track.Track;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Visual reordering editor for one track's group sequence, using a select-then-place interaction
 * since chest GUIs have no native drag-reorder: left-click a group to select it (its tile
 * changes to a lit-up marker), then left-click a different group's tile to move the selected one
 * to that position - everything else shifts to make room, exactly like a drag-and-drop list.
 * Right-click removes a group from the track outright, regardless of selection state.
 *
 * <p>{@link #moveSelectedTo} passes the destination's *original* list index straight through to
 * {@link LuckPermsHandler#moveGroupInTrack} unadjusted - it's tempting to think a forward move
 * needs the target index reduced by one to account for the shift caused by removing the source
 * group first, but that's not so: after removal, inserting at the destination's original index
 * lands the moved group exactly there and pushes the (now shifted-down) destination group one
 * further - i.e. the moved group ends up occupying the slot the player actually clicked on. The
 * handler's own post-removal clamp (against the shrunk list) is what keeps the index in range;
 * subtracting one here as well would double-count that shift and land one slot short.
 */
public final class TrackSequenceEditorMenu extends AbstractMenu {

    private static final int INFO_SLOT = 4;
    private static final int SEQUENCE_ROW_START = 9;
    private static final int SEQUENCE_ROW_END = 16;
    private static final int ADD_SLOT = 19;
    private static final int DELETE_SLOT = 21;

    private final String trackName;
    private final Map<Integer, String> groupSlots = new HashMap<>();
    private final ConfirmGuard deleteGuard = new ConfirmGuard();

    private Track cachedTrack;
    private String selectedGroup;

    public TrackSequenceEditorMenu(LuckpermsGUI plugin, MenuSession session, Player viewer, String trackName) {
        super(plugin, session, viewer, 27, MenuItems.render("<dark_purple><bold>Track • <name></bold></dark_purple>",
                Placeholder.unparsed("name", trackName)));
        this.trackName = trackName;
    }

    @Override
    protected CompletableFuture<Void> load() {
        // Tracks are always pre-loaded by TrackDirectoryMenu before this menu can be reached, and
        // Track's own mutation methods act in-memory on the same cached instance getTrack(...)
        // returns - so a full loadAllTracks() reload isn't needed on every refresh, just a fresh
        // (cheap, synchronous) reference plus an existence check in case it was deleted elsewhere.
        this.cachedTrack = plugin.getLuckPermsHandler().getLuckPerms().getTrackManager().getTrack(trackName);
        if (cachedTrack == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Track '" + trackName + "' no longer exists."));
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    protected void render() {
        Track track = cachedTrack;
        if (track == null) {
            return;
        }
        for (int i = 0; i < 27; i++) {
            getInventory().setItem(i, MenuItems.filler());
        }
        groupSlots.clear();

        List<String> groups = track.getGroups();
        getInventory().setItem(INFO_SLOT, MenuItems.itemWithLore(Material.WRITTEN_BOOK,
                MenuItems.render("<gold><bold><name></bold></gold>", Placeholder.unparsed("name", trackName)),
                List.of(
                        MenuItems.render("<gray>Groups: <aqua><count>", Placeholder.unparsed("count", String.valueOf(groups.size()))),
                        selectedGroup == null
                                ? MenuItems.render("<gray>Left-click a group to select it.")
                                : MenuItems.render("<yellow>Selected: <name> - click a destination.", Placeholder.unparsed("name", selectedGroup))
                )));

        int slot = SEQUENCE_ROW_START;
        int position = 1;
        for (String group : groups) {
            if (slot > SEQUENCE_ROW_END) {
                break;
            }
            boolean isSelected = group.equals(selectedGroup);
            getInventory().setItem(slot, MenuItems.item(
                    isSelected ? Material.GLOWSTONE_DUST : Material.NAME_TAG,
                    (isSelected ? "<green><bold>" : "<yellow>") + "<position>. <name>" + (isSelected ? "</bold></green>" : ""),
                    isSelected
                            ? List.of("<green>Selected", "<gray>Left-click another group to move here", "<gray>Left-click again to deselect")
                            : List.of("<yellow>Left-click to select and move", "<red>Right-click to remove"),
                    Placeholder.unparsed("position", String.valueOf(position)),
                    Placeholder.unparsed("name", group)
            ));
            groupSlots.put(slot, group);
            slot++;
            position++;
        }

        getInventory().setItem(ADD_SLOT, MenuItems.item(Material.LEAD,
                "<green><bold>Add Group</bold></green>",
                List.of("<gray>Appends a group to the end.", "", "<yellow>Click to add")));

        getInventory().setItem(DELETE_SLOT, deleteGuard.isArmed()
                ? MenuItems.item(Material.TNT, "<red><bold>Click again to confirm</bold></red>",
                        List.of("<gray>This cannot be undone."))
                : MenuItems.item(Material.LAVA_BUCKET, "<red><bold>Delete Track</bold></red>",
                        List.of("<gray>Click twice within 10 seconds", "<gray>to permanently delete this track.")));

        renderBackOrExitButton();
    }

    @Override
    protected void onClick(int slot, ClickType clickType, Player player) {
        if (slot != DELETE_SLOT) {
            deleteGuard.reset();
        }

        String clickedGroup = groupSlots.get(slot);
        if (clickedGroup != null) {
            if (clickType.isRightClick()) {
                removeGroup(clickedGroup);
            } else {
                handleSequenceClick(clickedGroup);
            }
            return;
        }

        if (slot == ADD_SLOT) {
            addGroup();
            return;
        }
        if (slot == DELETE_SLOT) {
            handleDeleteClick();
        }
    }

    private void handleSequenceClick(String clickedGroup) {
        if (selectedGroup == null) {
            selectedGroup = clickedGroup;
            render();
            return;
        }
        if (selectedGroup.equals(clickedGroup)) {
            selectedGroup = null;
            render();
            return;
        }
        moveSelectedTo(clickedGroup);
    }

    private void moveSelectedTo(String destinationGroup) {
        List<String> groups = cachedTrack.getGroups();
        int fromIndex = groups.indexOf(selectedGroup);
        int toIndex = groups.indexOf(destinationGroup);
        if (fromIndex < 0 || toIndex < 0) {
            // Stale click against data that changed since this menu was last rendered - just re-sync.
            selectedGroup = null;
            refresh();
            return;
        }

        String movingGroup = selectedGroup;
        selectedGroup = null;
        beginMutation();
        plugin.getLuckPermsHandler().moveGroupInTrack(viewer, trackName, movingGroup, toIndex).whenComplete((ignored, throwable) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (throwable != null) {
                        viewer.sendMessage(MenuItems.error("Failed to reorder track: <cause>",
                                Placeholder.unparsed("cause", MenuItems.rootCause(throwable).getMessage())));
                    } else {
                        viewer.sendMessage(MenuItems.success("Moved <group>.", Placeholder.unparsed("group", movingGroup)));
                    }
                    refresh();
                }));
    }

    private void removeGroup(String group) {
        if (group.equals(selectedGroup)) {
            selectedGroup = null;
        }
        beginMutation();
        plugin.getLuckPermsHandler().removeGroupFromTrack(viewer, trackName, group).whenComplete((result, throwable) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (throwable != null) {
                        viewer.sendMessage(MenuItems.error("Failed to remove group: <cause>",
                                Placeholder.unparsed("cause", MenuItems.rootCause(throwable).getMessage())));
                    } else if (result == DataMutateResult.SUCCESS) {
                        viewer.sendMessage(MenuItems.success("Removed <group> from the track.", Placeholder.unparsed("group", group)));
                    } else {
                        viewer.sendMessage(MenuItems.info("That group isn't on this track."));
                    }
                    refresh();
                }));
    }

    private void addGroup() {
        promptChatInput(
                MenuItems.info("Type the name of an existing group to append to this track."),
                text -> !text.isBlank(),
                MenuItems.error("Please enter a group name, or type 'cancel'."),
                text -> {
                    beginMutation();
                    plugin.getLuckPermsHandler().appendGroupToTrack(viewer, trackName, text).whenComplete((result, throwable) ->
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (throwable != null) {
                                    viewer.sendMessage(MenuItems.error("Failed to add group: <cause>",
                                            Placeholder.unparsed("cause", MenuItems.rootCause(throwable).getMessage())));
                                } else if (result == DataMutateResult.SUCCESS) {
                                    viewer.sendMessage(MenuItems.success("Added <group> to the track.", Placeholder.unparsed("group", text)));
                                } else {
                                    viewer.sendMessage(MenuItems.info("That group is already on this track."));
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
        plugin.getLuckPermsHandler().deleteTrack(viewer, trackName).whenComplete((ignored, throwable) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (throwable != null) {
                        viewer.sendMessage(MenuItems.error("Failed to delete track: <cause>",
                                Placeholder.unparsed("cause", MenuItems.rootCause(throwable).getMessage())));
                        session.setMutationInFlight(false);
                        render();
                        return;
                    }
                    viewer.sendMessage(MenuItems.success("Deleted track <name>.", Placeholder.unparsed("name", trackName)));
                    session.goBack(viewer);
                }));
    }
}
