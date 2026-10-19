import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/**
 * Generates the HotbarX textures from the vanilla templates in a Minecraft 26.3 (or 26.2) client jar,
 * following docs/SPEC.md. Run from the repository root:
 *
 *   java tools/BuildTextures.java [path/to/client.jar] [--preview <dir>]
 *
 * Output goes to pack/. The build is deterministic and fails on any off-palette pixel or broken
 * layout rule (anchors, terminals, text-safe zones, the generic_54 seam).
 */
public class BuildTextures {

    // Palette tokens (SPEC §4). Every token has G == B.
    static final int VOID = 0x000000, OBSIDIAN = 0x0A0A0A, OBSIDIAN_2 = 0x141414, CHARCOAL = 0x1E1E1E,
            SLATE = 0x2A2A2A, BLOOD_950 = 0x1A0000, BLOOD_900 = 0x330000, BLOOD_700 = 0x660000,
            BLOOD = 0x990000, CRIMSON_600 = 0xCC0000, CRIMSON = 0xFF0000, CRIMSON_HOT = 0xFF6666,
            CORE = 0xFFE5E5;
    static final Set<Integer> TOKENS = Set.of(VOID, OBSIDIAN, OBSIDIAN_2, CHARCOAL, SLATE, BLOOD_950,
            BLOOD_900, BLOOD_700, BLOOD, CRIMSON_600, CRIMSON, CRIMSON_HOT, CORE);
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
        Path jarPath = null, preview = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--preview")) preview = Path.of(args[++i]);
            else jarPath = Path.of(args[i]);
        }
        if (jarPath == null) jarPath = findJar();
        Path pack = Path.of("pack");
        if (!Files.exists(pack.resolve("pack.mcmeta"))) throw new IllegalStateException("Run from the repository root (pack/pack.mcmeta not found).");
        try (ZipFile jar = new ZipFile(jarPath.toFile())) {
            BuildTextures b = new BuildTextures(jar, pack);
            b.containers();
            b.hotbar();
            b.hotbarSelection();
            b.offhand();
            b.attackIndicator();
            b.hearts();
            b.slotSprites();
            b.buttons();
            b.items();
            b.packIcon();
            System.out.println("Wrote " + b.written + " files from " + jarPath);
            if (preview != null) {
                b.writePreviews(preview);
                System.out.println("Previews in " + preview.toAbsolutePath());
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

    static List<Arc> standardArcs(int w, int h) {
        return List.of(new Arc(2, 2, 14), new Arc(w - 3, 2, 14), new Arc(2, h - 3, 14), new Arc(w - 3, h - 3, 14),
                new Arc(w / 2 - 20, 2, 9), new Arc(2, h / 2, 10), new Arc(w - 3, h / 2 + 10, 10), new Arc(w / 2 + 12, h - 3, 11));
    }

    void containers() throws IOException {
        container("inventory", new int[][] {{95, 4, 166, 15}}, new int[0][], standardArcs(176, 166), false);
        container("shulker_box", new int[][] {{6, 4, 166, 15}, {6, 71, 90, 82}}, new int[0][], standardArcs(176, 166), false);
        container("crafting_table", new int[][] {{27, 4, 166, 15}, {6, 71, 90, 82}}, new int[0][], standardArcs(176, 166), false);
        // generic_54: no long arcs on the side bands over rows 14-127; those get the repeated 18-row tile instead.
        container("generic_54", new int[][] {{6, 4, 166, 15}, {6, 127, 90, 138}},
                new int[][] {{0, 14, 7, 127}, {168, 14, 175, 127}},
                List.of(new Arc(2, 2, 12), new Arc(173, 2, 12), new Arc(70, 2, 9),
                        new Arc(2, 219, 14), new Arc(173, 219, 14), new Arc(100, 219, 11),
                        new Arc(2, 175, 9), new Arc(173, 190, 9)),
                true);
    }

    void container(String name, int[][] textZones, int[][] noArcRects, List<Arc> arcs, boolean seamTile) throws IOException {
        String path = "gui/container/" + name + ".png";
        Img v = read(path);
        Img img = mapContainerColors(v, name);
        int[][] d = distance(img);
        int[] ring = {0, VOID, BLOOD_900, BLOOD, BLOOD_950};
        for (int y = 0; y < img.h; y++)
            for (int x = 0; x < img.w; x++) {
                if (img.alpha(x, y) == 0) continue;
                if (d[x][y] <= 4) img.set(x, y, opaque(ring[d[x][y]]));
                else if (v.rgb(x, y) == 0x555555) throw new IllegalStateException(name + ": bevel colour inside the panel at " + x + "," + y);
            }
        Img plain = img.copy();

        boolean[][] allowed = arcMask(img, d, textZones, noArcRects, null);
        List<int[]> loop = ringLoop(d, img.w, img.h, 3);
        int[][] inward = inwardDirs(loop, d);
        Random rnd = rng(name);
        Set<Long> body = new HashSet<>();
        for (Arc a : arcs) drawArc(img, loop, inward, a, allowed, rnd, body);

        boolean[][] haloMask = allowed;
        if (seamTile) {
            // Short sparks that live strictly inside the tile rows 19-32, then repeated every 18 rows (SPEC §6.2).
            boolean[][] tile = arcMask(img, d, textZones, new int[0][], (x, y) -> y >= 19 && y <= 32 && (x <= 6 || x >= 169));
            drawArc(img, loop, inward, new Arc(2, 26, 4), tile, rnd, body);
            drawArc(img, loop, inward, new Arc(173, 25, 4), tile, rnd, body);
            haloMask = or(allowed, tile);
        }
        halo(img, body, haloMask);
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
                    case 0x373737, 0x555555 -> out.set(x, y, opaque(BLOOD_700));
                    case 0xFFFFFF -> out.set(x, y, opaque(BLOOD));
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
                            int col = offCorner ? BLOOD_700 : arrow ? (p[0] == maxX ? CRIMSON : BLOOD) : OBSIDIAN_2;
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

    record Pt(int x, int y, int loopIdx, boolean bend) {}

    /** One lightning arc that follows the middle frame ring, zig-zagging across rings 1-3 (SPEC §5.1). */
    static void drawArc(Img img, List<int[]> loop, int[][] inward, Arc arc, boolean[][] allowed, Random rnd, Set<Long> body) {
        int n = loop.size();
        int center = nearest(loop, arc.x(), arc.y());
        List<Pt> pts = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        int o = 0, run = 0, maxRun = 2 + rnd.nextInt(3), centerPos = -1;
        Pt prev = null;
        for (int k = -arc.half(); k <= arc.half(); k++) {
            int i = Math.floorMod(center + k, n);
            boolean bend = false;
            if (prev != null) {
                run++;
                if (run >= maxRun || rnd.nextInt(100) < 30) {
                    o = o != 0 ? 0 : (rnd.nextBoolean() ? 1 : -1);
                    run = 0;
                    maxRun = 2 + rnd.nextInt(3);
                    bend = true;
                }
            }
            int[] lp = loop.get(i), dir = inward[i];
            Pt p = new Pt(lp[0] + o * dir[0], lp[1] + o * dir[1], i, bend);
            if (prev != null)
                for (int[] q : between(prev.x(), prev.y(), p.x(), p.y()))
                    if (seen.add(key(q[0], q[1]))) pts.add(new Pt(q[0], q[1], i, false));
            if (seen.add(key(p.x(), p.y()))) pts.add(p);
            if (k == 0) centerPos = pts.size() - 1;
            prev = p;
        }
        if (centerPos < 0 || !ok(allowed, pts.get(centerPos).x(), pts.get(centerPos).y())) return;
        int lo = centerPos, hi = centerPos;
        while (lo > 0 && ok(allowed, pts.get(lo - 1).x(), pts.get(lo - 1).y())) lo--;
        while (hi < pts.size() - 1 && ok(allowed, pts.get(hi + 1).x(), pts.get(hi + 1).y())) hi++;
        List<Pt> seg = pts.subList(lo, hi + 1);
        int len = seg.size();
        if (len < 5) return;
        int cores = 0;
        for (int k = 0; k < len; k++) {
            Pt p = seg.get(k);
            int fromEnd = Math.min(k, len - 1 - k);
            int c = CRIMSON;
            if (fromEnd == 0) c = BLOOD;
            else if (fromEnd == 1) c = CRIMSON_600;
            else if (p.bend() && (cores + 1) * 4 <= len && rnd.nextInt(100) < 45) { c = CORE; cores++; }
            img.set(p.x(), p.y(), opaque(c));
            if (c == CRIMSON || c == CORE) body.add(key(p.x(), p.y()));
        }
        for (int k = 2; k < len - 2; k++)
            if (seg.get(k).bend() && rnd.nextInt(2) == 0) fork(img, loop, inward, seg.get(k), allowed, rnd, body);
    }

    static void fork(Img img, List<int[]> loop, int[][] inward, Pt from, boolean[][] allowed, Random rnd, Set<Long> body) {
        int n = loop.size(), i = from.loopIdx();
        int[] in = inward[i], a = loop.get(i), b = loop.get((i + 1) % n);
        int tx = Integer.signum(b[0] - a[0]), ty = Integer.signum(b[1] - a[1]);
        int s = rnd.nextBoolean() ? 1 : -1, len = 2 + rnd.nextInt(3);
        List<int[]> fp = new ArrayList<>();
        int x = from.x(), y = from.y();
        for (int k = 0; k < len; k++) {
            boolean diag = k == 0 || rnd.nextBoolean();
            int dx = Integer.signum(in[0] + (diag ? s * tx : 0)), dy = Integer.signum(in[1] + (diag ? s * ty : 0));
            if (dx == 0 && dy == 0) break;
            x += dx;
            y += dy;
            if (!ok(allowed, x, y) || LIT.contains(img.rgb(x, y)) || img.rgb(x, y) == BLOOD && img.get(x, y) != opaque(BLOOD)) break;
            fp.add(new int[] {x, y});
        }
        if (fp.size() < 2) return;
        for (int k = 0; k < fp.size(); k++) {
            int c = k == fp.size() - 1 ? BLOOD : k == fp.size() - 2 ? CRIMSON_600 : CRIMSON;
            img.set(fp.get(k)[0], fp.get(k)[1], opaque(c));
            if (c == CRIMSON) body.add(key(fp.get(k)[0], fp.get(k)[1]));
        }
    }

    /** Orthogonal neighbours of bright arc pixels that land on fill or the inner ring become the glow halo. */
    static void halo(Img img, Set<Long> body, boolean[][] mask) {
        for (long k : body) {
            int x = (int) (k >> 32), y = (int) k;
            for (int[] o : FOUR) {
                int nx = x + o[0], ny = y + o[1];
                if (!ok(mask, nx, ny)) continue;
                int c = img.get(nx, ny);
                if (c == opaque(OBSIDIAN) || c == opaque(BLOOD_950)) img.set(nx, ny, opaque(BLOOD_900));
            }
        }
    }

    // ------------------------------------------------------------------ hotbar (SPEC §7)

    static final int HOTBAR_FRAMES = 6;
    static final String HOTBAR_MCMETA = """
            {
              "animation": {
                "height": 22,
                "frametime": 2,
                "frames": [ { "index": 0, "time": 60 }, 1, 2, 3, { "index": 0, "time": 40 }, 4, 5 ]
              }
            }
            """;

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
                else if (y <= 2 || y >= 19 || x <= 2 || x >= 179) c = opaque(y == 2 || y == 19 || x == 2 || x == 179 ? BLOOD : CHARCOAL);
                else if (isCell(x)) c = CELL;
                else c = opaque(OBSIDIAN_2);
                im.set(x, y, c);
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
        int full = f == 4 ? 3 : f >= 1 && f <= 3 ? 1 + rnd.nextInt(2) : 0;
        for (int i = 0; i < Math.min(full, seps.size()); i++) bolt(im, seps.get(i), 3, 18, rnd, false);
        if (f == 0) {
            bolt(im, 2, 3, 6, rnd, true);
            bolt(im, 5, 15, 18, rnd, true);
        } else if (f == 5) {
            bolt(im, 3 + rnd.nextInt(3), 3, 8, rnd, true);
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

    static void bolt(Img im, int sep, int y0, int y1, Random rnd, boolean taper) {
        int x0 = 19 + 20 * sep, c = 1 + rnd.nextInt(2), run = 0;
        List<int[]> pts = new ArrayList<>();
        boolean down = y0 <= y1;
        int len = Math.abs(y1 - y0) + 1, cores = 0;
        for (int k = 0; k < len; k++) {
            int y = down ? y0 + k : y0 - k;
            boolean bend = false;
            if (k > 0 && (++run >= 3 || rnd.nextInt(100) < 40)) {
                int nc = c + (rnd.nextBoolean() ? 1 : -1);
                if (nc < 0 || nc > 3) nc = c == 0 ? 1 : c - 1;
                c = nc;
                run = 0;
                bend = true;
            }
            pts.add(new int[] {x0 + c, y, bend ? 1 : 0});
        }
        if (taper) java.util.Collections.reverse(pts); // stubs grow out of the rail, so the taper is at the free end
        for (int k = 0; k < pts.size(); k++) {
            int[] p = pts.get(k);
            int col = CRIMSON;
            int fromEnd = pts.size() - 1 - k;
            if (taper && fromEnd == pts.size() - 1) col = BLOOD;
            else if (taper && fromEnd == pts.size() - 2) col = CRIMSON_600;
            else if (p[2] == 1 && (cores + 1) * 4 <= pts.size() && rnd.nextInt(100) < 45) { col = CORE; cores++; }
            im.set(p[0], p[1], opaque(col));
        }
        for (int[] p : pts)
            for (int[] o : FOUR) {
                int nx = p[0] + o[0], ny = p[1] + o[1];
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
                else if (y <= 3 || y >= 20 || x <= 2 || x >= 19) c = opaque(y == 3 || y == 20 || x == 2 || x == 19 ? BLOOD : CHARCOAL);
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
        write("gui/sprites/hud/hotbar_offhand_left.png", left, false);
        Img right = new Img(29, 24);
        for (int y = 0; y < 24; y++) for (int x = 0; x < 29; x++) right.set(28 - x, y, left.get(x, y));
        write("gui/sprites/hud/hotbar_offhand_right.png", right, false);
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
                    if (v.alpha(x, y) == 0) { out.set(x, y, opaque(OBSIDIAN)); continue; }
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
        button("button", BLOOD, SLATE, OBSIDIAN, CHARCOAL, false);
        button("button_highlighted", CRIMSON, CRIMSON_HOT, BLOOD, CHARCOAL, true);
        button("button_disabled", BLOOD_900, OBSIDIAN, OBSIDIAN, OBSIDIAN, false);
    }

    void button(String name, int ring, int bevelTopLeft, int bevelBottomRight, int face, boolean sparks) throws IOException {
        Img im = new Img(200, 20);
        for (int y = 0; y < 20; y++)
            for (int x = 0; x < 200; x++) {
                int r = Math.min(Math.min(x, 199 - x), Math.min(y, 19 - y));
                int c = switch (r) {
                    case 0 -> VOID;
                    case 1 -> ring;
                    case 2 -> (y == 2 && x < 197) || (x == 2 && y < 17) ? bevelTopLeft : bevelBottomRight;
                    default -> face;
                };
                im.set(x, y, opaque(c));
            }
        if (sparks) for (int[] p : new int[][] {{1, 1}, {198, 1}, {1, 18}, {198, 18}}) im.set(p[0], p[1], opaque(CORE));
        String path = "gui/sprites/widget/" + name + ".png";
        write(path, im, false);
        copyMeta(path);
    }

    // ------------------------------------------------------------------ items (SPEC §9)

    static final Set<Integer> TOOL_HEAD = Set.of(0x4A2940, 0x867B86, 0x706770, 0x51444E, 0x5D565D, 0x4F3C3E, 0x322727);
    static final int TOOL_OUTLINE = 0x231012;
    static final Map<Integer, Integer> HEAD_SHADE = Map.of(0x867B86, SLATE, 0x706770, SLATE, 0x5D565D, CHARCOAL,
            0x51444E, CHARCOAL, 0x4F3C3E, CHARCOAL, 0x4A2940, OBSIDIAN, 0x322727, OBSIDIAN, TOOL_OUTLINE, OBSIDIAN);
    static final Map<Integer, Integer> HANDLE_SHADE = Map.of(0x734543, SLATE, 0x603432, CHARCOAL, 0x2F2122, BLOOD_700,
            TOOL_OUTLINE, BLOOD_700, 0x3B393B, BLOOD, 0x434043, BLOOD, 0x160814, BLOOD_700);

    void items() throws IOException {
        for (String t : new String[] {"netherite_sword", "netherite_pickaxe", "netherite_axe"}) tool(t);
        bloodApple();
        Map<Integer, Integer> bottle = Map.of(0xD46D49, CRIMSON_600, 0xA94725, BLOOD, 0x973716, BLOOD_700, 0xCCAFA5, SLATE,
                0xD4E5F7, BLOOD, 0xB3CFEC, BLOOD_700, 0x5D8FC2, SLATE, 0x8BADD0, SLATE);
        recolorItem("potion", bottle, new int[][] {{5, 2, CRIMSON}, {11, 4, CRIMSON}, {3, 7, CRIMSON_600}, {12, 7, CORE}});
        recolorItem("splash_potion", bottle, new int[][] {{8, 2, CRIMSON}, {14, 4, CRIMSON}, {4, 6, CRIMSON_600}, {13, 7, CORE}});
        recolorItem("lingering_potion", bottle, new int[][] {{6, 4, CRIMSON}, {10, 4, CRIMSON}, {4, 7, CRIMSON_600}, {12, 8, CORE}});
        enderEye();
    }

    void tool(String name) throws IOException {
        String path = "item/" + name + ".png";
        Img v = read(path), out = new Img(16, 16);
        int coreX = -1, coreY = -1, best = Integer.MIN_VALUE;
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++) {
                if (v.alpha(x, y) == 0) continue;
                int c = v.rgb(x, y);
                boolean head = TOOL_HEAD.contains(c);
                if (c == TOOL_OUTLINE)
                    for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) if (TOOL_HEAD.contains(v.rgb(x + dx, y + dy)) && v.alpha(x + dx, y + dy) != 0) head = true;
                int col;
                if (head) {
                    col = edge(v, x, y) ? CRIMSON : HEAD_SHADE.get(c);
                    if (col == CRIMSON && x - y > best) { best = x - y; coreX = x; coreY = y; }
                } else {
                    Integer h = HANDLE_SHADE.get(c);
                    if (h == null) throw new IllegalStateException(name + ": unmapped colour " + hex(c));
                    col = h;
                }
                out.set(x, y, opaque(col));
            }
        out.set(coreX, coreY, opaque(CORE));
        write(path, out, true);
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

    void packIcon() throws IOException {
        Img im = new Img(32, 32);
        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) im.set(x, y, opaque(OBSIDIAN));
        int[][] d = distance(im);
        int[] ring = {0, VOID, BLOOD_900, BLOOD, BLOOD_950};
        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) if (d[x][y] <= 4) im.set(x, y, opaque(ring[d[x][y]]));
        Polygon boltShape = new Polygon(new int[] {20, 9, 15, 11, 24, 17, 23}, new int[] {4, 18, 18, 29, 13, 13, 4}, 7);
        boolean[][] in = new boolean[32][32];
        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) in[x][y] = boltShape.contains(x + 0.5, y + 0.5);
        for (int y = 0; y < 32; y++)
            for (int x = 0; x < 32; x++) {
                if (in[x][y]) {
                    int inner = 0;
                    for (int[] o : FOUR) if (x + o[0] >= 0 && x + o[0] < 32 && y + o[1] >= 0 && y + o[1] < 32 && in[x + o[0]][y + o[1]]) inner++;
                    im.set(x, y, opaque(inner == 4 ? CRIMSON_HOT : CRIMSON));
                } else {
                    for (int[] o : FOUR)
                        if (x + o[0] >= 0 && x + o[0] < 32 && y + o[1] >= 0 && y + o[1] < 32 && in[x + o[0]][y + o[1]] && d[x][y] > 4) im.set(x, y, opaque(BLOOD_900));
                }
            }
        boolean[][] allowed = new boolean[32][32];
        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) allowed[x][y] = d[x][y] <= 4;
        List<int[]> loop = ringLoop(d, 32, 32, 3);
        int[][] inward = inwardDirs(loop, d);
        Random rnd = rng("pack");
        Set<Long> body = new HashSet<>();
        for (Arc a : List.of(new Arc(2, 2, 6), new Arc(29, 29, 6))) drawArc(im, loop, inward, a, allowed, rnd, body);
        Img big = im.scaled(4);
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
        for (String n : new String[] {"netherite_sword", "netherite_pickaxe", "netherite_axe", "golden_apple", "ender_eye"})
            icons.add(built.get("item/" + n + ".png"));
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
