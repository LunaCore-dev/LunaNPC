package net.lunacore.luna_npc.client.screen;

import java.util.Locale;
import java.util.Optional;

// Shared numeric-field plumbing for the advanced-combat editors: one place formats a stored number for a
// field and one place turns a partially-typed field back into a value, so every editor sanitizes input the
// same way (mid-typing states like "" or "-" simply do not commit).
final class CombatText {

    private CombatText() {
    }

    static String number(float value) {
        return value == Math.rint(value)
                ? String.valueOf((long) value)
                : String.format(Locale.ROOT, "%.2f", value);
    }

    static Optional<Float> parse(String text) {
        try {
            return Optional.of(Float.parseFloat(text));
        } catch (NumberFormatException incomplete) {
            return Optional.empty();
        }
    }
}
