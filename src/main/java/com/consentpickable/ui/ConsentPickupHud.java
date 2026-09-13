package com.consentpickable.ui;

import com.consentpickable.util.I18nHelper;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Custom HUD layer that renders the interactive "[Use] PICK UP <Item Name> xCount" prompt
 * directly under the player's crosshair, styled to match the native Hytale game UI.
 */
public final class ConsentPickupHud extends CustomUIHud {

    @Nonnull
    public static final String KEY = "consent_pickup";
    @Nonnull
    public static final String UI_PATH = "Hud/ConsentPickable/PickupPrompt.ui";

    @Nonnull
    private Message currentDisplayName;
    @Nonnull
    private String currentItemName;
    private int currentItemCount;
    @Nullable
    private String currentRarityKey;
    @Nullable
    private String currentRarityText;
    @Nonnull
    private String currentRarityColor;
    private boolean currentRarityVisible;
    private boolean isVisible;

    public ConsentPickupHud(@Nonnull final PlayerRef playerRef,
                            @Nonnull final Message displayName,
                            @Nonnull final String itemName,
                            final int itemCount,
                            @Nullable final String rarityKey,
                            @Nullable final String rarityText,
                            @Nullable final String rarityColor,
                            final boolean rarityVisible) {
        super(playerRef, KEY, 10);
        this.currentDisplayName = displayName;
        this.currentItemName = itemName;
        this.currentItemCount = itemCount;
        this.currentRarityKey = rarityKey;
        this.currentRarityText = rarityText;
        this.currentRarityColor = rarityColor != null && !rarityColor.isEmpty() ? rarityColor : "#ffffff";
        this.currentRarityVisible = rarityVisible;
        this.isVisible = true;
    }

    @Nonnull
    public static String getLocalizedPickupText(@Nullable final String language) {
        return I18nHelper.getOrFallback(language, "consentpickable.action.pickup", "PICK UP  •  [HOLD] SWAP");
    }

    @Override
    protected void build(@Nonnull final UICommandBuilder commandBuilder) {
        commandBuilder.append(UI_PATH);
        final String localizedAction = getLocalizedPickupText(getPlayerRef().getLanguage());
        commandBuilder.set("#ActionTitle.TextSpans", Message.translation("consentpickable.action.pickup"));
        commandBuilder.set("#ActionTitle.Text", localizedAction);
        commandBuilder.set("#ItemName.TextSpans", currentDisplayName);
        commandBuilder.set("#ItemName.Style.TextColor", currentRarityColor);
        commandBuilder.set("#ItemCount.Text", currentItemCount > 1 ? ("x" + currentItemCount) : "");
        commandBuilder.set("#ItemRarity.Visible", currentRarityVisible);
        if (currentRarityVisible) {
            if (currentRarityKey != null && !currentRarityKey.isEmpty()) {
                commandBuilder.set("#ItemRarity.TextSpans", Message.translation(currentRarityKey));
            }
            commandBuilder.set("#ItemRarity.Text", currentRarityText != null ? currentRarityText : "");
            commandBuilder.set("#ItemRarity.Style.TextColor", currentRarityColor);
        }
        commandBuilder.set("#PickupPromptRoot.Visible", isVisible);
    }

    public void showPrompt(@Nonnull final Message safeMsg,
                           @Nonnull final String safeName,
                           final int itemCount,
                           @Nullable final String rarityKey,
                           @Nullable final String rarityText,
                           @Nullable final String rarityColor,
                           final boolean rarityVisible) {
        final String safeColor = rarityColor != null && !rarityColor.isEmpty() ? rarityColor : "#ffffff";
        final String safeRarityKey = rarityKey != null ? rarityKey : "";
        final String safeRarityText = rarityText != null ? rarityText : "";

        if (this.isVisible
                && this.currentItemName.equals(safeName)
                && this.currentItemCount == itemCount
                && this.currentRarityVisible == rarityVisible
                && this.currentRarityColor.equals(safeColor)
                && (this.currentRarityKey != null ? this.currentRarityKey : "").equals(safeRarityKey)) {
            return;
        }

        this.currentDisplayName = safeMsg;
        this.currentItemName = safeName;
        this.currentItemCount = itemCount;
        this.currentRarityKey = rarityKey;
        this.currentRarityText = rarityText;
        this.currentRarityColor = safeColor;
        this.currentRarityVisible = rarityVisible;

        final var cmd = new UICommandBuilder();
        if (!this.isVisible) {
            this.isVisible = true;
            cmd.set("#PickupPromptRoot.Visible", true);
        }
        final String localizedAction = getLocalizedPickupText(getPlayerRef().getLanguage());
        cmd.set("#ActionTitle.TextSpans", Message.translation("consentpickable.action.pickup"));
        cmd.set("#ActionTitle.Text", localizedAction);
        cmd.set("#ItemName.TextSpans", safeMsg);
        cmd.set("#ItemName.Style.TextColor", safeColor);
        cmd.set("#ItemCount.Text", itemCount > 1 ? ("x" + itemCount) : "");
        cmd.set("#ItemRarity.Visible", rarityVisible);
        if (rarityVisible) {
            if (rarityKey != null && !rarityKey.isEmpty()) {
                cmd.set("#ItemRarity.TextSpans", Message.translation(rarityKey));
            }
            cmd.set("#ItemRarity.Text", safeRarityText);
            cmd.set("#ItemRarity.Style.TextColor", safeColor);
        }
        update(false, cmd);
    }

    public void hidePrompt() {
        if (!this.isVisible) {
            return;
        }
        this.isVisible = false;
        this.currentItemName = "";
        this.currentItemCount = 0;
        this.currentRarityKey = null;
        this.currentRarityText = null;
        this.currentRarityColor = "#ffffff";
        this.currentRarityVisible = false;

        final var cmd = new UICommandBuilder();
        cmd.set("#PickupPromptRoot.Visible", false);
        update(false, cmd);
    }
}
