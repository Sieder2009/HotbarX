# HotbarX — Design prompts

There are two copy-paste blocks here:

- **Production brief:** the original request corrected for Minecraft 26.3. Hand it to a pixel artist or an AI agent that will make the actual files.
- **Concept-art prompts:** for image generators. Use them for mood and direction only. Generators don't produce pixel-accurate 176×166 textures, so [`SPEC.md`](SPEC.md) governs every file that ships.

## 1. Production brief

```text
Build a Minecraft Java Edition 26.3 resource pack (format 97.1, also loading in 26.2) called HotbarX, following docs/SPEC.md exactly.

Look: matte obsidian-black UI (#0A0A0A) with subtle blood-red borders (#990000). Vibrant crimson (#FF0000)
is used only for glow, the selected hotbar slot, hover states and lightning. Dark fantasy, clean, on the
1x vanilla pixel grid. Use only the palette tokens in SPEC.md section 4 (every colour has G = B).
No gold, yellow, orange, bronze, copper or brown pixel anywhere.

Deliver:
1. Containers (static): inventory.png, generic_54.png, shulker_box.png, crafting_table.png.
   Black frames edged by one thin crimson line that glows inward, quiet slots outlined in dark crimson,
   and jagged 1-px red lightning with one white-hot strike point per bolt: crackling along about half
   of the outer border, across the free channels between slot groups, and through the empty areas of
   the inventory and crafting table. Respect the generic_54 seam rule and the text-safe zones.
2. Container labels ("Crafting", "Inventory", "Shulker Box", ...) in crimson via the text.vsh
   recolor (SPEC 5.6).
3. Armor and shield slot placeholders: obsidian silhouettes with red outlines.
4. Hotbar sprites: matte black cells, a glowing crimson selection frame, and animated lightning
   along the top and bottom bands that links the slots through the separators. Follow the anchor
   rule so the arcs plug into the selection frame on any slot.
5. Buttons: dark slate/obsidian with crimson trim, sparks only in the 3x3 corners. Pause-menu text
   in crimson via lang ("Resume Game", "Options...").
6. Items: golden apple becomes a crimson "Blood Apple" (texture + red name). Potion bottles and
   the eye of ender crackle with red sparks. Tools (swords, pickaxes, axes) stay vanilla.
7. Status bars: crimson glossy hearts, blood-red drumsticks, obsidian armor with a crimson rim, and an
   animated crimson XP bar with lightning (the lightning outside the 9 slots). Absorption hearts
   crimson/black. Poisoned, withered and frozen hearts stay vanilla.
8. Recipe book: obsidian panel with lightning frame, crimson search lens, red book button, tabs,
   toggles, page arrows and variant popup. Craftable recipes glow; uncraftable ones go dim.
9. Heavy Knight update (v0.7, inspired by "The Exiled Heavy Knight Knows How to Game the System";
   motifs only, no names or logos): riveted steel corner guards with a crimson inlay on every panel,
   lightning bursting from the guard tips, a tower-shield crest struck by lightning in the empty areas,
   steel plate buttons with cut corners and rivets, riveted straps between hotbar slots, steel armor
   icons, steel slider handles, tooltips as "system windows" with lock-on corner brackets, guarded
   advancement toasts headed "Title Acquired!", and a great-helm pack icon. Steel uses only the
   four steel tokens (SPEC section 4).

Done when `tools/Test-Palette.ps1 pack -Strict` exits 0 and every in-game check in SPEC section 12 passes.
```

## 2. Concept-art prompts

**Master prompt**

```text
Minecraft Java Edition GUI resource pack concept sheet, crisp pixel art, nearest-neighbor pixels,
flat orthographic UI, no perspective. Dark fantasy theme: matte obsidian-black panels (#0A0A0A),
thin dark blood-red borders (#990000), vibrant crimson (#FF0000) glow accents. Jagged one-pixel
electric-red lightning arcs with pale pink-white cores crackle along the outer edges of every panel
and link the nine hotbar slots, with a faint red aura around the arcs. Heavy-knight armour accents:
riveted cold-steel corner guards with a glowing crimson inlay on every panel, a small tower-shield
crest with a crimson sword, steel plate buttons with cut corners. Shown: player inventory with
crafting grid and the recipe book open beside it, six-row chest, hotbar with the selected slot
glowing crimson, "Resume Game" and "Options" buttons with crimson text, a glowing crimson blood
apple, a potion and an eye of ender crackling with red sparks. Clean, high contrast, readable,
minimal.
```

**Negative prompt**

```text
gold, golden, yellow, amber, orange, bronze, brass, copper, brown, beige, warm highlights, gold trim,
metallic gold, purple, blue, green, blur, soft gradient, painterly, photorealistic, 3D render,
perspective, glossy plastic, bloom, lens flare, text artifacts, watermark, logo
```

**Per-asset variants.** Append one of these to the master prompt:

| Asset | Append |
|---|---|
| Inventory | `close-up of the player inventory screen only, 176 by 166 pixel proportions, empty slots` |
| Chest | `six-row chest screen only, tall panel, lightning concentrated at the corners` |
| Hotbar | `wide strip of nine square slots, third slot selected with a glowing crimson frame, lightning running along the top and bottom edges and jumping between slots` |
| Buttons | `two wide rectangular buttons stacked, normal and hovered states, crimson text` |
| Icons | `sheet of 16 by 16 pixel item icons on a dark slot background, large and centered` |

**Using the output.** Judge generated images only on colour, density and composition. Then rebuild them as pixel art at the exact sizes in `SPEC.md`, using the palette tokens. Downscaling a generated image never produces clean pixel art and usually lets warm colours leak in.
