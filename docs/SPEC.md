# HotbarX — Crimson Obsidian UI · Design Specification

**v0.7 · 2026-10-23 · Heavy Knight update** · Target: Minecraft Java **26.3** (resource pack format **97.1**), also loads in **26.2** (88.0) · Resolution **1×** (vanilla pixel grid)

Companions:
- [`PROMPT.md`](PROMPT.md): production brief + concept-art prompts
- [`../tools/BuildTextures.java`](../tools/BuildTextures.java): generates every texture in `pack/` from this spec and asserts its layout rules
- [`../tools/Test-Palette.ps1`](../tools/Test-Palette.ps1): palette check

Paths, sizes, slot positions, sprite metadata and the `pack.mcmeta` format below were read from the 26.2 and 26.3 client jars. Everything this pack uses is identical in both versions, except the text shader (§5.6). The label colour, title offsets and item rarities come from the (unobfuscated) classes of both versions. Anything that could not be verified that way is marked **(verify in-game)**.

The v0.7 additions (sliders, tooltips and toasts: §8, §8.2, §8.3) were read from the **26.2** jar only, because no 26.3 jar was at hand. Their paths, sizes and `.mcmeta` are marked **(verify in 26.3)**.

---

## 1. The look

Matte obsidian-black panels, each edged by one thin blood-red line that glows inward. Slots are dark wells with a dark crimson rim, so they stay out of the way. Bright crimson appears only where something is "alive": the selected hotbar slot, hovered buttons and slots, and the lightning. The lightning is 1 px wide and jagged, with one white-hot strike point per bolt. It crackles along the outer edges of panels, strikes through the empty areas between slot groups and links the hotbar slots, and it never crosses an item or a line of text. **No gold, yellow, orange or bronze pixel ships in this pack.**

**Heavy Knight update (v0.7).** The look is inspired by the anime *The Exiled Heavy Knight Knows How to Game the System*: a heavy knight in plate armour who plays the world like a game, with levels, skills and titles. So a second material joins the crimson: cold **steel**, used only where armour would be. Riveted steel guards cover every panel corner, with a crimson inlay that the lightning bursts out of. A tower-shield crest takes the strike in the empty panel areas. Buttons become heavy plates, the hotbar separators carry riveted straps, and the armor bar turns to steel. The game-system side is a set of "system windows": tooltips with lock-on corner brackets, toasts with guarded frames, and a "Title Acquired!" header on advancements. The pack shows no names, logos or characters from the show; it borrows only these motifs.

## 2. Corrections to the original brief

| Brief said | Reality in 26.2 / 26.3 | This spec |
|---|---|---|
| `textures/gui/widgets.png` | Removed in 1.20.2 and split into individual GUI sprites | Hotbar §7, buttons §8 |
| Title text in "glowing crimson" | Labels are rendered text, not part of any PNG. They're hardcoded `#404040` with no shadow. Text can't glow. | Core-shader recolor to `#FF0000` (§5.6) |
| "Resume Game" button | Key `menu.returnToGame`; vanilla text is "Back to Game" | Lang override renames it (§8.1) |
| Crimson button text | Button text is white, set in code | Lang `§c` codes per key (§8.1) |
| Lightning "around the outer frame" | Only the texture rectangle is drawn. Nothing can render outside the 176-px panel or the 182×22 hotbar (the selection frame overhangs it by 1 px). | Arcs live in the outer 4 px band, inside the bounds |
| Lightning on "all UI containers" | Container backgrounds are standalone textures, so they can't animate. GUI sprites can (vanilla animates several). | Static arcs on containers, animated arcs on the hotbar (§7) |
| "Shulker Box", "Crafting" titles | Those screens use `shulker_box.png` and `crafting_table.png`, not `generic_54.png` | Both added to the required set (§6) |
| Golden Apple → Blood Apple | `golden_apple.png` is also the Enchanted Golden Apple's texture, and eating either apple shows **gold absorption hearts** | §9.2 and §7.4 |
| Potion with crimson particles | The liquid layer (`potion_overlay.png`) is tinted per effect; only the bottle layer is untinted | Sparks go on the bottle layers (§9.3) |

## 3. Decisions

Each row is a v1 default. Changing any of them doesn't affect the rest of the spec.

| # | Decision | Default | Alternative |
|---|---|---|---|
| D1 | Version | 26.3, plus 26.2 via a pack overlay (`min_format` 88, `max_format` 97; §5.6, §11) | 26.3 only: drop the overlay and set `min_format` to 97. 1.21.10 (format 69) uses different shader file names; not planned. |
| D2 | Resolution | 1× | 2× (GUI textures scale by UV), but never mix scales within one screen |
| D3 | Container label color | `text.vsh` recolor → exact `#FF0000`, also covers renamed and server-titled containers | Lang `§c` overrides: no shader, but misses renamed containers |
| D4 | Button text | Lang `§c` on pause-menu keys, "Resume Game" wording | Keep vanilla white text |
| D5 | Hotbar cell opacity | `#0A0A0A` at α `0xE6` (90 %) | Vanilla α `0xBA` (73 %) or fully opaque |
| D6 | Tools | **Stay vanilla** (owner's call, v0.4): no sword, pickaxe or axe texture ships | Retexture the netherite tier, and optionally golden and copper, as in v0.3 |
| D7 | Splash and lingering potions | Included (same bottle recipe) | Plain potion only |
| D8 | Enchanted Golden Apple | Shares the Blood Apple texture; the glint tells them apart | Its own texture via a model override (§9.2) |
| D9 | Repo layout | Pack lives in `pack/`; zip the folder's contents | Pack at repo root |
| D10 | Heavy Knight update (v0.7) | Steel guards, crests, plate buttons, riveted hotbar straps, steel armor icons, system-window tooltips and toasts (§5.7, §8) | The v0.6 look: plain crimson frames, crimson-trim buttons, vanilla tooltips and toasts |
| D11 | Advancement toast header | Lang `§c`, with the task header renamed "Title Acquired!" and the challenge header "Challenge Cleared!" (§8.1) | Keep the vanilla wording, in `§c` only |

## 4. Palette

Every token has **G = B**, so every pixel is either a neutral gray or a pure red. That makes "no gold" mechanically checkable (`-Strict` in the palette check).

| Token | Hex | Use |
|---|---|---|
| `void` | `#000000` | Outermost outline, player-preview window |
| `obsidian` | `#0A0A0A` | **Primary**: panel fill, hotbar cells |
| `obsidian-2` | `#141414` | Slot wells, hotbar separators |
| `charcoal` | `#1E1E1E` | Button face, hotbar bands |
| `slate` | `#2A2A2A` | Hovered button face and toggles, potion glass, apple stem |
| `steel-800` | `#383838` | Steel shadow edges, armor-slot silhouettes, hotbar strap |
| `steel-600` | `#5A5A5A` | Steel body, unlit rivet ring |
| `steel-400` | `#8C8C8C` | Lit steel edges, rivet bodies |
| `steel-200` | `#C8C8C8` | Rivet glints, steel highlights, light toast face |
| `blood-950` | `#1A0000` | Inner shadow of borders |
| `blood-900` | `#330000` | Glow halo (outer ring), aura |
| `blood-700` | `#660000` | Slot shadow edge, quiet trim |
| `blood` | `#990000` | **Accent**: borders, slot outlines |
| `crimson-600` | `#CC0000` | Arc taper, glow mid-tone |
| `crimson` | `#FF0000` | **Accent/glow**: arc body, selection, hover |
| `crimson-hot` | `#FF6666` | Inner edge of glowing frames |
| `core` | `#FFE5E5` | Lightning core flecks, specular glints |

- **Forbidden:** any visible pixel with hue 11°–75° (saturation ≥ 20 %, value ≥ 12 %). That band covers gold, yellow, amber, orange, bronze, copper and brown. The vanilla hotbar fails this test: its cells hold dark olive pixels (`#272505`), so all of it gets repainted.
- **Steel is for armour only:** corner guards, rivets, the crest, buttons, the slider handle, the armor icons, armor-slot placeholders and the light toasts. Panels, slots and cells stay obsidian and crimson.
- **Text on `obsidian`:** `#FF0000` gives 4.95:1 and `§c #FF5555` gives 6.3:1. **Never** use `blood #990000` (2.2:1) or `§4 #AA0000` (2.55:1) for text.
- **Glow is faked.** The GUI has no bloom or emissive layer. A glow is concentric 1-px rings: bright core → `crimson` → `blood` → `blood-900`.
- **Matte** means flat fills: no noise, gradients or dithering on panel areas.

## 5. Shared recipes

### 5.1 Lightning
- **Shape:** jags that alternate direction with random lengths of 1–5 px, so the bolt drifts instead of oscillating around a line. Each jag is steep (one step per pixel) or shallow (one step per two pixels), with a flat run of 0–1 px at each turn. The reach swells toward the middle: a bolt leaves its line at one end and sinks back into it at the other. The exception is the 3-px hotbar bands (§7.1), where kinks must stay rare.
- **Body:** 1 px `crimson`. The ends taper `crimson-600` → `blood`.
- **Strike point:** one per ~40 px of bolt, on the inward tooth tip nearest the middle: a `core` pixel with `crimson-hot` on either side. Sparks shorter than 14 px get none. No other `core` pixels: scattered flecks read as dust, not lightning.
- **Forks:** off about half the inward tooth tips, 2–4 px long, one diagonal step and then onward. The last 2 px taper `crimson-600` → `blood`.
- **Glow:** orthogonal neighbours of bright pixels (`crimson`, `crimson-hot`, `core`) that land on the fill or `blood-950` become `blood-900`. The 8 neighbours of a strike point that land on the fill, `blood-950` or `blood-900` become `blood-700`, a brighter flare.
- **Conduits:** in a 2-row channel there's no room to zig-zag, so the bolt is a line that crackles between its rows, with flat runs of up to 4 px. It's `crimson-600`, with a `crimson` pulse of ±5 px around each strike point.
- **Density:** about half of each panel's free perimeter, between the corner guards (§5.7): two arcs on every side, plus one arc per corner that bursts out of the tip of a guard arm. The four corner arcs turn clockwise like a pinwheel: down from the top-left guard, left from the top-right, up from the bottom-right, right from the bottom-left. Leave quiet stretches between them, so strikes read as events rather than noise. Channel and free-area bolts (§5.2) add more inside the panel.
- **Clearance:** at least 1 px from every slot frame. Nothing inside a text-safe zone (§5.5).

### 5.2 Container frame — outer 4 px band, outside → in
1. `void`. Keep vanilla's exact alpha silhouette (rounded corners) by copying the alpha mask from the vanilla PNG.
2. `blood`: the thin crimson border line
3. `blood-900`
4. `blood-950`, then the `obsidian` fill. Rings 3 and 4 are the border's glow, fading inward.

- **Arcs:** lightning follows ring 3 and zig-zags from ring 2 (it touches the border line) up to ring 6, as far as the margin is free (ring offset −1 to +3). On the sides that's x 1–5; under a title it's rings 2–4 only. The arc spends most of its length on the dark rings and the fill, not on the red border line, so it keeps its contrast. An arc stops where it meets a corner guard.
- **Forks:** may reach into the panel margin but must stay at least 1 px clear of every slot frame. With a 7 px margin, that means at most 2 px deep on the sides.
- **Corners:** the rings are measured as distance from the panel's transparent edge, so they bend diagonally around the rounded corners. In vanilla the top-right corner cut is 3 px and the others are 2 px.
- **Channel bolts:** a bolt also crosses each free channel between slot groups, staying 1 px clear of every slot frame and every text-safe zone. The channels are inclusive rectangles (x0, y0)–(x1, y1):

  | Texture | Hotbar gap (2-row conduit) | Wider gap (jagged bolt with forks) |
  |---|---|---|
  | `inventory.png` | (4,138)–(171,139) | Between the armor/crafting area and the main inventory (also a 2-row conduit): (4,80)–(171,81) |
  | `generic_54.png` | (4,194)–(171,195) | Right of the "Inventory" label, in the bottom piece: (94,128)–(171,137) |
  | `shulker_box.png`, `crafting_table.png` | (4,138)–(171,139) | Right of the "Inventory" label: (94,72)–(171,81) |

- **Free-area bolts and crests:** two screens have a large empty area. A tower-shield crest (§5.7) stands in it, and jagged bolts with forks strike the shield, along the area's longer side.

  | Texture | Area | Crest (top-left) | Bolts |
  |---|---|---|---|
  | `inventory.png` | (126,55)–(171,78): below the crafting result, right of the recipe-book button at (104,61), 20×18 | (154,58) | Across (126,55)–(152,78), from the button to the shield |
  | `crafting_table.png` | (146,16)–(171,70): right of the result frame, below the title | (152,35) | Down (151,16)–(165,33), from the title row onto the shield; and down (151,53)–(165,70), from below its point to the "Inventory" channel |

  The recipe-book button positions (inventory (104,61), crafting table (5,34)) come from `getRecipeBookButtonPosition()`, identical in the 26.2 and 26.3 classes.

### 5.3 Slot — 18×18 frame around a 16×16 item area
- Top and left edges, including the two off-corners: `blood-900`. Bottom and right edges: `blood-700`. The slot reads as recessed, and the grid stays a quiet dark crimson, so items and lightning carry the screen.
- Well: `obsidian-2`. It's slightly lighter than the panel so black items stay visible.

### 5.4 Item textures
Alpha must be 0 or 255; the palette check enforces this. Keep vanilla silhouettes so items stay recognisable. Item PNGs also change the item held in hand, dropped on the ground and shown in item frames, not only the icon.

### 5.5 Text-safe zones
Flat `obsidian` only: no arcs, no halo. Coordinates are texture pixels. The title x offsets (97 for the inventory, 29 for the crafting table) are in the 26.2 and 26.3 classes. The zone widths are approximate and allow for longer titles; confirm them in the acceptance pass.

| Texture | Title zone | "Inventory" label zone |
|---|---|---|
| `inventory.png` | "Crafting": x 95–166, y 4–15 | — (not drawn) |
| `generic_54.png` | x 6–166, y 4–15 | x 6–90, y 127–138 (bottom piece) |
| `shulker_box.png` | x 6–166, y 4–15 | x 6–90, y 71–82 |
| `crafting_table.png` | x 27–166, y 4–15 | x 6–90, y 71–82 |

Title zones end at x 166 because the frame rings bend inward at the top-right corner.

### 5.6 Label color (D3)
The label colour `0xFF404040` is a constant in `AbstractContainerScreen` and `InventoryScreen` in both 26.2 and 26.3.

**Two copies of the shader.** 26.3 rewrote `text.vsh`: it uses `#include` instead of `#moj_import`, adds explicit `layout(location = …)` qualifiers, and pairs with a new OIT-aware `text.fsh`. A copy from one version breaks the other, so the pack ships one per version:

| File | Copied from | Used by |
|---|---|---|
| `pack/assets/minecraft/shaders/core/text.vsh` | 26.3 | 26.3 (the base pack) |
| `pack/mc_26_2/assets/minecraft/shaders/core/text.vsh` | 26.2 | 26.2, through the `mc_26_2` overlay (§11) |

In both versions, the `pipeline/gui_text` render pipeline uses `core/text` as its vertex and fragment shader, with the `IS_GUI` define. This was checked in `RenderPipelines`; in 26.3 it's built from `TEXT_SNIPPET` + `GUI_TEXT_SNIPPET`. So both copies get the same one change. The GUI path is the `#else` branch, which passes `Color` straight through. Change it to:

```glsl
#else
    vertexColor = Color;
#ifdef IS_GUI
    // HotbarX: vanilla container labels are exactly #404040; draw them crimson instead.
    if (all(lessThan(abs(Color.rgb - vec3(64.0 / 255.0)), vec3(0.5 / 255.0)))) {
        vertexColor = vec4(1.0, 0.0, 0.0, Color.a);
    }
    // HotbarX: the XP level and affordable anvil/enchanting costs are exactly #80FF20; draw them pale crimson (#FFE5E5).
    if (all(lessThan(abs(Color.rgb - vec3(128.0, 255.0, 32.0) / 255.0), vec3(0.5 / 255.0)))) {
        vertexColor = vec4(1.0, 229.0 / 255.0, 229.0 / 255.0, Color.a);
    }
#endif
#endif
```

- **What it affects:** every GUI string drawn in exactly `#404040`. That's all vanilla container labels, renamed containers and uncolored server menu titles. Text that already has its own color is untouched.
- **XP green:** `#80FF20` is used, in both versions, by `ContextualBar` (the XP level number), `AnvilScreen` (an affordable cost) and `EnchantmentScreen` (an affordable level). It becomes `core`, not red, because the anvil draws "too expensive" in `#FF6060`; a red would erase that difference. The enchanting table's unaffordable `#407F10` stays as it is.
- **Version-locked:** on every Minecraft update, diff the vanilla `text.vsh`. If it changed, add the new copy (with the change) as the base, and move the old one into an overlay for the older format. 1.21.10 still named this file `rendertype_text.vsh`.
- **Not checked offline:** neither copy has been compiled outside the game, but the added GLSL is identical in both. **(verify in-game)**
- **Test with mods:** Sodium, Iris and ImmediatelyFast are all in the profile. **(verify in-game)**
- **Fallback:** delete the shader and use lang overrides instead: `"container.chest": "§cChest"` and the same for `container.inventory`, `.crafting`, `.chestDouble`, `.enderchest`, `.barrel`, `.shulkerBox`. Don't combine the two approaches: a `§` color wins over the shader, and you lose the exact hex.

### 5.7 Heavy-knight armour (v0.7)
Steel pieces are painted after the frame rings and before the lightning. Arcs and halos never cross them.

- **Corner guards:** one on each corner of every framed panel: the containers, the recipe book, the advancement toast, the banner and the pack icon. Corners are taken from the panel's bounding box; ring 1 (the `void` outline) always stays.
  - **Boss:** a 5×5 stud over rings 2–6 of the corner, lit from the top left on every corner: a `steel-400` top and left edge, a `steel-800` bottom and right edge, and chamfered corners. In the middle sits a crimson gem: a `crimson` centre, `crimson-hot` left, `crimson-600` right, `blood` below, and a `core` glint on top, with `steel-600` between them.
  - **Arms:** a 3 px plate over rings 2–4, running 20 px along the top and bottom edges and 15 px down the sides. The sides end at row 15, so generic_54's header never reaches seam row 16 (§6.2). Panels under 64 px tall (toasts, the banner) get no side arms.
  - **Shading:** the ring that faces up or left is `steel-400` and the one that faces down or right is `steel-800`, with `steel-600` in the middle.
  - **Rivet and inlay:** 7 px from the corner, the middle ring holds a `steel-200` rivet with a `steel-800` shadow. From 9 px to the tip, the middle ring is a `crimson-600` inlay, so the corner arc seems to flow out of it.
  - **Tip:** bevelled, one pixel shorter per ring inward, and edged in `steel-800`.
  - **Clearance:** the boss stays inside x/y 1–5 from the corner, clear of the title zones (they start at x 6) and of the armor slot frames at (7,7).
- **Crest:** a 13×16 tower shield bearing a crimson sword, point down. The rim is `steel-400` on the left and top and `steel-800` on the right, with a `void` outline. The field is split, `blood-700` and `blood-900`. The sword has a `steel-200` pommel, a `steel-600` grip, a steel crossguard and a three-tone blade (`crimson-hot`, `crimson`, `crimson-600`). Every pixel it covers must be plain fill, and bolts keep 1 px from it, like from a slot frame (§5.2).

## 6. Containers (static)

Each texture is 256×256 with the panel at (0,0); the rest of the canvas is unused. An "item origin" is the top-left of a slot's 16×16 item area. The 18×18 slot frame starts 1 px up and left of it.

### 6.1 `textures/gui/container/inventory.png` — panel 176×166

| Element | Item origin(s) |
|---|---|
| Armor: head, chest, legs, feet | (8,8) (8,26) (8,44) (8,62) |
| Offhand | (77,62) |
| Crafting 2×2 | (98,18) (116,18) (98,36) (116,36) |
| Crafting result | (154,28) |
| Main inventory | x = 8 + 18c (c 0–8), y = 84 / 102 / 120 |
| Hotbar row | x as above, y = 142 |

- **Player-preview window** (between the armor column and the crafting grid): `void` fill. Its frame keeps the slot bevel (`blood-900` top/left, `blood-700` bottom/right). No arcs inside, because the player model renders there.
- **Crafting arrow:** `blood` with a `crimson` tip. Next to the quiet slots it is the brightest static element, which suits a direction cue.

### 6.2 `textures/gui/container/generic_54.png` — panel 176×222
Used by chests, large chests, barrels, ender chests and 1–6-row server menus.

- **Slots:** container rows at x = 8 + 18c, y = 18 + 18r (r 0–5). Player inventory at y = 140 / 158 / 176, hotbar row at y = 198.
- **Drawn in two pieces:** first texture rows 0 … 16 + 18n (n = row count), then rows 126–221 placed directly underneath. Row 125 is never shown.
- **Seam rule:** the side borders over rows 17–124 must be one **18-row tile (rows 17–34) repeated 6×**.
  - The tile's top must continue from row 16.
  - The tile's bottom must flow into both its own top and row 126.
  - Long arcs belong in the header (rows 0–16) and the bottom piece (rows 126–221). Side tiles carry only short sparks that start and end inside the tile.

### 6.3 `textures/gui/container/shulker_box.png` — panel 176×166
Container rows at y = 18 / 36 / 54, player inventory at 84 / 102 / 120, hotbar at 142. Single piece, so no seam rule.

### 6.4 `textures/gui/container/crafting_table.png` — panel 176×166
Grid at x = 30 + 18c, y = 17 + 18r. The result sits in a 26×26 frame at (119,30), with the item at (124,35). Player inventory as in §6.3.

### 6.5 Slot sprites — `textures/gui/sprites/container/`

| File | Size | Design |
|---|---|---|
| `slot/helmet.png`, `chestplate`, `leggings`, `boots`, `shield` | 16×16 | Empty armor and offhand placeholders. `steel-800` silhouette, 1 px `blood` outline, one `blood-700` detail line |
| `slot_highlight_back.png`, `slot_highlight_front.png` | 24×24, with `.mcmeta` (nine-slice, border 4) | Hover state. Back: `crimson` at α `0x40`, a red wash under the item. Front: 1 px `crimson` ring on the slot frame with `core` corner pixels, plus a `blood-900` ring at α `0x99` just outside it. Its 4 px overhang is the one place a glow may spill past a slot. |

**Always ship the `.mcmeta` alongside any sprite that has one.** Metadata is read from the pack that supplies the PNG, so leaving it out loses the nine-slice or animation.

### 6.6 Recipe book — `textures/gui/recipe_book.png` + `gui/sprites/recipe_book/`
This is the book that opens from the inventory and the crafting table. Every asset is identical in 26.2 and 26.3, and each colour is mapped explicitly; an unknown colour fails the build.

- **Panel** (`recipe_book.png`, 147×166 at (1,1)):
  - The outer bevel becomes the frame rings with corner guards (§5.7) and perimeter arcs (§5.2).
  - The inner well is `obsidian-2` and keeps the slot bevel (`blood-900` top/left, `blood-700` bottom/right).
  - The search magnifier (x 11–22, y 15–26) has a crimson lens, a `core` glint and a dark-red handle. The vanilla handle is brown and would fail the palette check.
- **Search box** (`widget/text_field*`, nine-slice border 1): `void` fill with a `blood` border, and a `crimson` border when focused. These sprites style every text field in the game.
- **Book button** (`button*`, 20×18, on the inventory and crafting table): a crimson book with pink-white pages on an `obsidian` button. On hover the button turns `charcoal` with a `crimson-hot` trim.
- **Tabs** (`tab`, `tab_selected`, 35×27): `obsidian` with a `blood-700` trim and `blood-900` shade. The selected tab is `charcoal` with a `crimson` trim.
- **"Craftable only" toggles** (`filter_*`, `furnace_filter_*`, 26×16):
  - The crafting-table and furnace icons are recoloured to the red ramp.
  - The check mark is `core` and the cross is `crimson-600`.
  - On: `charcoal` with a `crimson-hot` trim. Off: `obsidian-2`. Hover: `slate`.
- **Recipe slots** (`slot_*craftable`, 25×25): craftable recipes stand out (`charcoal` well, `blood` frame, the inner bevel as in §5.3). Uncraftable ones, red in vanilla, go dim (`obsidian` well, `blood-900` frame).
- **Variant popup** (`overlay_recipe`, nine-slice border 4) and its buttons (`crafting_overlay*`, `furnace_overlay*`):
  - Popup: `obsidian` panel with a `blood` trim.
  - Buttons: `obsidian-2` cells. Hover: `charcoal` cells with `crimson-600` grid lines. Disabled: `obsidian` with `blood-900` lines.
- **Page arrows** (`page_*`): `blood` body with a `crimson` edge; on hover a `crimson` body with a `core` edge.

## 7. Hotbar (animated sprites) — `textures/gui/sprites/hud/`

### 7.1 `hotbar.png` — 182×22

| Region | Pixels | Fill |
|---|---|---|
| Outline | row 0, row 21, col 0, col 181 | `void` |
| Bands | rows 0–2 and 19–21; cols 0–2 and 179–181 | `charcoal`, with `blood-700` trim on the line next to the cells (row 2, row 19, col 2, col 179) |
| Cells | x = 3+20i … 18+20i (i 0–8), y 3–18 | `obsidian` at α `0xE6` (D5) |
| Separators | x = 19+20k … 22+20k (k 0–7), y 3–18 | `obsidian-2` with a riveted steel strap in the middle two columns: a 2×2 stud at rows 4–5 and 16–17 (`steel-200` glint, `steel-400`, `steel-800` shadow) and a `steel-800`/`charcoal` strap between them. It carries vertical bolts linking the two bands, painted over the strap. A bolt zig-zags across the 4 columns (§5.1 shape) with one strike point; a stub tapers at its free end |

- **Items** draw at (3+20i, 3). **No arc pixel may enter a cell.**
- **Rails:** the lightning rail sits on row 1 and row 20 (and on cols 1 and 180) and kinks into the outline or trim rows. Straight runs can be up to 7 px, because on a 3 px band frequent kinks read as a dashed line.
- **Anchor rule (seamless selection):** in the top band, the arc passes through **row 1** at every column where x mod 20 ∈ {3, 18}. In the bottom band it passes through **row 20** at the same columns. Between anchors it may zig-zag freely across its band. The rule holds in every animation frame.
- **Animation:** make a vertical strip 182 × 22·N and add `hotbar.png.mcmeta` (the same format vanilla uses for its animated HUD sprites):

  ```json
  { "animation": { "height": 22, "frametime": 2,
      "frames": [ { "index": 0, "time": 30 }, 1, 2, 3, { "index": 0, "time": 20 }, 4, 5 ] } }
  ```

  - Frame 0 is a complete still image with two full separator bolts and two stubs. Frames 1–3 sweep a strike left to right, with two bolts each. Frame 4 flashes four bolts, and frame 5 is the afterglow.
  - A burst comes about every 1.5 s (the cycle is 60 ticks with two bursts).
  - Flicker only thin arc pixels. Never pulse whole cells or bands (photosensitivity).
  - Keep frame 0 on screen most of the time.

### 7.2 `hotbar_selection.png` — 24×23, drawn at hotbar (−1 + 20i, −1)
- **Frame band:** 3 px wide, at cols 1–3 and 20–22, rows 1–3 and 20–22. Outside → in: `blood`, `crimson`, `crimson-hot`, with `core` sparks at the four inner corners.
- **Outer ring** (row 0, cols 0 and 23): `crimson` at α `0x50`, a soft red glow over whatever is behind the hotbar.
- **Interior** (x 4–19, y 4–19): stays fully transparent. It sits exactly over the cell.
- **Terminals:** pixels (0,2), (23,2), (0,21) and (23,21) are opaque `crimson`. On whichever slot is selected, they land beside the hotbar anchors at x mod 20 = 18 and 3, so the band arcs plug straight into the frame.
- **Sparks:** opaque `crimson` pixels at (6,0), (7,0) and (16,0), and a `core` pixel at (17,0), jump into the 1 px overhang above the hotbar. Two more sit at (0,9) and (23,14), beside it.
- **Animation:** may animate independently (same `.mcmeta` format, `"height": 23`), as long as the terminals stay lit in every frame.

### 7.3 Other hotbar sprites
- `hotbar_offhand_left.png`, `hotbar_offhand_right.png` (29×24): one cell using the §7.1 band and cell recipe. Keep the vanilla alpha silhouette.
  - A `crimson` bolt with one `core` pixel bridges the 7 px gap (x 22–28, rows 8–15) between the cell and the hotbar edge.
  - The bridge has a soft glow: `crimson` at α `0x50`.
  - The right sprite is the left one mirrored.
- `hotbar_attack_indicator_background.png`, `hotbar_attack_indicator_progress.png` (18×18): the background outline is `obsidian-2`. The progress sprite keeps its `void` outline, and its vanilla shading maps onto a 6-step red ramp (`blood-900` → `crimson-hot`).

### 7.4 Absorption hearts — `hud/heart/absorbing_*.png` (8 files, 9×9)
These are required for the no-gold rule. The vanilla art fails the palette check, and it appears the moment someone eats a Blood Apple.
- **Normal:** `obsidian-2` heart, `crimson` outline, one `core` glint.
- **`*_blinking`:** `charcoal` fill, `crimson-hot` outline.
- **`*_hardcore_*`:** the same colours, following vanilla's hardcore shape.

### 7.5 Status bars — hearts, hunger, armor, XP
Each colour is mapped explicitly; an unknown colour fails the build.

| Sprites (`hud/…`) | Design |
|---|---|
| `heart/container*`, `heart/vehicle_container` | `blood-900` outline, `charcoal` inside. Damage flash (`*_blinking`): `crimson-hot` outline |
| `heart/full`, `half`, `hardcore_*` (+ `_blinking`) | `crimson` body, `blood` shading, `core` glint. The top lobes (row 1) are `crimson-hot` for a glossy, charged look. Hardcore spots are `blood-900` |
| `heart/vehicle_full`, `vehicle_half` | Vanilla is orange-brown (fails the gold band). Now `crimson-600`/`blood` with a pale saddle, so mount hearts stay distinct |
| `heart/poisoned_*`, `withered_*`, `frozen_*` | **Stay vanilla:** their colour is gameplay information |
| `food_empty`, `food_full`, `food_half` | Blood-red drumsticks (`crimson-hot` → `blood-900`) with `core` bones. Empty: `blood-900` outline, `charcoal` inside |
| `food_*_hunger` (Hunger effect) | Dull and "rotten": `charcoal`/`slate` with dim blood, `slate` bone |
| `armor_full`, `armor_half`, `armor_empty` | Heavy-knight steel plates: `steel-400` body, `steel-800` shade, `steel-200` glint and a glowing `crimson-600` rim. Empty: `blood-900` outline, `obsidian-2` inside |
| `experience_bar_background` (+ `.mcmeta`) | Vanilla segments recoloured to `obsidian`/`obsidian-2` with `blood-900`/`void` notches. Dim bolts crackle in the empty part. A 3-frame animation holds frame 0 for 40 ticks, then flickers brighter for 3 + 3 ticks |
| `experience_bar_progress` (+ `.mcmeta`) | Vanilla's noisy fill mapped by brightness onto `blood-700` → `crimson-hot`, with a `blood-900` rim. Five `crimson-hot` streaks with `core` bends crackle through it; 6 frames, `frametime` 2 |

The XP bar sits 2 px above the hotbar, so its bolts are the lightning outside the 9 slots. The XP level number turns `core` through the shader (§5.6).

## 8. Buttons — `textures/gui/sprites/widget/`

`button.png`, `button_highlighted.png` and `button_disabled.png` are 200×20, each with a `.mcmeta` (nine-slice, border 3). The 3×3 corners are always drawn whole. The edges and centre are cut and repeated to whatever width the button has, from about 20 to 310 px.

Each button is a heavy plate (v0.7):

| State | Ring 1 (outline) | Ring 2 (steel rim) | Ring 3 (inlay) | Face | Corner pixel of the inlay |
|---|---|---|---|---|---|
| Normal | `void` | top/left `steel-400`, bottom/right `steel-800` | `blood` | `charcoal` | `steel-400` rivet |
| Highlighted (hover/focus) | `void` | top/left `steel-200`, bottom/right `steel-600` | `crimson` | `slate` | `core` spark |
| Disabled | `void` | `steel-800` | `blood-900` | `obsidian` | — |

- **Chamfer:** the three pixels at each outer corner are transparent, and the diagonal next to them is `void`, so the plate has cut corners like a game window.
- **Flat face:** no centred motif, nothing that breaks when cut. Rivets and sparks live **only in the 3×3 corners**.
- **Text contrast:** `§c` text on the hovered `slate` face is 4.6:1, and on `charcoal` 5.4:1.
- These sprites restyle every vanilla button. The text fields are restyled in §6.6.
- **Sliders** (`widget/slider`, `slider_highlighted`: 200×20, nine-slice border 1; `slider_handle`, `slider_handle_highlighted`: 8×20, borders L2 T2 R2 B3; vanilla `.mcmeta` shipped) **(verify in 26.3)**:
  - **Track:** `obsidian` with a 1 px `blood` edge, like a text field; `crimson` when hovered or focused.
  - **Handle:** a steel plate with a `void` outline, a `steel-200`/`steel-400` lit top and left, a `steel-800` bottom and right, and a crimson grip (`blood` + `crimson-600`) down the stretchable middle, rows 3–16.
  - **Hovered handle:** a `crimson` outline, brighter steel and a `crimson` + `crimson-hot` grip.

### 8.1 Button text (D4) — `assets/minecraft/lang/en_us.json`
The game language is `en_us`; add another language's file if you switch. Write `§` as `§` so the file's encoding can't break it.

```json
{
  "menu.returnToGame": "§cResume Game",
  "menu.options": "§cOptions..."
}
```

- **Rest of the pause menu:** the shipped `en_us.json` also colours the other pause-menu keys (identical in 26.2 and 26.3), so the menu reads as one set: `menu.shareToLan`, `gui.advancements`, `gui.stats`, `menu.feedback`, `menu.server_links`, `menu.playerReporting`, `menu.returnToMenu`, `menu.disconnect`.
- **Advancement toast headers (D11):** `AdvancementToast` draws the header in `#FFFF00` (yellow, a gold leak in text) and a challenge's in `#FF88FF`. A `§c` in the lang string overrides both. The headers also take the game-system wording:

  | Key | Vanilla | HotbarX |
  |---|---|---|
  | `advancements.toast.task` | Advancement Made! | `§c`Title Acquired! |
  | `advancements.toast.goal` | Goal Reached! | `§c`Goal Reached! |
  | `advancements.toast.challenge` | Challenge Complete! | `§c`Challenge Cleared! |

- **Limits:** legacy codes offer only 16 colours (`§c` ≈ `#FF5555`, `§4` = `#AA0000`). A `§` colour also overrides the gray of a disabled button's text. **(verify in-game)**

### 8.2 Tooltips — `textures/gui/sprites/tooltip/` (v0.7) **(verify in 26.3)**

`background.png` and `frame.png` are 100×100 nine-slices (borders 9 and 10, the frame with `stretch_inner`), drawn 12 px out from the text on every side. The box itself spans 8–91 in both; the 8 px outside it are a margin that only the brackets use. The vanilla `.mcmeta` ship with them.

- **Background:** `obsidian` at α `0xF0` over the box, with its three corner pixels cut (a chamfer).
- **Frame:** a 1 px line on the box edge and the chamfer: `crimson-600` along the top (the "title bar"), `blood` elsewhere. One ring inside it, a `blood-900` glow. Text starts 2 px inside the glow.
- **Lock-on brackets:** an L 2 px outside each corner with 4 px arms: `core` on the corner, then `crimson-hot`, ending in `crimson-600`. They stay inside the frame's 10 px corner, so they never stretch.
- The vanilla frame is purple (G ≠ B), so it failed the strict palette check.

### 8.3 Toasts — `textures/gui/sprites/toast/` (v0.7) **(verify in 26.3)**

| Sprite | Size, scaling | Design |
|---|---|---|
| `advancement` | 160×32, drawn 1:1 | A framed panel: the §5.2 rings, corner guards without side arms (§5.7), and four arcs on the long sides. Text-safe zone (6,6)–(153,25). The header is crimson through lang (§8.1); the description stays white. |
| `recipe` | 160×32, drawn 1:1 | `RecipeToast` draws its text in `#500050` and black, set in code. A dark toast would hide it, so this one is a **light steel plate**: `void` outline, `blood` line, `steel-400` rim, `steel-200` face, and a 2×2 rivet in each corner. Black text on it is 12.6:1. |
| `tutorial` | 160×32, nine-slice border 3 | The same light steel plate without rivets, for the same reason (its text is dark too). |
| `now_playing` | 160×32, nine-slice border 4 | Recoloured: `void` outline, `blood` border, `obsidian` fill. |
| `system` | 160×64, nine-slice (L17 T30 R4 B4) | Recoloured: `obsidian` fill, `blood` border. The yellow "!" (`#CAB60D`) becomes `crimson` with a `blood-700` shade and a `core` glint. |

Toasts that need a nine-slice keep their vanilla `.mcmeta`.

## 9. Items — `textures/item/` (16×16, alpha 0/255)

### 9.1 Tools (D6)
Swords, pickaxes and axes stay vanilla; the pack ships no tool texture. Golden and copper tools therefore keep their vanilla colours. They're game items, not GUI, and are accepted under D6.

### 9.2 Blood Apple: `golden_apple.png` + lang
- **Texture:** body `blood` → `crimson`, with a 2–3 px `core` specular and a 1 px `blood-900` rim on the shadow side (the "aura"). Stem `slate` (not brown), and a 3-px `blood-700` leaf.
- **Name:** `"item.minecraft.golden_apple": "§cBlood Apple"`. In 26.2 and 26.3 the Golden Apple has Common rarity (white name), so this turns the name red in tooltips and above the hotbar. **(verify in-game)**
- **Enchanted version:** `"item.minecraft.enchanted_golden_apple": "Enchanted Blood Apple"` keeps its Rare aqua name, which is allowed.
- **D8 split (optional):** in `models/item/enchanted_golden_apple.json`, set `"layer0": "minecraft:item/enchanted_blood_apple"` and add that PNG.

### 9.3 Potions: `potion.png`, `splash_potion.png`, `lingering_potion.png`
The model is `layer0 = potion_overlay` (tinted by the effect, drawn first) and `layer1 = <bottle>` (untinted, drawn on top).
- **Bottles:** sparks and aura go on the bottle files.
  - **Glass:** the lit side is `blood` and the body `blood-700`. The shadow outline and inner shine are `slate`, and so is the neck.
  - **Cork:** `crimson-600`/`blood`/`blood-700`, so it reads as a red wax seal.
  - **Sparks:** 4 pixels (`crimson`, `crimson-600` and one `core`) just outside the silhouette, around the neck and shoulders.
- **Liquid:** leave `potion_overlay.png` alone. It's grayscale by design, and the liquid colour is gameplay information. At most 2 spark pixels may sit over the liquid area.

### 9.4 Ender eye: `ender_eye.png`
- Keep the round silhouette:
  - a `blood-700` rim around a dark sclera (`obsidian`, `charcoal`, `slate`)
  - a `crimson` iris ramp (`blood` → `crimson-hot`)
  - a `void` pupil with a `core` glint
  - 3 spark pixels leaving the rim
- The same texture is used for a thrown Eye of Ender in the world.

## 10. Gold-leak audit

"No gold anywhere" reaches past the files the brief named. These vanilla files (identical in 26.2 and 26.3) were run through `tools/Test-Palette.ps1`:

| Vanilla asset | Result | v1 |
|---|---|---|
| `gui/sprites/hud/hotbar.png` | ✗ 1876 olive px | Repainted (§7.1) |
| `gui/sprites/hud/heart/absorbing_full.png` | ✗ 34 gold px | All 8 `absorbing_*` repainted (§7.4) |
| `item/golden_apple.png` | ✗ 100 px | Repainted (§9.2) |
| Golden Apple item name | ✓ white (Common rarity in 26.2 and 26.3) | Made red by lang (§9.2) |
| `item/copper_sword.png` (and the other copper and golden tools) | ✗ 76 px | Stay vanilla (D6) |
| Recipe-book magnifier handle, crafting-table icon in the filter toggle | ✗ brown | Recoloured (§6.6) |
| `gui/sprites/hud/food_full.png` (hunger bar) | ✗ 13 brown px | Recoloured (§7.5) |
| `gui/sprites/hud/heart/vehicle_full.png` (mount hearts) | ✗ orange-brown | Recoloured (§7.5) |
| `item/netherite_sword.png`, `item/potion_overlay.png`, `gui/container/*.png` | ✓ | — |
| `gui/sprites/toast/system.png` (v0.7 audit, 26.2 jar) | ✗ 26 `#CAB60D` px, the "!" | Recoloured (§8.3) |
| Advancement toast header text, `#FFFF00` set in code | ✗ yellow text | `§c` through lang (§8.1) |
| System toast title text, `#FFFF00` set in code | ✗ yellow text | **Stays**: it has many lang keys, and only shows for errors such as a failed world backup |
| `gui/sprites/tooltip/frame.png` | ✓ no gold, but purple (G ≠ B) | Repainted (§8.2) |

## 11. Pack layout

```
pack/
├── pack.mcmeta
├── pack.png                                      128×128 (drawn at 64×64, shipped at 2×), palette tokens only:
│                                                 a great helm with a glowing eye slit in the guarded frame
├── mc_26_2/assets/minecraft/shaders/core/text.vsh   overlay: 26.2 copy + §5.6 change
└── assets/minecraft/
    ├── lang/en_us.json
    ├── shaders/core/text.vsh                     26.3 copy + §5.6 change
    ├── models/item/enchanted_golden_apple.json   optional (D8)
    └── textures/
        ├── gui/container/            inventory, generic_54, shulker_box, crafting_table
        ├── gui/sprites/container/    slot_highlight_back/front (+ .mcmeta)
        ├── gui/sprites/container/slot/   helmet, chestplate, leggings, boots, shield
        ├── gui/sprites/hud/          hotbar (+ .mcmeta), hotbar_selection,
        │                             hotbar_offhand_left/right,
        │                             hotbar_attack_indicator_background/progress,
        │                             experience_bar_background/progress (+ .mcmeta),
        │                             food_*, armor_*
        ├── gui/sprites/hud/heart/    absorbing_*, container*, full/half*, hardcore_*, vehicle_*
        ├── gui/recipe_book.png
        ├── gui/sprites/recipe_book/  button, tab, filter, furnace_filter, page, slot,
        │                             overlay_recipe (+ .mcmeta), crafting/furnace_overlay (30 files)
        ├── gui/sprites/widget/       button, button_highlighted, button_disabled,
        │                             text_field, text_field_highlighted,
        │                             slider, slider_highlighted,
        │                             slider_handle, slider_handle_highlighted (+ .mcmeta)
        ├── gui/sprites/tooltip/      background, frame (+ .mcmeta)
        ├── gui/sprites/toast/        advancement, recipe, tutorial, now_playing, system
        │                             (+ .mcmeta for the last three)
        └── item/                     golden_apple, potion, splash_potion, lingering_potion, ender_eye
```

`pack.mcmeta` covers formats 88.0 (26.2) through 97.x (26.3). The `mc_26_2` overlay is applied only at format 88, where it swaps in the 26.2 shader:

```json
{
  "pack": {
    "description": { "text": "HotbarX - Crimson Obsidian UI\n", "color": "#FF0000", "extra": [ { "text": "Heavy Knight Edition", "color": "#8C8C8C" } ] },
    "min_format": 88,
    "max_format": 97
  },
  "overlays": {
    "entries": [
      { "min_format": 88, "max_format": 88, "directory": "mc_26_2" }
    ]
  }
}
```

This was checked against the games' own metadata codecs, run against the 26.2 and 26.3 jars:
- **Format range:** both parse it as [88.0, 97.*], which includes 97.1 and 88.0.
- **Overlay:** 26.3 activates none; 26.2 activates `mc_26_2`.
- **Not checked:** the description, because its text-component codec needs the full game bootstrap. Since v0.7 it has a second, `steel-400` line, "Heavy Knight Edition", as an `extra` component. **(verify in-game)**

## 12. Acceptance tests

**Automated** — both must exit 0:

```
java tools/BuildTextures.java [path\to\client.jar] [--preview <dir>] [--readme docs\images]
powershell -ExecutionPolicy Bypass -File tools\Test-Palette.ps1 pack -Strict
```

The generator reads the vanilla templates from the client jar and writes `pack/`. Reruns are byte-identical. It fails the build on any of these:
- an off-palette pixel
- a semi-transparent item pixel
- an unlit hotbar anchor in any frame, or a missing selection terminal
- a cell pixel other than `obsidian` at α `0xE6`
- a text-safe zone that isn't flat
- a `generic_54` seam row that isn't plain
- a channel or free-area bolt pixel that isn't free
- a crest over a pixel that isn't plain fill

`--preview` writes enlarged composites for review (including `hud.png`, the survival HUD):
- chests at 1, 3 and 6 rows
- the recipe book open next to the inventory
- the hotbar with the selection on every slot
- buttons at five widths
- items in slots

`--readme docs\images` writes the README images from the finished textures, laid out as the game draws them: labels in the game font (from the client jar) and in the colours this pack gives them, item stacks with counts, a tooltip, `system.png` (toasts, a tooltip and sliders), and `hud.gif`, the HUD animated on the same timelines as the `.mcmeta` files. They are committed, so rerun it after every texture change. The images contain a few vanilla item textures from the client jar.

**In-game** — in 26.3, and once in 26.2 to confirm the overlay shader. Use GUI scale 1–4 and Auto, and run with an Iris shaderpack on and off:

- [ ] Chest (3 rows), large chest (6), barrel, ender chest and a 1-row server menu: side borders are continuous at every height, with no seam at the bottom piece.
- [ ] Survival inventory, shulker box and crafting table: arcs stay clear of slots and labels, and the free-area bolts stay clear of the recipe-book button, with the book open and closed.
- [ ] Every label is crimson and readable, including a chest renamed in an anvil.
- [ ] Hotbar: select each of the 9 slots. The terminals meet the band arcs with no gap in every animation frame. A netherite sword, obsidian and coal are each readable in a cell.
- [ ] Recipe book: open it from the inventory and from the crafting table. Check search, tabs, the "craftable only" toggle, page arrows and the variant popup. Craftable and uncraftable recipes must be clearly different.
- [ ] Offhand cell on both sides; attack indicator.
- [ ] Eat the Blood Apple: the name is red in the tooltip and above the hotbar, and no gold hearts appear.
- [ ] Status bars:
  - Full, half and empty hearts, food and armor are each clearly readable, on a bright sky and at night.
  - Get poisoned, withered and frozen; those hearts must still show their vanilla colours.
  - The Hunger effect makes the drumsticks dull.
  - The XP level number is pale.
  - At an anvil, an affordable cost is pale and "Too Expensive!" is red.
- [ ] Pause menu: text is red, the hover state works, and buttons look right at different widths (pause menu, Options screen, title screen). The chamfered corners and rivets are never cut off.
- [ ] Options screen: sliders (FOV, volume) show the steel handle at both ends of the track, and hover lights track and handle.
- [ ] Corner guards: every themed panel shows all four guards, and the corner arcs start at the arm tips.
- [ ] Tooltips: a one-line and a long multi-line tooltip, near each screen edge. The frame is unbroken, the lock-on brackets sit on all four corners, and the text keeps its gap from the frame.
- [ ] Toasts: earn an advancement ("Title Acquired!" in red), unlock a recipe (dark text readable on the steel plate), and start a music track with the "Now Playing" toast enabled.
- [ ] Resource-pack list: the helm icon and the two-line description ("Heavy Knight Edition" in gray).
- [ ] Potions: Healing and Swiftness liquids still show different colours.

## 13. Out of scope (v1)

- **Not covered:** modded screens (the profile has ~230 mods), the other vanilla containers (furnaces, anvil, villager, brewing, enchanting, beacon, creative tabs, …), fonts and world textures. In the HUD: air bubbles, the horse jump bar and the locator bar.
- **System toast text:** its title stays yellow (`#FFFF00`, set in code; §10).
- **Still vanilla:** the status-effect backgrounds still draw over themed screens. The effect panels appear right after eating the Blood Apple. They pass the palette check (no gold), but they stay vanilla gray.
- **Interim look:** with the shader active, labels on the unthemed containers turn `#FF0000` on light gray (2.3:1). They stay readable, but contrast is low until those screens are themed.
