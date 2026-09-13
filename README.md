# Hytale Consent Pickable

A Hytale server-side mod that disables automatic item suction / vacuum pickup and replaces it with an immersive, interactive crosshair HUD prompt.

## Features

- **No Vacuum Pickup**: Items dropped on the ground stay where they are until players explicitly interact with them.
- **Interactive HUD Prompt**: Aim at any dropped item to see its name, count, rarity tag (with official rarity colors matching the inventory), and action hints.
- **Tap to Pick Up**: Tap the Use key (F by default) to pick up items into your inventory or top up existing stacks.
- **Hold to Swap**: Hold the Use key for 0.5 seconds to instantly exchange the targeted ground item with whatever item you are currently holding in your active hotbar slot. The previous held item is dropped safely to the ground (never deleted).
- **Native Localization Support**: Built-in support for English, Portuguese (pt-BR), Spanish (es-ES), German (de-DE), and French (fr-FR), integrating directly with Hytale's native `I18nModule`.
- **Pure Server-Side**: Built using standard Hytale server plugin architecture.

## Installation

1. Download the latest Hytale-ConsentPickable-x.x.x.jar from Releases.
2. Place the JAR file into your Hytale server's mods or plugins directory.
3. Restart the server.
