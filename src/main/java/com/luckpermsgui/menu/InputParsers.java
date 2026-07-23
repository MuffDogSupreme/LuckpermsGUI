package com.luckpermsgui.menu;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small, self-contained parsers for the compact chat-input syntaxes used by the Groups/Tracks/
 * Users node editors. There is no multi-field form input available in a chest GUI, so free-text
 * entry (permission nodes with an optional value/expiry/context) is accepted as a single
 * space-separated line rather than a multi-step wizard - see {@link #parseNodeInput}.
 */
public final class InputParsers {

    private static final Pattern DURATION_PATTERN = Pattern.compile("^(\\d+)(s|m|h|d|w|mo|y)$", Pattern.CASE_INSENSITIVE);

    private InputParsers() {
    }

    /** Parses "30s", "10m", "2h", "7d", "4w", "6mo", "1y" into a {@link Duration}. Empty if the text doesn't match. */
    public static Optional<Duration> parseDuration(String text) {
        Matcher matcher = DURATION_PATTERN.matcher(text.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        long amount = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2).toLowerCase(Locale.ROOT);
        Duration duration = switch (unit) {
            case "s" -> Duration.ofSeconds(amount);
            case "m" -> Duration.ofMinutes(amount);
            case "h" -> Duration.ofHours(amount);
            case "d" -> Duration.ofDays(amount);
            case "w" -> Duration.ofDays(amount * 7);
            case "mo" -> Duration.ofDays(amount * 30);
            case "y" -> Duration.ofDays(amount * 365);
            default -> null;
        };
        return Optional.ofNullable(duration);
    }

    /** Splits "key=value" into its two halves. Empty if there's no '=', or nothing on either side of it. */
    public static Optional<String[]> parseContextPair(String text) {
        int eq = text.indexOf('=');
        if (eq <= 0 || eq == text.length() - 1) {
            return Optional.empty();
        }
        return Optional.of(new String[]{text.substring(0, eq).trim(), text.substring(eq + 1).trim()});
    }

    /**
     * Parses a compact node-entry line: {@code <key> [true|false] [duration] [context=value]}.
     * Only the key is required; the rest can appear in any order after it. Returns empty if the
     * line is blank or the key segment is missing.
     */
    public static Optional<ParsedNode> parseNodeInput(String text) {
        String[] parts = text.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isBlank()) {
            return Optional.empty();
        }

        String key = parts[0];
        boolean value = true;
        Duration expiry = null;
        String contextKey = null;
        String contextValue = null;

        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (part.equalsIgnoreCase("true") || part.equalsIgnoreCase("false")) {
                value = Boolean.parseBoolean(part);
                continue;
            }
            Optional<String[]> pair = parseContextPair(part);
            if (pair.isPresent()) {
                contextKey = pair.get()[0];
                contextValue = pair.get()[1];
                continue;
            }
            Optional<Duration> duration = parseDuration(part);
            if (duration.isPresent()) {
                expiry = duration.get();
            }
        }

        return Optional.of(new ParsedNode(key, value, expiry, contextKey, contextValue));
    }

    public record ParsedNode(String key, boolean value, Duration expiry, String contextKey, String contextValue) {
    }
}
