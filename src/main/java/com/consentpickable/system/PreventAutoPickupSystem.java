package com.consentpickable.system;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.modules.entity.component.PropComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.item.PickupItemComponent;
import com.hypixel.hytale.server.core.modules.entity.item.PreventPickup;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Ensures that all dropped item entities in the world are tagged with PreventPickup.INSTANCE,
 * preventing vanilla PlayerItemEntityPickupSystem from automatically picking them up.
 */
public final class PreventAutoPickupSystem extends HolderSystem<EntityStore> {

    private static final Query<EntityStore> QUERY = Query.and(
            ItemComponent.getComponentType(),
            TransformComponent.getComponentType(),
            Query.not(PickupItemComponent.getComponentType()),
            Query.not(PropComponent.getComponentType())
    );

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
    public void onEntityAdd(@Nonnull final Holder<EntityStore> holder,
                            @Nonnull final AddReason reason,
                            @Nonnull final Store<EntityStore> store) {
        final var archetype = holder.getArchetype();
        if (archetype == null || !archetype.contains(PreventPickup.getComponentType())) {
            holder.addComponent(PreventPickup.getComponentType(), PreventPickup.INSTANCE);
        }
    }

    @Override
    public void onEntityRemoved(@Nonnull final Holder<EntityStore> holder,
                                @Nonnull final RemoveReason reason,
                                @Nonnull final Store<EntityStore> store) {
    }

    /**
     * Companion RefSystem to ensure any pre-existing or dynamically loaded item entities
     * also receive PreventPickup.INSTANCE.
     */
    public static final class ExistingEntitiesWatcher extends RefSystem<EntityStore> {

        private static final Query<EntityStore> QUERY = Query.and(
                ItemComponent.getComponentType(),
                TransformComponent.getComponentType(),
                Query.not(PreventPickup.getComponentType()),
                Query.not(PickupItemComponent.getComponentType()),
                Query.not(PropComponent.getComponentType())
        );

        @Nonnull
        @Override
        public Query<EntityStore> getQuery() {
            return QUERY;
        }

        @Override
        public void onEntityAdded(@Nonnull final Ref<EntityStore> ref,
                                  @Nonnull final AddReason reason,
                                  @Nonnull final Store<EntityStore> store,
                                  @Nonnull final CommandBuffer<EntityStore> commandBuffer) {
            commandBuffer.addComponent(ref, PreventPickup.getComponentType(), PreventPickup.INSTANCE);
        }

        @Override
        public void onEntityRemove(@Nonnull final Ref<EntityStore> ref,
                                   @Nonnull final RemoveReason reason,
                                   @Nonnull final Store<EntityStore> store,
                                   @Nonnull final CommandBuffer<EntityStore> commandBuffer) {
        }
    }
}
