package com.consentpickable.system;

import com.consentpickable.service.PickupService;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.OrderPriority;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.entity.component.PropComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Spectating;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.item.PickupItemComponent;
import com.hypixel.hytale.server.core.modules.entity.system.PlayerSpatialSystem;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Set;

/**
 * Ticking system that handles selective walk-over pickup directly on dropped items,
 * matching vanilla Hytale's exact pickup timing, delay countdown, and pickup radius.
 *
 * It counts down item pickupDelay every tick naturally. When an item is eligible for pickup,
 * it queries the player spatial index using the item's native pickupRadius.
 * If a nearby player already possesses an incomplete stack of the item in their inventory,
 * it tops off that existing stack without filling new slots or exceeding max stack capacity.
 */
public final class PlayerWalkOverPickupSystem extends EntityTickingSystem<EntityStore> {

    private static final Query<EntityStore> QUERY = Query.and(
            ItemComponent.getComponentType(),
            TransformComponent.getComponentType(),
            Query.not(Interactable.getComponentType()),
            Query.not(PickupItemComponent.getComponentType()),
            Query.not(PropComponent.getComponentType())
    );

    private final Set<Dependency<EntityStore>> dependencies = Set.of(
            new SystemDependency<>(Order.AFTER, PlayerSpatialSystem.class, OrderPriority.CLOSEST)
    );

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        return false;
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        final var itemRef = archetypeChunk.getReferenceTo(index);
        if (itemRef == null || !itemRef.isValid()) {
            return;
        }

        final var itemComponent = archetypeChunk.getComponent(index, ItemComponent.getComponentType());
        if (itemComponent == null) {
            return;
        }

        // 1. Natural pickup delay countdown every tick (identical to vanilla Hytale)
        if (!itemComponent.pollPickupDelay(dt)) {
            return;
        }

        // 2. Pickup throttle to avoid scanning inventories 20x/sec if an item cannot be picked up
        if (!itemComponent.pollPickupThrottle(dt)) {
            return;
        }

        final var transformComponent = archetypeChunk.getComponent(index, TransformComponent.getComponentType());
        if (transformComponent == null) {
            return;
        }

        final var itemStack = itemComponent.getItemStack();
        if (itemStack == null || itemStack.isEmpty()) {
            return;
        }

        // Unstackable items cannot be merged into existing stacks, skip walk-over checking
        if (itemStack.getItem() == null || itemStack.getItem().getMaxStack() <= 1) {
            return;
        }

        final var itemEntityPosition = transformComponent.getPosition();
        // Exact native game autopicker distance from item/gameplay configuration
        final float pickupRadius = itemComponent.getPickupRadius(commandBuffer);

        final var playerSpatialResource = store.getResource(EntityModule.get().getPlayerSpatialResourceType());
        final var spatialStructure = playerSpatialResource.getSpatialStructure();

        final var targetPlayerRefs = SpatialResource.<EntityStore>getThreadLocalReferenceList();
        spatialStructure.ordered(itemEntityPosition, pickupRadius, targetPlayerRefs);

        final double maxDistSq = (pickupRadius + 0.25) * (pickupRadius + 0.25);

        for (final var targetPlayerRef : targetPlayerRefs) {
            if (targetPlayerRef == null || !targetPlayerRef.isValid()) {
                continue;
            }

            final var targetArchetype = store.getArchetype(targetPlayerRef);
            if (targetArchetype.contains(DeathComponent.getComponentType())
                    || targetArchetype.contains(Spectating.getComponentType())) {
                continue;
            }

            final PlayerRef playerRef = store.getComponent(targetPlayerRef, PlayerRef.getComponentType());
            if (playerRef == null) {
                continue;
            }

            final int pickedUp = PickupService.getInstance().tryPickupIntoExistingStacks(
                    commandBuffer, targetPlayerRef, playerRef, itemRef, dt, maxDistSq
            );

            if (pickedUp > 0) {
                break;
            }
        }
    }
}