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

import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import org.jspecify.annotations.NonNull;

/**
 * Custom SimpleInstantInteraction inserted into the unarmed Use chain in Empty.json.
 * Triggered when the player presses the Use key. If the player is targeting a dropped item,
 * picks it up and marks the interaction Finished. If not, marks it Failed so subsequent
 * interactions (BreakBlock, Harvest, etc.) proceed as normal.
 */
public final class ConsentPickupUseInteraction extends SimpleInstantInteraction {

    public static final String TYPE_ID = "ConsentPickupUse";
    public static final String ROOT_ID = "ConsentPickupUseRoot";
    public static final RootInteraction ROOT_INTERACTION = new RootInteraction(ROOT_ID, "ConsentPickupCharge");

    public static final BuilderCodec<ConsentPickupUseInteraction> CODEC = BuilderCodec.builder(
            ConsentPickupUseInteraction.class,
            ConsentPickupUseInteraction::new,
            SimpleInstantInteraction.CODEC
    ).documentation("ConsentPickable: picks up the targeted item. Fails when nothing is targeted so the rest of the chain runs.")
    .build();

    public ConsentPickupUseInteraction() {
        super(TYPE_ID);
    }

    public ConsentPickupUseInteraction(String id) {
        super(id);
    }

    @Override
    public @NonNull WaitForDataFrom getWaitForDataFrom() {
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
        if (!instigatorRef.isValid()) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        final PlayerRef playerRef = commandBuffer.getComponent(instigatorRef, PlayerRef.getComponentType());
        if (playerRef == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        final boolean pickedUp = PickupService.getInstance().tryPickup(instigatorRef, playerRef, commandBuffer);
        if (pickedUp) {
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
        return "ConsentPickupUseInteraction{" + super.toString() + "}";
    }
}
