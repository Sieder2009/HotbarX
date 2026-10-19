# HotbarX

Crimson Obsidian UI: a dark crimson and obsidian-black GUI resource pack for Minecraft Java Edition 26.3. It also loads in 26.2.

- [`docs/SPEC.md`](docs/SPEC.md): design specification (palette, exact texture geometry, engine rules, acceptance tests)
- [`docs/PROMPT.md`](docs/PROMPT.md): production brief and concept-art prompts
- [`pack/`](pack): the resource pack.
  - Textures are generated.
  - `pack.mcmeta`, the lang file and the two shader copies are hand-written: the 26.3 base, and the 26.2 copy in the `mc_26_2` overlay.
- [`tools/BuildTextures.java`](tools/BuildTextures.java): generates the textures from a 26.3 (or 26.2) client jar
- [`tools/Test-Palette.ps1`](tools/Test-Palette.ps1): fails on any gold, yellow or bronze pixel

## Build

Needs Java 17+ and a Minecraft 26.3 or 26.2 client jar; both produce identical textures. Without an argument, the generator looks in `.minecraft` and the Modrinth App, newest version first.

```
java tools/BuildTextures.java [path\to\client.jar]
powershell -ExecutionPolicy Bypass -File tools\Test-Palette.ps1 pack -Strict
jar --create --no-manifest --file dist\HotbarX.zip -C pack .
```

## Install

Copy `dist/HotbarX.zip` into your instance's `resourcepacks` folder. In-game, enable it under Options → Resource Packs and move it to the top.
