# Pretty Simple NPCs

Status: Work in Progress (WIP)

Fabric mod for Minecraft 1.20.1 implementing interactive NPCs, complex branching dialogue trees, 3D navigation mesh pathfinding, and a trading system.

## Dependencies

- Minecraft 1.20.1
- Fabric Loader >= 0.15.0
- Fabric API
- PolyNav (>= 0.1.0): https://github.com/neuror1ston/PolyNav
- Java 17

PolyNav is not embedded (JiJ) into this jar and must be installed alongside Pretty Simple NPCs in your mods folder.

## Features

- NPC management via in-game Admin GUI (/npc or NPC Wand).
- Narrative dialogue interface with typography scaling, conversation history transcript, and action triggers.
- Visual node graph editor and writing canvas for authoring dialogue trees.
- Dialogue action processor: console/player commands, item exchange, sound playback, SQLite flags, and trade transitions.
- Integrated 3D NavMesh navigation via PolyNav (baking, jump links, door traversal, patrol loops).
- Currency and economy trading matrix (silver and gold nuggets, player purchase limits, reset cooldowns).
- Ambient idle barks with configurable radius and intervals.

## Building from Source

```bash
git clone https://github.com/neuror1ston/PrettySimpleNPCs.git
cd PrettySimpleNPCs
./gradlew build
```

The compiled mod jar will be located in build/libs/.

## License

This project is distributed under a Fair Source License.
- Non-commercial forks and usage on non-commercial servers are permitted.
- Content creators are explicitly allowed to stream and record videos/storylines created with this mod, including commercial and monetized videos on YouTube, Twitch, etc.
- Commercial usage (commercial/monetized servers, commercial forks, paid distribution) requires prior written consent from the author.

See LICENSE for the full license text.

## Contact

Email: anar1ston@proton.me
