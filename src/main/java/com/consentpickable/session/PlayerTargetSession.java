package com.consentpickable.session;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Tracks the targeted dropped item and movement cache for a single player.
 */
public class PlayerTargetSession {

    @Nonnull
    private final UUID playerUuid;

    @Nullable
    private Ref<EntityStore> targetedItemRef;
    private boolean promptShowing = false;

    @Nullable
    private String lastItemName;
    private int lastItemCount;

    private final Vector3d lastEyePos = new Vector3d(Double.NaN, Double.NaN, Double.NaN);
    private final Vector3d lastLookDir = new Vector3d(Double.NaN, Double.NaN, Double.NaN);
    private long lastCheckTimeMs = 0;

    public PlayerTargetSession(@Nonnull final UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    @Nonnull
    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public boolean hasTarget() {
        return targetedItemRef != null && targetedItemRef.isValid();
    }

    public boolean hasValidTarget() {
        return targetedItemRef != null && targetedItemRef.isValid();
    }

    public boolean hasTrackedTarget() {
        return targetedItemRef != null;
    }

    public boolean isPromptShowing() {
        return promptShowing;
    }

    public void setPromptShowing(final boolean promptShowing) {
        this.promptShowing = promptShowing;
    }

    @Nullable
    public Ref<EntityStore> getRawTargetedItemRef() {
        return targetedItemRef;
    }

    @Nullable
    public Ref<EntityStore> getTargetedItemRef() {
        return (targetedItemRef != null && targetedItemRef.isValid()) ? targetedItemRef : null;
    }

    public void setTarget(@Nullable final Ref<EntityStore> itemRef, @Nullable final String itemName, final int itemCount) {
        this.targetedItemRef = itemRef;
        this.lastItemName = itemName;
        this.lastItemCount = itemCount;
        this.promptShowing = itemRef != null;
    }

    public void clearTarget() {
        this.targetedItemRef = null;
        this.lastItemName = null;
        this.lastItemCount = 0;
        this.lastTargetSeenMs = 0;
        this.promptShowing = false;
    }

    public static final long TARGET_LOSS_DEBOUNCE_MS = 120;
    private long lastTargetSeenMs = 0;

    public void recordTargetSeen(final long nowMs) {
        this.lastTargetSeenMs = nowMs;
    }

    public boolean isTargetDebounceActive(final long nowMs) {
        return (nowMs - lastTargetSeenMs) < TARGET_LOSS_DEBOUNCE_MS;
    }

    @Nullable
    public String getLastItemName() {
        return lastItemName;
    }

    public static final long MIN_SCAN_INTERVAL_MS = 50;
    public static final long STATIONARY_SCAN_INTERVAL_MS = 250;
    public int getLastItemCount() {
        return lastItemCount;
    }

    public boolean shouldSkipScan(@Nonnull final Vector3d eyePos, @Nonnull final Vector3d lookDir, final long nowMs) {
        final long elapsed = nowMs - lastCheckTimeMs;
        if (elapsed < MIN_SCAN_INTERVAL_MS) {
            return true;
        }
        if (elapsed < STATIONARY_SCAN_INTERVAL_MS
                && eyePos.distanceSquared(lastEyePos) < 0.0001
                && lastLookDir.dot(lookDir) > 0.9999) {
            return true;
        }
        return false;
    }

    public void updatePose(@Nonnull final Vector3d eyePos, @Nonnull final Vector3d lookDir, final long nowMs) {
        this.lastEyePos.set(eyePos);
        this.lastLookDir.set(lookDir);
        this.lastCheckTimeMs = nowMs;
    }
}
