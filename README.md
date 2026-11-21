<img src="src/main/resources/logo.png" alt="Waifing Skilltree logo" width="128">

# Waifing Skilltree

A data-driven skill tree framework for Minecraft Forge 1.20.1, inspired by Passive Skill Trees and Path of Exile.

Waifing Skilltree adds a full-screen skill tree where players spend skill points on nodes that grant attributes, unlock recipes, run commands and more. The mod itself contains no tree: every tree is defined in a datapack, so pack makers and server owners decide exactly what their players can learn.

## How it works

- Players open the tree with a key (**U** by default) and spend skill points on connected nodes.
- Skill points are bought with experience levels. Each purchase costs one level more than the previous one. Free points per level, starting points and a purchase limit can be set in the config.
- Nodes can have several levels, and their bonuses grow with each level.
- Trees are loaded from datapacks and reload with `/reload`.

## Node rewards

- Attributes from vanilla or any other mod, active all the time or only under a condition.
- Recipes added to the recipe book, or recipes that can only be crafted by players who own the node.
- Right-click recipes: hold an item on the cursor and right-click another one in the inventory.
- Commands that run when a level is gained or refunded.
- Commands that stay locked until the player owns a specific node.

## Conditions

An attribute bonus can depend on what the player is doing, for example:

- the item held in the main hand or off hand (single items or tags);
- worn armor pieces;
- health, hunger, light level, height, time of day, weather, biome or dimension;
- sneaking, sprinting, being in water or on fire;
- active beneficial or harmful effects;
- enchanted weapons or armor (Tinkers' Construct modifiers count as enchantments);
- Better Combat weapon categories, two-handed weapons and dual wielding;
- Curios slots.

Conditions that depend on another mod are simply inactive when that mod is not installed.

## Tree design tools

- **Requirements:** advancements, attribute values, items, or owning other nodes.
- **Choice links:** from one node, only one of several paths can be taken. The number of picks can be raised by other nodes, shared across groups of choices or start locked at zero.
- **Limited groups:** allow only a set number of nodes from a group, for example two keystones out of four.
- **Level caps:** higher levels of a node can require other nodes first.
- **Exclusive trees** for class systems, with nodes that unlock other trees.
- **Links:** one-way links, curved links and long-distance links.

## Player experience

- Smooth zoom and panning, search by node name or attribute, and highlighted paths.
- Detailed tooltips showing bonuses, conditions, requirements and why a node is locked.
- Optional respec item and admin commands (`/skilltree respec`, `/skilltree points`).
- Optional hardcore mode: dying resets the whole tree.

## Tree editor

Trees are created with a free web-based editor that runs in the browser, with no installation required. It lets you place and connect nodes visually, edit attributes and conditions, preview the tree as a player would see it, check for errors and export the datapack directly.

Editor: [link to the editor]

## Datapacks

This mod does not include a skill tree. Install a datapack that provides one, or create your own with the editor.

Official tree: [link to the Waifing Skilltree datapack]

## Compatibility

Optional integrations, none of them required:

- **Apothic Attributes:** shows the skill tree as the source of its bonuses in the attributes screen.
- **Better Combat:** weapon category conditions.
- **Curios API:** curio slot conditions.
- **Tinkers' Construct:** tool modifiers count as enchantments.
- Attributes from any mod can be used in trees.

## Datapack format

```
data/<namespace>/waifingskilltree/trees/<name>.json
data/<namespace>/waifingskilltree/click_recipes/<name>.json
data/<namespace>/recipes/<name>.json
```

Every field, condition and config option is described in [docs/datapack-format.md](docs/datapack-format.md).

## Building

Requires JDK 17.

1. Download `ApothicAttributes-1.20.1-1.3.7.jar` from CurseForge and place it in `libs/` (compile-only, not bundled).
2. Run `./gradlew build`. The mod is created in `build/libs/`.

`./gradlew runClient` starts a test client.

## License

Copyright (C) 2026 Dankest0. Released under the [GNU General Public License v3.0](LICENSE) or later.
