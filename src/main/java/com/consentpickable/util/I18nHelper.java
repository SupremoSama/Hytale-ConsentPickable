package com.consentpickable.util;

import com.hypixel.hytale.server.core.modules.i18n.I18nModule;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Helper utility for querying Hytale's native I18nModule with robust fallback handling.
 */
public final class I18nHelper {

    private I18nHelper() {
    }

    /**
     * Resolves a localized string from Hytale's I18nModule for the player's language,
     * falling back to en-US if available, or to the provided default fallback string.
     *
     * @param language        The player's language code (e.g. "pt-BR", "en-US")
     * @param key             The translation key (e.g. "consentpickable.action.pickup", "itemquality.rare")
     * @param defaultFallback Default fallback if translation is not found
     * @return The localized message
     */
    @Nonnull
    public static String getOrFallback(@Nullable final String language,
                                       @Nonnull final String key,
                                       @Nonnull final String defaultFallback) {
        final I18nModule i18n = I18nModule.get();
        if (i18n != null) {
            try {
                final String value = i18n.getMessage(language, key);
                if (value != null && !value.isBlank() && !value.equals(key)) {
                    return value;
                }
                if (language != null && !"en-US".equalsIgnoreCase(language)) {
                    final String enValue = i18n.getMessage("en-US", key);
                    if (enValue != null && !enValue.isBlank() && !enValue.equals(key)) {
                        return enValue;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return defaultFallback;
    }

    /**
     * Resolves a localized string, automatically generating a capitalized fallback from the key if not found.
     */
    @Nonnull
    public static String getOrFallback(@Nullable final String language, @Nonnull final String key) {
        return getOrFallback(language, key, formatKeyFallback(key));
    }

    @Nonnull
    public static String formatKeyFallback(@Nonnull final String key) {
        final String[] parts = key.split("\\.");
        final String last = parts[parts.length - 1];
        if (last.isEmpty()) {
            return key;
        }
        return Character.toUpperCase(last.charAt(0)) + last.substring(1);
    }
}
