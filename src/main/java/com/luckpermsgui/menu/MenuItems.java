package com.luckpermsgui.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared MiniMessage/item-building helpers used by every menu.
 *
 * <p>{@link #render(String, TagResolver...)} and everything built on it is the only place in this
 * plugin's menu code that should touch {@link MiniMessage} directly. Every caller MUST pass any
 * data that did not originate as a literal string in this codebase (player names, permission
 * keys, group names, chat input, ...) through a {@link TagResolver} such as
 * {@code Placeholder.unparsed(key, value)} - never by concatenating it directly into a template
 * string. {@code unparsed} substitutes the value as inert text after parsing, so a value
 * containing something that looks like a MiniMessage tag (e.g. a permission node someone named
 * {@code <red>fake}) can never inject formatting or markup.
 */
public final class MenuItems {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private MenuItems() {
    }

    public static Component render(String template, TagResolver... resolvers) {
        return MINI.deserialize(template, resolvers).decoration(TextDecoration.ITALIC, false);
    }

    public static ItemStack item(Material material, String nameTemplate, List<String> loreTemplates, TagResolver... resolvers) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(render(nameTemplate, resolvers));
        List<Component> lore = new ArrayList<>(loreTemplates.size());
        for (String line : loreTemplates) {
            lore.add(render(line, resolvers));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * Like {@link #item}, but for the case where each lore line needs its own distinct set of
     * substitutions (e.g. one context key/value pair per line) rather than one shared resolver
     * set for the whole item. Build each line with {@link #render(String, TagResolver...)} at the
     * call site and pass the finished {@link Component}s here.
     */
    public static ItemStack itemWithLore(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    public static ItemStack filler() {
        return item(Material.GRAY_STAINED_GLASS_PANE, "<gray> ", List.of());
    }

    public static ItemStack backButton() {
        return item(Material.ARROW, "<yellow><bold>Back to Previous Menu</bold></yellow>",
                List.of("<gray>Click to go back</gray>"));
    }

    public static ItemStack exitButton() {
        return item(Material.BARRIER, "<red><bold>Exit Menu</bold></red>",
                List.of("<gray>Click to close</gray>"));
    }

    public static ItemStack prevPageButton() {
        return item(Material.ARROW, "<gold>Previous Page</gold>", List.of());
    }

    public static ItemStack nextPageButton() {
        return item(Material.ARROW, "<gold>Next Page</gold>", List.of());
    }

    public static ItemStack pageIndicator(int page, int totalPages) {
        return item(Material.PAPER, "<yellow>Page <page> of <total></yellow>", List.of(),
                Placeholder.unparsed("page", String.valueOf(page)),
                Placeholder.unparsed("total", String.valueOf(totalPages)));
    }

    public static Component error(String template, TagResolver... resolvers) {
        return render("<red>" + template, resolvers);
    }

    public static Component success(String template, TagResolver... resolvers) {
        return render("<green>" + template, resolvers);
    }

    public static Component info(String template, TagResolver... resolvers) {
        return render("<yellow>" + template, resolvers);
    }

    /** Unwraps {@link java.util.concurrent.CompletionException} wrapping applied by CompletableFuture chains. */
    public static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
