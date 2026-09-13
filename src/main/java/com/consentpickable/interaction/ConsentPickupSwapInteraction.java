package com.consentpickable.interaction;

import com.consentpickable.service.PickupService;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Custom SimpleInstantInteraction triggered when the player completes a 0.5-second hold.
 * Swaps the player's held item in their active hotbar slot with the targeted ground item.
 * The previously held item is safely dropped onto the ground (never deleted).
 */
public final class ConsentPickupSwapInteraction extends SimpleInstantInteraction {

    public static final String TYPE_ID = "ConsentPickupSwap";

    public static final BuilderCodec<ConsentPickupSwapInteraction> CODEC = BuilderCodec.builder(
            ConsentPickupSwapInteraction.class,
            ConsentPickupSwapInteraction::new,
            SimpleInstantInteraction.CODEC
    ).documentation("ConsentPickable: swaps player's held item with targeted ground item, dropping previous held item.")
    .build();

    public ConsentPickupSwapInteraction() {
        super(TYPE_ID);
    }

    public ConsentPickupSwapInteraction(String id) {
        super(id);
    }

    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }

    @Override
    protected void firstRun(@Nonnull final InteractionType type,
                            @Nonnull final InteractionContext context,
                            @Nonnull final CooldownHandler cooldownHandler) {
        final CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        if (commandBuffer == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        final Ref<EntityStore> instigatorRef = context.getEntity();
        if (instigatorRef == null || !instigatorRef.isValid()) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        final PlayerRef playerRef = commandBuffer.getComponent(instigatorRef, PlayerRef.getComponentType());
        if (playerRef == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        final boolean swapped = PickupService.getInstance().trySwapHandItem(instigatorRef, playerRef, commandBuffer);
        if (swapped) {
            context.getState().state = InteractionState.Finished;
        } else {
            context.getState().state = InteractionState.Failed;
        }
    }

    @Override
    protected void simulateFirstRun(@Nonnull final InteractionType type,
                                    @Nonnull final InteractionContext context,
                                    @Nonnull final CooldownHandler cooldownHandler) {
    }

    @Override
    public String toString() {
        return "ConsentPickupSwapInteraction{" + super.toString() + "}";
    }
}
