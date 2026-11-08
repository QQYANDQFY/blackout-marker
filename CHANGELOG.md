# Changelog

## 1.0.0 - 2026-11-08

Initial release.

### Features

- Client-side black squares over selected entities and blocks.
- Entity-type, block-type, scoreboard-tag, and manual target selection.
- Any-part / center visibility modes, with a global default and per-target overrides.
- Global and per-target square sizes, with blank values inheriting the global size.
- Optional hide/show-when-occluded behavior, globally and per target.
- Multiple fixed world-direction markers, each with its own size style.
- Sun and moon markers, each with its own size style.
- Vanilla-style configuration UI; Mod Menu support is optional.
- Native 26.3 rendering through a Mixin after post-processing; no shaderpack required.

### Fixes and limitations

- Fully occluded targets are hidden by default using CPU-side block ray checks.
- Targets behind the camera are rejected before projection, avoiding mirrored duplicate squares.
- Entity occlusion uses axis-aligned collision boxes, not exact model geometry.
- Transparent blocks are treated as occluders for visibility checks.

### Disclosure

This project was developed with AI assistance. The maintainer reviewed, compiled, and tested the behavior before release. AI output is provided without warranty.

### License

LGPL-3.0-only. See `LICENSE`, `COPYING`, and `NOTICE`.
