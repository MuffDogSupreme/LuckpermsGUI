package com.luckpermsgui.api;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.actionlog.Action;
import net.luckperms.api.actionlog.ActionLog;
import net.luckperms.api.context.ImmutableContextSet;
import net.luckperms.api.messaging.MessagingService;
import net.luckperms.api.model.data.DataMutateResult;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeBuilder;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.DisplayNameNode;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.MetaNode;
import net.luckperms.api.node.types.PrefixNode;
import net.luckperms.api.node.types.SuffixNode;
import net.luckperms.api.node.types.WeightNode;
import net.luckperms.api.track.DemotionResult;
import net.luckperms.api.track.PromotionResult;
import net.luckperms.api.track.Track;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Encapsulates every interaction this plugin makes with the LuckPerms API.
 *
 * <p>Every method here is thread-agnostic with respect to server-tick-sensitive Bukkit calls: it
 * never touches the scheduler, an inventory, or anything else that must run on the main thread.
 * Callers (commands, menus) are responsible for hopping back onto the main thread before doing
 * that. {@link CommandSender} is accepted purely to attribute audit-log entries (name + UUID) -
 * reading those is not a thread-sensitive operation.
 *
 * <p>User/Group mutations are performed via {@code modifyUser}/{@code modifyGroup}
 * (load-if-needed, mutate, save-back in one call). Tracks have no such convenience method in the
 * API - {@link Track}'s own mutation methods ({@code appendGroup}, {@code removeGroup}, ...) are
 * synchronous, in-memory operations on an already-loaded track, so track methods here call
 * {@code saveTrack(track)} explicitly afterward. Every mutation that reports
 * {@link DataMutateResult#SUCCESS} (or the equivalent for tracks/meta) is additionally submitted
 * to the LuckPerms action log, so changes made through this plugin show up in {@code /lp log}
 * exactly like changes made through LuckPerms' own commands.
 */
public final class LuckPermsHandler {

    /** Attributed to actions run from the console, which has no UUID of its own. */
    private static final UUID CONSOLE_UUID = new UUID(0, 0);

    private final LuckPerms luckPerms;

    public LuckPermsHandler(LuckPerms luckPerms) {
        this.luckPerms = luckPerms;
    }

    public LuckPerms getLuckPerms() {
        return luckPerms;
    }

    // ================================================================================
    // Users
    // ================================================================================

    /**
     * Resolves a player name to a UUID, checking online players first and falling back to the
     * LuckPerms lookup service (which consults the storage backend) for offline players.
     */
    public CompletableFuture<UUID> lookupUniqueId(String name) {
        return luckPerms.getUserManager().lookupUniqueId(name);
    }

    /** Looks up a stored username for a UUID without loading the user's full node data. May resolve to null if unknown. */
    public CompletableFuture<String> lookupUsername(UUID uniqueId) {
        return luckPerms.getUserManager().lookupUsername(uniqueId);
    }

    /** Loads a user (from cache or storage) without mutating anything. */
    public CompletableFuture<User> loadUser(UUID uniqueId) {
        return luckPerms.getUserManager().loadUser(uniqueId);
    }

    /** Every UUID LuckPerms has ever stored data for - used to size the user directory and decide the head-rendering threshold. */
    public CompletableFuture<Set<UUID>> getUniqueUsers() {
        return luckPerms.getUserManager().getUniqueUsers();
    }

    /**
     * Sets the player's primary group. LuckPerms only honours a primary group the user actually
     * has membership of, so this first ensures an {@link InheritanceNode} for the group exists
     * before flipping the primary group flag.
     */
    public CompletableFuture<Void> setPrimaryGroup(CommandSender actor, UUID targetId, String targetName, String groupName) {
        return luckPerms.getUserManager().modifyUser(targetId, user -> {
            InheritanceNode inheritanceNode = InheritanceNode.builder(groupName).build();
            user.data().add(inheritanceNode);
            user.setPrimaryGroup(groupName);
        }).thenRun(() -> auditUser(actor, targetId, targetName, "Set primary group to '" + groupName + "'"));
    }

    /** Adds (non-primary) group membership to the user, optionally expiring after {@code expiry}. */
    public CompletableFuture<DataMutateResult> addGroupMembership(CommandSender actor, UUID targetId, String targetName,
                                                                    String groupName, Duration expiry) {
        CompletableFuture<DataMutateResult> result = new CompletableFuture<>();
        luckPerms.getUserManager().modifyUser(targetId, user -> {
            NodeBuilder<?, ?> builder = InheritanceNode.builder(groupName);
            if (expiry != null) {
                builder = builder.expiry(expiry);
            }
            result.complete(user.data().add(builder.build()));
        }).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                result.completeExceptionally(throwable);
            }
        });
        return result.thenApply(mutateResult -> {
            if (mutateResult == DataMutateResult.SUCCESS) {
                String suffix = expiry != null ? " for " + formatDuration(expiry) : "";
                auditUser(actor, targetId, targetName, "Added group '" + groupName + "'" + suffix);
            }
            return mutateResult;
        });
    }

    /** Removes an inherited group from the user. Callers should avoid removing the primary group. */
    public CompletableFuture<DataMutateResult> removeGroup(CommandSender actor, UUID targetId, String targetName, String groupName) {
        CompletableFuture<DataMutateResult> result = new CompletableFuture<>();
        luckPerms.getUserManager().modifyUser(targetId, user -> {
            InheritanceNode inheritanceNode = InheritanceNode.builder(groupName).build();
            result.complete(user.data().remove(inheritanceNode));
        }).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                result.completeExceptionally(throwable);
            }
        });
        return result.thenApply(mutateResult -> {
            if (mutateResult == DataMutateResult.SUCCESS) {
                auditUser(actor, targetId, targetName, "Removed group '" + groupName + "'");
            }
            return mutateResult;
        });
    }

    /** Adds a permission node with the given value to the user. */
    public CompletableFuture<DataMutateResult> addPermission(CommandSender actor, UUID targetId, String targetName, String permission, boolean value) {
        return addPermission(actor, targetId, targetName, permission, value, null, null, null);
    }

    /** Adds a permission node to the user, optionally with an expiry and/or a single context restriction. */
    public CompletableFuture<DataMutateResult> addPermission(CommandSender actor, UUID targetId, String targetName,
                                                               String permission, boolean value, Duration expiry,
                                                               String contextKey, String contextValue) {
        CompletableFuture<DataMutateResult> result = new CompletableFuture<>();
        luckPerms.getUserManager().modifyUser(targetId, user ->
                result.complete(user.data().add(buildPermissionNode(permission, value, expiry, contextKey, contextValue))))
                .whenComplete((ignored, throwable) -> {
                    if (throwable != null) {
                        result.completeExceptionally(throwable);
                    }
                });
        return result.thenApply(mutateResult -> {
            if (mutateResult == DataMutateResult.SUCCESS) {
                auditUser(actor, targetId, targetName, "Added permission '" + permission + "' (" + value + ")" + describeExtras(expiry, contextKey, contextValue));
            }
            return mutateResult;
        });
    }

    /** Removes a permission node (regardless of its value) from the user. */
    public CompletableFuture<DataMutateResult> removePermission(CommandSender actor, UUID targetId, String targetName, String permission) {
        CompletableFuture<DataMutateResult> result = new CompletableFuture<>();
        luckPerms.getUserManager().modifyUser(targetId, user -> {
            Node node = Node.builder(permission).build();
            result.complete(user.data().remove(node));
        }).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                result.completeExceptionally(throwable);
            }
        });
        return result.thenApply(mutateResult -> {
            if (mutateResult == DataMutateResult.SUCCESS) {
                auditUser(actor, targetId, targetName, "Removed permission '" + permission + "'");
            }
            return mutateResult;
        });
    }

    /**
     * Removes the given node and re-adds an equivalent copy with the value inverted. Used by the
     * GUI's "toggle" action on a permission item.
     */
    public CompletableFuture<Void> toggleNode(CommandSender actor, UUID targetId, String targetName, Node node) {
        boolean newValue = !node.getValue();
        return luckPerms.getUserManager().modifyUser(targetId, user -> {
            user.data().remove(node);
            Node inverted = node.toBuilder().value(newValue).build();
            user.data().add(inverted);
        }).thenRun(() -> auditUser(actor, targetId, targetName, "Toggled permission '" + node.getKey() + "' to " + newValue));
    }

    /** Sets (replacing any existing value for the same key) a meta key on the user. */
    public CompletableFuture<DataMutateResult> addUserMeta(CommandSender actor, UUID targetId, String targetName,
                                                             String key, String value, Duration expiry) {
        CompletableFuture<DataMutateResult> result = new CompletableFuture<>();
        luckPerms.getUserManager().modifyUser(targetId, user -> {
            user.data().clear(metaKeyPredicate(key));
            NodeBuilder<?, ?> builder = MetaNode.builder(key, value);
            if (expiry != null) {
                builder = builder.expiry(expiry);
            }
            result.complete(user.data().add(builder.build()));
        }).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                result.completeExceptionally(throwable);
            }
        });
        return result.thenApply(mutateResult -> {
            if (mutateResult == DataMutateResult.SUCCESS) {
                auditUser(actor, targetId, targetName, "Set meta '" + key + "' = '" + value + "'");
            }
            return mutateResult;
        });
    }

    /** Removes a meta key from the user. Returns whether the key was actually present. */
    public CompletableFuture<Boolean> removeUserMeta(CommandSender actor, UUID targetId, String targetName, String key) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        luckPerms.getUserManager().modifyUser(targetId, user -> {
            boolean hadKey = user.getNodes(NodeType.META).stream().anyMatch(m -> m.getMetaKey().equalsIgnoreCase(key));
            user.data().clear(metaKeyPredicate(key));
            result.complete(hadKey);
        }).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                result.completeExceptionally(throwable);
            }
        });
        return result.thenApply(hadKey -> {
            if (hadKey) {
                auditUser(actor, targetId, targetName, "Removed meta '" + key + "'");
            }
            return hadKey;
        });
    }

    /** Promotes the user one step along the given track. */
    public CompletableFuture<PromotionResult> promoteOnTrack(CommandSender actor, UUID targetId, String targetName, String trackName) {
        Track track = luckPerms.getTrackManager().getTrack(trackName);
        if (track == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Track '" + trackName + "' is not loaded."));
        }
        CompletableFuture<PromotionResult> result = new CompletableFuture<>();
        luckPerms.getUserManager().modifyUser(targetId, user ->
                result.complete(track.promote(user, ImmutableContextSet.empty()))
        ).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                result.completeExceptionally(throwable);
            }
        });
        return result.thenApply(promotionResult -> {
            if (promotionResult.wasSuccessful()) {
                auditUser(actor, targetId, targetName, "Promoted on track '" + trackName + "'" + describeTransition(
                        promotionResult.getGroupFrom(), promotionResult.getGroupTo()));
            }
            return promotionResult;
        });
    }

    /** Demotes the user one step along the given track. */
    public CompletableFuture<DemotionResult> demoteOnTrack(CommandSender actor, UUID targetId, String targetName, String trackName) {
        Track track = luckPerms.getTrackManager().getTrack(trackName);
        if (track == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Track '" + trackName + "' is not loaded."));
        }
        CompletableFuture<DemotionResult> result = new CompletableFuture<>();
        luckPerms.getUserManager().modifyUser(targetId, user ->
                result.complete(track.demote(user, ImmutableContextSet.empty()))
        ).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                result.completeExceptionally(throwable);
            }
        });
        return result.thenApply(demotionResult -> {
            if (demotionResult.wasSuccessful()) {
                auditUser(actor, targetId, targetName, "Demoted on track '" + trackName + "'" + describeTransition(
                        demotionResult.getGroupFrom(), demotionResult.getGroupTo()));
            }
            return demotionResult;
        });
    }

    // ================================================================================
    // Groups
    // ================================================================================

    public CompletableFuture<Void> loadAllGroups() {
        return luckPerms.getGroupManager().loadAllGroups();
    }

    /**
     * Checks whether a group with the given name is known to LuckPerms. Checks already-loaded
     * groups first (cheap, synchronous) and falls back to an async storage lookup.
     */
    public CompletableFuture<Boolean> groupExists(String groupName) {
        Set<Group> loadedGroups = luckPerms.getGroupManager().getLoadedGroups();
        for (Group group : loadedGroups) {
            if (group.getName().equalsIgnoreCase(groupName)) {
                return CompletableFuture.completedFuture(true);
            }
        }
        return luckPerms.getGroupManager().loadGroup(groupName).thenApply(Optional::isPresent);
    }

    /** Returns the names of every currently loaded group, for tab completion. */
    public Set<String> getLoadedGroupNames() {
        Set<Group> loadedGroups = luckPerms.getGroupManager().getLoadedGroups();
        Set<String> names = new HashSet<>();
        for (Group group : loadedGroups) {
            names.add(group.getName());
        }
        return names;
    }

    public CompletableFuture<Group> createGroup(CommandSender actor, String name) {
        return luckPerms.getGroupManager().createAndLoadGroup(name).thenApply(group -> {
            auditGroup(actor, name, "Created group");
            return group;
        });
    }

    public CompletableFuture<Void> deleteGroup(CommandSender actor, String groupName) {
        Group group = luckPerms.getGroupManager().getGroup(groupName);
        if (group == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Group '" + groupName + "' is not loaded."));
        }
        return luckPerms.getGroupManager().deleteGroup(group)
                .thenRun(() -> auditGroup(actor, groupName, "Deleted group"));
    }

    public CompletableFuture<DataMutateResult> addGroupParent(CommandSender actor, String groupName, String parentName, Duration expiry) {
        CompletableFuture<DataMutateResult> result = new CompletableFuture<>();
        luckPerms.getGroupManager().modifyGroup(groupName, group -> {
            NodeBuilder<?, ?> builder = InheritanceNode.builder(parentName);
            if (expiry != null) {
                builder = builder.expiry(expiry);
            }
            result.complete(group.data().add(builder.build()));
        }).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                result.completeExceptionally(throwable);
            }
        });
        return result.thenApply(mutateResult -> {
            if (mutateResult == DataMutateResult.SUCCESS) {
                auditGroup(actor, groupName, "Added parent '" + parentName + "'");
            }
            return mutateResult;
        });
    }

    public CompletableFuture<DataMutateResult> removeGroupParent(CommandSender actor, String groupName, String parentName) {
        CompletableFuture<DataMutateResult> result = new CompletableFuture<>();
        luckPerms.getGroupManager().modifyGroup(groupName, group -> {
            InheritanceNode inheritanceNode = InheritanceNode.builder(parentName).build();
            result.complete(group.data().remove(inheritanceNode));
        }).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                result.completeExceptionally(throwable);
            }
        });
        return result.thenApply(mutateResult -> {
            if (mutateResult == DataMutateResult.SUCCESS) {
                auditGroup(actor, groupName, "Removed parent '" + parentName + "'");
            }
            return mutateResult;
        });
    }

    public CompletableFuture<DataMutateResult> addGroupPermission(CommandSender actor, String groupName, String permission,
                                                                    boolean value, Duration expiry, String contextKey, String contextValue) {
        CompletableFuture<DataMutateResult> result = new CompletableFuture<>();
        luckPerms.getGroupManager().modifyGroup(groupName, group ->
                result.complete(group.data().add(buildPermissionNode(permission, value, expiry, contextKey, contextValue)))
        ).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                result.completeExceptionally(throwable);
            }
        });
        return result.thenApply(mutateResult -> {
            if (mutateResult == DataMutateResult.SUCCESS) {
                auditGroup(actor, groupName, "Added permission '" + permission + "' (" + value + ")" + describeExtras(expiry, contextKey, contextValue));
            }
            return mutateResult;
        });
    }

    public CompletableFuture<DataMutateResult> removeGroupPermission(CommandSender actor, String groupName, String permission) {
        CompletableFuture<DataMutateResult> result = new CompletableFuture<>();
        luckPerms.getGroupManager().modifyGroup(groupName, group -> {
            Node node = Node.builder(permission).build();
            result.complete(group.data().remove(node));
        }).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                result.completeExceptionally(throwable);
            }
        });
        return result.thenApply(mutateResult -> {
            if (mutateResult == DataMutateResult.SUCCESS) {
                auditGroup(actor, groupName, "Removed permission '" + permission + "'");
            }
            return mutateResult;
        });
    }

    public CompletableFuture<Void> toggleGroupNode(CommandSender actor, String groupName, Node node) {
        boolean newValue = !node.getValue();
        return luckPerms.getGroupManager().modifyGroup(groupName, group -> {
            group.data().remove(node);
            Node inverted = node.toBuilder().value(newValue).build();
            group.data().add(inverted);
        }).thenRun(() -> auditGroup(actor, groupName, "Toggled permission '" + node.getKey() + "' to " + newValue));
    }

    public CompletableFuture<Void> setGroupWeight(CommandSender actor, String groupName, int weight) {
        return luckPerms.getGroupManager().modifyGroup(groupName, group -> {
            group.data().clear(NodeType.WEIGHT.predicate());
            group.data().add(WeightNode.builder(weight).build());
        }).thenRun(() -> auditGroup(actor, groupName, "Set weight to " + weight));
    }

    public CompletableFuture<Void> setGroupDisplayName(CommandSender actor, String groupName, String displayName) {
        return luckPerms.getGroupManager().modifyGroup(groupName, group -> {
            group.data().clear(NodeType.DISPLAY_NAME.predicate());
            group.data().add(DisplayNameNode.builder(displayName).build());
        }).thenRun(() -> auditGroup(actor, groupName, "Set display name to '" + displayName + "'"));
    }

    /** Replaces every existing prefix with a single new one at a fixed priority (100). */
    public CompletableFuture<Void> setGroupPrefix(CommandSender actor, String groupName, String prefix) {
        return luckPerms.getGroupManager().modifyGroup(groupName, group -> {
            group.data().clear(NodeType.PREFIX.predicate());
            group.data().add(PrefixNode.builder(prefix, 100).build());
        }).thenRun(() -> auditGroup(actor, groupName, "Set prefix to '" + prefix + "'"));
    }

    /** Replaces every existing suffix with a single new one at a fixed priority (100). */
    public CompletableFuture<Void> setGroupSuffix(CommandSender actor, String groupName, String suffix) {
        return luckPerms.getGroupManager().modifyGroup(groupName, group -> {
            group.data().clear(NodeType.SUFFIX.predicate());
            group.data().add(SuffixNode.builder(suffix, 100).build());
        }).thenRun(() -> auditGroup(actor, groupName, "Set suffix to '" + suffix + "'"));
    }

    /** Names of every currently loaded track that lists this group as a member - a cheap, in-memory-only check. */
    public List<String> getTracksContainingGroup(String groupName) {
        List<String> names = new ArrayList<>();
        for (Track track : luckPerms.getTrackManager().getLoadedTracks()) {
            if (track.containsGroup(groupName)) {
                names.add(track.getName());
            }
        }
        return names;
    }

    // ================================================================================
    // Tracks
    // ================================================================================

    public CompletableFuture<Void> loadAllTracks() {
        return luckPerms.getTrackManager().loadAllTracks();
    }

    public CompletableFuture<Track> createTrack(CommandSender actor, String name) {
        return luckPerms.getTrackManager().createAndLoadTrack(name).thenApply(track -> {
            auditTrack(actor, name, "Created track");
            return track;
        });
    }

    public CompletableFuture<Void> deleteTrack(CommandSender actor, String trackName) {
        Track track = luckPerms.getTrackManager().getTrack(trackName);
        if (track == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Track '" + trackName + "' is not loaded."));
        }
        return luckPerms.getTrackManager().deleteTrack(track)
                .thenRun(() -> auditTrack(actor, trackName, "Deleted track"));
    }

    /** Appends a group to the end of the track. The group must already exist. */
    public CompletableFuture<DataMutateResult> appendGroupToTrack(CommandSender actor, String trackName, String groupName) {
        Track track = luckPerms.getTrackManager().getTrack(trackName);
        if (track == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Track '" + trackName + "' is not loaded."));
        }
        return luckPerms.getGroupManager().loadGroup(groupName).thenCompose(optionalGroup -> {
            if (optionalGroup.isEmpty()) {
                return CompletableFuture.failedFuture(new IllegalStateException("Group '" + groupName + "' does not exist."));
            }
            DataMutateResult result = track.appendGroup(optionalGroup.get());
            return luckPerms.getTrackManager().saveTrack(track).thenApply(ignored -> result);
        }).thenApply(result -> {
            if (result == DataMutateResult.SUCCESS) {
                auditTrack(actor, trackName, "Added group '" + groupName + "'");
            }
            return result;
        });
    }

    public CompletableFuture<DataMutateResult> removeGroupFromTrack(CommandSender actor, String trackName, String groupName) {
        Track track = luckPerms.getTrackManager().getTrack(trackName);
        if (track == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Track '" + trackName + "' is not loaded."));
        }
        DataMutateResult result = track.removeGroup(groupName);
        return luckPerms.getTrackManager().saveTrack(track).thenApply(ignored -> {
            if (result == DataMutateResult.SUCCESS) {
                auditTrack(actor, trackName, "Removed group '" + groupName + "'");
            }
            return result;
        });
    }

    /** Moves an existing track member to a new zero-based position, clamped to the track's current bounds. */
    public CompletableFuture<Void> moveGroupInTrack(CommandSender actor, String trackName, String groupName, int newIndex) {
        Track track = luckPerms.getTrackManager().getTrack(trackName);
        if (track == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Track '" + trackName + "' is not loaded."));
        }
        return luckPerms.getGroupManager().loadGroup(groupName).thenCompose(optionalGroup -> {
            if (optionalGroup.isEmpty()) {
                return CompletableFuture.failedFuture(new IllegalStateException("Group '" + groupName + "' does not exist."));
            }
            track.removeGroup(groupName);
            int clampedIndex = Math.max(0, Math.min(newIndex, track.getGroups().size()));
            track.insertGroup(optionalGroup.get(), clampedIndex);
            return luckPerms.getTrackManager().saveTrack(track);
        }).thenRun(() -> auditTrack(actor, trackName, "Reordered group '" + groupName + "' to position " + (newIndex + 1)));
    }

    // ================================================================================
    // System
    // ================================================================================

    /**
     * Runs a network-wide permission sync: a local {@link LuckPerms#runUpdateTask()} followed by
     * {@link MessagingService#pushUpdate()} if one is configured, so other servers on the network
     * pick up the change too. On a single, non-networked server this just runs the local update
     * task - {@link #hasMessagingService()} tells callers whether the network half will happen at
     * all, so they can warn the operator if it won't.
     */
    public CompletableFuture<Void> synchronizeNetwork() {
        return luckPerms.runUpdateTask().thenRun(() ->
                luckPerms.getMessagingService().ifPresent(MessagingService::pushUpdate));
    }

    public boolean hasMessagingService() {
        return luckPerms.getMessagingService().isPresent();
    }

    /** Fetches the complete LuckPerms action log. There is no server-side filter or pagination API - callers must do that themselves. */
    public CompletableFuture<ActionLog> fetchActionLog() {
        return luckPerms.getActionLogger().getLog();
    }

    /**
     * Invalidates cached permission/meta data for every user and group currently resident in
     * memory on this server, forcing them to be recalculated on next access. This is the closest
     * real equivalent to a "purge permission cache" operation - the public API has no single
     * call that purges literally everything, and there is no "flush storage" operation exposed
     * at all (LuckPerms persists on every mutation already; there is nothing to flush).
     */
    public int purgeLocalCaches() {
        int count = 0;
        for (User user : luckPerms.getUserManager().getLoadedUsers()) {
            user.getCachedData().invalidate();
            count++;
        }
        for (Group group : luckPerms.getGroupManager().getLoadedGroups()) {
            group.getCachedData().invalidate();
            count++;
        }
        return count;
    }

    // ================================================================================
    // Shared helpers
    // ================================================================================

    private Node buildPermissionNode(String permission, boolean value, Duration expiry, String contextKey, String contextValue) {
        NodeBuilder<?, ?> builder = Node.builder(permission).value(value);
        if (expiry != null) {
            builder = builder.expiry(expiry);
        }
        if (contextKey != null && !contextKey.isBlank()) {
            builder = builder.withContext(contextKey, contextValue);
        }
        return builder.build();
    }

    private java.util.function.Predicate<Node> metaKeyPredicate(String key) {
        return node -> NodeType.META.matches(node) && NodeType.META.cast(node).getMetaKey().equalsIgnoreCase(key);
    }

    private String describeExtras(Duration expiry, String contextKey, String contextValue) {
        StringBuilder builder = new StringBuilder();
        if (expiry != null) {
            builder.append(" for ").append(formatDuration(expiry));
        }
        if (contextKey != null && !contextKey.isBlank()) {
            builder.append(" in ").append(contextKey).append('=').append(contextValue);
        }
        return builder.toString();
    }

    private String describeTransition(Optional<String> from, Optional<String> to) {
        StringBuilder builder = new StringBuilder();
        from.ifPresent(group -> builder.append(" from '").append(group).append('\''));
        to.ifPresent(group -> builder.append(" to '").append(group).append('\''));
        return builder.toString();
    }

    private String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds % 86400 == 0) {
            return (seconds / 86400) + "d";
        }
        if (seconds % 3600 == 0) {
            return (seconds / 3600) + "h";
        }
        if (seconds % 60 == 0) {
            return (seconds / 60) + "m";
        }
        return seconds + "s";
    }

    /** Submits an entry to the LuckPerms action log, attributing it to {@code actor}, for a user-targeted change. */
    private void auditUser(CommandSender actor, UUID targetId, String targetName, String description) {
        submitAudit(actor, builder -> builder.targetType(Action.Target.Type.USER).target(targetId).targetName(targetName), description);
    }

    /** Submits an entry to the LuckPerms action log, attributing it to {@code actor}, for a group-targeted change. */
    private void auditGroup(CommandSender actor, String groupName, String description) {
        submitAudit(actor, builder -> builder.targetType(Action.Target.Type.GROUP).targetName(groupName), description);
    }

    /** Submits an entry to the LuckPerms action log, attributing it to {@code actor}, for a track-targeted change. */
    private void auditTrack(CommandSender actor, String trackName, String description) {
        submitAudit(actor, builder -> builder.targetType(Action.Target.Type.TRACK).targetName(trackName), description);
    }

    private void submitAudit(CommandSender actor, java.util.function.UnaryOperator<Action.Builder> targetConfigurer, String description) {
        UUID sourceId = (actor instanceof Player player) ? player.getUniqueId() : CONSOLE_UUID;
        Action.Builder builder = luckPerms.getActionLogger().actionBuilder()
                .timestamp(Instant.now())
                .source(sourceId)
                .sourceName(actor.getName());
        builder = targetConfigurer.apply(builder);
        Action action = builder.description(description).build();
        luckPerms.getActionLogger().submit(action).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                throwable.printStackTrace();
            }
        });
    }
}
