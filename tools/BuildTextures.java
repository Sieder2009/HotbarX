import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;

/**
 * Generates the HotbarX textures from the vanilla templates in a Minecraft 26.3 (or 26.2) client jar,
 * following docs/SPEC.md. Run from the repository root:
 *
 *   java tools/BuildTextures.java [path/to/client.jar] [--preview <dir>] [--readme <dir>]
 *
 * Output goes to pack/. The build is deterministic and fails on any off-palette pixel or broken
 * layout rule (anchors, terminals, text-safe zones, the generic_54 seam). --preview writes enlarged
 * review sheets; --readme writes the README images (docs/images) from the finished textures.
 */
public class BuildTextures {

    // Palette tokens (SPEC §4). Every token has G == B.
    static final int VOID = 0x000000, OBSIDIAN = 0x0A0A0A, OBSIDIAN_2 = 0x141414, CHARCOAL = 0x1E1E1E,
            SLATE = 0x2A2A2A, BLOOD_950 = 0x1A0000, BLOOD_900 = 0x330000, BLOOD_700 = 0x660000,
            BLOOD = 0x990000, CRIMSON_600 = 0xCC0000, CRIMSON = 0xFF0000, CRIMSON_HOT = 0xFF6666,
            CORE = 0xFFE5E5;
    /** Heavy-knight steel (SPEC §4): plate armour on corners, rivets, buttons and the slider handle. */
    static final int STEEL_800 = 0x383838, STEEL_600 = 0x5A5A5A, STEEL_400 = 0x8C8C8C, STEEL_200 = 0xC8C8C8;
    static final Set<Integer> TOKENS = Set.of(VOID, OBSIDIAN, OBSIDIAN_2, CHARCOAL, SLATE, BLOOD_950,
            BLOOD_900, BLOOD_700, BLOOD, CRIMSON_600, CRIMSON, CRIMSON_HOT, CORE, STEEL_800, STEEL_600, STEEL_400, STEEL_200);
    /** Colours that count as a lit lightning pixel. */
    static final Set<Integer> LIT = Set.of(CRIMSON_600, CRIMSON, CRIMSON_HOT, CORE);
    static final int CELL = withAlpha(OBSIDIAN, 0xE6);

    final ZipFile jar;
    final Path pack;
    final Map<String, Img> built = new LinkedHashMap<>();
    int written;

    BuildTextures(ZipFile jar, Path pack) {
        this.jar = jar;
        this.pack = pack;
    }

    public static void main(String[] args) throws Exception {
        Path jarPath = null, preview = null, readme = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--preview")) preview = Path.of(args[++i]);
            else if (args[i].equals("--readme")) readme = Path.of(args[++i]);
            else jarPath = Path.of(args[i]);
        }
        if (jarPath == null) jarPath = findJar();
        Path pack = Path.of("pack");
        if (!Files.exists(pack.resolve("pack.mcmeta"))) throw new IllegalStateException("Run from the repository root (pack/pack.mcmeta not found).");
        try (ZipFile jar = new ZipFile(jarPath.toFile())) {
            BuildTextures b = new BuildTextures(jar, pack);
            b.containers();
            b.recipeBook();
            b.hotbar();
            b.hotbarSelection();
            b.offhand();
            b.attackIndicator();
            b.hearts();
            b.statusIcons();
            b.experienceBar();
            b.slotSprites();
            b.buttons();
            b.tooltip();
            b.toasts();
            b.items();
            b.packIcon();
            System.out.println("Wrote " + b.written + " files from " + jarPath);
            if (preview != null) {
                b.writePreviews(preview);
                System.out.println("Previews in " + preview.toAbsolutePath());
            }
            if (readme != null) {
                b.writeReadmeImages(readme);
                System.out.println("README images in " + readme.toAbsolutePath());
            }
        }
    }

    /** Newest supported client first. The templates this generator reads are identical in 26.2 and 26.3. */
    static Path findJar() throws IOException {
        String appData = System.getenv("APPDATA");
        for (String version : new String[] {"26.3", "26.2"}) {
            Path vanilla = Path.of(appData, ".minecraft", "versions", version, version + ".jar");
            if (Files.exists(vanilla)) return vanilla;
            Path modrinth = Path.of(appData, "ModrinthApp", "meta", "versions");
            if (!Files.isDirectory(modrinth)) continue;
            try (var dirs = Files.list(modrinth)) {
                for (Path dir : dirs.sorted().toList()) {
                    String name = dir.getFileName().toString();
                    Path jar = dir.resolve(name + ".jar");
                    if ((name.equals(version) || name.startsWith(version + "-")) && Files.exists(jar)) return jar;
                }
            }
        }
        throw new IllegalStateException("No 26.3 or 26.2 client jar found; pass its path as the first argument.");
    }

    // ------------------------------------------------------------------ containers (SPEC §5, §6)

    record Arc(int x, int y, int half) {}

    /**
     * Two arcs on every side, plus one arc that bursts out of each corner guard, turning clockwise
     * (down, left, up, right) like a pinwheel: about half of the free perimeter crackles.
     */
    static List<Arc> standardArcs(int w, int h) {
        int outH = GUARD_H + 11, outV = GUARD_V + 11;
        List<Arc> arcs = new ArrayList<>(List.of(new Arc(2, outV, 10), new Arc(w - 3 - outH, 2, 10),
                new Arc(w - 3, h - 3 - outV, 10), new Arc(outH, h - 3, 10)));
        for (int x : new int[] {w * 3 / 10, w * 6 / 10}) arcs.add(new Arc(x, 2, 10));
        for (int x : new int[] {w * 4 / 10, w * 7 / 10}) arcs.add(new Arc(x + 8, h - 3, 11));
        for (int y : new int[] {h * 4 / 10, h * 7 / 10}) arcs.add(new Arc(2, y, 10));
        for (int y : new int[] {h * 3 / 10, h * 6 / 10}) arcs.add(new Arc(w - 3, y + 6, 10));
        return arcs;
    }

    // Channels are free rows between slot groups (x0, y0, x1, y1, inclusive), at least 1 px from every slot frame.
    static final int[] HOTBAR_GAP = {4, 138, 171, 139};
    static final int[] LABEL_GAP = {94, 72, 171, 81};

    void containers() throws IOException {
        // The free areas hold a tower-shield crest; lightning strikes it from the recipe button or from the title row.
        container("inventory", new int[][] {{95, 4, 166, 15}}, new int[0][], standardArcs(176, 166),
                new int[][] {{4, 80, 171, 81}, HOTBAR_GAP, {126, 55, 151, 78}}, new int[][] {{154, 58}}, false);
        container("shulker_box", new int[][] {{6, 4, 166, 15}, {6, 71, 90, 82}}, new int[0][], standardArcs(176, 166),
                new int[][] {LABEL_GAP, HOTBAR_GAP}, new int[0][], false);
        container("crafting_table", new int[][] {{27, 4, 166, 15}, {6, 71, 90, 82}}, new int[0][], standardArcs(176, 166),
                new int[][] {LABEL_GAP, HOTBAR_GAP, {151, 16, 165, 33}, {151, 53, 165, 70}}, new int[][] {{152, 35}}, false);
        // generic_54: no long arcs on the side bands over rows 14-127; those get the repeated 18-row tile instead.
        container("generic_54", new int[][] {{6, 4, 166, 15}, {6, 127, 90, 138}},
                new int[][] {{0, 14, 7, 127}, {168, 14, 175, 127}},
                List.of(new Arc(GUARD_H + 11, 2, 10), new Arc(60, 2, 10), new Arc(110, 2, 10), new Arc(173 - GUARD_H - 11, 2, 10),
                        new Arc(GUARD_H + 11, 219, 10), new Arc(78, 219, 11), new Arc(131, 219, 11),
                        new Arc(2, 150, 9), new Arc(2, 188, 9), new Arc(173, 145, 9), new Arc(173, 219 - GUARD_V - 11, 10)),
                new int[][] {{94, 128, 171, 137}, {4, 194, 171, 195}}, new int[0][], true);
    }

    void container(String name, int[][] textZones, int[][] noArcRects, List<Arc> arcs, int[][] channels, int[][] crests, boolean seamTile) throws IOException {
        String path = "gui/container/" + name + ".png";
        Img v = read(path);
        int[][] d = distance(v);
        for (int y = 0; y < v.h; y++)
            for (int x = 0; x < v.w; x++)
                if (v.alpha(x, y) != 0 && d[x][y] > 4 && v.rgb(x, y) == 0x555555)
                    throw new IllegalStateException(name + ": bevel colour inside the panel at " + x + "," + y);
        framedPanel(path, v, mapContainerColors(v, name), textZones, noArcRects, arcs, channels, crests, seamTile);
    }

    /** Frame rings, corner guards, crests, perimeter arcs, channel bolts and halo on an already recoloured panel (SPEC §5.2). */
    void framedPanel(String path, Img v, Img img, int[][] textZones, int[][] noArcRects, List<Arc> arcs, int[][] channels, int[][] crests, boolean seamTile) throws IOException {
        String name = path.substring(path.lastIndexOf('/') + 1, path.length() - 4);
        int[][] d = distance(img);
        int[] ring = {0, VOID, BLOOD, BLOOD_900, BLOOD_950};
        for (int y = 0; y < img.h; y++)
            for (int x = 0; x < img.w; x++) {
                if (img.alpha(x, y) != 0 && d[x][y] <= 4) img.set(x, y, opaque(ring[d[x][y]]));
            }
        boolean[][] guard = cornerGuards(img, d);
        for (int[] c : crests) crest(img, c[0], c[1], name);
        Img plain = img.copy();

        boolean[][] allowed = arcMask(img, d, textZones, noArcRects, (x, y) -> !guard[x][y]);
        List<int[]> loop = ringLoop(d, img.w, img.h, 3);
        int[][] inward = inwardDirs(loop, d);
        Random rnd = rng(name);
        Glow glow = new Glow();
        for (Arc a : arcs) edgeBolt(img, loop, inward, a, allowed, rnd, glow);
        for (int[] ch : channels) channelBolt(img, ch, allowed, rnd, glow, name);

        boolean[][] glowMask = allowed;
        if (seamTile) {
            // Short sparks that live strictly inside the tile rows 19-32, then repeated every 18 rows (SPEC §6.2).
            boolean[][] tile = arcMask(img, d, textZones, new int[0][], (x, y) -> y >= 19 && y <= 32 && (x <= 6 || x >= 169));
            edgeBolt(img, loop, inward, new Arc(2, 26, 5), tile, rnd, glow);
            edgeBolt(img, loop, inward, new Arc(173, 25, 5), tile, rnd, glow);
            glowMask = or(allowed, tile);
        }
        glow(img, glow, glowMask);
        if (seamTile) {
            for (int k = 1; k <= 5; k++)
                for (int y = 17; y <= 34; y++)
                    for (int x = 0; x < img.w; x++)
                        if (x <= 6 || x >= 169) img.set(x, y + 18 * k, img.get(x, y));
            for (int y : new int[] {16, 17, 34, 126})
                for (int x = 0; x < img.w; x++)
                    if ((x <= 6 || x >= 169) && img.get(x, y) != plain.get(x, y))
                        throw new IllegalStateException(name + ": seam row " + y + " is not plain at x=" + x);
        }
        for (int[] z : textZones)
            for (int y = z[1]; y <= z[3]; y++)
                for (int x = z[0]; x <= z[2]; x++)
                    if (img.get(x, y) != opaque(OBSIDIAN))
                        throw new IllegalStateException(name + ": text-safe zone not flat at " + x + "," + y + " (" + hex(img.rgb(x, y)) + ", vanilla " + hex(v.rgb(x, y)) + ")");
        write(path, img, false);
    }

    // ------------------------------------------------------------------ recipe book (SPEC §6.6)

    void recipeBook() throws IOException {
        String path = "gui/recipe_book.png";
        Img v = read(path);
        Map<Integer, Integer> lens = Map.ofEntries(Map.entry(0xC6C6C6, SLATE), Map.entry(0x20BAD5, CRIMSON),
                Map.entry(0x1F82A7, CRIMSON_600), Map.entry(0x206384, BLOOD), Map.entry(0xFFFFFF, CORE),
                Map.entry(0x543823, BLOOD_900), Map.entry(0x9E5A33, BLOOD_700), Map.entry(0xAF7246, BLOOD),
                Map.entry(0x8B8B8B, CHARCOAL), Map.entry(0x000000, VOID), Map.entry(0x373737, OBSIDIAN_2));
        // The inner well keeps the slot bevel (dark top/left, lit bottom/right); the outer bevel becomes the frame rings.
        Map<Integer, Integer> panel = Map.of(0xC6C6C6, OBSIDIAN, 0x373737, OBSIDIAN_2, 0xFFFFFF, BLOOD_700,
                0x000000, BLOOD_900, 0x555555, BLOOD_900, 0x8B8B8B, BLOOD_700);
        Img img = recolor(v, (x, y) -> x >= 10 && x <= 23 && y >= 14 && y <= 27 ? lens : panel, "recipe_book");
        framedPanel(path, v, img, new int[0][], new int[0][], standardArcs(149, 168), new int[0][], new int[0][], false);

        Map<Integer, Integer> book = Map.of(0x56AD56, CRIMSON, 0x478E47, CRIMSON_600, 0x3D7A3D, BLOOD, 0x2A592A, BLOOD_700,
                0x1B361B, BLOOD_900, 0xD6D6D6, CORE, 0xB7B7B7, CRIMSON_HOT, 0x999999, BLOOD, 0x5B5B5B, BLOOD_700, 0x161005, VOID);
        sprite("recipe_book/button", merge(book, Map.of(0x000000, VOID, 0xFFFFFF, BLOOD, 0x0C0500, VOID, 0xC6C6C6, OBSIDIAN, 0x555555, BLOOD_700)));
        sprite("recipe_book/button_highlighted", merge(book, Map.of(0x00073E, VOID, 0xFFFFFF, CRIMSON_HOT, 0x0C0500, VOID, 0x8892C9, CHARCOAL, 0x343E75, BLOOD)));

        sprite("recipe_book/tab", Map.of(0x000000, VOID, 0xFFFFFF, BLOOD_700, 0xC3C3C3, OBSIDIAN, 0x9C9C9C, BLOOD_900));
        sprite("recipe_book/tab_selected", Map.of(0x000000, VOID, 0xFFFFFF, CRIMSON, 0xE4E4E4, CHARCOAL, 0x9C9C9C, BLOOD));

        // "Show craftable only" toggles: crafting-table or furnace icon, check (on) or cross (off), blue hover in vanilla.
        Map<Integer, Integer> table = Map.of(0x473A23, BLOOD_700, 0x9E5A33, CRIMSON_600, 0xAF7246, CRIMSON, 0x4B2B18, BLOOD_900,
                0x9F844D, BLOOD, 0x735E39, BLOOD_700, 0x543823, BLOOD_900);
        Map<Integer, Integer> furnace = Map.of(0xB7B7B7, CRIMSON, 0xA3A3A3, CRIMSON_600, 0x8E8E8E, CRIMSON_600, 0x848484, BLOOD,
                0x7F7F7F, BLOOD, 0x666666, BLOOD_700, 0x515151, BLOOD_700, 0x494949, BLOOD_900, 0x3F3F3F, BLOOD_900);
        Map<Integer, Integer> marks = Map.of(0x80B847, CORE, 0xB43D2C, CRIMSON_600, 0x000000, VOID);
        Map<Integer, Integer> on = Map.of(0xAEC57A, CRIMSON_HOT, 0x218756, CHARCOAL, 0x235F5B, BLOOD_700, 0x0E2725, BLOOD_700);
        Map<Integer, Integer> off = Map.of(0xFFFFFF, BLOOD_700, 0xC6C6C6, BLOOD_700, 0x8B8B8B, OBSIDIAN_2);
        Map<Integer, Integer> hover = Map.of(0xFFFFFF, CRIMSON_HOT, 0xAFB6DA, CRIMSON_HOT, 0x8892C9, SLATE, 0x444964, BLOOD);
        for (String kind : new String[] {"filter", "furnace_filter"}) {
            Map<Integer, Integer> icon = kind.equals("filter") ? table : furnace;
            sprite("recipe_book/" + kind + "_enabled", merge(icon, marks, on));
            sprite("recipe_book/" + kind + "_disabled", merge(icon, marks, off));
            sprite("recipe_book/" + kind + "_enabled_highlighted", merge(icon, marks, hover));
            sprite("recipe_book/" + kind + "_disabled_highlighted", merge(icon, marks, hover));
        }

        for (String dir : new String[] {"forward", "backward"}) {
            sprite("recipe_book/page_" + dir, Map.of(0x000000, VOID, 0xFFFFFF, CRIMSON, 0xC3C3C3, BLOOD, 0x808080, BLOOD_700));
            sprite("recipe_book/page_" + dir + "_highlighted", Map.of(0x00073E, VOID, 0xFFFFFF, CORE, 0x8892C9, CRIMSON, 0x343E75, BLOOD));
        }

        // Craftable recipes glow; uncraftable ones (red in vanilla) go dim.
        Map<Integer, Integer> craftable = Map.of(0xE7E7E7, BLOOD, 0x808080, BLOOD_900, 0x000000, VOID, 0xC3C3C3, CHARCOAL, 0xFFFFFF, BLOOD_700);
        Map<Integer, Integer> uncraftable = Map.of(0x8A2222, BLOOD_900, 0x501B1B, VOID, 0x000000, VOID, 0x6A6A6A, OBSIDIAN, 0xC24F4F, BLOOD_700);
        sprite("recipe_book/slot_craftable", craftable);
        sprite("recipe_book/slot_many_craftable", craftable);
        sprite("recipe_book/slot_uncraftable", uncraftable);
        sprite("recipe_book/slot_many_uncraftable", uncraftable);

        // The popup with recipe variants, and the variant buttons in it.
        sprite("recipe_book/overlay_recipe", Map.of(0x000000, VOID, 0xFFFFFF, BLOOD, 0xE4E4E4, OBSIDIAN, 0x9C9C9C, BLOOD_700));
        for (String kind : new String[] {"crafting_overlay", "furnace_overlay"}) {
            sprite("recipe_book/" + kind, Map.of(0x000000, VOID, 0xFFFFFF, BLOOD, 0xC3C3C3, OBSIDIAN_2, 0x808080, BLOOD_700, 0x9C9C9C, BLOOD_700));
            sprite("recipe_book/" + kind + "_highlighted", Map.of(0x00031E, VOID, 0xFFFFFF, CRIMSON_HOT, 0x8892C9, CHARCOAL, 0x5F647F, CRIMSON_600, 0x444964, BLOOD));
            sprite("recipe_book/" + kind + "_disabled", Map.of(0x000000, VOID, 0x555555, BLOOD_900, 0x936B6B, OBSIDIAN, 0x373737, BLOOD_900, 0xFFFFFF, BLOOD_700));
            sprite("recipe_book/" + kind + "_disabled_highlighted", Map.of(0x00031E, VOID, 0x444964, BLOOD, 0x8D7E99, OBSIDIAN_2, 0x5F647F, BLOOD_700, 0xFFFFFF, CRIMSON_600));
        }

        // The search box; these sprites style every text field in the game.
        sprite("widget/text_field", Map.of(0xA0A0A0, BLOOD, 0x000000, VOID));
        sprite("widget/text_field_highlighted", Map.of(0xFFFFFF, CRIMSON, 0x000000, VOID));
    }

    /** Recolours one GUI sprite through an exact colour map and ships vanilla's .mcmeta with it, if it has one. */
    void sprite(String name, Map<Integer, Integer> map) throws IOException {
        String path = "gui/sprites/" + name + ".png";
        write(path, recolor(read(path), (x, y) -> map, name), false);
        if (jar.getEntry("assets/minecraft/textures/" + path + ".mcmeta") != null) copyMeta(path);
    }

    interface ColourMapAt { Map<Integer, Integer> at(int x, int y); }

    static Img recolor(Img v, ColourMapAt maps, String name) {
        Img out = new Img(v.w, v.h);
        for (int y = 0; y < v.h; y++)
            for (int x = 0; x < v.w; x++) {
                int a = v.alpha(x, y);
                if (a == 0) continue;
                Integer c = maps.at(x, y).get(v.rgb(x, y));
                if (c == null) throw new IllegalStateException(name + ": unmapped colour " + hex(v.rgb(x, y)) + " at " + x + "," + y);
                out.set(x, y, withAlpha(c, a));
            }
        return out;
    }

    @SafeVarargs
    static Map<Integer, Integer> merge(Map<Integer, Integer>... parts) {
        Map<Integer, Integer> m = new HashMap<>();
        for (Map<Integer, Integer> part : parts)
            for (Map.Entry<Integer, Integer> e : part.entrySet()) {
                Integer prev = m.put(e.getKey(), e.getValue());
                if (prev != null && !prev.equals(e.getValue())) throw new IllegalStateException("conflicting mapping for " + hex(e.getKey()));
            }
        return m;
    }

    /** Flat recolour of the vanilla grays; slot positions, arrows and frames stay where the game expects them. */
    static Img mapContainerColors(Img v, String name) {
        Img out = new Img(v.w, v.h);
        boolean[] done = new boolean[v.w * v.h];
        for (int y = 0; y < v.h; y++)
            for (int x = 0; x < v.w; x++) {
                int a = v.alpha(x, y);
                if (a == 0) continue;
                if (a != 255) throw new IllegalStateException(name + ": unexpected alpha " + a + " at " + x + "," + y);
                int c = v.rgb(x, y);
                switch (c) {
                    case 0xC6C6C6 -> out.set(x, y, opaque(OBSIDIAN));
                    case 0x373737, 0x555555 -> out.set(x, y, opaque(BLOOD_900));
                    case 0xFFFFFF -> out.set(x, y, opaque(BLOOD_700));
                    case 0x000000 -> out.set(x, y, opaque(VOID));
                    case 0x8B8B8B -> {
                        if (done[y * v.w + x]) break;
                        // 4-connected run of slot gray: a slot well, an arrow, or a lone slot off-corner.
                        List<int[]> comp = new ArrayList<>();
                        ArrayDeque<int[]> q = new ArrayDeque<>();
                        q.add(new int[] {x, y});
                        done[y * v.w + x] = true;
                        boolean touchesPanel = false, nextToDark = false, nextToLight = false;
                        while (!q.isEmpty()) {
                            int[] p = q.poll();
                            comp.add(p);
                            for (int[] o : FOUR) {
                                int nx = p[0] + o[0], ny = p[1] + o[1];
                                int nc = v.alpha(nx, ny) == 0 ? -1 : v.rgb(nx, ny);
                                if (nc == 0xC6C6C6) touchesPanel = true;
                                if (nc == 0x373737) nextToDark = true;
                                if (nc == 0xFFFFFF) nextToLight = true;
                                if (nc == 0x8B8B8B && !done[ny * v.w + nx]) {
                                    done[ny * v.w + nx] = true;
                                    q.add(new int[] {nx, ny});
                                }
                            }
                        }
                        boolean offCorner = comp.size() == 1 && nextToDark && nextToLight;
                        boolean arrow = !offCorner && touchesPanel;
                        int maxX = comp.stream().mapToInt(p -> p[0]).max().getAsInt();
                        for (int[] p : comp) {
                            int col = offCorner ? BLOOD_900 : arrow ? (p[0] == maxX ? CRIMSON : BLOOD) : OBSIDIAN_2;
                            out.set(p[0], p[1], opaque(col));
                        }
                    }
                    default -> throw new IllegalStateException(name + ": unmapped colour " + hex(c) + " at " + x + "," + y);
                }
            }
        return out;
    }

    /** Where arcs and halos may go: the frame band, or plain fill at least 1 px from anything else. */
    static boolean[][] arcMask(Img img, int[][] d, int[][] textZones, int[][] noArcRects, BiIntPredicate extra) {
        boolean[][] m = new boolean[img.w][img.h];
        for (int y = 0; y < img.h; y++)
            for (int x = 0; x < img.w; x++) {
                if (img.alpha(x, y) == 0) continue;
                boolean ok = d[x][y] <= 4;
                if (!ok && img.rgb(x, y) == OBSIDIAN) {
                    ok = true;
                    for (int dy = -1; dy <= 1 && ok; dy++)
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = x + dx, ny = y + dy;
                            if (img.alpha(nx, ny) != 0 && d[nx][ny] >= 5 && img.rgb(nx, ny) != OBSIDIAN) { ok = false; break; }
                        }
                }
                if (inAny(textZones, x, y) || inAny(noArcRects, x, y)) ok = false;
                if (extra != null && !extra.test(x, y)) ok = false;
                m[x][y] = ok;
            }
        return m;
    }

    interface BiIntPredicate { boolean test(int x, int y); }

    // ------------------------------------------------------------------ heavy-knight armour (SPEC §5.7)

    /** Pixel-art legend: steel, blood and crimson tokens by letter; '.' leaves the pixel as it is. */
    static final Map<Character, Integer> ART = Map.ofEntries(Map.entry('O', VOID), Map.entry('1', OBSIDIAN),
            Map.entry('2', OBSIDIAN_2), Map.entry('k', CHARCOAL), Map.entry('s', SLATE), Map.entry('e', BLOOD_950),
            Map.entry('n', BLOOD_900), Map.entry('b', BLOOD_700), Map.entry('B', BLOOD), Map.entry('c', CRIMSON_600),
            Map.entry('C', CRIMSON), Map.entry('h', CRIMSON_HOT), Map.entry('H', CORE), Map.entry('D', STEEL_800),
            Map.entry('m', STEEL_600), Map.entry('L', STEEL_400), Map.entry('W', STEEL_200));

    /**
     * Arm lengths of a corner guard, counted from the panel corner: along the top and bottom edges, and down
     * the sides. The side arms end at row 15, so generic_54's header never reaches its seam row 16.
     */
    static final int GUARD_H = 20, GUARD_V = 15;
    /** The guard's boss, 5×5 over rings 2-6 of the corner: a steel stud lit from the top left around a crimson gem. */
    static final String[] BOSS = {".LLL.", "LmHmD", "LhCcD", "LmBmD", ".DDD."};
    /** A tower shield bearing a crimson sword, point down: the heavy knight's crest. */
    static final String[] CREST = {
            ".OOOOOOOOOOO.",
            "OLLLLLLLLLLmO",
            "OLbbbbWnnnnDO",
            "OLbbbbmnnnnDO",
            "OLbWLLLLLDnDO",
            "OLbbbhCcnnnDO",
            "OLbbbhCcnnnDO",
            "OLbbbhCcnnnDO",
            "OLbbbhCcnnnDO",
            "OLbbbhCcnnnDO",
            ".OLbbhCcnnDO.",
            ".OLbbbCnnnDO.",
            "..OLbbCnnDO..",
            "...OLbnnDO...",
            "....OLnDO....",
            ".....OOO....."};

    /**
     * Riveted steel guards on the four corners of the panel's bounding box: the boss on the corner and a 3-px
     * plate arm along each edge over rings 2-4, lit from the top left, with a rivet, a crimson inlay down its
     * middle and a bevelled tip. Panels under 64 px tall (toasts, the banner) get no side arms. Ring 1 (the
     * outline) stays. Returns the guard pixels; arcs stop where they meet them.
     */
    static boolean[][] cornerGuards(Img img, int[][] d) {
        int x0 = img.w, y0 = img.h, x1 = -1, y1 = -1;
        for (int y = 0; y < img.h; y++)
            for (int x = 0; x < img.w; x++)
                if (img.alpha(x, y) != 0) {
                    x0 = Math.min(x0, x);
                    y0 = Math.min(y0, y);
                    x1 = Math.max(x1, x);
                    y1 = Math.max(y1, y);
                }
        int armV = y1 - y0 + 1 < 64 ? 5 : GUARD_V;
        boolean[][] g = new boolean[img.w][img.h];
        List<int[]> rivets = new ArrayList<>();
        for (int[] c : new int[][] {{x0, y0, 1, 1}, {x1, y0, -1, 1}, {x0, y1, 1, -1}, {x1, y1, -1, -1}}) {
            int bx = Math.min(c[0] + c[2], c[0] + 5 * c[2]), by = Math.min(c[1] + c[3], c[1] + 5 * c[3]);
            for (int ly = 1; ly <= armV; ly++)
                for (int lx = 1; lx <= GUARD_H; lx++) {
                    int x = c[0] + c[2] * lx, y = c[1] + c[3] * ly, r = d[x][y], col;
                    if (img.alpha(x, y) == 0 || r < 2) continue;
                    if (lx <= 5 && ly <= 5) {
                        char ch = BOSS[y - by].charAt(x - bx);
                        if (ch == '.') continue;
                        col = ART.get(ch);
                    } else if (r <= 4 && (lx <= 5 || ly <= 5)) {
                        boolean horizontal = ly <= 5;
                        int along = horizontal ? lx : ly, end = (horizontal ? GUARD_H : armV) - (r - 2);
                        if (along > end) continue;
                        // The outer ring faces up or left on the top and left edges, so it catches the light there.
                        boolean lit = horizontal ? c[3] > 0 : c[2] > 0;
                        col = along == end ? STEEL_800 : (lit ? new int[] {STEEL_400, STEEL_600, STEEL_800} : new int[] {STEEL_800, STEEL_600, STEEL_400})[r - 2];
                        if (r == 3 && along == 7) rivets.add(new int[] {x, y, horizontal ? 1 : 0, horizontal ? 0 : 1});
                        else if (r == 3 && along >= 9 && along < end) col = CRIMSON_600;
                    } else continue;
                    img.set(x, y, opaque(col));
                    g[x][y] = true;
                }
        }
        // A rivet is a glint with its shadow to the right (on top and bottom arms) or below it (on side arms).
        for (int[] rv : rivets) {
            img.set(rv[0], rv[1], opaque(STEEL_200));
            img.set(rv[0] + rv[2], rv[1] + rv[3], opaque(STEEL_800));
        }
        return g;
    }

    /** Paints the crest with its top-left at (x, y); every pixel it covers must be plain fill. */
    static void crest(Img img, int x, int y, String name) {
        for (int r = 0; r < CREST.length; r++)
            for (int c = 0; c < CREST[r].length(); c++) {
                char ch = CREST[r].charAt(c);
                if (ch == '.') continue;
                if (img.get(x + c, y + r) != opaque(OBSIDIAN)) throw new IllegalStateException(name + ": crest over a non-fill pixel at " + (x + c) + "," + (y + r));
                img.set(x + c, y + r, opaque(ART.get(ch)));
            }
    }

    // ------------------------------------------------------------------ lightning (SPEC §5.1)

    /** Bright bolt pixels (they get the halo) and strike points (they get the flare), gathered for the glow pass. */
    record Glow(Set<Long> body, Set<Long> flare) {
        Glow() {
            this(new HashSet<>(), new HashSet<>());
        }
    }

    /** A bolt pixel. {@code tip} is +1 or -1 where a stroke turns back (a tooth tip), 0 elsewhere. */
    record Pt(int x, int y, int loopIdx, int tip) {}

    /**
     * Offsets for one bolt: jags that alternate direction with random lengths (1-5), so the bolt drifts instead
     * of oscillating around a line. Each jag is steep (one step per pixel) or shallow (one per two pixels), with
     * flat runs of up to maxHold pixels at the turns. The reach swells toward the middle, so the bolt leaves its
     * line at one end and sinks back into it at the other.
     */
    static int[] zigzag(int n, int start, int lo, int[] cap, int maxHold, Random rnd) {
        int hi = lo;
        for (int c : cap) hi = Math.max(hi, c);
        int[] o = new int[n];
        int cur = start, target = start, hold = 0, step = 0, dir = rnd.nextBoolean() ? 1 : -1;
        boolean shallow = false;
        for (int k = 0; k < n; k++) {
            double mid = Math.min(k, n - 1 - k) / Math.max(1.0, (n - 1) / 2.0);
            int reach = (int) Math.round(Math.min(1.0, 0.3 + 1.4 * mid) * (hi - lo));
            int a = Math.max(lo, start - reach), b = Math.max(a, Math.min(cap[k], start + reach));
            if (k > 0) {
                if (hold > 0) hold--;
                else if (!shallow || ++step % 2 == 0) cur += Integer.signum(target - cur);
            }
            cur = Math.max(a, Math.min(b, cur));
            target = Math.max(a, Math.min(b, target));
            if (cur == target && b > a) {
                int len = Math.min(b - a, 1 + rnd.nextInt(3) + rnd.nextInt(3));
                dir = -dir;
                if (cur + dir * len < a || cur + dir * len > b) dir = -dir;
                target = Math.max(a, Math.min(b, cur + dir * len));
                hold = rnd.nextInt(maxHold + 1);
                shallow = rnd.nextInt(100) < 30;
                step = 0;
            }
            o[k] = cur;
        }
        return o;
    }

    /** +1 or -1 on the first pixel of every turn where the offsets reverse: the tooth tips of a zig-zag. */
    static int[] tips(int[] o) {
        int[] t = new int[o.length];
        for (int k = 1; k < o.length; k++) {
            if (o[k] == o[k - 1]) continue;
            int j = k + 1;
            while (j < o.length && o[j] == o[k]) j++;
            if (j < o.length && Integer.signum(o[j] - o[k]) == -Integer.signum(o[k] - o[k - 1])) t[k] = Integer.signum(o[k] - o[k - 1]);
        }
        return t;
    }

    /**
     * One bolt along the frame (SPEC §5.2): it follows ring 3 and zig-zags from ring 2 up to three pixels
     * further in, as far as the margin is free (titles, slot clearance).
     */
    static void edgeBolt(Img img, List<int[]> loop, int[][] inward, Arc arc, boolean[][] allowed, Random rnd, Glow glow) {
        int n = loop.size(), center = nearest(loop, arc.x(), arc.y()), len = 2 * arc.half() + 1;
        int[] idx = new int[len], cap = new int[len];
        for (int k = 0; k < len; k++) {
            idx[k] = Math.floorMod(center - arc.half() + k, n);
            int[] lp = loop.get(idx[k]), dir = inward[idx[k]];
            while (cap[k] < 3 && ok(allowed, lp[0] + (cap[k] + 1) * dir[0], lp[1] + (cap[k] + 1) * dir[1])) cap[k]++;
        }
        int[] off = zigzag(len, 0, -1, cap, 1, rnd), tip = tips(off);
        List<Pt> pts = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        int centerPos = -1;
        Pt prev = null;
        for (int k = 0; k < len; k++) {
            int[] lp = loop.get(idx[k]), dir = inward[idx[k]];
            Pt p = new Pt(lp[0] + off[k] * dir[0], lp[1] + off[k] * dir[1], idx[k], tip[k]);
            if (prev != null)
                for (int[] q : between(prev.x(), prev.y(), p.x(), p.y()))
                    if (seen.add(key(q[0], q[1]))) pts.add(new Pt(q[0], q[1], idx[k], 0));
            if (seen.add(key(p.x(), p.y()))) pts.add(p);
            if (k == arc.half()) centerPos = pts.size() - 1;
            prev = p;
        }
        if (centerPos < 0 || !ok(allowed, pts.get(centerPos).x(), pts.get(centerPos).y())) return;
        int lo = centerPos, hi = centerPos;
        while (lo > 0 && ok(allowed, pts.get(lo - 1).x(), pts.get(lo - 1).y())) lo--;
        while (hi < pts.size() - 1 && ok(allowed, pts.get(hi + 1).x(), pts.get(hi + 1).y())) hi++;
        List<Pt> seg = pts.subList(lo, hi + 1);
        if (seg.size() < 5) return;
        paintBolt(img, seg, glow, false);
        for (int k = 2; k < seg.size() - 2; k++) {
            Pt p = seg.get(k);
            if (p.tip() <= 0 || img.rgb(p.x(), p.y()) != CRIMSON || rnd.nextInt(2) != 0) continue;
            int[] a = loop.get(p.loopIdx()), b = loop.get((p.loopIdx() + 1) % n);
            fork(img, p.x(), p.y(), inward[p.loopIdx()], new int[] {Integer.signum(b[0] - a[0]), Integer.signum(b[1] - a[1])},
                    2 + rnd.nextInt(2), allowed, rnd, glow);
        }
    }

    /**
     * A bolt through a free area (x0, y0)-(x1, y1) (SPEC §5.2): across a channel between slot groups, or down
     * an area that is taller than it is wide. Wide areas get a jagged bolt with forks; a 2-row channel gets a
     * line that crackles between its rows.
     */
    static void channelBolt(Img img, int[] ch, boolean[][] allowed, Random rnd, Glow glow, String name) {
        int x0 = ch[0], y0 = ch[1], x1 = ch[2], y1 = ch[3];
        boolean down = y1 - y0 > x1 - x0;
        int n = down ? y1 - y0 + 1 : x1 - x0 + 1, lo = down ? x0 : y0, hi = down ? x1 : y1;
        boolean tall = hi - lo >= 3;
        int[] cap = new int[n];
        java.util.Arrays.fill(cap, hi);
        int[] off = zigzag(n, (lo + hi) / 2, lo, cap, tall ? 1 : 4, rnd), tip = tips(off);
        List<Pt> pts = new ArrayList<>();
        for (int k = 0; k < n; k++) {
            int x = down ? off[k] : x0 + k, y = down ? y0 + k : off[k];
            if (!ok(allowed, x, y)) throw new IllegalStateException(name + ": channel pixel " + x + "," + y + " is not free");
            pts.add(new Pt(x, y, -1, tip[k]));
        }
        paintBolt(img, pts, glow, !tall);
        if (!tall) return;
        boolean[][] inside = new boolean[img.w][img.h];
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) inside[x][y] = allowed[x][y];
        for (int k = 2; k < n - 2; k++) {
            Pt p = pts.get(k);
            if (p.tip() != 0 && img.rgb(p.x(), p.y()) == CRIMSON && rnd.nextInt(2) == 0)
                fork(img, p.x(), p.y(), down ? new int[] {p.tip(), 0} : new int[] {0, p.tip()}, down ? new int[] {0, 1} : new int[] {1, 0},
                        2 + rnd.nextInt(3), inside, rnd, glow);
        }
    }

    /** A short branch off a tooth tip: one diagonal step, then onward along nrm, tapering to blood. */
    static void fork(Img img, int x, int y, int[] nrm, int[] tan, int len, boolean[][] allowed, Random rnd, Glow glow) {
        int s = rnd.nextBoolean() ? 1 : -1;
        List<int[]> fp = new ArrayList<>();
        for (int k = 0; k < len; k++) {
            boolean diag = k == 0 || rnd.nextBoolean();
            int dx = Integer.signum(nrm[0] + (diag ? s * tan[0] : 0)), dy = Integer.signum(nrm[1] + (diag ? s * tan[1] : 0));
            if (dx == 0 && dy == 0) break;
            x += dx;
            y += dy;
            if (!ok(allowed, x, y) || LIT.contains(img.rgb(x, y))) break;
            fp.add(new int[] {x, y});
        }
        if (fp.size() < 2) return;
        for (int k = 0; k < fp.size(); k++) {
            int c = k == fp.size() - 1 ? BLOOD : k == fp.size() - 2 ? CRIMSON_600 : CRIMSON;
            img.set(fp.get(k)[0], fp.get(k)[1], opaque(c));
            if (c == CRIMSON) glow.body().add(key(fp.get(k)[0], fp.get(k)[1]));
        }
    }

    /**
     * Paints a bolt (SPEC §5.1): crimson body, ends tapering crimson-600 → blood, and one strike point per
     * ~40 px on an inward tooth tip: a core pixel with crimson-hot on either side. Sparks under 14 px get no
     * strike point. A dim bolt (a conduit) is crimson-600 except for a crimson pulse around each strike point.
     */
    static void paintBolt(Img img, List<Pt> seg, Glow glow, boolean dim) {
        int len = seg.size(), count = len < 14 ? 0 : Math.max(1, len / 40);
        List<Integer> strikes = new ArrayList<>();
        for (int j = 0; j < count; j++) strikes.add(strikeNear(seg, (2 * j + 1) * len / (2 * count)));
        for (int k = 0; k < len; k++) {
            Pt p = seg.get(k);
            int fromEnd = Math.min(k, len - 1 - k), near = Integer.MAX_VALUE;
            for (int s : strikes) near = Math.min(near, Math.abs(k - s));
            int c = fromEnd == 0 ? BLOOD : fromEnd == 1 ? CRIMSON_600
                    : near == 0 ? CORE : near == 1 ? CRIMSON_HOT : dim && near > 5 ? CRIMSON_600 : CRIMSON;
            img.set(p.x(), p.y(), opaque(c));
            if (fromEnd >= 2 && c != CRIMSON_600) glow.body().add(key(p.x(), p.y()));
            if (c == CORE) glow.flare().add(key(p.x(), p.y()));
        }
    }

    /** The inward tooth tip nearest to position k (any tip if there is no inward one), clear of the tapered ends. */
    static int strikeNear(List<Pt> seg, int k) {
        int best = k, bd = Integer.MAX_VALUE;
        for (int pass = 0; pass < 2 && bd == Integer.MAX_VALUE; pass++)
            for (int j = 3; j < seg.size() - 3; j++) {
                int t = seg.get(j).tip();
                if ((pass == 0 ? t > 0 : t != 0) && Math.abs(j - k) < bd) {
                    bd = Math.abs(j - k);
                    best = j;
                }
            }
        return best;
    }

    /** The glow pass: a blood-900 halo beside bright bolt pixels and a blood-700 flare around every strike point. */
    static void glow(Img img, Glow glow, boolean[][] mask) {
        for (long k : glow.body()) {
            int x = (int) (k >> 32), y = (int) k;
            for (int[] o : FOUR) {
                int nx = x + o[0], ny = y + o[1], c = img.get(nx, ny);
                if (ok(mask, nx, ny) && (c == opaque(OBSIDIAN) || c == opaque(BLOOD_950))) img.set(nx, ny, opaque(BLOOD_900));
            }
        }
        for (long k : glow.flare()) {
            int x = (int) (k >> 32), y = (int) k;
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx, ny = y + dy, c = img.get(nx, ny);
                    if (ok(mask, nx, ny) && (c == opaque(OBSIDIAN) || c == opaque(BLOOD_950) || c == opaque(BLOOD_900))) img.set(nx, ny, opaque(BLOOD_700));
                }
        }
    }

    // ------------------------------------------------------------------ hotbar (SPEC §7)

    static final int HOTBAR_FRAMES = 6;
    /** (frame, ticks): idle, a sweep, idle again, the flash and its afterglow; 60 ticks with two bursts. */
    static final int[][] HOTBAR_TIMELINE = {{0, 30}, {1, 2}, {2, 2}, {3, 2}, {0, 20}, {4, 2}, {5, 2}};
    static final String HOTBAR_MCMETA = animationMeta(22, 2, HOTBAR_TIMELINE);

    /** An animation .mcmeta: frames that last the frametime are bare indices, and a plain 0..n-1 run is left implicit. */
    static String animationMeta(int height, int frametime, int[][] timeline) {
        List<String> frames = new ArrayList<>();
        boolean plain = true;
        for (int i = 0; i < timeline.length; i++) {
            int[] f = timeline[i];
            plain &= f[0] == i && f[1] == frametime;
            frames.add(f[1] == frametime ? String.valueOf(f[0]) : "{ \"index\": " + f[0] + ", \"time\": " + f[1] + " }");
        }
        StringBuilder sb = new StringBuilder("{\n  \"animation\": {\n    \"height\": " + height);
        if (frametime != 1) sb.append(",\n    \"frametime\": ").append(frametime);
        if (!plain) sb.append(",\n    \"frames\": [ ").append(String.join(", ", frames)).append(" ]");
        return sb.append("\n  }\n}\n").toString();
    }

    /** The frame a timeline shows at tick t, looping. */
    static int frameAt(int[][] timeline, int t) {
        int total = 0;
        for (int[] f : timeline) total += f[1];
        t %= total;
        for (int[] f : timeline) {
            if (t < f[1]) return f[0];
            t -= f[1];
        }
        throw new IllegalStateException();
    }

    static boolean isCell(int x) {
        return x >= 3 && x <= 178 && (x - 3) % 20 < 16;
    }

    static boolean isAnchor(int x) {
        return x >= 3 && x <= 178 && (x % 20 == 3 || x % 20 == 18);
    }

    void hotbar() throws IOException {
        Random rnd = rng("hotbar");
        Img strip = new Img(182, 22 * HOTBAR_FRAMES);
        for (int f = 0; f < HOTBAR_FRAMES; f++) {
            Img fr = hotbarFrame(f, rnd);
            for (int x = 3; x <= 178; x++) {
                if (!isAnchor(x)) continue;
                if (!LIT.contains(fr.rgb(x, 1)) || !LIT.contains(fr.rgb(x, 20)))
                    throw new IllegalStateException("hotbar frame " + f + ": anchor x=" + x + " not lit");
            }
            for (int y = 3; y <= 18; y++)
                for (int x = 0; x < 182; x++)
                    if (isCell(x) && fr.get(x, y) != CELL) throw new IllegalStateException("hotbar frame " + f + ": cell pixel " + x + "," + y);
            strip.paste(fr, 0, 22 * f);
        }
        write("gui/sprites/hud/hotbar.png", strip, false);
        writeText("assets/minecraft/textures/gui/sprites/hud/hotbar.png.mcmeta", HOTBAR_MCMETA);
    }

    static Img hotbarBase() {
        Img im = new Img(182, 22);
        for (int y = 0; y < 22; y++)
            for (int x = 0; x < 182; x++) {
                int c;
                if (x == 0 || x == 181 || y == 0 || y == 21) c = opaque(VOID);
                else if (y <= 2 || y >= 19 || x <= 2 || x >= 179) c = opaque(y == 2 || y == 19 || x == 2 || x == 179 ? BLOOD_700 : CHARCOAL);
                else if (isCell(x)) c = CELL;
                else c = opaque(OBSIDIAN_2);
                im.set(x, y, c);
            }
        // Each separator carries a riveted steel strap: a 2×2 stud at either end and a dark strap between them.
        for (int k = 0; k < 8; k++) {
            int x = 20 + 20 * k;
            for (int y = 6; y <= 15; y++) {
                im.set(x, y, opaque(STEEL_800));
                im.set(x + 1, y, opaque(CHARCOAL));
            }
            for (int y : new int[] {4, 16}) {
                im.set(x, y, opaque(STEEL_200));
                im.set(x + 1, y, opaque(STEEL_400));
                im.set(x, y + 1, opaque(STEEL_400));
                im.set(x + 1, y + 1, opaque(STEEL_800));
            }
        }
        return im;
    }

    /** Frame 0 is the idle still; 1-3 sweep a strike left to right; 4 is the full flash; 5 the afterglow. */
    static Img hotbarFrame(int f, Random rnd) {
        Img im = hotbarBase();
        int lo = switch (f) { case 1 -> 0; case 2 -> 60; case 3 -> 120; case 4 -> 0; default -> -1; };
        int hi = switch (f) { case 1 -> 60; case 2 -> 120; case 3 -> 181; case 4 -> 181; default -> -1; };
        int bendHot = f == 5 ? 60 : 25;
        List<int[]> rail = new ArrayList<>(); // x, y, bend
        IntPredicate railAnchor = x -> x == 1 || x == 180 || isAnchor(x);
        int[] top = walk(182, railAnchor, rnd), bottom = walk(182, railAnchor, rnd);
        for (int x = 1; x <= 180; x++) {
            rail.add(new int[] {x, 1 + top[x], top[x] != top[x - 1] ? 1 : 0});
            rail.add(new int[] {x, 20 + bottom[x], bottom[x] != bottom[x - 1] ? 1 : 0});
        }
        IntPredicate sideAnchor = y -> y == 1 || y == 20;
        int[] left = walk(22, sideAnchor, rnd), right = walk(22, sideAnchor, rnd);
        for (int y = 2; y <= 19; y++) {
            rail.add(new int[] {1 + left[y], y, left[y] != left[y - 1] ? 1 : 0});
            rail.add(new int[] {180 + right[y], y, right[y] != right[y - 1] ? 1 : 0});
        }
        int hot = 0, cores = 0;
        for (int[] r : rail) if (r[0] >= lo && r[0] <= hi) hot++;
        for (int[] r : rail) {
            boolean strike = r[0] >= lo && r[0] <= hi, bend = r[2] == 1;
            int c;
            if (strike) {
                c = CRIMSON;
                if (bend && (cores + 1) * 4 <= hot && rnd.nextInt(100) < 40) { c = CORE; cores++; }
            } else c = bend && rnd.nextInt(100) < bendHot ? CRIMSON : CRIMSON_600;
            im.set(r[0], r[1], opaque(c));
        }
        // Bolts down the separators connect the slots (separator k spans x 19+20k .. 22+20k).
        List<Integer> seps = new ArrayList<>();
        for (int k = 0; k < 8; k++) if (lo >= 0 && 19 + 20 * k >= lo && 22 + 20 * k <= hi) seps.add(k);
        java.util.Collections.shuffle(seps, rnd);
        int full = f == 4 ? 4 : f >= 1 && f <= 3 ? 2 : 0;
        for (int i = 0; i < Math.min(full, seps.size()); i++) bolt(im, seps.get(i), 3, 18, rnd, false);
        // Stubs grow from the rail at y0 and taper toward y1.
        if (f == 0) {
            bolt(im, 1, 3, 18, rnd, false);
            bolt(im, 6, 3, 18, rnd, false);
            bolt(im, 3, 3, 7, rnd, true);
            bolt(im, 4, 18, 13, rnd, true);
        } else if (f == 5) {
            bolt(im, 2, 3, 18, rnd, false);
            bolt(im, 5, 3, 9, rnd, true);
            bolt(im, 7, 18, 12, rnd, true);
        }
        return im;
    }

    /**
     * Offsets in {-1, 0, 1}: 0 at every anchor, steps of at most 1. The bands are only 3 px tall, so
     * kinks stay rare (no straight run longer than 7) or the rail reads as a dashed line, not lightning.
     */
    static int[] walk(int len, IntPredicate anchor, Random rnd) {
        int[] v = new int[len];
        int cur = 0, run = 0;
        for (int i = 0; i < len; i++) {
            if (anchor.test(i)) {
                run = cur == 0 ? run + 1 : 1;
                cur = 0;
            } else {
                int toAnchor = Integer.MAX_VALUE;
                for (int j = i + 1; j < len; j++) if (anchor.test(j)) { toAnchor = j - i; break; }
                List<Integer> options = new ArrayList<>();
                for (int c = cur - 1; c <= cur + 1; c++) if (c >= -1 && c <= 1 && Math.abs(c) <= toAnchor) options.add(c);
                int next = cur;
                boolean mustTurn = run >= 7 && options.size() > 1;
                if (mustTurn || rnd.nextInt(100) < 12) {
                    options.remove(Integer.valueOf(cur));
                    if (!options.isEmpty()) next = options.get(rnd.nextInt(options.size()));
                }
                if (Math.abs(next) > toAnchor) next = 0;
                run = next == cur ? run + 1 : 1;
                cur = next;
            }
            v[i] = cur;
        }
        return v;
    }

    /** A bolt down separator {@code sep} from row y0 toward y1. A stub (taper) fades out at y1; a full bolt joins both bands. */
    static void bolt(Img im, int sep, int y0, int y1, Random rnd, boolean taper) {
        int x0 = 19 + 20 * sep, len = Math.abs(y1 - y0) + 1, dir = y1 >= y0 ? 1 : -1;
        int[] cap = new int[len];
        java.util.Arrays.fill(cap, 3);
        int[] cols = zigzag(len, 1 + rnd.nextInt(2), 0, cap, 1, rnd), tip = tips(cols);
        List<Pt> pts = new ArrayList<>();
        for (int k = 0; k < len; k++) pts.add(new Pt(x0 + cols[k], y0 + dir * k, -1, tip[k]));
        int strike = strikeNear(pts, taper ? len / 3 : len / 2);
        for (int k = 0; k < len; k++) {
            Pt p = pts.get(k);
            int col = taper && k == len - 1 ? BLOOD : taper && k == len - 2 ? CRIMSON_600
                    : k == strike ? CORE : Math.abs(k - strike) == 1 ? CRIMSON_HOT : CRIMSON;
            im.set(p.x(), p.y(), opaque(col));
        }
        for (Pt p : pts)
            for (int[] o : FOUR) {
                int nx = p.x() + o[0], ny = p.y() + o[1];
                if (nx >= x0 && nx <= x0 + 3 && ny >= 3 && ny <= 18 && im.get(nx, ny) == opaque(OBSIDIAN_2))
                    im.set(nx, ny, opaque(BLOOD_900));
            }
    }

    void hotbarSelection() throws IOException {
        Img im = new Img(24, 23);
        int[] band = {BLOOD, CRIMSON, CRIMSON_HOT};
        for (int y = 0; y < 23; y++)
            for (int x = 0; x < 24; x++) {
                if (x == 0 || x == 23 || y == 0) im.set(x, y, withAlpha(CRIMSON, 0x50));
                else if (x >= 4 && x <= 19 && y >= 4 && y <= 19) im.set(x, y, 0);
                else im.set(x, y, opaque(band[Math.min(Math.min(x - 1, 22 - x), Math.min(y - 1, 22 - y))]));
            }
        for (int[] p : new int[][] {{3, 3}, {20, 3}, {3, 20}, {20, 20}}) im.set(p[0], p[1], opaque(CORE));
        // Sparks jump off the frame into the 1 px overhang above and beside the hotbar.
        for (int[] p : new int[][] {{6, 0}, {7, 0}, {16, 0}, {0, 9}, {23, 14}}) im.set(p[0], p[1], opaque(CRIMSON));
        im.set(17, 0, opaque(CORE));
        int[][] terminals = {{0, 2}, {23, 2}, {0, 21}, {23, 21}};
        for (int[] p : terminals) im.set(p[0], p[1], opaque(CRIMSON));
        for (int[] p : terminals)
            if (im.get(p[0], p[1]) != opaque(CRIMSON)) throw new IllegalStateException("selection terminal " + p[0] + "," + p[1]);
        write("gui/sprites/hud/hotbar_selection.png", im, false);
    }

    void offhand() throws IOException {
        Random rnd = rng("offhand");
        Img left = new Img(29, 24);
        for (int y = 1; y <= 22; y++)
            for (int x = 0; x <= 21; x++) {
                int c;
                if (x == 0 || x == 21 || y == 1 || y == 22) c = opaque(VOID);
                else if (y <= 3 || y >= 20 || x <= 2 || x >= 19) c = opaque(y == 3 || y == 20 || x == 2 || x == 19 ? BLOOD_700 : CHARCOAL);
                else c = CELL;
                left.set(x, y, c);
            }
        IntPredicate ends = i -> i == 1 || i == 20;
        int[] top = walk(22, ends, rnd), bottom = walk(22, ends, rnd);
        IntPredicate sideEnds = i -> i == 2 || i == 21;
        int[] l = walk(23, sideEnds, rnd), r = walk(23, sideEnds, rnd);
        for (int x = 1; x <= 20; x++) {
            left.set(x, 2 + top[x], opaque(top[x] != top[x - 1] && rnd.nextBoolean() ? CRIMSON : CRIMSON_600));
            left.set(x, 21 + bottom[x], opaque(bottom[x] != bottom[x - 1] && rnd.nextBoolean() ? CRIMSON : CRIMSON_600));
        }
        for (int y = 3; y <= 20; y++) {
            left.set(1 + l[y], y, opaque(CRIMSON_600));
            left.set(20 + r[y], y, opaque(CRIMSON_600));
        }
        // A bolt bridges the 7 px gap between the offhand cell and the hotbar, with a soft glow over the world.
        int by = 11;
        List<int[]> bridge = new ArrayList<>();
        for (int x = 22; x <= 28; x++) {
            if (x > 22 && x < 28 && rnd.nextInt(100) < 55) by = Math.max(8, Math.min(15, by + (rnd.nextBoolean() ? 1 : -1)));
            bridge.add(new int[] {x, by});
        }
        for (int k = 0; k < bridge.size(); k++) {
            int[] p = bridge.get(k);
            left.set(p[0], p[1], opaque(k == 3 ? CORE : CRIMSON));
        }
        for (int[] p : bridge)
            for (int[] o : FOUR)
                if (p[0] + o[0] >= 22 && left.alpha(p[0] + o[0], p[1] + o[1]) == 0) left.set(p[0] + o[0], p[1] + o[1], withAlpha(CRIMSON, 0x50));
        write("gui/sprites/hud/hotbar_offhand_left.png", left, false);
        Img right = new Img(29, 24);
        for (int y = 0; y < 24; y++) for (int x = 0; x < 29; x++) right.set(28 - x, y, left.get(x, y));
        write("gui/sprites/hud/hotbar_offhand_right.png", right, false);
    }

    // ------------------------------------------------------------------ status bars (SPEC §7.5)

    /** Hearts, hunger and armor. Poisoned, withered and frozen hearts stay vanilla: their colour is gameplay information. */
    void statusIcons() throws IOException {
        String h = "hud/heart/";
        Map<Integer, Integer> container = Map.of(0x000000, BLOOD_900, 0x6E6E6E, CHARCOAL);
        Map<Integer, Integer> containerFlash = Map.of(0xFFFFFF, CRIMSON_HOT, 0x6E6E6E, CHARCOAL);
        for (String c : new String[] {"container", "container_hardcore", "vehicle_container"}) sprite(h + c, container);
        for (String c : new String[] {"container_blinking", "container_hardcore_blinking"}) sprite(h + c, containerFlash);

        // Player hearts: crimson body, glossy top lobes, pale glint.
        Map<Integer, Integer> heart = Map.of(0xFF1313, CRIMSON, 0xFFC8C8, CORE, 0xBB1313, BLOOD);
        Map<Integer, Integer> heartFlash = Map.of(0xFFA1A1, CRIMSON_HOT, 0xFFE3E3, CORE, 0xDEA1A1, CRIMSON_600);
        Map<Integer, Integer> hardcore = Map.of(0xFF1313, CRIMSON, 0x600707, BLOOD_900, 0xBB1313, BLOOD);
        Map<Integer, Integer> hardcoreFlash = Map.of(0xFFA1A1, CRIMSON_HOT, 0xB78F8F, BLOOD_700, 0xDEA1A1, CRIMSON_600);
        for (String fh : new String[] {"full", "half"}) {
            glossyHeart(h + fh, heart);
            glossyHeart(h + fh + "_blinking", heartFlash);
            glossyHeart(h + "hardcore_" + fh, hardcore);
            glossyHeart(h + "hardcore_" + fh + "_blinking", hardcoreFlash);
            // Mount hearts are orange-brown in vanilla; darker crimson with a pale saddle keeps them distinct.
            sprite(h + "vehicle_" + fh, Map.of(0xEF7E4A, CRIMSON_600, 0xDA662C, BLOOD, 0x7F3919, BLOOD_900, 0x000000, VOID,
                    0xC1C1C1, CORE, 0x8F8F8F, SLATE, 0x797979, CHARCOAL));
        }

        // Hunger: blood-red drumsticks with pale bones; the Hunger effect turns them dull and rotten.
        sprite("hud/food_empty", Map.of(0x000000, BLOOD_900, 0x6E6E6E, CHARCOAL));
        sprite("hud/food_empty_hunger", Map.of(0x12410B, BLOOD_700, 0x282828, OBSIDIAN));
        Map<Integer, Integer> meat = Map.ofEntries(Map.entry(0xD42A2A, CRIMSON), Map.entry(0xB21818, BLOOD),
                Map.entry(0xDFB18F, CRIMSON_HOT), Map.entry(0xE8C4A4, CRIMSON_HOT), Map.entry(0xD7977A, CRIMSON_600),
                Map.entry(0xB88458, CRIMSON_600), Map.entry(0x9D6D43, BLOOD), Map.entry(0x7B512D, BLOOD_700),
                Map.entry(0x613C1B, BLOOD_900), Map.entry(0xE2D5AA, CORE), Map.entry(0xFFF7DC, CORE));
        Map<Integer, Integer> rotten = Map.ofEntries(Map.entry(0x5F0D0D, BLOOD_900), Map.entry(0x2D0202, VOID),
                Map.entry(0xDFB67D, SLATE), Map.entry(0x748458, CHARCOAL), Map.entry(0x928458, SLATE),
                Map.entry(0x5F6D43, CHARCOAL), Map.entry(0x805E25, BLOOD_700), Map.entry(0x946E32, BLOOD_700),
                Map.entry(0xAD8343, BLOOD), Map.entry(0x403C1B, OBSIDIAN), Map.entry(0x7B512D, BLOOD_700),
                Map.entry(0xE2C945, SLATE), Map.entry(0xFFEB7E, BLOOD));
        for (String fh : new String[] {"full", "half"}) {
            sprite("hud/food_" + fh, meat);
            sprite("hud/food_" + fh + "_hunger", rotten);
        }

        // Armor: heavy-knight steel plates with a glowing crimson rim; empty slots go dark.
        sprite("hud/armor_empty", Map.of(0x000000, BLOOD_900, 0x868686, OBSIDIAN_2));
        sprite("hud/armor_full", Map.of(0x000000, CRIMSON_600, 0xE6E7F2, STEEL_200, 0xB8B9C4, STEEL_400, 0x696A70, STEEL_800));
        sprite("hud/armor_half", Map.of(0x000000, CRIMSON_600, 0xE6E7F2, STEEL_200, 0xB8B9C4, STEEL_400, 0x696A70, STEEL_800, 0x3D3D3D, OBSIDIAN_2));
    }

    /** A heart recoloured through the map, with the top lobes lifted to crimson-hot for a glossy, charged look. */
    void glossyHeart(String name, Map<Integer, Integer> map) throws IOException {
        String path = "gui/sprites/" + name + ".png";
        Img img = recolor(read(path), (x, y) -> map, name);
        for (int x = 0; x < img.w; x++)
            if (img.alpha(x, 1) != 0 && img.rgb(x, 1) != CORE) img.set(x, 1, opaque(CRIMSON_HOT));
        write(path, img, false);
    }

    static final int[][] XP_BACKGROUND_TIMELINE = {{0, 40}, {1, 3}, {2, 3}};
    static final int[][] XP_PROGRESS_TIMELINE = {{0, 2}, {1, 2}, {2, 2}, {3, 2}, {4, 2}, {5, 2}};
    static final String XP_BACKGROUND_MCMETA = animationMeta(5, 1, XP_BACKGROUND_TIMELINE);
    static final String XP_PROGRESS_MCMETA = animationMeta(5, 2, XP_PROGRESS_TIMELINE);

    /** The XP bar sits 2 px above the hotbar: dim crackle in the empty bar, live lightning through the filled part. */
    void experienceBar() throws IOException {
        Random rnd = rng("experience_bar");
        Map<Integer, Integer> bgMap = Map.of(0x09100C, VOID, 0x36433C, BLOOD_900, 0x2E3A33, OBSIDIAN_2, 0x29352F, OBSIDIAN_2,
                0x141F19, VOID, 0x202C25, OBSIDIAN, 0x19241E, OBSIDIAN, 0x202B25, OBSIDIAN, 0x28332D, OBSIDIAN_2);
        Img bg = recolor(read("gui/sprites/hud/experience_bar_background.png"), (x, y) -> bgMap, "experience_bar_background");
        Img bgStrip = new Img(182, 5 * 3);
        for (int f = 0; f < 3; f++) {
            Img fr = bg.copy();
            barBolts(fr, rnd, 3 + f, f == 0 ? BLOOD_700 : BLOOD, f == 0 ? BLOOD : CRIMSON_600);
            bgStrip.paste(fr, 0, 5 * f);
        }
        write("gui/sprites/hud/experience_bar_background.png", bgStrip, false);
        writeText("assets/minecraft/textures/gui/sprites/hud/experience_bar_background.png.mcmeta", XP_BACKGROUND_MCMETA);

        // Progress: vanilla's noisy fill mapped by brightness onto the red ramp, then a crackling streak per frame.
        Img pr = read("gui/sprites/hud/experience_bar_progress.png");
        int[] ramp = {BLOOD_700, BLOOD, CRIMSON_600, CRIMSON, CRIMSON_HOT};
        double lo = 1, hi = 0;
        for (int y = 1; y <= 3; y++)
            for (int x = 1; x <= 180; x++) {
                double l = luminance(pr.rgb(x, y));
                lo = Math.min(lo, l);
                hi = Math.max(hi, l);
            }
        Img base = new Img(182, 5);
        for (int y = 0; y < 5; y++)
            for (int x = 0; x < 182; x++) {
                if (pr.alpha(x, y) == 0) continue;
                if (y == 0 || y == 4 || x == 0 || x == 181) { base.set(x, y, opaque(BLOOD_900)); continue; }
                int idx = (int) Math.round((luminance(pr.rgb(x, y)) - lo) / (hi - lo) * (ramp.length - 1));
                base.set(x, y, opaque(ramp[Math.max(0, Math.min(ramp.length - 1, idx))]));
            }
        int frames = 6;
        Img prStrip = new Img(182, 5 * frames);
        for (int f = 0; f < frames; f++) {
            Img fr = base.copy();
            barBolts(fr, rnd, 5, CRIMSON_HOT, CORE);
            prStrip.paste(fr, 0, 5 * f);
        }
        write("gui/sprites/hud/experience_bar_progress.png", prStrip, false);
        writeText("assets/minecraft/textures/gui/sprites/hud/experience_bar_progress.png.mcmeta", XP_PROGRESS_MCMETA);
    }

    /** Short zig-zag bolts inside rows 1-3 of a 182x5 bar; ends fade, bends take the brighter colour. */
    static void barBolts(Img im, Random rnd, int count, int body, int bright) {
        for (int b = 0; b < count; b++) {
            int len = 10 + rnd.nextInt(18), x0 = 2 + rnd.nextInt(178 - len), y = 1 + rnd.nextInt(3), run = 0;
            for (int k = 0; k < len; k++) {
                boolean bend = false;
                if (k > 0 && (++run >= 5 || rnd.nextInt(100) < 25)) {
                    int ny = y + (rnd.nextBoolean() ? 1 : -1);
                    if (ny < 1 || ny > 3) ny = 2 * y - ny;
                    y = ny;
                    run = 0;
                    bend = true;
                }
                int c = k == 0 || k == len - 1 ? BLOOD_700 : bend ? bright : body;
                im.set(x0 + k, y, opaque(c));
            }
        }
    }

    void attackIndicator() throws IOException {
        Img bg = read("gui/sprites/hud/hotbar_attack_indicator_background.png");
        Img out = new Img(bg.w, bg.h);
        for (int y = 0; y < bg.h; y++)
            for (int x = 0; x < bg.w; x++)
                if (bg.alpha(x, y) != 0) out.set(x, y, opaque(OBSIDIAN_2));
        write("gui/sprites/hud/hotbar_attack_indicator_background.png", out, false);
        Img pr = read("gui/sprites/hud/hotbar_attack_indicator_progress.png");
        Img out2 = new Img(pr.w, pr.h);
        int[] ramp = {BLOOD_900, BLOOD_700, BLOOD, CRIMSON_600, CRIMSON, CRIMSON_HOT};
        for (int y = 0; y < pr.h; y++)
            for (int x = 0; x < pr.w; x++) {
                if (pr.alpha(x, y) == 0) continue;
                int c = pr.rgb(x, y);
                if (c == 0x000000) { out2.set(x, y, opaque(VOID)); continue; }
                double lum = luminance(c); // 0.12 .. 0.84 in the vanilla sprite
                int idx = (int) Math.max(0, Math.min(ramp.length - 1, Math.round((lum - 0.12) / 0.72 * (ramp.length - 1))));
                out2.set(x, y, opaque(ramp[idx]));
            }
        write("gui/sprites/hud/hotbar_attack_indicator_progress.png", out2, false);
    }

    // ------------------------------------------------------------------ hearts, slots, buttons (SPEC §6.5, §7.4, §8)

    void hearts() throws IOException {
        for (String hc : new String[] {"", "hardcore_"})
            for (String fh : new String[] {"full", "half"})
                for (String bl : new String[] {"", "_blinking"}) {
                    String name = "absorbing_" + hc + fh + bl;
                    String path = "gui/sprites/hud/heart/" + name + ".png";
                    Img v = read(path), full = read("gui/sprites/hud/heart/absorbing_" + hc + "full" + bl + ".png");
                    boolean blink = !bl.isEmpty();
                    Img out = new Img(v.w, v.h);
                    for (int y = 0; y < v.h; y++)
                        for (int x = 0; x < v.w; x++) {
                            if (v.alpha(x, y) == 0) continue;
                            int c = v.rgb(x, y), col;
                            switch (c) {
                                case 0xD4AF37, 0xD4A200 -> col = edge(full, x, y) ? (blink ? CRIMSON_HOT : CRIMSON) : (blink ? CHARCOAL : OBSIDIAN_2);
                                case 0xD4C07E, 0xD0BF89 -> col = CORE;
                                case 0xBEAE77, 0xC7B780 -> col = BLOOD_700;
                                default -> throw new IllegalStateException(name + ": unmapped colour " + hex(c));
                            }
                            out.set(x, y, opaque(col));
                        }
                    write(path, out, false);
                }
    }

    void slotSprites() throws IOException {
        for (String name : new String[] {"helmet", "chestplate", "leggings", "boots", "shield"}) {
            String path = "gui/sprites/container/slot/" + name + ".png";
            Img v = read(path);
            boolean[][] outside = new boolean[v.w][v.h];
            ArrayDeque<int[]> q = new ArrayDeque<>();
            for (int y = 0; y < v.h; y++)
                for (int x = 0; x < v.w; x++)
                    if ((x == 0 || y == 0 || x == v.w - 1 || y == v.h - 1) && v.alpha(x, y) == 0) { outside[x][y] = true; q.add(new int[] {x, y}); }
            while (!q.isEmpty()) {
                int[] p = q.poll();
                for (int[] o : FOUR) {
                    int nx = p[0] + o[0], ny = p[1] + o[1];
                    if (nx < 0 || ny < 0 || nx >= v.w || ny >= v.h || outside[nx][ny] || v.alpha(nx, ny) != 0) continue;
                    outside[nx][ny] = true;
                    q.add(new int[] {nx, ny});
                }
            }
            Img out = new Img(v.w, v.h);
            for (int y = 0; y < v.h; y++)
                for (int x = 0; x < v.w; x++) {
                    if (outside[x][y]) continue;
                    if (v.alpha(x, y) == 0) { out.set(x, y, opaque(STEEL_800)); continue; }
                    boolean rim = false;
                    for (int[] o : FOUR) {
                        int nx = x + o[0], ny = y + o[1];
                        if (nx < 0 || ny < 0 || nx >= v.w || ny >= v.h || outside[nx][ny]) rim = true;
                    }
                    out.set(x, y, opaque(rim ? BLOOD : BLOOD_700));
                }
            write(path, out, false);
        }
        Img back = new Img(24, 24), front = new Img(24, 24);
        for (int y = 0; y < 24; y++)
            for (int x = 0; x < 24; x++) {
                if (x >= 4 && x <= 19 && y >= 4 && y <= 19) back.set(x, y, withAlpha(CRIMSON, 0x40));
                int r = Math.max(Math.abs(2 * x - 23), Math.abs(2 * y - 23)); // 17 on the slot frame, 19 one px outside
                if (r == 17) front.set(x, y, opaque((x == 3 || x == 20) && (y == 3 || y == 20) ? CORE : CRIMSON));
                else if (r == 19) front.set(x, y, withAlpha(BLOOD_900, 0x99));
            }
        write("gui/sprites/container/slot_highlight_back.png", back, false);
        write("gui/sprites/container/slot_highlight_front.png", front, false);
        copyMeta("gui/sprites/container/slot_highlight_back.png");
        copyMeta("gui/sprites/container/slot_highlight_front.png");
    }

    void buttons() throws IOException {
        button("button", STEEL_400, STEEL_800, BLOOD, CHARCOAL, STEEL_400);
        button("button_highlighted", STEEL_200, STEEL_600, CRIMSON, SLATE, CORE);
        button("button_disabled", STEEL_800, STEEL_800, BLOOD_900, OBSIDIAN, -1);
        sliders();
    }

    /**
     * A heavy plate (SPEC §8): chamfered corners, a steel rim lit from the top left, a crimson inlay and a flat
     * face, with a rivet (or, on hover, a spark) where the inlay turns each corner. Only the 3×3 corners hold
     * detail; the edges and the face are cut and repeated to the button's width.
     */
    void button(String name, int rimLit, int rimShade, int inlay, int face, int rivet) throws IOException {
        Img im = new Img(200, 20);
        for (int y = 0; y < 20; y++)
            for (int x = 0; x < 200; x++) {
                int cx = Math.min(x, 199 - x), cy = Math.min(y, 19 - y), r = Math.min(cx, cy);
                if (cx + cy < 2) continue;
                int c = cx + cy == 2 ? VOID : switch (r) {
                    case 0 -> VOID;
                    case 1 -> y == 1 || x == 1 ? rimLit : rimShade;
                    case 2 -> inlay;
                    default -> face;
                };
                im.set(x, y, opaque(c));
            }
        if (rivet >= 0) for (int[] p : new int[][] {{2, 2}, {197, 2}, {2, 17}, {197, 17}}) im.set(p[0], p[1], opaque(rivet));
        String path = "gui/sprites/widget/" + name + ".png";
        write(path, im, false);
        copyMeta(path);
    }

    /** Sliders: a track edged like the text fields, and a steel handle with a crimson grip; hover lights both up. */
    void sliders() throws IOException {
        for (boolean hot : new boolean[] {false, true}) {
            String suffix = hot ? "_highlighted" : "";
            Img track = new Img(200, 20);
            for (int y = 0; y < 20; y++)
                for (int x = 0; x < 200; x++)
                    track.set(x, y, opaque(x == 0 || y == 0 || x == 199 || y == 19 ? (hot ? CRIMSON : BLOOD) : OBSIDIAN));
            write("gui/sprites/widget/slider" + suffix + ".png", track, false);
            copyMeta("gui/sprites/widget/slider" + suffix + ".png");

            // 8×20, nine-slice borders L2 T2 R2 B3: the grip runs down the stretchable middle.
            String[] rows = hot ? new String[] {"CCCCCCCC", "CWWWWWLC", "CWLLLLmC", "CWLChLmC", "CmmmmmmC"}
                    : new String[] {"OOOOOOOO", "OWLLLLmO", "OLmmmmDO", "OLmBcmDO", "ODDDDDDO"};
            Img handle = new Img(8, 20);
            for (int y = 0; y < 20; y++) {
                String row = rows[y == 0 || y == 19 ? 0 : y == 1 ? 1 : y == 18 ? 4 : y >= 3 && y <= 16 ? 3 : 2];
                for (int x = 0; x < 8; x++) handle.set(x, y, opaque(ART.get(row.charAt(x))));
            }
            write("gui/sprites/widget/slider_handle" + suffix + ".png", handle, false);
            copyMeta("gui/sprites/widget/slider_handle" + suffix + ".png");
        }
    }

    /**
     * Tooltips as a "system window" (SPEC §8.2). Both sprites are 100×100 nine-slices drawn 12 px out from the
     * text; vanilla's box spans 8-91. The background is obsidian at α 0xF0 with chamfered corners. The frame is a
     * crimson line on the box edge (brighter along the top) over an inner blood glow, plus a lock-on bracket 2 px
     * outside each corner, small enough to stay inside the 10 px corner of the frame's nine-slice.
     */
    void tooltip() throws IOException {
        Img bg = new Img(100, 100), frame = new Img(100, 100);
        for (int y = 8; y <= 91; y++)
            for (int x = 8; x <= 91; x++) {
                int cx = Math.min(x - 8, 91 - x), cy = Math.min(y - 8, 91 - y), r = Math.min(cx, cy);
                if (cx + cy < 2) continue;
                bg.set(x, y, withAlpha(OBSIDIAN, 0xF0));
                if (r == 0 || cx + cy == 2) frame.set(x, y, opaque(y == 8 ? CRIMSON_600 : BLOOD));
                else if (r == 1) frame.set(x, y, opaque(BLOOD_900));
            }
        for (int[] m : new int[][] {{1, 1}, {-1, 1}, {1, -1}, {-1, -1}}) {
            int ox = m[0] > 0 ? 6 : 93, oy = m[1] > 0 ? 6 : 93;
            for (int k = 0; k < 4; k++) {
                int c = k == 0 ? CORE : k == 3 ? CRIMSON_600 : CRIMSON_HOT;
                frame.set(ox + m[0] * k, oy, opaque(c));
                frame.set(ox, oy + m[1] * k, opaque(c));
            }
        }
        write("gui/sprites/tooltip/background.png", bg, false);
        write("gui/sprites/tooltip/frame.png", frame, false);
        copyMeta("gui/sprites/tooltip/background.png");
        copyMeta("gui/sprites/tooltip/frame.png");
    }

    /**
     * Toasts (SPEC §8.3). The advancement toast (160×32, drawn 1:1) becomes a small framed panel with corner
     * guards and arcs along its long sides. The recipe and tutorial toasts keep their dark text, set in code, so
     * they become light steel plates with a crimson line. The music and system toasts are recoloured.
     */
    void toasts() throws IOException {
        String path = "gui/sprites/toast/advancement.png";
        Img v = read(path), img = new Img(v.w, v.h);
        for (int y = 0; y < v.h; y++) for (int x = 0; x < v.w; x++) if (v.alpha(x, y) != 0) img.set(x, y, opaque(OBSIDIAN));
        framedPanel(path, v, img, new int[][] {{6, 6, 153, 25}}, new int[0][],
                List.of(new Arc(50, 2, 12), new Arc(108, 2, 9), new Arc(74, 29, 12), new Arc(126, 29, 8)), new int[0][], new int[0][], false);

        // Light steel: rings by distance (outline, crimson line, steel rim) around a pale face, rivets in the corners.
        for (String name : new String[] {"recipe", "tutorial"}) {
            String p = "gui/sprites/toast/" + name + ".png";
            Img t = read(p), out = new Img(t.w, t.h);
            int[][] d = distance(t);
            int[] ring = {0, VOID, BLOOD, STEEL_400};
            for (int y = 0; y < t.h; y++)
                for (int x = 0; x < t.w; x++)
                    if (t.alpha(x, y) != 0) out.set(x, y, opaque(d[x][y] < ring.length ? ring[d[x][y]] : STEEL_200));
            if (name.equals("recipe"))
                for (int[] c : new int[][] {{4, 4}, {t.w - 6, 4}, {4, t.h - 6}, {t.w - 6, t.h - 6}}) {
                    out.set(c[0], c[1], opaque(STEEL_600));
                    out.set(c[0] + 1, c[1], opaque(STEEL_400));
                    out.set(c[0], c[1] + 1, opaque(STEEL_400));
                    out.set(c[0] + 1, c[1] + 1, opaque(STEEL_800));
                }
            write(p, out, false);
            if (jar.getEntry("assets/minecraft/textures/" + p + ".mcmeta") != null) copyMeta(p);
        }
        sprite("toast/now_playing", Map.of(0x000000, VOID, 0x555555, BLOOD, 0x212121, OBSIDIAN));
        // The system toast's yellow "!" is a gold leak; it becomes a crimson one with a pale glint.
        sprite("toast/system", Map.of(0x000000, VOID, 0x082C4C, OBSIDIAN, 0x286485, BLOOD, 0x5C5C00, BLOOD_700,
                0xCAB60D, CRIMSON, 0xFFFFFF, CORE));
    }

    // ------------------------------------------------------------------ items (SPEC §9)

    // Tools (swords, pickaxes, axes) deliberately stay vanilla.
    void items() throws IOException {
        bloodApple();
        Map<Integer, Integer> bottle = Map.of(0xD46D49, CRIMSON_600, 0xA94725, BLOOD, 0x973716, BLOOD_700, 0xCCAFA5, SLATE,
                0xD4E5F7, BLOOD, 0xB3CFEC, BLOOD_700, 0x5D8FC2, SLATE, 0x8BADD0, SLATE);
        recolorItem("potion", bottle, new int[][] {{5, 2, CRIMSON}, {11, 4, CRIMSON}, {3, 7, CRIMSON_600}, {12, 7, CORE}});
        recolorItem("splash_potion", bottle, new int[][] {{8, 2, CRIMSON}, {14, 4, CRIMSON}, {4, 6, CRIMSON_600}, {13, 7, CORE}});
        recolorItem("lingering_potion", bottle, new int[][] {{6, 4, CRIMSON}, {10, 4, CRIMSON}, {4, 7, CRIMSON_600}, {12, 8, CORE}});
        enderEye();
    }

    void bloodApple() throws IOException {
        Map<Integer, Integer> body = Map.of(0x752802, BLOOD_700, 0x541209, BLOOD_900, 0x542409, BLOOD_900, 0xB26411, BLOOD,
                0xDBA213, CRIMSON_600, 0xECCB45, CRIMSON, 0xEAEE57, CRIMSON, 0xF6F892, CRIMSON_HOT, 0xFEFFE6, CORE);
        Img v = read("item/golden_apple.png"), out = new Img(16, 16);
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++) {
                if (v.alpha(x, y) == 0) continue;
                Integer c = body.get(v.rgb(x, y));
                if (c == null) throw new IllegalStateException("golden_apple: unmapped colour " + hex(v.rgb(x, y)));
                out.set(x, y, opaque(y <= 3 ? SLATE : c)); // rows 1-3 are the stem
            }
        for (int[] p : new int[][] {{10, 2}, {11, 1}, {12, 1}}) {
            if (v.alpha(p[0], p[1]) != 0) throw new IllegalStateException("golden_apple: leaf pixel taken");
            out.set(p[0], p[1], opaque(BLOOD_700));
        }
        write("item/golden_apple.png", out, true);
    }

    void recolorItem(String name, Map<Integer, Integer> map, int[][] sparks) throws IOException {
        String path = "item/" + name + ".png";
        Img v = read(path), out = new Img(v.w, v.h);
        for (int y = 0; y < v.h; y++)
            for (int x = 0; x < v.w; x++) {
                if (v.alpha(x, y) == 0) continue;
                Integer c = map.get(v.rgb(x, y));
                if (c == null) throw new IllegalStateException(name + ": unmapped colour " + hex(v.rgb(x, y)));
                out.set(x, y, opaque(c));
            }
        for (int[] s : sparks) {
            if (v.alpha(s[0], s[1]) != 0) throw new IllegalStateException(name + ": spark on an occupied pixel " + s[0] + "," + s[1]);
            out.set(s[0], s[1], opaque(s[2]));
        }
        write(path, out, true);
    }

    void enderEye() throws IOException {
        Map<Integer, Integer> map = Map.ofEntries(Map.entry(0x102C31, BLOOD_700), Map.entry(0x316364, CHARCOAL),
                Map.entry(0x1E4835, OBSIDIAN), Map.entry(0x4E8386, SLATE), Map.entry(0x659B7D, SLATE),
                Map.entry(0xB5E45A, CRIMSON_HOT), Map.entry(0x97D16B, CRIMSON_HOT), Map.entry(0x88C35A, CRIMSON),
                Map.entry(0x71AC49, CRIMSON_600), Map.entry(0x579143, BLOOD), Map.entry(0xCBFCDD, CORE), Map.entry(0x1B2B10, VOID));
        Img v = read("item/ender_eye.png"), out = new Img(16, 16);
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++) {
                if (v.alpha(x, y) == 0) continue;
                int c = v.rgb(x, y);
                boolean pupil = c == 0x1E4835 && x >= 6 && x <= 10 && y >= 6 && y <= 10;
                Integer col = pupil ? Integer.valueOf(VOID) : map.get(c);
                if (col == null) throw new IllegalStateException("ender_eye: unmapped colour " + hex(c));
                out.set(x, y, opaque(col));
            }
        for (int[] s : new int[][] {{13, 2, CRIMSON_600}, {1, 10, CRIMSON}, {13, 13, CRIMSON}}) {
            if (v.alpha(s[0], s[1]) != 0) throw new IllegalStateException("ender_eye: spark on an occupied pixel");
            out.set(s[0], s[1], opaque(s[2]));
        }
        write("item/ender_eye.png", out, true);
    }

    // ------------------------------------------------------------------ pack icon

    /**
     * 64×64, shipped at 2×: the guarded panel frame with crackling arcs around a heavy knight's great helm. The
     * helm is steel lit from the left, with a centre ridge, breathing holes, a crimson plume and an eye slit that
     * glows crimson with two pale-hot eyes; a stepped blood aura sets it off the fill.
     */
    void packIcon() throws IOException {
        int s = 64;
        Img im = new Img(s, s).fill(opaque(OBSIDIAN));
        int[][] d = distance(im);
        int[] ring = {0, VOID, BLOOD, BLOOD_900, BLOOD_950};
        for (int y = 0; y < s; y++) for (int x = 0; x < s; x++) if (d[x][y] <= 4) im.set(x, y, opaque(ring[d[x][y]]));
        boolean[][] guard = cornerGuards(im, d);
        Polygon helm = new Polygon(new int[] {22, 42, 46, 47, 43, 21, 17, 18}, new int[] {12, 12, 16, 48, 54, 54, 48, 16}, 8);
        Polygon plume = new Polygon(new int[] {24, 27, 32, 38, 41, 40}, new int[] {13, 7, 4, 6, 11, 13}, 6);
        Img shape = new Img(s, s);
        for (int y = 0; y < s; y++)
            for (int x = 0; x < s; x++)
                if (helm.contains(x + 0.5, y + 0.5) || plume.contains(x + 0.5, y + 0.5)) shape.set(x, y, opaque(VOID));
        int[][] reach = near(shape, 3);
        int[] aura = {0, BLOOD_700, BLOOD_900, BLOOD_950};
        boolean[][] allowed = new boolean[s][s];
        for (int y = 0; y < s; y++)
            for (int x = 0; x < s; x++) {
                allowed[x][y] = d[x][y] <= 6 && reach[x][y] > 3 && !guard[x][y];
                if (shape.alpha(x, y) == 0) {
                    if (reach[x][y] <= 3 && d[x][y] > 4) im.set(x, y, opaque(aura[reach[x][y]]));
                    continue;
                }
                boolean edge = false;
                for (int[] o : FOUR) if (shape.alpha(x + o[0], y + o[1]) == 0) edge = true;
                boolean eye = (x >= 20 && x <= 29) || (x >= 34 && x <= 43);
                int c;
                if (edge) c = VOID;
                else if (!helm.contains(x + 0.5, y + 0.5)) c = y <= 6 ? CRIMSON_HOT : x >= 37 || y >= 11 ? BLOOD : CRIMSON;
                else if (y >= 28 && y <= 32 && x >= 19 && x <= 44 && !(x == 31 || x == 32))
                    c = y == 28 || y == 32 || !eye ? VOID : y != 30 ? CRIMSON : x == 24 || x == 25 || x == 38 || x == 39 ? CORE : CRIMSON_HOT;
                else if (x == 31) c = STEEL_200;
                else if (x == 32) c = STEEL_400;
                else if (x >= 35 && x <= 44 && y >= 37 && y <= 49 && (x - 35) % 3 == 0 && (y - 37) % 3 == 0) c = VOID;
                else c = x <= 21 ? STEEL_400 : x <= 36 ? STEEL_600 : STEEL_800;
                im.set(x, y, opaque(c));
            }
        List<int[]> loop = ringLoop(d, s, s, 3);
        int[][] inward = inwardDirs(loop, d);
        Random rnd = rng("pack");
        Glow glow = new Glow();
        for (Arc a : List.of(new Arc(2, 32, 12), new Arc(61, 30, 12), new Arc(32, 61, 9))) edgeBolt(im, loop, inward, a, allowed, rnd, glow);
        glow(im, glow, allowed);
        Img big = im.scaled(2);
        checkTokens("pack.png", big, false);
        writePng(pack.resolve("pack.png"), big);
        built.put("pack.png", big);
    }

    // ------------------------------------------------------------------ previews (scratch, not shipped)

    void writePreviews(Path dir) throws IOException {
        Files.createDirectories(dir);
        int bg = 0xFF3A3A3A;
        Img sheet = new Img(3 * 176 + 4 * 6, 166 + 12).fill(bg);
        int ox = 6;
        for (String n : new String[] {"inventory", "shulker_box", "crafting_table"}) {
            sheet.over(built.get("gui/container/" + n + ".png").crop(0, 0, 176, 166), ox, 6);
            ox += 182;
        }
        writePng(dir.resolve("containers.png"), sheet.scaled(3));

        // Recipe book open next to the survival inventory, roughly as the game lays it out.
        Img rb = new Img(30 + 147 + 4 + 176 + 12, 166 + 12).fill(bg);
        int bx = 6 + 30, by = 6;
        for (int i = 0; i < 5; i++)
            rb.over(built.get("gui/sprites/recipe_book/" + (i == 0 ? "tab_selected" : "tab") + ".png"), 6, by + 5 + 27 * i);
        rb.over(built.get("gui/recipe_book.png").crop(1, 1, 147, 166), bx, by);
        rb.over(nineSlice(built.get("gui/sprites/widget/text_field.png"), 81, 14, 1), bx + 25, by + 13);
        rb.over(built.get("gui/sprites/recipe_book/filter_enabled.png"), bx + 110, by + 12);
        List<Img> rbIcons = new ArrayList<>();
        for (String n : new String[] {"iron_pickaxe", "bread", "torch", "stick"})
            if (jar.getEntry("assets/minecraft/textures/item/" + n + ".png") != null) rbIcons.add(read("item/" + n + ".png"));
        rbIcons.add(built.get("item/golden_apple.png"));
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 5; c++) {
                int i = r * 5 + c;
                String s = i % 7 == 3 ? "slot_uncraftable" : i % 5 == 1 ? "slot_many_craftable" : "slot_craftable";
                rb.over(built.get("gui/sprites/recipe_book/" + s + ".png"), bx + 11 + 25 * c, by + 31 + 25 * r);
                rb.over(rbIcons.get(i % rbIcons.size()), bx + 15 + 25 * c, by + 35 + 25 * r);
            }
        rb.over(built.get("gui/sprites/recipe_book/page_backward.png"), bx + 38, by + 137);
        rb.over(built.get("gui/sprites/recipe_book/page_forward_highlighted.png"), bx + 93, by + 137);
        rb.over(nineSlice(built.get("gui/sprites/recipe_book/overlay_recipe.png"), 60, 32, 4), bx + 60, by + 82);
        rb.over(built.get("gui/sprites/recipe_book/crafting_overlay.png"), bx + 64, by + 86);
        rb.over(built.get("gui/sprites/recipe_book/crafting_overlay_highlighted.png"), bx + 92, by + 86);
        int ix = bx + 147 + 4;
        rb.over(built.get("gui/container/inventory.png").crop(0, 0, 176, 166), ix, by);
        rb.over(built.get("gui/sprites/recipe_book/button_highlighted.png"), ix + 104, by + 61);
        writePng(dir.resolve("recipe_book.png"), rb.scaled(3));

        Img g = built.get("gui/container/generic_54.png");
        Img chests = new Img(3 * 176 + 4 * 6, 221 + 12).fill(bg);
        ox = 6;
        for (int n : new int[] {1, 3, 6}) {
            chests.over(g.crop(0, 0, 176, 17 + 18 * n), ox, 6);
            chests.over(g.crop(0, 126, 176, 96), ox, 6 + 17 + 18 * n);
            ox += 182;
        }
        writePng(dir.resolve("chests.png"), chests.scaled(3));

        List<Img> icons = new ArrayList<>();
        for (String n : new String[] {"netherite_sword", "netherite_pickaxe", "netherite_axe"}) icons.add(read("item/" + n + ".png"));
        for (String n : new String[] {"golden_apple", "ender_eye"}) icons.add(built.get("item/" + n + ".png"));
        icons.add(potion("potion", 0xF82423));
        icons.add(potion("splash_potion", 0x33EBFF));
        icons.add(potion("lingering_potion", 0x8BBE19));

        Img strip = built.get("gui/sprites/hud/hotbar.png"), sel = built.get("gui/sprites/hud/hotbar_selection.png");
        Img bar = new Img(182 + 2 * 36, 9 * 30 + 6).fill(0xFF78A7D8);
        for (int i = 0; i < 9; i++) {
            int y = 4 + 30 * i;
            bar.over(built.get("gui/sprites/hud/hotbar_offhand_left.png"), 36 - 29, y - 1);
            bar.over(strip.crop(0, 22 * (i % HOTBAR_FRAMES), 182, 22), 36, y);
            bar.over(sel, 36 - 1 + 20 * i, y - 1);
            for (int j = 0; j < icons.size(); j++) bar.over(icons.get(j), 36 + 3 + 20 * j, y + 3);
            bar.over(built.get("gui/sprites/hud/heart/absorbing_full.png"), 36 + 182 + 4, y + 2);
        }
        writePng(dir.resolve("hotbar.png"), bar.scaled(4));

        // Survival HUD as the game lays it out: XP bar 7 px above the hotbar, hearts and food 17, armor 27.
        Img hud = new Img(182 + 2 * 36, 64).fill(0xFF78A7D8);
        int hudX = 36, hudY = 38;
        hud.over(built.get("gui/sprites/hud/hotbar_offhand_left.png"), hudX - 29, hudY - 1);
        hud.over(strip.crop(0, 0, 182, 22), hudX, hudY);
        hud.over(sel, hudX - 1 + 20 * 2, hudY - 1);
        for (int j = 0; j < icons.size(); j++) hud.over(icons.get(j), hudX + 3 + 20 * j, hudY + 3);
        hud.over(built.get("gui/sprites/hud/experience_bar_background.png").crop(0, 5, 182, 5), hudX, hudY - 7);
        hud.over(built.get("gui/sprites/hud/experience_bar_progress.png").crop(0, 0, 110, 5), hudX, hudY - 7);
        for (int i = 0; i < 10; i++) {
            int x = hudX + 8 * i, fx = hudX + 182 - 9 - 8 * i;
            hud.over(built.get("gui/sprites/hud/heart/container.png"), x, hudY - 17);
            if (i < 8) hud.over(built.get("gui/sprites/hud/heart/" + (i < 7 ? "full" : "half") + ".png"), x, hudY - 17);
            hud.over(built.get("gui/sprites/hud/food_empty.png"), fx, hudY - 17);
            if (i < 7) hud.over(built.get("gui/sprites/hud/" + (i < 6 ? "food_full" : "food_half") + ".png"), fx, hudY - 17);
            hud.over(built.get("gui/sprites/hud/" + (i < 5 ? "armor_full" : i == 5 ? "armor_half" : "armor_empty") + ".png"), x, hudY - 27);
        }
        writePng(dir.resolve("hud.png"), hud.scaled(4));

        Img btn = new Img(310 + 12, 15 * 24 + 6).fill(bg);
        int row = 0;
        for (String s : new String[] {"button", "button_highlighted", "button_disabled"})
            for (int w : new int[] {20, 98, 150, 200, 310})
                btn.over(nineSlice(built.get("gui/sprites/widget/" + s + ".png"), w, 20, 3), 6, 6 + 24 * row++);
        writePng(dir.resolve("buttons.png"), btn.scaled(3));

        Img inv = built.get("gui/container/inventory.png");
        Img slot = inv.crop(7, 83, 18, 18);
        List<Img> cells = new ArrayList<>(icons);
        for (String n : new String[] {"helmet", "chestplate", "leggings", "boots", "shield"})
            cells.add(built.get("gui/sprites/container/slot/" + n + ".png"));
        Img items = new Img(cells.size() * 20 + 30, 64).fill(opaque(OBSIDIAN));
        for (int i = 0; i < cells.size(); i++) {
            items.over(slot, 4 + 20 * i, 4);
            items.over(cells.get(i), 5 + 20 * i, 5);
        }
        // hovered slot
        items.over(slot, 4, 30);
        items.over(built.get("gui/sprites/container/slot_highlight_back.png"), 1, 27);
        items.over(icons.get(0), 5, 31);
        items.over(built.get("gui/sprites/container/slot_highlight_front.png"), 1, 27);
        int hx = 30;
        for (String hc : new String[] {"", "hardcore_"})
            for (String fh : new String[] {"full", "half"})
                for (String bl : new String[] {"", "_blinking"}) {
                    Img container = read("gui/sprites/hud/heart/container.png");
                    items.over(container, hx, 36);
                    items.over(built.get("gui/sprites/hud/heart/absorbing_" + hc + fh + bl + ".png"), hx, 36);
                    hx += 10;
                }
        items.over(built.get("gui/sprites/hud/hotbar_attack_indicator_background.png"), hx + 6, 32);
        items.over(built.get("gui/sprites/hud/hotbar_attack_indicator_progress.png"), hx + 26, 32);
        items.over(built.get("pack.png").scaled(1).crop(0, 0, 128, 128).downscale(4), hx + 50, 30);
        writePng(dir.resolve("items.png"), items.scaled(6));
    }

    Img potion(String bottle, int tint) throws IOException {
        Img overlay = read("item/potion_overlay.png"), out = new Img(16, 16);
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++) {
                int p = overlay.get(x, y);
                if ((p >>> 24) == 0) continue;
                int g = p & 0xFF;
                int r = ((tint >> 16) & 0xFF) * g / 255, gg = ((tint >> 8) & 0xFF) * g / 255, b = (tint & 0xFF) * g / 255;
                out.set(x, y, 0xFF000000 | r << 16 | gg << 8 | b);
            }
        out.over(built.get("item/" + bottle + ".png"), 0, 0);
        return out;
    }

    static Img nineSlice(Img src, int w, int h, int border) {
        Img out = new Img(w, h);
        int sw = src.w - 2 * border, sh = src.h - 2 * border;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int sx = x < border ? x : x >= w - border ? src.w - (w - x) : border + (x - border) % sw;
                int sy = y < border ? y : y >= h - border ? src.h - (h - y) : border + (y - border) % sh;
                out.set(x, y, src.get(sx, sy));
            }
        return out;
    }

    // ------------------------------------------------------------------ README images (docs/images, committed)

    static final int BACKDROP = 0xFF1E1E1E, SKY = 0xFF78A7D8;
    /** Text colours as the game draws them with this pack: labels after the shader, §c buttons, the XP level. */
    static final int LABEL = 0xFF0000, MENU_TEXT = 0xFF5555, XP_TEXT = 0xFFE5E5, WHITE = 0xFFFFFF;

    /** The vanilla ASCII bitmap font (font/ascii.png): 8×8 cells, advance = glyph width + 1, a space advances 4. */
    record Font(Img sheet) {
        int advance(char c) {
            if (c == ' ') return 4;
            int w = 0;
            for (int y = 0; y < 8; y++)
                for (int x = 0; x < 8; x++)
                    if (sheet.alpha(c % 16 * 8 + x, c / 16 * 8 + y) != 0) w = Math.max(w, x + 1);
            return w + 1;
        }

        int width(String s) {
            int w = 0;
            for (char c : s.toCharArray()) w += advance(c);
            return w;
        }

        /** Draws as the game does: glyph tops at y; a shadow is the same text at +1,+1 in a quarter of the colour. */
        void draw(Img im, String s, int x, int y, int rgb, boolean shadow) {
            if (shadow) draw(im, s, x + 1, y + 1, (rgb & 0xFCFCFC) >> 2, false);
            for (char c : s.toCharArray()) {
                for (int gy = 0; gy < 8 && c != ' '; gy++)
                    for (int gx = 0; gx < 8; gx++)
                        if (sheet.alpha(c % 16 * 8 + gx, c / 16 * 8 + gy) != 0) im.set(x + gx, y + gy, opaque(rgb));
                x += advance(c);
            }
        }
    }

    /** Composites of the finished textures, laid out as the game draws them, with labels in the game font. */
    void writeReadmeImages(Path dir) throws IOException {
        Files.createDirectories(dir);
        Font font = new Font(read("font/ascii.png"));
        writePng(dir.resolve("banner.png"), banner(font).scaled(4));
        writePng(dir.resolve("inventory.png"), inventoryScene(font).scaled(2));
        writePng(dir.resolve("containers.png"), containerScene(font).scaled(2));
        writePng(dir.resolve("pause_menu.png"), pauseScene(font).scaled(2));
        writePng(dir.resolve("system.png"), systemScene(font).scaled(2));
        writePng(dir.resolve("palette.png"), paletteScene(font).scaled(2));
        hudGif(font, dir.resolve("hud.gif"));
    }

    /** The title in the game font at 3×, glowing on a framed panel with perimeter lightning. */
    Img banner(Font font) {
        int w = 200, h = 52;
        Img im = new Img(w, h).fill(opaque(OBSIDIAN));
        for (int[] c : new int[][] {{0, 0}, {1, 0}, {0, 1}})
            for (int[] m : new int[][] {{1, 1}, {-1, 1}, {1, -1}, {-1, -1}})
                im.set(m[0] > 0 ? c[0] : w - 1 - c[0], m[1] > 0 ? c[1] : h - 1 - c[1], 0);
        int[][] d = distance(im);
        int[] ring = {0, VOID, BLOOD, BLOOD_900, BLOOD_950};
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (im.alpha(x, y) != 0 && d[x][y] <= 4) im.set(x, y, opaque(ring[d[x][y]]));

        String title = "HotbarX", subtitle = "CRIMSON OBSIDIAN UI";
        Img line = new Img(font.width(title) - 1, 8);
        font.draw(line, title, 0, 0, WHITE, false);
        Img big = line.scaled(3), glyphs = new Img(w, h);
        int ox = (w - big.w) / 2, oy = 9, sy = oy + 21 + 6, sx = (w - font.width(subtitle) + 1) / 2;
        glyphs.over(big, ox, oy);
        int[][] reach = near(glyphs, 3);
        int[] aura = {0, BLOOD_700, BLOOD_900, BLOOD_950};
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                if (glyphs.alpha(x, y) != 0) im.set(x, y, opaque(glyphs.alpha(x, y - 1) == 0 ? CRIMSON_HOT : CRIMSON));
                else if (reach[x][y] <= 3 && d[x][y] > 4) im.set(x, y, opaque(aura[reach[x][y]]));
            }
        font.draw(im, subtitle, sx, sy, CRIMSON_600, false);

        boolean[][] guard = cornerGuards(im, d), allowed = new boolean[w][h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                allowed[x][y] = im.alpha(x, y) != 0 && d[x][y] <= 6 && reach[x][y] > 3 && !(y >= sy - 2 && y <= sy + 9) && !guard[x][y];
        List<int[]> loop = ringLoop(d, w, h, 3);
        int[][] inward = inwardDirs(loop, d);
        Random rnd = rng("banner");
        Glow glow = new Glow();
        for (Arc a : List.of(new Arc(45, 2, 12), new Arc(100, 2, 12), new Arc(155, 2, 12), new Arc(50, 49, 12),
                new Arc(110, 49, 10), new Arc(160, 49, 12), new Arc(2, 26, 10), new Arc(197, 26, 10)))
            edgeBolt(im, loop, inward, a, allowed, rnd, glow);
        glow(im, glow, allowed);
        return im;
    }

    /** An item icon: the pack's own if it ships one, else vanilla's (first frame; torches live under block/). */
    Img icon(String name) throws IOException {
        Img own = built.get("item/" + name + ".png");
        if (own != null) return own;
        String path = jar.getEntry("assets/minecraft/textures/item/" + name + ".png") != null ? "item/" + name + ".png" : "block/" + name + ".png";
        return read(path).crop(0, 0, 16, 16);
    }

    /** An item stack at item origin (x, y), with its count drawn as the game does: white, shadowed, bottom right. */
    void stack(Img im, Font font, Img icon, int x, int y, int count) {
        im.over(icon, x, y);
        String s = String.valueOf(count);
        if (count > 1) font.draw(im, s, x + 17 - font.width(s), y + 9, WHITE, true);
    }

    /** A few stacks of different items on some of the given item origins, chosen by a seeded generator. */
    void scatter(Img im, Font font, int[][] origins, int ox, int oy, Random rnd, int count) throws IOException {
        List<String> pool = new ArrayList<>(List.of("arrow", "bread", "cooked_beef", "ender_pearl", "diamond", "redstone",
                "iron_ingot", "stick", "coal", "string", "bone", "feather", "golden_apple", "ender_eye", "emerald", "lapis_lazuli", "gunpowder"));
        List<int[]> free = new ArrayList<>(List.of(origins));
        java.util.Collections.shuffle(pool, rnd);
        java.util.Collections.shuffle(free, rnd);
        for (int k = 0; k < count; k++) {
            String name = pool.get(k % pool.size());
            int n = name.endsWith("apple") || name.equals("ender_eye") ? 1 + rnd.nextInt(8) : 1 + rnd.nextInt(64);
            stack(im, font, icon(name), ox + free.get(k)[0], oy + free.get(k)[1], n);
        }
    }

    /** Item origins of a grid: cols × rows from (x, y), 18 px apart. */
    static int[][] grid(int x, int y, int cols, int rows) {
        int[][] o = new int[cols * rows][];
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) o[r * cols + c] = new int[] {x + 18 * c, y + 18 * r};
        return o;
    }

    /** The survival inventory with the recipe book open: placeholders, a stocked hotbar and a hovered Blood Apple. */
    Img inventoryScene(Font font) throws IOException {
        Img im = new Img(30 + 147 + 4 + 176 + 12, 166 + 12).fill(BACKDROP);
        int bx = 6 + 30, by = 6, ix = bx + 147 + 4;
        for (int i = 0; i < 5; i++)
            im.over(built.get("gui/sprites/recipe_book/" + (i == 0 ? "tab_selected" : "tab") + ".png"), 6, by + 5 + 27 * i);
        String[] tabs = {"diamond_pickaxe", "iron_ingot", "bread", "redstone", "arrow"};
        for (int i = 0; i < 5; i++) im.over(icon(tabs[i]), 6 + (i == 0 ? 9 : 11), by + 10 + 27 * i);
        im.over(built.get("gui/recipe_book.png").crop(1, 1, 147, 166), bx, by);
        im.over(nineSlice(built.get("gui/sprites/widget/text_field.png"), 81, 14, 1), bx + 25, by + 13);
        im.over(built.get("gui/sprites/recipe_book/filter_enabled.png"), bx + 110, by + 12);
        String[] recipes = {"diamond_pickaxe", "bread", "stick", "golden_apple", "iron_ingot", "arrow", "torch", "ender_eye",
                "diamond", "netherite_sword", "bow", "cooked_beef", "string", "redstone", "emerald", "coal", "feather", "bone", "apple", "ender_pearl"};
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 5; c++) {
                int i = r * 5 + c;
                String s = i % 7 == 3 ? "slot_uncraftable" : i % 5 == 1 ? "slot_many_craftable" : "slot_craftable";
                im.over(built.get("gui/sprites/recipe_book/" + s + ".png"), bx + 11 + 25 * c, by + 31 + 25 * r);
                im.over(icon(recipes[i]), bx + 15 + 25 * c, by + 35 + 25 * r);
            }
        im.over(built.get("gui/sprites/recipe_book/page_backward.png"), bx + 38, by + 137);
        im.over(built.get("gui/sprites/recipe_book/page_forward_highlighted.png"), bx + 93, by + 137);
        String page = "1/3";
        font.draw(im, page, bx + 74 - font.width(page) / 2, by + 141, WHITE, true);

        im.over(built.get("gui/container/inventory.png").crop(0, 0, 176, 166), ix, by);
        im.over(built.get("gui/sprites/recipe_book/button_highlighted.png"), ix + 104, by + 61);
        font.draw(im, "Crafting", ix + 97, by + 6, LABEL, false);
        String[] armor = {"helmet", "chestplate", "leggings", "boots"};
        for (int i = 0; i < 4; i++) im.over(built.get("gui/sprites/container/slot/" + armor[i] + ".png"), ix + 8, by + 8 + 18 * i);
        im.over(built.get("gui/sprites/container/slot/shield.png"), ix + 77, by + 62);
        Img[] hotbar = {icon("netherite_sword"), icon("netherite_pickaxe"), icon("bow"), icon("golden_apple"), icon("ender_eye"),
                potion("potion", 0xF82423), potion("splash_potion", 0x33EBFF), icon("bread"), icon("torch")};
        int[] counts = {1, 1, 1, 8, 12, 1, 1, 32, 64};
        for (int i = 0; i < 9; i++) {
            int x = ix + 8 + 18 * i, y = by + 142;
            if (i == 3) im.over(built.get("gui/sprites/container/slot_highlight_back.png"), x - 4, y - 4);
            stack(im, font, hotbar[i], x, y, counts[i]);
            if (i == 3) im.over(built.get("gui/sprites/container/slot_highlight_front.png"), x - 4, y - 4);
        }
        scatter(im, font, grid(8, 84, 9, 3), ix, by, rng("inventory"), 9);
        drawTooltip(im, font, ix + 8 + 18 * 3 + 12, by + 142 - 12, new String[] {"Blood Apple"}, new int[] {MENU_TEXT});
        return im;
    }

    /**
     * A tooltip as the game draws it: both nine-slices 12 px out from the text block, then the lines, 10 px apart
     * with 2 px more after the first, shadowed. A one-line tooltip is 8 px tall.
     */
    void drawTooltip(Img im, Font font, int x, int y, String[] lines, int[] colours) {
        int w = 0;
        for (String l : lines) w = Math.max(w, font.width(l) - 1);
        int h = lines.length == 1 ? 8 : 10 * lines.length;
        im.over(nineSlice(built.get("gui/sprites/tooltip/background.png"), w + 24, h + 24, 9), x - 12, y - 12);
        im.over(nineSlice(built.get("gui/sprites/tooltip/frame.png"), w + 24, h + 24, 10), x - 12, y - 12);
        for (int i = 0; i < lines.length; i++) font.draw(im, lines[i], x, y + 10 * i + (i > 0 ? 2 : 0), colours[i], true);
    }

    /**
     * The "system windows": an advancement toast with the lang's crimson header, a recipe toast with its dark
     * text from code, a weapon tooltip, and two sliders, the second hovered.
     */
    Img systemScene(Font font) throws IOException {
        Img im = new Img(8 + 160 + 24 + 132 + 8, 8 + 32 + 6 + 32 + 10 + 20 + 4 + 20 + 8).fill(BACKDROP);
        int x = 8, y = 8;
        im.over(built.get("gui/sprites/toast/advancement.png"), x, y);
        im.over(icon("iron_chestplate"), x + 8, y + 8);
        font.draw(im, "Title Acquired!", x + 30, y + 7, MENU_TEXT, false);
        font.draw(im, "Suit Up", x + 30, y + 18, WHITE, false);
        y += 32 + 6;
        im.over(built.get("gui/sprites/toast/recipe.png"), x, y);
        im.over(icon("iron_sword"), x + 8, y + 8);
        font.draw(im, "New Recipes Unlocked!", x + 30, y + 7, 0x500050, false);
        font.draw(im, "Check your recipe book", x + 30, y + 18, VOID, false);
        y += 32 + 10;
        String[][] sliders = {{"FOV: 70", "slider", "slider_handle", "0.35"}, {"Music: 100%", "slider_highlighted", "slider_handle_highlighted", "1"}};
        for (String[] s : sliders) {
            im.over(nineSlice(built.get("gui/sprites/widget/" + s[1] + ".png"), 160, 20, 1), x, y);
            im.over(built.get("gui/sprites/widget/" + s[2] + ".png"), x + (int) Math.round(Double.parseDouble(s[3]) * (160 - 8)), y);
            font.draw(im, s[0], x + (160 - font.width(s[0])) / 2, y + 6, WHITE, true);
            y += 24;
        }
        drawTooltip(im, font, 8 + 160 + 24 + 12, 8 + 12, new String[] {"Netherite Sword", "", "When in Main Hand:", " 8 Attack Damage", " 1.6 Attack Speed"},
                new int[] {WHITE, WHITE, 0xAAAAAA, 0x00AA00, 0x00AA00});
        return im;
    }

    /** Crafting table, shulker box and chests at 3 and 6 rows, labelled as in game, with a few stacks. */
    Img containerScene(Font font) throws IOException {
        int gap = 8, col2 = 2 * gap + 176, row2 = 166 + 2 * gap;
        Img im = new Img(2 * 176 + 3 * gap, 166 + 222 + 3 * gap).fill(BACKDROP);
        Random rnd = rng("containers");

        im.over(built.get("gui/container/crafting_table.png").crop(0, 0, 176, 166), gap, gap);
        font.draw(im, "Crafting", gap + 29, gap + 6, LABEL, false);
        font.draw(im, "Inventory", gap + 8, gap + 72, LABEL, false);
        for (int c = 0; c < 3; c++) stack(im, font, icon("diamond"), gap + 30 + 18 * c, gap + 17, 1);
        for (int r = 1; r < 3; r++) stack(im, font, icon("stick"), gap + 48, gap + 17 + 18 * r, 1);
        stack(im, font, icon("diamond_pickaxe"), gap + 124, gap + 35, 1);
        scatter(im, font, grid(8, 84, 9, 3), gap, gap, rnd, 6);

        im.over(built.get("gui/container/shulker_box.png").crop(0, 0, 176, 166), col2, gap);
        font.draw(im, "Shulker Box", col2 + 8, gap + 6, LABEL, false);
        font.draw(im, "Inventory", col2 + 8, gap + 72, LABEL, false);
        scatter(im, font, grid(8, 18, 9, 3), col2, gap, rnd, 12);

        chest(im, font, gap, row2, 3, "Chest", rnd);
        chest(im, font, col2, row2, 6, "Large Chest", rnd);
        return im;
    }

    /** generic_54 drawn as the game does: the top rows 0 … 16 + 18n, then the bottom piece from row 126. */
    void chest(Img im, Font font, int x, int y, int rows, String title, Random rnd) throws IOException {
        Img g = built.get("gui/container/generic_54.png");
        im.over(g.crop(0, 0, 176, 17 + 18 * rows), x, y);
        im.over(g.crop(0, 126, 176, 96), x, y + 17 + 18 * rows);
        font.draw(im, title, x + 8, y + 6, LABEL, false);
        font.draw(im, "Inventory", x + 8, y + 20 + 18 * rows, LABEL, false);
        scatter(im, font, grid(8, 18, 9, rows), x, y, rnd, 3 * rows);
    }

    /** The pause menu's buttons with the red lang text, "Options..." hovered and one button at its smallest width. */
    Img pauseScene(Font font) {
        int w = 204 + 32, x0 = 16, y0 = 28;
        String[][] rows = {{"Resume Game"}, {"Advancements", "Statistics"}, {"Feedback...", "Server Links..."},
                {"Options...", "Open to LAN"}, {"Save and Quit to Title"}};
        Img im = new Img(w, y0 + 24 * rows.length + 12).fill(BACKDROP);
        font.draw(im, "Game Menu", (w - font.width("Game Menu")) / 2, 10, WHITE, true);
        for (int r = 0; r < rows.length; r++)
            for (int c = 0; c < rows[r].length; c++) {
                String s = rows[r][c];
                int bw = rows[r].length == 1 ? 204 : 98, x = x0 + 106 * c, y = y0 + 24 * r;
                im.over(nineSlice(built.get("gui/sprites/widget/" + (s.equals("Options...") ? "button_highlighted" : "button") + ".png"), bw, 20, 3), x, y);
                font.draw(im, s, x + (bw - font.width(s)) / 2, y + 6, MENU_TEXT, true);
            }
        return im;
    }

    /** The 17 palette tokens in two columns, grays and steel on the left, reds on the right: swatch, token name, hex. */
    Img paletteScene(Font font) {
        String[] names = {"void", "obsidian", "obsidian-2", "charcoal", "slate", "steel-800", "steel-600", "steel-400", "steel-200",
                "blood-950", "blood-900", "blood-700", "blood", "crimson-600", "crimson", "crimson-hot", "core"};
        int[] colours = {VOID, OBSIDIAN, OBSIDIAN_2, CHARCOAL, SLATE, STEEL_800, STEEL_600, STEEL_400, STEEL_200,
                BLOOD_950, BLOOD_900, BLOOD_700, BLOOD, CRIMSON_600, CRIMSON, CRIMSON_HOT, CORE};
        int colW = 156, rowH = 14, perCol = 9;
        Img im = new Img(2 * colW + 8, 8 + perCol * rowH).fill(BACKDROP);
        for (int i = 0; i < names.length; i++) {
            int x = 8 + colW * (i / perCol), y = 6 + rowH * (i % perCol);
            for (int sy = 0; sy < 11; sy++)
                for (int sx = 0; sx < 30; sx++)
                    im.set(x + sx, y + sy, sx == 0 || sy == 0 || sx == 29 || sy == 10 ? 0xFF3A3A3A : opaque(colours[i]));
            font.draw(im, names[i], x + 36, y + 2, WHITE, true);
            font.draw(im, hex(colours[i]), x + 106, y + 2, 0xAAAAAA, true);
        }
        return im;
    }

    /**
     * The survival HUD over a bright sky for 180 ticks (9 s): the selection steps across all nine slots, a second
     * each, while the hotbar and XP bar animate on their .mcmeta timelines. Everything loops cleanly except the
     * XP background, whose 46-tick cycle is cut once, in a flicker.
     */
    void hudGif(Font font, Path out) throws IOException {
        Img[] items = {icon("netherite_sword"), icon("netherite_pickaxe"), icon("bow"), icon("golden_apple"), icon("ender_eye"),
                potion("potion", 0xF82423), potion("splash_potion", 0x33EBFF), potion("lingering_potion", 0x8BBE19), icon("bread")};
        int[] counts = {1, 1, 1, 8, 12, 1, 1, 1, 32};
        Img strip = built.get("gui/sprites/hud/hotbar.png"), bg = built.get("gui/sprites/hud/experience_bar_background.png"),
                progress = built.get("gui/sprites/hud/experience_bar_progress.png");
        List<Img> frames = new ArrayList<>();
        List<Integer> ticks = new ArrayList<>();
        for (int t = 0; t < 180; t++) {
            Img im = new Img(182 + 2 * 36, 70).fill(SKY);
            int hx = 36, hy = 44;
            im.over(built.get("gui/sprites/hud/hotbar_offhand_left.png"), hx - 29, hy - 1);
            stack(im, font, icon("torch"), hx - 26, hy + 3, 64);
            im.over(strip.crop(0, 22 * frameAt(HOTBAR_TIMELINE, t), 182, 22), hx, hy);
            im.over(built.get("gui/sprites/hud/hotbar_selection.png"), hx - 1 + 20 * (t / 20), hy - 1);
            for (int i = 0; i < 9; i++) stack(im, font, items[i], hx + 3 + 20 * i, hy + 3, counts[i]);
            im.over(bg.crop(0, 5 * frameAt(XP_BACKGROUND_TIMELINE, t), 182, 5), hx, hy - 7);
            im.over(progress.crop(0, 5 * frameAt(XP_PROGRESS_TIMELINE, t), 110, 5), hx, hy - 7);
            String level = "30";
            int lx = hx + 91 - font.width(level) / 2, ly = hy - 13;
            for (int[] o : FOUR) font.draw(im, level, lx + o[0], ly + o[1], VOID, false);
            font.draw(im, level, lx, ly, XP_TEXT, false);
            for (int i = 0; i < 10; i++) {
                int x = hx + 8 * i, fx = hx + 182 - 9 - 8 * i;
                im.over(built.get("gui/sprites/hud/heart/container.png"), x, hy - 17);
                if (i < 8) im.over(built.get("gui/sprites/hud/heart/" + (i < 7 ? "full" : "half") + ".png"), x, hy - 17);
                im.over(built.get("gui/sprites/hud/food_empty.png"), fx, hy - 17);
                if (i < 7) im.over(built.get("gui/sprites/hud/" + (i < 6 ? "food_full" : "food_half") + ".png"), fx, hy - 17);
                im.over(built.get("gui/sprites/hud/" + (i < 5 ? "armor_full" : i == 5 ? "armor_half" : "armor_empty") + ".png"), x, hy - 27);
            }
            if (!frames.isEmpty() && java.util.Arrays.equals(frames.get(frames.size() - 1).p, im.p)) ticks.set(ticks.size() - 1, ticks.get(ticks.size() - 1) + 1);
            else {
                frames.add(im);
                ticks.add(1);
            }
        }
        writeGif(out, frames, ticks, 3);
    }

    /** A looping GIF at an integer scale; after the first frame each stores only the rectangle that changed. 1 tick = 5 cs. */
    void writeGif(Path out, List<Img> frames, List<Integer> ticks, int scale) throws IOException {
        Map<Integer, Integer> index = new HashMap<>();
        byte[] r = new byte[256], g = new byte[256], b = new byte[256];
        for (Img f : frames)
            for (int p : f.p)
                if (!index.containsKey(p)) {
                    int i = index.size();
                    if (i == 256) throw new IllegalStateException(out.getFileName() + ": more than 256 colours");
                    index.put(p, i);
                    r[i] = (byte) (p >> 16);
                    g[i] = (byte) (p >> 8);
                    b[i] = (byte) p;
                }
        IndexColorModel cm = new IndexColorModel(8, 256, r, g, b);
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        Files.deleteIfExists(out);
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out.toFile())) {
            writer.setOutput(ios);
            writer.prepareWriteSequence(null);
            for (int i = 0; i < frames.size(); i++) {
                Img f = frames.get(i);
                int x0 = 0, y0 = 0, x1 = f.w - 1, y1 = f.h - 1;
                if (i > 0) {
                    Img prev = frames.get(i - 1);
                    x0 = f.w;
                    y0 = f.h;
                    x1 = y1 = -1;
                    for (int y = 0; y < f.h; y++)
                        for (int x = 0; x < f.w; x++)
                            if (f.get(x, y) != prev.get(x, y)) {
                                x0 = Math.min(x0, x);
                                y0 = Math.min(y0, y);
                                x1 = Math.max(x1, x);
                                y1 = Math.max(y1, y);
                            }
                }
                BufferedImage bi = new BufferedImage((x1 - x0 + 1) * scale, (y1 - y0 + 1) * scale, BufferedImage.TYPE_BYTE_INDEXED, cm);
                for (int y = 0; y < bi.getHeight(); y++)
                    for (int x = 0; x < bi.getWidth(); x++)
                        bi.getRaster().setSample(x, y, 0, index.get(f.get(x0 + x / scale, y0 + y / scale)));
                IIOMetadata meta = writer.getDefaultImageMetadata(new ImageTypeSpecifier(bi), null);
                String format = meta.getNativeMetadataFormatName();
                IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree(format);
                IIOMetadataNode descriptor = child(root, "ImageDescriptor");
                descriptor.setAttribute("imageLeftPosition", String.valueOf(x0 * scale));
                descriptor.setAttribute("imageTopPosition", String.valueOf(y0 * scale));
                IIOMetadataNode control = child(root, "GraphicControlExtension");
                control.setAttribute("disposalMethod", "doNotDispose");
                control.setAttribute("userInputFlag", "FALSE");
                control.setAttribute("transparentColorFlag", "FALSE");
                control.setAttribute("transparentColorIndex", "0");
                control.setAttribute("delayTime", String.valueOf(5 * ticks.get(i)));
                if (i == 0) {
                    IIOMetadataNode loop = new IIOMetadataNode("ApplicationExtension");
                    loop.setAttribute("applicationID", "NETSCAPE");
                    loop.setAttribute("authenticationCode", "2.0");
                    loop.setUserObject(new byte[] {1, 0, 0});
                    child(root, "ApplicationExtensions").appendChild(loop);
                }
                meta.setFromTree(format, root);
                writer.writeToSequence(new IIOImage(bi, null, meta), null);
            }
            writer.endWriteSequence();
        }
        written++;
    }

    static IIOMetadataNode child(IIOMetadataNode root, String name) {
        for (int i = 0; i < root.getLength(); i++)
            if (root.item(i).getNodeName().equals(name)) return (IIOMetadataNode) root.item(i);
        IIOMetadataNode node = new IIOMetadataNode(name);
        root.appendChild(node);
        return node;
    }

    // ------------------------------------------------------------------ geometry helpers

    static final int[][] FOUR = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
    static final int[][] DIAG = {{1, 1}, {-1, 1}, {-1, -1}, {1, -1}};

    /** Chessboard distance to the nearest transparent pixel; outside the image counts as transparent. */
    static int[][] distance(Img img) {
        int w = img.w, h = img.h, inf = 1 << 20;
        int[][] d = new int[w][h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) d[x][y] = img.alpha(x, y) == 0 ? 0 : inf;
        int[][] fwd = {{-1, 0}, {-1, -1}, {0, -1}, {1, -1}}, bwd = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}};
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) relax(d, w, h, x, y, fwd);
        for (int y = h - 1; y >= 0; y--) for (int x = w - 1; x >= 0; x--) relax(d, w, h, x, y, bwd);
        return d;
    }

    /** Chessboard distance to the nearest opaque pixel of shape, capped at r + 1. Unlike distance(), the image edge doesn't count. */
    static int[][] near(Img shape, int r) {
        int[][] d = new int[shape.w][shape.h];
        for (int y = 0; y < shape.h; y++)
            for (int x = 0; x < shape.w; x++) {
                d[x][y] = r + 1;
                for (int dy = -r; dy <= r; dy++)
                    for (int dx = -r; dx <= r; dx++)
                        if (shape.alpha(x + dx, y + dy) != 0) d[x][y] = Math.min(d[x][y], Math.max(Math.abs(dx), Math.abs(dy)));
            }
        return d;
    }

    static void relax(int[][] d, int w, int h, int x, int y, int[][] dirs) {
        for (int[] o : dirs) {
            int nx = x + o[0], ny = y + o[1];
            int nd = nx < 0 || ny < 0 || nx >= w || ny >= h ? 0 : d[nx][ny];
            if (nd + 1 < d[x][y]) d[x][y] = nd + 1;
        }
    }

    /** The pixels at one distance, ordered as a closed loop (clockwise from the top-left). */
    static List<int[]> ringLoop(int[][] d, int w, int h, int ring) {
        boolean[][] seen = new boolean[w][h];
        int total = 0, sx = -1, sy = -1;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                if (d[x][y] == ring) {
                    total++;
                    if (sx < 0) { sx = x; sy = y; }
                }
        List<int[]> loop = new ArrayList<>();
        int x = sx, y = sy;
        while (true) {
            loop.add(new int[] {x, y});
            seen[x][y] = true;
            int[] next = null;
            for (int[][] dirs : new int[][][] {FOUR, DIAG}) {
                for (int[] o : dirs) {
                    int nx = x + o[0], ny = y + o[1];
                    if (nx >= 0 && ny >= 0 && nx < w && ny < h && d[nx][ny] == ring && !seen[nx][ny]) { next = new int[] {nx, ny}; break; }
                }
                if (next != null) break;
            }
            if (next == null) break;
            x = next[0];
            y = next[1];
        }
        if (loop.size() != total) throw new IllegalStateException("ring " + ring + " is not a simple loop (" + loop.size() + "/" + total + ")");
        return loop;
    }

    static int[][] inwardDirs(List<int[]> loop, int[][] d) {
        int w = d.length, h = d[0].length;
        int[][] res = new int[loop.size()][];
        for (int i = 0; i < loop.size(); i++) {
            int[] p = loop.get(i);
            int target = d[p[0]][p[1]] + 1;
            res[i] = new int[] {0, 0};
            outer:
            for (int[][] dirs : new int[][][] {FOUR, DIAG})
                for (int[] o : dirs) {
                    int nx = p[0] + o[0], ny = p[1] + o[1];
                    if (nx >= 0 && ny >= 0 && nx < w && ny < h && d[nx][ny] == target) { res[i] = o; break outer; }
                }
        }
        return res;
    }

    static int nearest(List<int[]> loop, int x, int y) {
        int best = 0, bd = Integer.MAX_VALUE;
        for (int i = 0; i < loop.size(); i++) {
            int dd = Math.abs(loop.get(i)[0] - x) + Math.abs(loop.get(i)[1] - y);
            if (dd < bd) { bd = dd; best = i; }
        }
        return best;
    }

    /** Pixels strictly between two points, so consecutive arc pixels always touch. */
    static List<int[]> between(int x0, int y0, int x1, int y1) {
        List<int[]> res = new ArrayList<>();
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int s = 1; s < steps; s++)
            res.add(new int[] {x0 + Math.round((float) (x1 - x0) * s / steps), y0 + Math.round((float) (y1 - y0) * s / steps)});
        return res;
    }

    static boolean edge(Img img, int x, int y) {
        for (int[] o : FOUR) if (img.alpha(x + o[0], y + o[1]) == 0) return true;
        return false;
    }

    static boolean ok(boolean[][] m, int x, int y) {
        return x >= 0 && y >= 0 && x < m.length && y < m[0].length && m[x][y];
    }

    static boolean[][] or(boolean[][] a, boolean[][] b) {
        boolean[][] r = new boolean[a.length][a[0].length];
        for (int x = 0; x < a.length; x++) for (int y = 0; y < a[0].length; y++) r[x][y] = a[x][y] || b[x][y];
        return r;
    }

    static boolean inAny(int[][] rects, int x, int y) {
        for (int[] r : rects) if (x >= r[0] && x <= r[2] && y >= r[1] && y <= r[3]) return true;
        return false;
    }

    static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    static Random rng(String name) {
        return new Random(0x484F5442L ^ name.hashCode());
    }

    static int opaque(int rgb) {
        return 0xFF000000 | rgb;
    }

    static int withAlpha(int rgb, int a) {
        return (a << 24) | rgb;
    }

    static double luminance(int rgb) {
        return (0.2126 * ((rgb >> 16) & 0xFF) + 0.7152 * ((rgb >> 8) & 0xFF) + 0.0722 * (rgb & 0xFF)) / 255.0;
    }

    static String hex(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    // ------------------------------------------------------------------ I/O

    Img read(String texturePath) throws IOException {
        ZipEntry e = jar.getEntry("assets/minecraft/textures/" + texturePath);
        if (e == null) throw new IOException("Not in the jar: " + texturePath);
        try (InputStream in = jar.getInputStream(e)) {
            BufferedImage bi = ImageIO.read(in);
            Img img = new Img(bi.getWidth(), bi.getHeight());
            bi.getRGB(0, 0, img.w, img.h, img.p, 0, img.w);
            return img;
        }
    }

    void write(String texturePath, Img img, boolean item) throws IOException {
        checkTokens(texturePath, img, item);
        writePng(pack.resolve("assets/minecraft/textures/" + texturePath), img);
        built.put(texturePath, img);
    }

    static void checkTokens(String name, Img img, boolean item) {
        for (int y = 0; y < img.h; y++)
            for (int x = 0; x < img.w; x++) {
                int a = img.alpha(x, y);
                if (a == 0) continue;
                if (!TOKENS.contains(img.rgb(x, y))) throw new IllegalStateException(name + ": off-palette " + hex(img.rgb(x, y)) + " at " + x + "," + y);
                if (item && a != 255) throw new IllegalStateException(name + ": item alpha " + a + " at " + x + "," + y);
            }
    }

    void writePng(Path out, Img img) throws IOException {
        Files.createDirectories(out.getParent());
        BufferedImage bi = new BufferedImage(img.w, img.h, BufferedImage.TYPE_INT_ARGB);
        bi.setRGB(0, 0, img.w, img.h, img.p, 0, img.w);
        ImageIO.write(bi, "png", out.toFile());
        written++;
    }

    void writeText(String packPath, String text) throws IOException {
        Path out = pack.resolve(packPath);
        Files.createDirectories(out.getParent());
        Files.writeString(out, text, StandardCharsets.UTF_8);
        written++;
    }

    /** Sprite metadata is read from the pack that supplies the PNG, so overridden sprites ship vanilla's copy. */
    void copyMeta(String texturePath) throws IOException {
        String name = "assets/minecraft/textures/" + texturePath + ".mcmeta";
        ZipEntry e = jar.getEntry(name);
        if (e == null) throw new IOException("Not in the jar: " + name);
        try (InputStream in = jar.getInputStream(e)) {
            Path out = pack.resolve(name);
            Files.createDirectories(out.getParent());
            Files.write(out, in.readAllBytes());
            written++;
        }
    }

    static final class Img {
        final int w, h;
        final int[] p;

        Img(int w, int h) {
            this.w = w;
            this.h = h;
            p = new int[w * h];
        }

        int get(int x, int y) {
            return x < 0 || y < 0 || x >= w || y >= h ? 0 : p[y * w + x];
        }

        void set(int x, int y, int argb) {
            if (x >= 0 && y >= 0 && x < w && y < h) p[y * w + x] = argb;
        }

        int alpha(int x, int y) {
            return get(x, y) >>> 24;
        }

        int rgb(int x, int y) {
            return get(x, y) & 0xFFFFFF;
        }

        Img copy() {
            Img c = new Img(w, h);
            System.arraycopy(p, 0, c.p, 0, p.length);
            return c;
        }

        Img fill(int argb) {
            java.util.Arrays.fill(p, argb);
            return this;
        }

        Img crop(int x0, int y0, int cw, int ch) {
            Img c = new Img(cw, ch);
            for (int y = 0; y < ch; y++) for (int x = 0; x < cw; x++) c.set(x, y, get(x0 + x, y0 + y));
            return c;
        }

        void paste(Img src, int ox, int oy) {
            for (int y = 0; y < src.h; y++) for (int x = 0; x < src.w; x++) set(ox + x, oy + y, src.get(x, y));
        }

        Img scaled(int k) {
            Img s = new Img(w * k, h * k);
            for (int y = 0; y < s.h; y++) for (int x = 0; x < s.w; x++) s.p[y * s.w + x] = p[(y / k) * w + x / k];
            return s;
        }

        Img downscale(int k) {
            Img s = new Img(w / k, h / k);
            for (int y = 0; y < s.h; y++) for (int x = 0; x < s.w; x++) s.p[y * s.w + x] = p[(y * k) * w + x * k];
            return s;
        }

        /** Source-over compositing, for previews. */
        void over(Img src, int ox, int oy) {
            for (int y = 0; y < src.h; y++)
                for (int x = 0; x < src.w; x++) {
                    int s = src.get(x, y), a = s >>> 24;
                    if (a == 0) continue;
                    int d = get(ox + x, oy + y);
                    int r = (((s >> 16) & 0xFF) * a + ((d >> 16) & 0xFF) * (255 - a)) / 255;
                    int g = (((s >> 8) & 0xFF) * a + ((d >> 8) & 0xFF) * (255 - a)) / 255;
                    int b = ((s & 0xFF) * a + (d & 0xFF) * (255 - a)) / 255;
                    int outA = Math.min(255, a + (d >>> 24) * (255 - a) / 255);
                    set(ox + x, oy + y, outA << 24 | r << 16 | g << 8 | b);
                }
        }
    }
}
