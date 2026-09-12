package com.consentpickable.service;

import com.consentpickable.interaction.ConsentPickupUseInteraction;
import com.consentpickable.session.PlayerTargetSession;
import com.consentpickable.ui.ConsentPickupHud;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service orchestrating dropped item pickups, player target sessions, and HUD prompts.
 */
public final class PickupService {

    public static final double DEFAULT_MAX_PICKUP_DIST_SQ = 6.0 * 6.0;

    private static final PickupService INSTANCE = new PickupService();

    private final Map<UUID, PlayerTargetSession> sessions = new ConcurrentHashMap<>();

    private PickupService() {
    }

    @Nonnull
    public static PickupService getInstance() {
        return INSTANCE;
    }

    @Nonnull
    public PlayerTargetSession getOrCreateSession(@Nonnull final UUID uuid) {
        return sessions.computeIfAbsent(uuid, PlayerTargetSession::new);
    }

    @Nullable
    public PlayerTargetSession getSession(@Nonnull final UUID uuid) {
        return sessions.get(uuid);
    }

    public void removeSession(@Nonnull final UUID uuid) {
        sessions.remove(uuid);
    }

    /**
     * Attempts to pick up the item currently targeted by the given player.
     *
     * @param playerEntityRef The entity ref of the player
     * @param playerRef       The PlayerRef networking component
     * @param accessor        The ComponentAccessor (Store or CommandBuffer)
     * @return true if an item was successfully picked up (fully or partially), false otherwise
     */
    public boolean tryPickup(@Nonnull final Ref<EntityStore> playerEntityRef,
                             @Nonnull final PlayerRef playerRef,
                             @Nonnull final ComponentAccessor<EntityStore> accessor) {
        if (!playerEntityRef.isValid()) {
            return false;
        }

        final var session = getSession(playerRef.getUuid());
        if (session == null || !session.hasTarget()) {
            return false;
        }

        final var itemRef = session.getTargetedItemRef();
        if (itemRef == null || !itemRef.isValid()) {
            session.clearTarget();
            hidePrompt(playerEntityRef, playerRef, accessor);
            return false;
        }

        return pickupTarget(accessor, playerEntityRef, playerRef, itemRef, DEFAULT_MAX_PICKUP_DIST_SQ);
    }

    /**
     * Executes the authoritative item pickup into the player's inventory using Hytale's native systems.
     */
    public boolean pickupTarget(@Nonnull final ComponentAccessor<EntityStore> accessor,
                                @Nonnull final Ref<EntityStore> playerEntityRef,
                                @Nonnull final PlayerRef playerRef,
                                @Nonnull final Ref<EntityStore> itemRef,
                                final double maxDistSq) {
        if (!playerEntityRef.isValid() || !itemRef.isValid()) {
            final var session = getSession(playerRef.getUuid());
            if (session != null) {
                session.clearTarget();
            }
            hidePrompt(playerEntityRef, playerRef, accessor);
            return false;
        }

        final var itemComponent = accessor.getComponent(itemRef, ItemComponent.getComponentType());
        final var itemTransform = accessor.getComponent(itemRef, TransformComponent.getComponentType());
        final var playerTransform = accessor.getComponent(playerEntityRef, TransformComponent.getComponentType());

        if (itemComponent == null || itemTransform == null || playerTransform == null) {
            final var session = getSession(playerRef.getUuid());
            if (session != null) {
                session.clearTarget();
            }
            hidePrompt(playerEntityRef, playerRef, accessor);
            return false;
        }

        final var itemStack = itemComponent.getItemStack();
        if (itemStack == null || itemStack.isEmpty()) {
            final var session = getSession(playerRef.getUuid());
            if (session != null) {
                session.clearTarget();
            }
            hidePrompt(playerEntityRef, playerRef, accessor);
            return false;
        }

        final var itemPos = itemTransform.getPosition();
        if (itemPos.distanceSquared(playerTransform.getPosition()) > maxDistSq) {
            final var session = getSession(playerRef.getUuid());
            if (session != null) {
                session.clearTarget();
            }
            hidePrompt(playerEntityRef, playerRef, accessor);
            return false;
        }

        // Give the item to the player using native inventory logic
        final ItemStackTransaction transaction = Player.giveItem(itemStack, playerEntityRef, accessor);
        final ItemStack remainder = transaction.getRemainder();

        final var session = getOrCreateSession(playerRef.getUuid());

        // Full pickup
        if (ItemStack.isEmpty(remainder)) {
            final Holder<EntityStore> pickupItemHolder = ItemComponent.generatePickedUpItem(itemStack, itemPos, accessor, playerEntityRef);
            itemComponent.setRemovedByPlayerPickup(true);

            if (accessor instanceof CommandBuffer<EntityStore> cb) {
                cb.removeEntity(itemRef, RemoveReason.REMOVE);
                if (pickupItemHolder != null) {
                    cb.addEntity(pickupItemHolder, AddReason.SPAWN);
                }
            } else if (accessor instanceof Store<EntityStore> store) {
                store.removeEntity(itemRef, RemoveReason.REMOVE);
                if (pickupItemHolder != null) {
                    store.addEntity(pickupItemHolder, AddReason.SPAWN);
                }
            }

            Player.notifyPickupItem(playerEntityRef, itemStack, itemPos, accessor);

            session.clearTarget();
            hidePrompt(playerEntityRef, playerRef, accessor);

            final var interactions = accessor.getComponent(playerEntityRef, Interactions.getComponentType());
            if (interactions != null && ConsentPickupUseInteraction.ROOT_ID.equals(interactions.getInteractionId(InteractionType.Use))) {
                interactions.removeInteractionId(InteractionType.Use);
            }
            return true;
        }

        // Inventory is completely full - no item was picked up
        if (remainder.equals(itemStack)) {
            return false;
        }

        // Partial pickup
        final int consumed = itemStack.getQuantity() - remainder.getQuantity();
        if (consumed > 0) {
            final ItemStack pickedPortion = itemStack.withQuantity(consumed);
            itemComponent.setItemStack(remainder);

            final Holder<EntityStore> pickupItemHolder = ItemComponent.generatePickedUpItem(pickedPortion, itemPos, accessor, playerEntityRef);
            Player.notifyPickupItem(playerEntityRef, pickedPortion, itemPos, accessor);

            if (pickupItemHolder != null) {
                if (accessor instanceof CommandBuffer<EntityStore> cb) {
                    cb.addEntity(pickupItemHolder, AddReason.SPAWN);
                } else if (accessor instanceof Store<EntityStore> store) {
                    store.addEntity(pickupItemHolder, AddReason.SPAWN);
                }
            }

            // Update session and prompt with remaining stack
            session.setTarget(itemRef, remainder.getItem().getId(), remainder.getQuantity());
            showPrompt(playerEntityRef, playerRef, remainder, accessor);
            return true;
        }

        return false;
    }

    /**
     * Attempts to selectively pick up a walked-over item into existing incomplete inventory stacks ONLY.
     * If the item is not already in the player's inventory, or if all existing stacks of that item
     * are already at maximum capacity, nothing is picked up.
     *
     * @param accessor        The ComponentAccessor (CommandBuffer or Store)
     * @param playerEntityRef The player entity reference
     * @param playerRef       The PlayerRef component
     * @param itemRef         The dropped item entity reference
     * @param dt              Tick delta time for pickup delay polling
     * @param maxDistSq       Maximum allowed distance squared for walk-over pickup
     * @return The quantity of items picked up into existing stacks (0 if none)
     */
    public int tryPickupIntoExistingStacks(@Nonnull final ComponentAccessor<EntityStore> accessor,
                                          @Nonnull final Ref<EntityStore> playerEntityRef,
                                          @Nonnull final PlayerRef playerRef,
                                          @Nonnull final Ref<EntityStore> itemRef,
                                          final float dt,
                                          final double maxDistSq) {
        return tryPickupIntoExistingStacks(accessor, playerEntityRef, playerRef, itemRef, dt, maxDistSq, null);
    }

    public int tryPickupIntoExistingStacks(@Nonnull final ComponentAccessor<EntityStore> accessor,
                                          @Nonnull final Ref<EntityStore> playerEntityRef,
                                          @Nonnull final PlayerRef playerRef,
                                          @Nonnull final Ref<EntityStore> itemRef,
                                          final float dt,
                                          final double maxDistSq,
                                          @Nullable final CombinedItemContainer preloadedContainer) {
        if (!playerEntityRef.isValid() || !itemRef.isValid()) {
            return 0;
        }

        final ItemComponent itemComponent = accessor.getComponent(itemRef, ItemComponent.getComponentType());
        final TransformComponent itemTransform = accessor.getComponent(itemRef, TransformComponent.getComponentType());
        final TransformComponent playerTransform = accessor.getComponent(playerEntityRef, TransformComponent.getComponentType());

        if (itemComponent == null || itemTransform == null || playerTransform == null) {
            return 0;
        }

        if (!itemComponent.pollPickupDelay(dt)) {
            return 0;
        }

        final ItemStack groundStack = itemComponent.getItemStack();
        if (groundStack == null || groundStack.isEmpty()) {
            return 0;
        }

        final var itemAsset = groundStack.getItem();
        if (itemAsset == null) {
            return 0;
        }

        final int maxStack = itemAsset.getMaxStack();
        if (maxStack <= 1) {
            return 0;
        }

        final Vector3d itemPos = itemTransform.getPosition();
        if (itemPos.distanceSquared(playerTransform.getPosition()) > maxDistSq) {
            return 0;
        }

        final CombinedItemContainer combined = preloadedContainer != null ? preloadedContainer : InventoryComponent.getCombined(
                accessor, playerEntityRef, InventoryComponent.HOTBAR_STORAGE_BACKPACK
        );
        if (combined == null) {
            return 0;
        }

        // Calculate available space in existing matching stacks that have not reached maxStack
        int availableSpace = 0;
        final short capacity = combined.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            final ItemStack slotStack = combined.getItemStack(slot);
            if (slotStack != null && !slotStack.isEmpty() && slotStack.isStackableWith(groundStack)) {
                final int spaceInSlot = maxStack - slotStack.getQuantity();
                if (spaceInSlot > 0) {
                    availableSpace += spaceInSlot;
                }
            }
        }

        if (availableSpace <= 0) {
            return 0;
        }

        final int toPickup = Math.min(availableSpace, groundStack.getQuantity());
        if (toPickup <= 0) {
            return 0;
        }

        // Insert only into existing stacks (allOrNothing=true, fullStacks=false, filter=true)
        final ItemStack portion = groundStack.withQuantity(toPickup);
        final ItemStackTransaction transaction = combined.addItemStack(portion, true, false, true);
        if (!transaction.succeeded() || !ItemStack.isEmpty(transaction.getRemainder())) {
            return 0;
        }

        final int remainingOnGround = groundStack.getQuantity() - toPickup;

        if (remainingOnGround <= 0) {
            final Holder<EntityStore> pickupHolder = ItemComponent.generatePickedUpItem(groundStack, itemPos, accessor, playerEntityRef);
            itemComponent.setRemovedByPlayerPickup(true);

            if (accessor instanceof CommandBuffer<EntityStore> cb) {
                cb.removeEntity(itemRef, RemoveReason.REMOVE);
                if (pickupHolder != null) {
                    cb.addEntity(pickupHolder, AddReason.SPAWN);
                }
            } else if (accessor instanceof Store<EntityStore> store) {
                store.removeEntity(itemRef, RemoveReason.REMOVE);
                if (pickupHolder != null) {
                    store.addEntity(pickupHolder, AddReason.SPAWN);
                }
            }

            Player.notifyPickupItem(playerEntityRef, groundStack, itemPos, accessor);

            final var session = getSession(playerRef.getUuid());
            if (session != null && itemRef.equals(session.getTargetedItemRef())) {
                session.clearTarget();
                hidePrompt(playerEntityRef, playerRef, accessor);
            }
        } else {
            final ItemStack newGroundStack = groundStack.withQuantity(remainingOnGround);
            itemComponent.setItemStack(newGroundStack);

            final Holder<EntityStore> pickupHolder = ItemComponent.generatePickedUpItem(portion, itemPos, accessor, playerEntityRef);
            Player.notifyPickupItem(playerEntityRef, portion, itemPos, accessor);

            if (pickupHolder != null) {
                if (accessor instanceof CommandBuffer<EntityStore> cb) {
                    cb.addEntity(pickupHolder, AddReason.SPAWN);
                } else if (accessor instanceof Store<EntityStore> store) {
                    store.addEntity(pickupHolder, AddReason.SPAWN);
                }
            }

            final var session = getSession(playerRef.getUuid());
            if (session != null && itemRef.equals(session.getTargetedItemRef())) {
                final String name = getSafeItemName(newGroundStack);
                session.setTarget(itemRef, name, newGroundStack.getQuantity());
                showPrompt(playerEntityRef, playerRef, newGroundStack, accessor);
            }
        }

        return toPickup;
    }

    /**
     * Safely retrieves a human-readable name string for an ItemStack, guaranteeing a non-null result.
     */
    @Nonnull
    public static String getSafeItemName(@Nonnull final ItemStack itemStack) {
        final Message displayName = itemStack.getDisplayName();
        if (displayName != null) {
            final String raw = displayName.getRawText();
            if (raw != null && !raw.trim().isEmpty()) {
                return raw;
            }
            try {
                final String ansi = displayName.getAnsiMessage();
                if (ansi != null && !ansi.trim().isEmpty()) {
                    return ansi;
                }
            } catch (Throwable ignored) {
            }
            final String msgId = displayName.getMessageId();
            if (msgId != null && !msgId.trim().isEmpty()) {
                return msgId;
            }
        }
        if (itemStack.getItem() != null && itemStack.getItem().getId() != null) {
            return itemStack.getItem().getId();
        }
        return "Item";
    }

    /**
     * Safely retrieves a Message for an ItemStack, guaranteeing a non-null result suitable for UI TextSpans.
     */
    @Nonnull
    public static Message getSafeItemDisplayName(@Nonnull final ItemStack itemStack) {
        final Message displayName = itemStack.getDisplayName();
        if (displayName != null) {
            return displayName;
        }
        return Message.raw(getSafeItemName(itemStack));
    }

    /**
     * Shows or updates the HUD prompt for the player. Reuses existing HUD instance without recreation.
     */
    public void showPrompt(@Nonnull final Ref<EntityStore> playerEntityRef,
                           @Nonnull final PlayerRef playerRef,
                           @Nonnull final ItemStack itemStack,
                           @Nonnull final ComponentAccessor<EntityStore> accessor) {
        final Player player = accessor.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) {
            return;
        }

        final Message displayName = getSafeItemDisplayName(itemStack);
        final String itemName = getSafeItemName(itemStack);
        final int itemCount = itemStack.getQuantity();

        final CustomUIHud existing = player.getHudManager().getCustomHud(ConsentPickupHud.KEY);
        if (existing instanceof ConsentPickupHud consentHud) {
            consentHud.showPrompt(displayName, itemName, itemCount);
        } else {
            final ConsentPickupHud newHud = new ConsentPickupHud(playerRef, displayName, itemName, itemCount);
            player.getHudManager().addCustomHud(playerRef, newHud);
        }
    }

    /**
     * Hides the HUD prompt from the player without destroying or re-instantiating the HUD layer.
     */
    public void hidePrompt(@Nonnull final Ref<EntityStore> playerEntityRef,
                           @Nonnull final PlayerRef playerRef,
                           @Nonnull final ComponentAccessor<EntityStore> accessor) {
        final Player player = accessor.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) {
            return;
        }
        final CustomUIHud existing = player.getHudManager().getCustomHud(ConsentPickupHud.KEY);
        if (existing instanceof ConsentPickupHud consentHud) {
            consentHud.hidePrompt();
        }
    }
}