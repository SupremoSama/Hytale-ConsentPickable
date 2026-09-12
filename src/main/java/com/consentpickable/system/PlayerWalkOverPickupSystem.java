package com.consentpickable.system;

import com.consentpickable.service.PickupService;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.OrderPriority;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.system.PlayerSpatialSystem;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Set;

/**
 * Ticking system that handles selective walk-over pickup.
 * If a player walks over a dropped item and already possesses an incomplete stack
 * of that item in their inventory, it automatically tops off that existing stack
 * without filling any new slots or exceeding the maximum stack limit.
 */
public final class PlayerWalkOverPickupSystem extends EntityTickingSystem<EntityStore> {

    public static final float WALK_OVER_RADIUS = 2.0f;
    public static final double WALK_OVER_RADIUS_SQ = WALK_OVER_RADIUS * WALK_OVER_RADIUS;

    private static final Query<EntityStore> QUERY = Query.and(
            Player.getComponentType(),
            PlayerRef.getComponentType(),
            TransformComponent.getComponentType(),
            Query.not(DeathComponent.getComponentType())
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
        final Ref<EntityStore> playerEntityRef = archetypeChunk.getReferenceTo(index);
        final PlayerRef playerRef = archetypeChunk.getComponent(index, PlayerRef.getComponentType());
        final TransformComponent playerTransform = archetypeChunk.getComponent(index, TransformComponent.getComponentType());

        if (playerEntityRef == null || !playerEntityRef.isValid() || playerRef == null || playerTransform == null) {
            return;
        }

        final Vector3d playerPos = playerTransform.getPosition();

        // Perform fast KD-tree proximity query around player
        final SpatialResource<Ref<EntityStore>, EntityStore> itemSpatialResource =
                store.getResource(EntityModule.get().getItemSpatialResourceType());
        final List<Ref<EntityStore>> candidateRefs = SpatialResource.getThreadLocalReferenceList();
        itemSpatialResource.getSpatialStructure().collect(playerPos, WALK_OVER_RADIUS, candidateRefs);

        if (candidateRefs.isEmpty()) {
            return;
        }

        for (final Ref<EntityStore> itemRef : candidateRefs) {
            if (itemRef == null || !itemRef.isValid()) {
                continue;
            }
            PickupService.getInstance().tryPickupIntoExistingStacks(
                    commandBuffer, playerEntityRef, playerRef, itemRef, dt, WALK_OVER_RADIUS_SQ
            );
        }
    }
}