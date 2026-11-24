import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Builds from inf-20100325-1640 on: the game's blocks tick again (leaves decay, fire spreads, grass spreads, flowers die
 * in the dark), and the server is the one that ticks them. The bridge opens a window around each world tick
 * ({@link #capturing}); the hooks that the class loader inserts into the world class report the blocks that change in it
 * and the items that the blocks drop, and the bridge sends both on to the players.
 *
 * The class sits in the bridge folder of the server jar, so the game's class loader finds it (as it does TWSeed).
 */
public final class TWTick {

    private TWTick() {
    }

    /** True while the bridge runs the world's tick. */
    public static boolean capturing;
    /** True while a chunk is being made: what the generator writes is not a change that the players have to hear of. */
    public static boolean quiet;

    private static final LinkedHashSet<Long> changes = new LinkedHashSet<Long>();
    private static final List<double[]> items = new ArrayList<double[]>();
    private static Field itemStack, stackCount, stackId, stackDamage, posX, posY, posZ, motX, motY, motZ;
    /** 0, or 1 for inf-20100413-1953 and later, whose entity fields have names one letter further (g, h, i became h, i, j). */
    public static int fieldShift;

    private static String shifted(char name) {
        return String.valueOf((char) (name + fieldShift));
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x + (1 << 25)) << 33) | ((long) (z + (1 << 25)) << 7) | y;
    }

    /** Hook: a block of the world is about to be set (world coordinates). */
    public static void changed(int x, int y, int z) {
        if (capturing && !quiet && y >= 0 && y < 128 && changes.size() < 30000) {
            changes.add(pack(x, y, z));
        }
    }

    /** The positions that changed since the last call (x, y, z each), and forgets them. */
    public static int[] takeChanges() {
        int[] out = new int[changes.size() * 3];
        int i = 0;
        for (long k : changes) {
            out[i++] = (int) (k >>> 33) - (1 << 25);
            out[i++] = (int) (k & 127);
            out[i++] = (int) ((k >>> 7) & 0x3FFFFFF) - (1 << 25);
        }
        changes.clear();
        return out;
    }

    /**
     * Hook: an entity is about to be added to the world. Nothing in the server's world is ever ticked, so no entity is
     * added; an item that a block drops during a tick is passed on (the players' games run the items).
     * @return true to cancel the addition
     */
    public static boolean entity(Object e) {
        if (capturing && e != null && "net.minecraft.a.c.c.b".equals(e.getClass().getName()) && items.size() < 2000) {
            try {
                if (itemStack == null) {
                    Class<?> ic = e.getClass();
                    Class<?> base = Class.forName("net.minecraft.a.c.b", true, ic.getClassLoader());
                    itemStack = ic.getField("a");
                    Class<?> st = Class.forName("net.minecraft.a.b.k", true, ic.getClassLoader());
                    stackCount = st.getField("a");
                    stackId = st.getField("c");
                    stackDamage = st.getField("d");
                    posX = base.getField(shifted('g'));
                    posY = base.getField(shifted('h'));
                    posZ = base.getField(shifted('i'));
                    motX = base.getField(shifted('j'));
                    motY = base.getField(shifted('k'));
                    motZ = base.getField(shifted('l'));
                }
                Object stack = itemStack.get(e);
                if (stack != null) {
                    items.add(new double[]{posX.getDouble(e), posY.getDouble(e), posZ.getDouble(e), motX.getDouble(e),
                            motY.getDouble(e), motZ.getDouble(e), stackId.getInt(stack), stackCount.getInt(stack),
                            stackDamage.getInt(stack)});
                }
            } catch (Exception ex) {
                // an item that cannot be read is dropped
            }
        }
        return true;
    }

    /** The items that blocks dropped since the last call: x, y, z, motion x, y, z, id, count, damage. */
    public static List<double[]> takeItems() {
        List<double[]> out = new ArrayList<double[]>(items);
        items.clear();
        return out;
    }
    // ----- falling sand and gravel ----------------------------------------------------------------------------

    /** What the falling blocks need of the world: read a block, and set one (the setting tells the players and the neighbours). */
    public interface Host {
        int block(int x, int y, int z);

        void set(int x, int y, int z, int id);
    }

    private static final class Faller {
        final int x, z, id;
        int y;
        double pos, speed;

        Faller(int x, int y, int z, int id) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.id = id;
            this.pos = y;
        }
    }

    private static final List<Faller> fallers = new ArrayList<Faller>();
    private static Host sandHost;

    private static boolean sandFree(int id) {
        return id == 0 || (id >= 8 && id <= 11) || id == 51;
    }

    /**
     * Hook: the sand or gravel block at this place was told to fall (the game moves it to the lowest free cell at once). It
     * falls by cells instead, with gravity, one step in each tick of the bridge, so that the players see it fall.
     * @return true: the game's own code does not run
     */
    public static boolean sand(Object world, int x, int y, int z) {
        Host h = sandHost;
        if (h == null || quiet || y <= 0 || y >= 128) {
            return h != null;
        }
        int id = h.block(x, y, z);
        if (id != 12 && id != 13) {
            return true;
        }
        if (!sandFree(h.block(x, y - 1, z))) {
            return true; // it rests on something: nothing to do
        }
        for (Faller f : fallers) {
            if (f.x == x && f.z == z && f.y == y) {
                return true;
            }
        }
        if (fallers.size() < 4000) {
            fallers.add(new Faller(x, y, z, id));
        }
        return true;
    }

    /** The number of blocks that are falling. */
    public static int fallingCount() {
        return fallers.size();
    }

    /** Moves the falling blocks on by one tick; the bridge calls it in every tick. */
    public static void sandTick(Host host) {
        sandHost = host;
        if (fallers.isEmpty()) {
            return;
        }
        for (Faller f : new ArrayList<Faller>(fallers)) {
            if (host.block(f.x, f.y, f.z) != f.id) {
                fallers.remove(f);
                continue;
            }
            f.speed = (f.speed - 0.04) * 0.98;
            f.pos += f.speed;
            int target = (int) Math.floor(f.pos);
            boolean landed = false;
            while (f.y > target && !landed) {
                if (f.y <= 0 || !sandFree(host.block(f.x, f.y - 1, f.z))) {
                    landed = true;
                } else {
                    int from = f.y;
                    f.y--;
                    host.set(f.x, f.y, f.z, f.id); // the block is at its new place first: its own neighbour call finds it there
                    host.set(f.x, from, f.z, 0);
                }
            }
            if (!landed && (f.y <= 0 || !sandFree(host.block(f.x, f.y - 1, f.z)))) {
                landed = true;
            }
            if (landed) {
                if (f.y <= 0) {
                    host.set(f.x, f.y, f.z, 0); // fell out of the world
                }
                fallers.remove(f);
            }
        }
    }
}
