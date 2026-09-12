package com.consentpickable;

import com.consentpickable.interaction.ConsentPickupUseInteraction;
import com.consentpickable.service.PickupService;
import com.consentpickable.system.PlayerAimTargetingSystem;
import com.consentpickable.system.PlayerWalkOverPickupSystem;
import com.consentpickable.system.PreventAutoPickupSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;

/**
 * Entry point for Hytale-ConsentPickable plugin.
 * Disables automatic walk-over item pickup for new items while selectively allowing
 * walk-over pickup to top off existing incomplete stacks. Enforces explicit, aim-targeted
 * pickup for everything else.
 */
public class ConsentPickablePlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public ConsentPickablePlugin(@Nonnull final JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("Initializing ConsentPickable mod...");

        // Register custom Use interaction codec
        getCodecRegistry(Interaction.CODEC).register(
                ConsentPickupUseInteraction.TYPE_ID,
                ConsentPickupUseInteraction.class,
                ConsentPickupUseInteraction.CODEC
        );

        // Pre-load Interaction and RootInteraction assets into the engine
        Interaction.getAssetStore().loadAssets(
                com.hypixel.hytale.assetstore.map.DefaultAssetMap.DEFAULT_PACK_KEY,
                java.util.List.of(new ConsentPickupUseInteraction(ConsentPickupUseInteraction.TYPE_ID))
        );
        ConsentPickupUseInteraction.ROOT_INTERACTION.build();
        RootInteraction.getAssetStore().loadAssets(
                com.hypixel.hytale.assetstore.map.DefaultAssetMap.DEFAULT_PACK_KEY,
                java.util.List.of(ConsentPickupUseInteraction.ROOT_INTERACTION)
        );

        // Register ECS systems to prevent automatic pickup, allow selective stack top-off, and track player aim
        getEntityStoreRegistry().registerSystem(new PreventAutoPickupSystem());
        getEntityStoreRegistry().registerSystem(new PreventAutoPickupSystem.ExistingEntitiesWatcher());
        getEntityStoreRegistry().registerSystem(new PlayerWalkOverPickupSystem());
        getEntityStoreRegistry().registerSystem(new PlayerAimTargetingSystem());


        // Register disconnect listener to clean up player sessions
        getEventRegistry().register(PlayerDisconnectEvent.class, event -> {
            if (event.getPlayerRef() != null) {
                PickupService.getInstance().removeSession(event.getPlayerRef().getUuid());
            }
        });

        LOGGER.atInfo().log("ConsentPickable initialized successfully!");
    }
}