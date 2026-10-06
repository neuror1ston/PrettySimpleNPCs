# Pretty Simple NPCs

Fabric mod for Minecraft 1.20.1 implementing interactive NPCs, complex branching dialogue trees, 3D navigation mesh pathfinding, and a trading system.

## Features

- NPC management via in-game Admin GUI (`/npc` or NPC Wand).
- Rogue Trader-style narrative dialogue interface with typography scaling, full conversation history transcript, and action triggers.
- Visual node graph editor and full-screen writing canvas for authoring dialogue trees.
- Dialogue action processor: console/player commands, item exchange, sound playback, SQLite flags, and trade transitions.
- Integrated 3D NavMesh navigation via polynav (baking, jump links, door traversal, patrol loops).
- Currency and economy trading matrix (silver and gold nuggets, player purchase limits, reset cooldowns).
- Ambient idle barks with configurable radius and intervals.

## Requirements

- Minecraft 1.20.1
- Fabric Loader >= 0.15.0
- Fabric API
- Java 17

## Building from Source

Clone the repository and build using Gradle:

```bash
git clone https://github.com/anar1ston/PrettySimpleNPCs.git
cd PrettySimpleNPCs
./gradlew build
```

The compiled mod jar will be located in `build/libs/`.

## License

This project is distributed under a Fair Source License.
- Non-commercial forks and usage on non-commercial servers are permitted.
- Content creators are explicitly allowed to stream and record videos/storylines created with this mod, including commercial and monetized videos on YouTube, Twitch, etc.
- Commercial usage (commercial/monetized servers, commercial forks, paid distribution) requires prior written consent from the author.

See `LICENSE` for the full license text.

## Contact

Email: anar1ston@proton.me
