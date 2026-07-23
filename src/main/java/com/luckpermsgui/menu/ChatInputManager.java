package com.luckpermsgui.menu;

import com.luckpermsgui.LuckpermsGUI;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Captures the next chat message from a player as free-text input for a menu (group names,
 * permission keys, meta values, search queries, ...), since chest GUIs cannot take text input on
 * their own.
 *
 * <p>{@link AsyncChatEvent} fires off the main thread. This listener only cancels the event and
 * reads {@link AsyncChatEvent#originalMessage()} inline - both safe, allocation-free operations -
 * before hopping onto the main thread via the scheduler for everything else (validation, the
 * caller's callback, any Bukkit API use). Listening at {@link EventPriority#LOWEST} ensures the
 * message is cancelled before any chat-formatting plugin broadcasts it, and
 * {@code originalMessage()} (rather than {@code message()}) guarantees we read what the player
 * actually typed, unmodified by anything else that might otherwise run first.
 */
public final class ChatInputManager implements Listener {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final LuckpermsGUI plugin;
    private final Map<UUID, PendingInput> pending = new ConcurrentHashMap<>();

    public ChatInputManager(LuckpermsGUI plugin) {
        this.plugin = plugin;
    }

    /**
     * Prompts {@code player} in chat for a line of text, closing their open inventory first.
     * {@code validator} runs on the main thread: returning {@code false} re-prompts with
     * {@code invalidMessage} and waits for another line rather than completing. Typing "cancel",
     * clicking the bundled Cancel button, or letting 30 seconds pass all invoke
     * {@code onCancelled} instead of {@code onInput} - exactly one of the two is ever called, and
     * never more than once.
     */
    public void prompt(Player player, Component promptMessage, Predicate<String> validator,
                        Component invalidMessage, Consumer<String> onInput, Runnable onCancelled) {
        UUID playerId = player.getUniqueId();
        cancelExisting(playerId, false);

        player.closeInventory();
        player.sendMessage(promptMessage.appendNewline().append(buildCancelButton(playerId)));

        BukkitTask timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PendingInput input = pending.remove(playerId);
            if (input != null) {
                player.sendMessage(MenuItems.error("Input timed out."));
                input.onCancelled.run();
            }
        }, TIMEOUT.toSeconds() * 20L);

        pending.put(playerId, new PendingInput(validator, invalidMessage, onInput, onCancelled, timeoutTask));
    }

    private Component buildCancelButton(UUID playerId) {
        ClickCallback<Audience> callback = audience -> {
            if (audience instanceof Player clicker && clicker.getUniqueId().equals(playerId)) {
                Bukkit.getScheduler().runTask(plugin, () -> cancelExisting(playerId, true));
            }
        };
        ClickEvent clickEvent = ClickEvent.callback(callback, builder -> builder.uses(1).lifetime(TIMEOUT));
        return MINI.deserialize("<red><underlined>[Cancel]</underlined></red>")
                .clickEvent(clickEvent)
                .hoverEvent(HoverEvent.showText(MINI.deserialize("<gray>Click to cancel</gray>")));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncChat(AsyncChatEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (!pending.containsKey(playerId)) {
            return;
        }
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.originalMessage());
        Bukkit.getScheduler().runTask(plugin, () -> handleInput(playerId, text));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        PendingInput input = pending.remove(event.getPlayer().getUniqueId());
        if (input != null) {
            input.timeoutTask.cancel();
        }
    }

    private void handleInput(UUID playerId, String text) {
        PendingInput input = pending.get(playerId);
        if (input == null) {
            return;
        }
        if (text.equalsIgnoreCase("cancel")) {
            cancelExisting(playerId, true);
            return;
        }
        if (!input.validator.test(text)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.sendMessage(input.invalidMessage);
            }
            return;
        }
        pending.remove(playerId);
        input.timeoutTask.cancel();
        input.onInput.accept(text);
    }

    private void cancelExisting(UUID playerId, boolean runCallback) {
        PendingInput input = pending.remove(playerId);
        if (input == null) {
            return;
        }
        input.timeoutTask.cancel();
        if (runCallback) {
            input.onCancelled.run();
        }
    }

    private record PendingInput(Predicate<String> validator, Component invalidMessage,
                                 Consumer<String> onInput, Runnable onCancelled, BukkitTask timeoutTask) {
    }
}
