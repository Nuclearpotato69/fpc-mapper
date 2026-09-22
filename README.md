# FPC Mapper

A Fabric client mod that quietly logs the chunks you walk through on [6b6t.org](https://6b6t.org)
and ships them off to build a shared map of the server. No client-side rendering, no overlay
telling you where bases are — it just records terrain as you explore it and uploads it in the
background. The community map gets built from everyone's combined legwork.

Built and maintained by Punchy, for the Fruit Punch Collective.

## What it actually does

- While you're connected to 6b6t (hard difficulty, overworld), it captures the chunk around you
  and queues it for upload.
- Uploads happen on background threads so it doesn't touch your framerate; anything that fails
  gets retried.
- Press `'` (apostrophe) in-game to toggle a small HUD showing chunks sent this session, total
  sent, and how many are still queued.
- Everything is scoped by a configurable radius — see `fpcmapper.json` in your Fabric config
  folder after the first launch.

It does not read or expose anything about other players — just block/terrain data for chunks
you've physically loaded.

## Installing

Grab the jar for your Minecraft version from the [Releases](../../releases) page and drop it in
your `mods` folder alongside [Fabric API](https://modrinth.com/mod/fabric-api).

| Minecraft | Java |
|---|---|
| 1.21.11 | 21 |
| 26.2 | 25 |

## Building from source

This repo builds two separate mod jars — one per targeted Minecraft version — out of a shared
`common/` source set:

```
common/       shared logic, both versions compile it in directly
mc1_21_11/    1.21.11-specific glue (Compat, HUD rendering)
mc26_2/       26.2-specific glue
```

Build everything:

```
./gradlew buildAll
```

Or just one version:

```
./gradlew :mc1_21_11:build
./gradlew :mc26_2:build
```

Jars land in `<module>/build/libs/`. CI builds both versions on every push; tagged releases
(`vX.Y.Z`) build and publish jars automatically.

## Contributing

PRs welcome — bug fixes, version bumps, whatever. A few things that'll save you a round-trip:

- Version-specific code goes in `mc1_21_11/` or `mc26_2/`, keeping the two in the same shape as
  each other. Anything that doesn't need to differ belongs in `common/`.
- Match the existing style: the codebase doesn't carry explanatory comments, so keep names and
  structure self-explanatory instead of narrating what a block of code does.
- There's currently no LICENSE file, which means default copyright applies — if you want to
  contribute something substantial, ask first so we're on the same page about terms.

## Questions / bugs / just hanging out

[discord.gg/gCxWmzzVP8](https://discord.gg/gCxWmzzVP8)
