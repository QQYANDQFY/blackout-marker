# Blackout Marker

Draws fixed-pixel, pure-black, screen-space squares over things you choose in Minecraft 26.3.

**Fabric 26.3 · client-side · Fabric API required · Mod Menu optional**

## AI disclosure

This project was developed with AI assistance. The maintainer reviewed, compiled, and tested the behavior before release. AI output is provided without warranty.

## Features

- Target entities by **entity type**, **scoreboard tag**, or **manual selection**.
- Target blocks by **block type** or **manual selection**.
- Any-part / center visibility modes for entities and blocks.
- Global and per-target square sizes, visibility modes, and hide/show-when-occluded behavior.
- Multiple **fixed world-direction** markers, each with its own size style.
- **Sun** and **moon** markers, each with its own size style.
- Native settings screen (press `B`); Mod Menu config entry is optional.
- No shaderpack required. Do not enable the retired BlackoutMarker shaderpack at the same time.

## Requirements

- Minecraft **26.3**
- Fabric Loader **0.19.5+**
- Java **25**
- Fabric API

## Usage

- `B`: open settings
- `V`: add the looked-at entity/block as a manual target
- `Shift+V`: remove the looked-at manual target

Settings highlight:

- **General**: global enable, entity/block visibility modes, occlusion behavior, scan radius, global size.
- **Direction**: add multiple current-facing world directions; select a marker and click **Style** to set its size.
- **Special**: toggle sun/moon squares and set their individual sizes.
- **Tags / Entity Types / Block Types / Manual Targets**: select an entry and click **Style** for per-target size, visibility mode, and occlusion override. Blank/Inherit uses the global value.

## License and disclosure

Licensed under **GNU Lesser General Public License v3.0 only** (`LGPL-3.0-only`). See `LICENSE`, `COPYING`, and `NOTICE`.

AI disclosure: developed with AI assistance; human-reviewed, compiled, and tested. Provided without warranty.

## Links

- Modrinth: [blackout-marker](https://modrinth.com/project/blackout-marker)
- GitHub: [QQYANDQFY/blackout-marker](https://github.com/QQYANDQFY/blackout-marker)
