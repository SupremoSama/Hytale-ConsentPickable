package com.consentpickable.ui;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

/**
 * Custom HUD layer that renders the interactive "[Use] PICK UP <Item Name> xCount" prompt
 * directly under the player's crosshair.
 */
public final class ConsentPickupHud extends CustomUIHud {

    @Nonnull
    public static final String KEY = "consent_pickup";

    @Nonnull
    private static final String DOCUMENT = """
        Group #PickupPromptRoot {
          Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0);
          LayoutMode: Left;

          Group #PosLeft { FlexWeight: 500; }

          Group #Column {
            Anchor: (Width: 300);
            LayoutMode: Top;

            Group #PosTop { FlexWeight: 540; }

            Group #PickupPanel {
              Anchor: (Left: 0, Right: 0);
              LayoutMode: Left;
              Padding: (Horizontal: 12, Vertical: 8);
              Background: #10141a(0.85);

              Group #KeyCap {
                Anchor: (Width: 28, Height: 28, Right: 8);
                LayoutMode: MiddleCenter;
                Background: #222834;
                OutlineColor: #ffd75e;
                OutlineSize: 1;

                Label #ActionKey {
                  Style: (FontSize: 13, TextColor: #ffd75e, RenderBold: true, RenderUppercase: true, HorizontalAlignment: Center, VerticalAlignment: Center);
                  Text: "F";
                }
              }

              Group #InfoCol {
                FlexWeight: 1;
                LayoutMode: Top;

                Label #ActionTitle {
                  Anchor: (Left: 0, Right: 0, Height: 14);
                  Style: (FontSize: 11, TextColor: #ffd75e, RenderBold: true, RenderUppercase: true, VerticalAlignment: Center);
                  Text: "PICK UP";
                }

                Group #ItemRow {
                  Anchor: (Left: 0, Right: 0, Height: 20);
                  LayoutMode: Left;

                  Label #ItemName {
                    FlexWeight: 1;
                    Style: (FontSize: 14, TextColor: #ffffff, RenderBold: true, VerticalAlignment: Center);
                    Text: "";
                  }

                  Label #ItemCount {
                    Anchor: (Width: 40, Height: 20);
                    Style: (FontSize: 14, TextColor: #ffd75e, RenderBold: true, HorizontalAlignment: End, VerticalAlignment: Center);
                    Text: "";
                  }
                }
              }
            }

            Group #PosBottom { FlexWeight: 460; }
          }

          Group #PosRight { FlexWeight: 500; }
        }
        """;

    @Nonnull
    private Message currentDisplayName;
    @Nonnull
    private String currentItemName;
    private int currentItemCount;

    public ConsentPickupHud(@Nonnull final PlayerRef playerRef,
                            @Nonnull final Message displayName,
                            @Nonnull final String itemName,
                            final int itemCount) {
        super(playerRef, KEY, 10);
        this.currentDisplayName = displayName != null ? displayName : Message.raw(itemName != null ? itemName : "Item");
        this.currentItemName = itemName != null ? itemName : "Item";
        this.currentItemCount = itemCount;
    }

    public ConsentPickupHud(@Nonnull final PlayerRef playerRef, @Nonnull final String itemName, final int itemCount) {
        this(playerRef, Message.raw(itemName != null ? itemName : "Item"), itemName, itemCount);
    }

    @Override
    protected void build(@Nonnull final UICommandBuilder commandBuilder) {
        commandBuilder.appendInline(null, DOCUMENT);
        commandBuilder.set("#ItemName.TextSpans", currentDisplayName);
        commandBuilder.set("#ItemCount.Text", currentItemCount > 1 ? ("x" + currentItemCount) : "");
    }

    public void updateContent(@Nonnull final Message displayName,
                              @Nonnull final String itemName,
                              final int itemCount) {
        final Message safeMsg = displayName != null ? displayName : Message.raw(itemName != null ? itemName : "Item");
        final String safeName = itemName != null ? itemName : "Item";
        if (this.currentItemName.equals(safeName) && this.currentItemCount == itemCount) {
            return;
        }
        this.currentDisplayName = safeMsg;
        this.currentItemName = safeName;
        this.currentItemCount = itemCount;

        final var cmd = new UICommandBuilder();
        cmd.set("#ItemName.TextSpans", safeMsg);
        cmd.set("#ItemCount.Text", itemCount > 1 ? ("x" + itemCount) : "");
        update(false, cmd);
    }

    public void updateContent(@Nonnull final String itemName, final int itemCount) {
        updateContent(Message.raw(itemName != null ? itemName : "Item"), itemName, itemCount);
    }
}
