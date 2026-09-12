package com.consentpickable.system;

import com.consentpickable.interaction.ConsentPickupUseInteraction;
import com.consentpickable.service.PickupService;
import com.consentpickable.session.PlayerTargetSession;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.function.predicate.BiIntPredicate;
import org.joml.Vector3d;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Ticking system tracking player aim in real time. Performs fast KD-tree spatial queries
 * and line-of-sight raycasts to detect dropped items within crosshair interaction range,
 * displaying or hiding the pickup HUD prompt accordingly and dynamically setting a fake 'Use'
 * key interaction override on the player entity.
 */
public final class PlayerAimTargetingSystem extends EntityTickingSystem<EntityStore> {

    public static final double MAX_REACH_DISTANCE = 5.0;

    private static final Query<EntityStore> QUERY = Query.and(
            Player.getComponentType(),
            PlayerRef.getComponentType(),
            TransformComponent.getComponentType(),
            HeadRotation.getComponentType()
    );

    private static final BiIntPredicate SOLID_BLOCK_FILTER = (blockId, fluidId) -> blockId != 0;

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
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
        if (playerEntityRef == null || !playerEntityRef.isValid() || playerRef == null) {
            return;
        }

        final Transform lookTransform = TargetUtil.getLook(playerEntityRef, store);
        final Vector3d eyePos = lookTransform.getPosition();
        final Vector3d lookDir = lookTransform.getDirection();

        final PlayerTargetSession session = PickupService.getInstance().getOrCreateSession(playerRef.getUuid());
        final long nowMs = System.currentTimeMillis();

        if (session.shouldSkipScan(eyePos, lookDir, nowMs)) {
            return;
        }
        session.updatePose(eyePos, lookDir, nowMs);

        // Fast KD-tree spatial query for dropped items in the player's immediate vicinity
        final SpatialResource<Ref<EntityStore>, EntityStore> itemSpatialResource =
                store.getResource(EntityModule.get().getItemSpatialResourceType());
        final List<Ref<EntityStore>> candidateRefs = SpatialResource.getThreadLocalReferenceList();
        itemSpatialResource.getSpatialStructure().collect(eyePos, (float) MAX_REACH_DISTANCE, candidateRefs);

        Ref<EntityStore> bestItemRef = null;
        ItemStack bestItemStack = null;
        double bestPerpDistSq = Double.MAX_VALUE;
        double bestT = 0;

        for (final Ref<EntityStore> candidateRef : candidateRefs) {
            if (candidateRef == null || !candidateRef.isValid()) {
                continue;
            }

            final ItemComponent itemComp = store.getComponent(candidateRef, ItemComponent.getComponentType());
            if (itemComp == null) {
                continue;
            }

            final ItemStack stack = itemComp.getItemStack();
            if (stack == null || stack.isEmpty()) {
                continue;
            }

            final TransformComponent itemTransform = store.getComponent(candidateRef, TransformComponent.getComponentType());
            if (itemTransform == null) {
                continue;
            }

            final Vector3d itemPos = itemTransform.getPosition();
            final double dx = itemPos.x - eyePos.x;
            final double dy = (itemPos.y + 0.25) - eyePos.y;
            final double dz = itemPos.z - eyePos.z;

            // Project onto look vector
            final double t = dx * lookDir.x + dy * lookDir.y + dz * lookDir.z;
            if (t < 0.2 || t > MAX_REACH_DISTANCE) {
                continue;
            }

            final double distSq = dx * dx + dy * dy + dz * dz;
            final double perpDistSq = distSq - (t * t);

            // Generous crosshair tolerance cone scaled slightly with distance
            final double allowedRadius = 0.35 + (0.04 * t);
            if (perpDistSq > (allowedRadius * allowedRadius)) {
                continue;
            }

            if (perpDistSq < bestPerpDistSq) {
                bestPerpDistSq = perpDistSq;
                bestItemRef = candidateRef;
                bestItemStack = stack;
                bestT = t;
            }
        }

        // Line-of-sight occlusion verification
        if (bestItemRef != null) {
            final Vector3i hitBlock = TargetUtil.getTargetBlock(
                    store.getExternalData().getWorld().getChunkStore(),
                    SOLID_BLOCK_FILTER,
                    eyePos.x, eyePos.y, eyePos.z,
                    lookDir.x, lookDir.y, lookDir.z,
                    bestT
            );
            if (hitBlock != null) {
                bestItemRef = null;
                bestItemStack = null;
            }
        }

        // Target transition & HUD updates
        if (bestItemRef != null && bestItemStack != null) {
            final Ref<EntityStore> currentTarget = session.getTargetedItemRef();
            if (!bestItemRef.equals(currentTarget) || session.getLastItemCount() != bestItemStack.getQuantity()) {
                final String name = PickupService.getSafeItemName(bestItemStack);
                session.setTarget(bestItemRef, name, bestItemStack.getQuantity());
                PickupService.getInstance().showPrompt(playerEntityRef, playerRef, bestItemStack, store);
            }

            // Fake 'Use' key: override player's Use interaction to trigger pickup
            var interactions = store.getComponent(playerEntityRef, Interactions.getComponentType());
            if (interactions == null) {
                interactions = new Interactions();
                commandBuffer.putComponent(playerEntityRef, Interactions.getComponentType(), interactions);
            }
            if (!ConsentPickupUseInteraction.ROOT_ID.equals(interactions.getInteractionId(InteractionType.Use))) {
                interactions.setInteractionId(InteractionType.Use, ConsentPickupUseInteraction.ROOT_ID);
            }
        } else {
            if (session.hasTarget()) {
                session.clearTarget();
                PickupService.getInstance().hidePrompt(playerEntityRef, playerRef, store);
            }

            // Remove fake 'Use' key when not looking at an item
            final var interactions = store.getComponent(playerEntityRef, Interactions.getComponentType());
            if (interactions != null && ConsentPickupUseInteraction.ROOT_ID.equals(interactions.getInteractionId(InteractionType.Use))) {
                interactions.removeInteractionId(InteractionType.Use);
            }
        }
    }
}
