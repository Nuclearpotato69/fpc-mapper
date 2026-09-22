# FPC Mapper

[![Build](https://github.com/Nuclearpotato69/fpc-mapper/actions/workflows/build.yml/badge.svg)](https://github.com/Nuclearpotato69/fpc-mapper/actions/workflows/build.yml)
[![Latest release](https://img.shields.io/github/v/release/Nuclearpotato69/fpc-mapper)](https://github.com/Nuclearpotato69/fpc-mapper/releases)
[![Discord](https://img.shields.io/badge/Discord-join-5865F2?logo=discord&logoColor=white)](https://discord.gg/gCxWmzzVP8)

A Fabric client mod that logs the terrain you walk through on [6b6t.org](https://6b6t.org) and
uploads it to build a shared map of the server. No overlay, no ESP, no gameplay changes — it just
records chunks in the background as you explore, and everyone's client feeds the same map.

Built and maintained by Punchy, for the Fruit Punch Collective.

## Range

Capture is hard-capped at **500,000 blocks in every direction from (0, 0)** — a 1,000,000 ×
1,000,000 block box centered on spawn. Nothing outside that box is ever read or sent, and it only
runs at all while you're on 6b6t, on hard difficulty, in the overworld. You can set a smaller
radius in `fpcmapper.json` (Fabric config folder, generated on first launch), but you can't raise
it past the 500k cap.

## Other than that

- Uploads run on background threads with retry — won't touch your framerate.
- `'` (apostrophe) toggles an in-game HUD: chunks sent this session, total sent, queue depth.
- Only terrain/block data for chunks you loaded. Nothing about other players.

## Install

Grab the jar for your version from [Releases](../../releases), drop it in `mods/` next to
[Fabric API](https://modrinth.com/mod/fabric-api).

| Minecraft | Java |
|:--|:--|
| 1.21.11 | 21 |
| 26.2 | 25 |

## Build

```
common/       shared logic — both versions compile this in directly
mc1_21_11/    1.21.11-specific glue
mc26_2/       26.2-specific glue
```

```sh
./gradlew buildAll          # both versions
./gradlew :mc1_21_11:build  # or just one
./gradlew :mc26_2:build
```

Jars land in `<module>/build/libs/`. Every push runs both builds in CI; pushing a `vX.Y.Z` tag
builds and publishes a release automatically.

## Contributing

- Version-specific code goes in `mc1_21_11/` or `mc26_2/`, kept in sync in shape; shared stuff
  goes in `common/`.
- No explanatory comments in this codebase — keep names/structure self-explanatory instead.
- No LICENSE file yet, so default copyright applies. Ask in Discord before sending a big PR.
