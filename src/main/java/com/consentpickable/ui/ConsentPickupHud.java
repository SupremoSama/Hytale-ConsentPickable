package com.consentpickable.ui;

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
    private boolean isVisible = false;

    public ConsentPickupHud(@Nonnull final PlayerRef playerRef,
                            @Nonnull final Message displayName,
                            @Nonnull final String itemName,
                            final int itemCount) {
        super(playerRef, KEY, 10);
        this.currentDisplayName = displayName != null ? displayName : Message.raw(itemName != null ? itemName : "Item");
        this.currentItemName = itemName != null ? itemName : "Item";
        this.currentItemCount = itemCount;
        this.isVisible = true;
    }

    public ConsentPickupHud(@Nonnull final PlayerRef playerRef, @Nonnull final String itemName, final int itemCount) {
        this(playerRef, Message.raw(itemName != null ? itemName : "Item"), itemName, itemCount);
    }

    public boolean isPromptVisible() {
        return isVisible;
    }

    @Nonnull
    public static String getLocalizedPickupText(@Nullable final String language) {
        if (language != null) {
            final String lower = language.toLowerCase();
            if (lower.startsWith("pt")) return "PEGAR";
            if (lower.startsWith("es")) return "RECOGER";
            if (lower.startsWith("fr")) return "RAMASSER";
            if (lower.startsWith("de")) return "AUFHEBEN";
            if (lower.startsWith("ru")) return "ПОДОБРАТЬ";
            if (lower.startsWith("zh")) return "拾取";
        }
        return "PICK UP";
    }

    @Override
    protected void build(@Nonnull final UICommandBuilder commandBuilder) {
        commandBuilder.append(UI_PATH);
        final String localizedAction = getLocalizedPickupText(getPlayerRef().getLanguage());
        commandBuilder.set("#ActionTitle.TextSpans", Message.translation("consentpickable.action.pickup"));
        commandBuilder.set("#ActionTitle.Text", localizedAction);
        commandBuilder.set("#ItemName.TextSpans", currentDisplayName);
        commandBuilder.set("#ItemCount.Text", currentItemCount > 1 ? ("x" + currentItemCount) : "");
        commandBuilder.set("#PickupPromptRoot.Visible", isVisible);
    }

    public void showPrompt(@Nonnull final Message displayName,
                           @Nonnull final String itemName,
                           final int itemCount) {
        final Message safeMsg = displayName != null ? displayName : Message.raw(itemName != null ? itemName : "Item");
        final String safeName = itemName != null ? itemName : "Item";

        if (this.isVisible && this.currentItemName.equals(safeName) && this.currentItemCount == itemCount) {
            return;
        }

        this.currentDisplayName = safeMsg;
        this.currentItemName = safeName;
        this.currentItemCount = itemCount;

        final var cmd = new UICommandBuilder();
        if (!this.isVisible) {
            this.isVisible = true;
            cmd.set("#PickupPromptRoot.Visible", true);
        }
        final String localizedAction = getLocalizedPickupText(getPlayerRef().getLanguage());
        cmd.set("#ActionTitle.TextSpans", Message.translation("consentpickable.action.pickup"));
        cmd.set("#ActionTitle.Text", localizedAction);
        cmd.set("#ItemName.TextSpans", safeMsg);
        cmd.set("#ItemCount.Text", itemCount > 1 ? ("x" + itemCount) : "");
        update(false, cmd);
    }

    public void showPrompt(@Nonnull final String itemName, final int itemCount) {
        showPrompt(Message.raw(itemName != null ? itemName : "Item"), itemName, itemCount);
    }

    public void hidePrompt() {
        if (!this.isVisible) {
            return;
        }
        this.isVisible = false;
        this.currentItemName = "";
        this.currentItemCount = 0;

        final var cmd = new UICommandBuilder();
        cmd.set("#PickupPromptRoot.Visible", false);
        update(false, cmd);
    }

    public void updateContent(@Nonnull final Message displayName,
                              @Nonnull final String itemName,
                              final int itemCount) {
        showPrompt(displayName, itemName, itemCount);
    }

    public void updateContent(@Nonnull final String itemName, final int itemCount) {
        showPrompt(Message.raw(itemName != null ? itemName : "Item"), itemName, itemCount);
    }
}
