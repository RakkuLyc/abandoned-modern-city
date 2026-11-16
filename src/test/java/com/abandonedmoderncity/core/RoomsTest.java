package com.abandonedmoderncity.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * Floors, rooms and places for a chest of the buildings ({@link Rooms}, stage 2 of the procedural chests): on small
 * templates built here and on the 10 buildings, with a floor plan image of each building in
 * {@code build/test-renders/rooms/}.
 */
class RoomsTest {
    /** The most chests a building of each height class will get (stage 3; immense approved, none exists yet). */
    static final Map<String, Integer> MOST = Map.of("small", 28, "medium", 47, "large", 65, "immense", 83);

    /** A template drawn in code: a floor of stone at y = 0 (the ground layer). */
    private static final class Box {
        final int sx;
        final int sy;
        final int sz;
        final List<String> palette = new ArrayList<>();
        final int[] cells;

        Box(int sx, int sy, int sz) {
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            cells = new int[sx * sy * sz];
            Arrays.fill(cells, idx("minecraft:air"));
            fill(0, 0, 0, sx - 1, 0, sz - 1, "minecraft:stone");
        }

        int idx(String s) {
            int i = palette.indexOf(s);
            if (i < 0) {
                palette.add(s);
                i = palette.size() - 1;
            }
            return i;
        }

        Box set(int x, int y, int z, String s) {
            cells[x + sx * (z + sz * y)] = idx(s);
            return this;
        }

        Box fill(int x0, int y0, int z0, int x1, int y1, int z1, String s) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    for (int x = x0; x <= x1; x++) {
                        set(x, y, z, s);
                    }
                }
            }
            return this;
        }

        /** Outer walls from y = 1 to 4 and a roof at 5. */
        Box shell() {
            fill(0, 1, 0, sx - 1, 4, 0, "minecraft:stone_bricks");
            fill(0, 1, sz - 1, sx - 1, 4, sz - 1, "minecraft:stone_bricks");
            fill(0, 1, 0, 0, 4, sz - 1, "minecraft:stone_bricks");
            fill(sx - 1, 1, 0, sx - 1, 4, sz - 1, "minecraft:stone_bricks");
            return fill(0, 5, 0, sx - 1, 5, sz - 1, "minecraft:stone_bricks");
        }

        Box door(int x, int z, String facing) {
            set(x, 1, z, "minecraft:oak_door[facing=" + facing + ",half=lower,hinge=left,open=false,powered=false]");
            return set(x, 2, z, "minecraft:oak_door[facing=" + facing + ",half=upper,hinge=left,open=false,powered=false]");
        }

        Structures.Def def() {
            return Structures.of("buildings_small", "rooms_test", Template.of("rooms_test", sx, sy, sz, palette, cells),
                    0, 0, "rooms_test", new HashMap<>());
        }
    }

    /** Two rooms of 9 x 9 side by side, a door in the wall between them and one to the street. */
    private static Box twoRooms() {
        return new Box(21, 6, 11).shell().fill(10, 1, 1, 10, 4, 9, "minecraft:stone_bricks").door(10, 5, "east")
                .door(0, 5, "west");
    }

    @Test
    void twoRoomsJoinedByADoor() {
        Structures.Def d = twoRooms().def();
        Rooms.Layout l = d.rooms();
        assertEquals(1, l.floors().size());
        assertEquals(2, l.rooms(), "the wall with a door splits the floor in two rooms");
        Rooms.Floor f = l.floors().get(0);
        assertEquals(Rooms.DOOR, f.plan()[10 + 21 * 5], "the door between the rooms");
        assertFalse(l.spots().isEmpty());
        for (Rooms.Spot s : l.spots()) {
            for (int[] door : new int[][]{{10, 5}, {0, 5}}) {
                assertTrue(Math.abs(s.x() - door[0]) > Rooms.DOOR_CLEAR || Math.abs(s.z() - door[1]) > Rooms.DOOR_CLEAR,
                        "a chest near a door at " + s);
            }
            assertTrue(s.x() == 1 || s.x() == 9 || s.x() == 11 || s.x() == 19 || s.z() == 1 || s.z() == 9,
                    "a chest against a wall: " + s);
            boolean corner = (s.x() == 1 || s.x() == 9 || s.x() == 11 || s.x() == 19) && (s.z() == 1 || s.z() == 9);
            assertEquals(corner, s.is(Rooms.CORNER), s.toString());
            assertEquals(corner ? 3 : 2, s.weight());
            assertFalse(s.is(Rooms.HARD), "on the ground floor, under a roof");
        }
    }

    @Test
    void noChestInACorridorNorBesideIt() {
        // two rooms joined by a corridor 2 wide and 5 long (z = 4..5, x = 8..12)
        Box b = new Box(21, 6, 11).shell().fill(8, 1, 1, 12, 4, 9, "minecraft:stone_bricks")
                .fill(8, 1, 4, 12, 4, 5, "minecraft:air").door(0, 5, "west");
        Rooms.Layout l = b.def().rooms();
        assertEquals(2, l.rooms());
        int[] plan = l.floors().get(0).plan();
        for (int x = 8; x <= 12; x++) {
            assertEquals(Rooms.PASSAGE, plan[x + 21 * 4], "corridor at x " + x);
        }
        for (Rooms.Spot s : l.spots()) {
            assertFalse(s.x() >= 7 && s.x() <= 13 && s.z() >= 3 && s.z() <= 6, "a chest in or beside the corridor " + s);
        }
    }

    @Test
    void neverOnOrBesideABarrelOrStairsOrOnASlab() {
        Box b = twoRooms().set(1, 1, 1, "minecraft:barrel[facing=up,open=false]")
                .set(19, 1, 9, "minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]")
                .fill(11, 0, 1, 19, 0, 3, "minecraft:stone_slab[type=bottom,waterlogged=false]");
        Rooms.Layout l = b.def().rooms();
        for (Rooms.Spot s : l.spots()) {
            assertFalse(Math.abs(s.x() - 1) <= 1 && Math.abs(s.z() - 1) <= 1, "beside the barrel " + s);
            assertFalse(Math.abs(s.x() - 19) <= Rooms.STAIRS_CLEAR && Math.abs(s.z() - 9) <= Rooms.STAIRS_CLEAR,
                    "beside the stairs " + s);
            assertFalse(s.x() >= 11 && s.z() <= 3, "on a slab " + s);
        }
    }

    /** A closed block of 10 x 10 with a flat roof at y = 5, climbed by a ladder; {@code railing}: iron bars around it. */
    private static Box roof(boolean railing) {
        Box b = new Box(12, 8, 12).fill(1, 1, 1, 10, 4, 10, "minecraft:stone_bricks").fill(1, 5, 1, 10, 5, 10, "minecraft:stone");
        if (railing) {
            b.fill(1, 6, 1, 10, 6, 1, "minecraft:iron_bars").fill(1, 6, 10, 10, 6, 10, "minecraft:iron_bars")
                    .fill(1, 6, 1, 1, 6, 10, "minecraft:iron_bars").fill(10, 6, 1, 10, 6, 10, "minecraft:iron_bars")
                    .set(1, 6, 5, "minecraft:air");
        }
        for (int y = 1; y <= 6; y++) {
            b.set(0, y, 5, "minecraft:ladder[facing=west,waterlogged=false]");
        }
        // something on top so that the box keeps its height without the railing
        return b.set(11, 7, 11, "minecraft:stone");
    }

    @Test
    void onARoofARailingIsAWall() {
        Rooms.Layout l = roof(true).def().rooms();
        assertFalse(l.spots().isEmpty(), "chests along the railing of the roof");
        for (Rooms.Spot s : l.spots()) {
            assertEquals(6, s.y(), s.toString());
            assertTrue(s.x() == 2 || s.x() == 9 || s.z() == 2 || s.z() == 9, "against the railing " + s);
            assertTrue(s.is(Rooms.OPEN), s.toString());
        }
        assertTrue(roof(false).def().rooms().spots().isEmpty(), "no railing: the edge is a drop, no chest");
    }

    @Test
    void fullFloorBlocks() {
        for (String s : List.of("minecraft:oak_planks", "minecraft:polished_andesite", "minecraft:white_concrete",
                "minecraft:deepslate_tiles", "minecraft:moss_block", "minecraft:smooth_stone_slab[type=double]",
                "minecraft:stripped_oak_wood[axis=y]", "minecraft:oxidized_cut_copper")) {
            assertTrue(Rooms.floorBlock(s), s);
        }
        for (String s : List.of("minecraft:barrel[facing=up]", "minecraft:oak_stairs", "minecraft:smooth_stone_slab[type=bottom]",
                "minecraft:white_bed[part=foot]", "minecraft:oak_leaves", "minecraft:glass", "minecraft:mud",
                "minecraft:bookshelf", "minecraft:crafting_table", "minecraft:chest", "minecraft:iron_bars",
                "minecraft:exposed_copper_grate", "minecraft:moss_carpet", "minecraft:spruce_trapdoor")) {
            assertFalse(Rooms.floorBlock(s), s);
        }
    }

    @Test
    void onlyTheBuildingsHaveRooms() {
        for (Structures.Def d : Structures.ALL) {
            if (d.type().startsWith("buildings_")) {
                assertFalse(d.rooms().spots().isEmpty(), d.name());
            } else {
                assertEquals(Rooms.Layout.EMPTY, d.rooms(), d.name());
            }
        }
    }

    @Test
    void buildingSpotsAreReachableOnAFullFloorAndRoomEnough() {
        for (Structures.Def d : Structures.ALL) {
            if (!d.type().startsWith("buildings_")) {
                continue;
            }
            Template t = d.t();
            String[] placed = new String[t.palette.size()];
            for (int p = 0; p < placed.length; p++) {
                placed[p] = Structures.swap(t.palette.get(p));
            }
            Access.Walk walk = Access.walk(t, placed, d.ox(), d.oy(), d.oz(), d.w(), d.h(), d.d(), d.ground());
            int gy = d.oy() + d.ground();
            int top = d.oy() + d.h() - 1;
            for (Rooms.Spot s : d.rooms().spots()) {
                String here = Access.bare(state(t, placed, s.x(), s.y(), s.z()));
                assertTrue(t.at(s.x(), s.y(), s.z()) >= 0, d.name() + ": nothing saved in the template at " + s);
                assertTrue(here.equals("air") || here.equals("cave_air"), d.name() + " " + s);
                assertTrue(Rooms.floorBlock(state(t, placed, s.x(), s.y() - 1, s.z())), d.name() + " floor " + s);
                assertTrue(Access.passable(Access.bare(state(t, placed, s.x(), s.y() + 1, s.z()))), d.name() + " lid " + s);
                assertTrue(walk.reaches(s.x(), s.y(), s.z()), d.name() + ": out of reach " + s);
                assertTrue(s.y() > gy || here.equals("cave_air"), d.name() + ": underground only in dug-out space " + s);
                assertEquals(Access.place(t, placed, s.x(), s.y(), s.z(), gy, top, false) != 0, s.is(Rooms.HARD));
                int fx = s.x() + new int[]{0, 1, 0, -1}[s.facing()];
                int fz = s.z() + new int[]{-1, 0, 1, 0}[s.facing()];
                assertTrue(walk.reachedAt(fx, s.y(), fz), d.name() + ": nobody stands in front of " + s);
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            assertFalse(Access.bare(state(t, placed, s.x() + dx, s.y() + dy, s.z() + dz)).equals("barrel"),
                                    d.name() + ": beside a barrel " + s);
                        }
                    }
                }
            }
            // the far spots are measured floor by floor: every floor with spots has some
            int[] spotsOn = new int[d.rooms().floors().size()];
            int[] farOn = new int[spotsOn.length];
            for (Rooms.Spot s : d.rooms().spots()) {
                spotsOn[s.floor()]++;
                farOn[s.floor()] += s.is(Rooms.FAR) ? 1 : 0;
                assertTrue(s.steps() >= 0, d.name() + " " + s);
                Rooms.Floor sf = d.rooms().floors().get(s.floor());
                assertTrue(!s.is(Rooms.STREET) || sf.from() <= gy + 1 && sf.to() >= gy + 1,
                        d.name() + ": a street spot off the street floor " + s);
            }
            for (int f = 0; f < spotsOn.length; f++) {
                assertTrue(spotsOn[f] == 0 || farOn[f] > 0, d.name() + ": no far spot on floor " + f);
            }
            int[] cap = Rooms.capacity(d.rooms(), Rooms.SPACING);
            int most = MOST.get(d.category().substring("building_".length()));
            assertTrue(cap[0] >= most, d.name() + ": room for " + cap[0] + " chests " + Rooms.SPACING
                    + " apart, fewer than " + most);
        }
    }

    @Test
    void theSameTemplateGivesTheSameRooms() {
        Structures.Def d = Structures.ALL.stream().filter(x -> x.name().equals("apartment_building")).findFirst()
                .orElseThrow();
        Template t = d.t();
        String[] placed = new String[t.palette.size()];
        for (int p = 0; p < placed.length; p++) {
            placed[p] = Structures.swap(t.palette.get(p));
        }
        Rooms.Layout again = Rooms.of(t, placed, d.ox(), d.oy(), d.oz(), d.w(), d.h(), d.d(), d.ground(), d.openings());
        assertEquals(d.rooms().spots(), again.spots());
        assertEquals(d.rooms().rooms(), again.rooms());
    }

    @Test
    void reportAndFloorPlans() throws IOException {
        File dir = new File(WorldPlanTest.renderDir(), "rooms");
        dir.mkdirs();
        for (Structures.Def d : Structures.ALL) {
            if (!d.type().startsWith("buildings_")) {
                continue;
            }
            Rooms.Layout l = d.rooms();
            int[] cap = Rooms.capacity(l, Rooms.SPACING);
            int[] per = new int[l.floors().size()];
            int[] n = new int[8];
            int[] flags = {Rooms.CORNER, Rooms.FURNITURE, Rooms.HARD, Rooms.UNDER, Rooms.TERRACE, Rooms.OPEN, Rooms.FAR,
                    Rooms.STREET};
            for (Rooms.Spot s : l.spots()) {
                per[s.floor()]++;
                for (int i = 0; i < flags.length; i++) {
                    n[i] += s.is(flags[i]) ? 1 : 0;
                }
            }
            StringBuilder fl = new StringBuilder();
            int passages = 0;
            for (int i = 0; i < l.floors().size(); i++) {
                Rooms.Floor f = l.floors().get(i);
                for (int v : f.plan()) {
                    passages += v == Rooms.PASSAGE ? 1 : 0;
                }
                fl.append(' ').append(f.y() - d.oy() - d.ground()).append(':').append(f.cells()).append('/')
                        .append(per[i]).append('/').append(cap[1 + i]);
            }
            System.out.printf("[RoomsTest] %-28s %-16s floors %2d, rooms %3d, corridors %2d, zones %3d, passage cells %4d, spots %4d"
                            + " (corner %3d, furniture %3d, hard %3d: underground %3d, terrace %2d; open sky %3d, far %3d,"
                            + " street %3d),"
                            + " room for %3d chests %d apart%n[RoomsTest]    floors (height: cells/spots/room):%s%n",
                    d.name(), d.type(), l.floors().size(), l.rooms(), l.corridors(), l.zones(), passages, l.spots().size(), n[0], n[1],
                    n[2], n[3], n[4], n[5], n[6], n[7], cap[0], Rooms.SPACING, fl);
            ImageIO.write(render(d), "png", new File(dir, d.name() + ".png"));
        }
    }

    private static String state(Template t, String[] placed, int x, int y, int z) {
        int p = t.at(x, y, z);
        return p < 0 ? "minecraft:air" : placed[p];
    }

    static final int SCALE = 5;
    static final int HEADER = 16;

    /** The floor plans of a building, front (the -Z side of the template) at the top of each. */
    static BufferedImage render(Structures.Def d) {
        Rooms.Layout l = d.rooms();
        int nf = l.floors().size();
        int cols = (int) Math.ceil(Math.sqrt(nf));
        int rows = (nf + cols - 1) / cols;
        int pw = l.w() * SCALE + 8;
        int ph = l.d() * SCALE + HEADER + 8;
        int legend = 18;
        BufferedImage img = new BufferedImage(Math.max(cols * pw, 720), rows * ph + legend, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0x101010));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        int[] per = new int[nf];
        for (Rooms.Spot s : l.spots()) {
            per[s.floor()]++;
        }
        for (int f = 0; f < nf; f++) {
            Rooms.Floor fl = l.floors().get(f);
            int px = (f % cols) * pw + 4;
            int py = (f / cols) * ph + 4;
            g.setColor(Color.WHITE);
            g.drawString("+" + (fl.y() - d.oy() - d.ground()) + ": " + per[f] + " spots", px, py + 12);
            for (int z = 0; z < l.d(); z++) {
                for (int x = 0; x < l.w(); x++) {
                    int v = fl.plan()[x + l.w() * z];
                    Color c;
                    if (v == Rooms.NONE) {
                        c = new Color(0x2e2e2e);
                    } else if (v == Rooms.PASSAGE) {
                        c = new Color(0xe8d27a);
                    } else if (v == Rooms.DOOR) {
                        c = new Color(0x8b5a2b);
                    } else if (v == Rooms.CORRIDOR) {
                        c = new Color(0xb8b8b8);
                    } else {
                        int room = l.zoneRoom()[v];
                        float hue = (room * 0.618034f) % 1f;
                        c = Color.getHSBColor(hue, 0.35f, (v & 1) == 0 ? 0.80f : 0.68f);
                    }
                    g.setColor(c);
                    g.fillRect(px + x * SCALE, py + HEADER + z * SCALE, SCALE, SCALE);
                }
            }
        }
        for (Rooms.Spot s : l.spots()) {
            int px = (s.floor() % cols) * pw + 4;
            int py = (s.floor() / cols) * ph + 4;
            Color c = s.is(Rooms.HARD) ? new Color(0xd62728) : s.is(Rooms.OPEN) ? new Color(0xff8c1a)
                    : s.is(Rooms.STREET) ? new Color(0xe040fb) : s.is(Rooms.FAR) ? new Color(0x1f5fd6) : s.is(Rooms.CORNER) ? new Color(0x075e26) : new Color(0x22b04b);
            g.setColor(c);
            g.fillRect(px + (s.x() - d.ox()) * SCALE + 1, py + HEADER + (s.z() - d.oz()) * SCALE + 1, SCALE - 2, SCALE - 2);
        }
        int ly = rows * ph + 13;
        String[] names = {"spot", "corner", "far", "street", "open sky", "hard", "passage", "door", "corridor"};
        Color[] colors = {new Color(0x22b04b), new Color(0x075e26), new Color(0x1f5fd6), new Color(0xe040fb),
                new Color(0xff8c1a), new Color(0xd62728), new Color(0xe8d27a), new Color(0x8b5a2b), new Color(0xb8b8b8)};
        int lx = 4;
        for (int i = 0; i < names.length; i++) {
            g.setColor(colors[i]);
            g.fillRect(lx, ly - 9, 10, 10);
            g.setColor(Color.WHITE);
            g.drawString(names[i], lx + 14, ly);
            lx += 18 + g.getFontMetrics().stringWidth(names[i]) + 12;
        }
        g.drawString(d.name() + " (front at the top; rooms in colors, zones lighter or darker)", lx, ly);
        g.dispose();
        return img;
    }

    @Test
    void renderWorks() {
        assertNotNull(render(twoRooms().def()));
    }
}
