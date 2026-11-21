package net.thewellodev.server.game;

import net.thewellodev.asm.HookTransformer;
import net.thewellodev.asm.HookTransformer.Hook;
import net.thewellodev.asm.HookTransformer.Mode;
import net.thewellodev.protocol.GameVersion;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Loads the game classes of one build from its game jar, and the bridge of that build from the server jar.
 *
 * inf-20100630-1340: obfuscated default-package classes, with ASM hooks (see {@link #hooks630}).
 * inf-20100227-1433: packaged classes (net/minecraft/...), no hooks; the bridge reaches the public
 *                    world and chunk classes directly and the private chunk map by reflection.
 *
 * The bridge classes (bridge/TWBridge, bridge/TWBridge227, ...) are read from the server jar and defined
 * by this loader, so they see the game classes of the build they belong to.
 */
public class GameClassLoader extends ClassLoader {

    private final GameVersion version;
    private final JarFile gameJar;
    /** Hooks of the build, or null if the build needs none. */
    private final HookTransformer transformer;

    public GameClassLoader(File gameJarFile, ClassLoader parent, GameVersion version) throws IOException {
        super(parent);
        this.version = version;
        this.gameJar = new JarFile(gameJarFile);
        String probe = probeEntry(version);
        if (gameJar.getEntry(probe) == null) {
            gameJar.close();
            throw new IOException(gameJarFile + " does not look like " + version.id + " (missing " + probe + ")");
        }
        transformer = version == GameVersion.INF_20100630_1340 ? hooks630() : seedHooks(version);
    }

    /**
     * The classes that make the terrain of inf-20100227-1433 and inf-20100313: their "new Random()" calls give
     * different terrain on every run, so they are replaced by TWSeed.random() (see bridgeshared/TWSeed.java).
     */
    private static String[] seededClasses(GameVersion v) {
        if (v == GameVersion.INF_20100313) {
            return new String[]{"net.minecraft.a.a.f", "net.minecraft.a.a.e.e", "net.minecraft.a.a.c.a.a",
                    "net.minecraft.a.a.c.a.c"};
        }
        if (v == GameVersion.INF_20100316) {
            return new String[]{"net.minecraft.a.a.e", "net.minecraft.a.a.e.e", "net.minecraft.a.a.c.a.a",
                    "net.minecraft.a.a.c.a.c"};
        }
        if (v == GameVersion.INF_20100320) {
            return new String[]{"net.minecraft.a.a.f", "net.minecraft.a.a.e.e", "net.minecraft.a.a.c.a.a",
                    "net.minecraft.a.a.c.a.c"};
        }
        return new String[]{"net.minecraft.a.a.e", "net.minecraft.a.a.f", "net.minecraft.a.a.c.a.a",
                "net.minecraft.a.a.c.a.c"};
    }

    private static HookTransformer seedHooks(GameVersion v) {
        HookTransformer t = new HookTransformer();
        for (final String c : seededClasses(v)) {
            t.custom(c.replace('.', '/'), new HookTransformer.Custom() {
                public boolean apply(org.objectweb.asm.tree.ClassNode cn) {
                    boolean done = false;
                    for (org.objectweb.asm.tree.MethodNode m : cn.methods) {
                        for (org.objectweb.asm.tree.AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                            if (n.getOpcode() != org.objectweb.asm.Opcodes.NEW
                                    || !"java/util/Random".equals(((org.objectweb.asm.tree.TypeInsnNode) n).desc)) {
                                continue;
                            }
                            org.objectweb.asm.tree.AbstractInsnNode dup = n.getNext();
                            org.objectweb.asm.tree.AbstractInsnNode init = dup == null ? null : dup.getNext();
                            if (dup == null || dup.getOpcode() != org.objectweb.asm.Opcodes.DUP || init == null
                                    || init.getOpcode() != org.objectweb.asm.Opcodes.INVOKESPECIAL
                                    || !"<init>".equals(((org.objectweb.asm.tree.MethodInsnNode) init).name)
                                    || !"()V".equals(((org.objectweb.asm.tree.MethodInsnNode) init).desc)
                                    || !"java/util/Random".equals(((org.objectweb.asm.tree.MethodInsnNode) init).owner)) {
                                continue;
                            }
                            org.objectweb.asm.tree.MethodInsnNode call = new org.objectweb.asm.tree.MethodInsnNode(
                                    org.objectweb.asm.Opcodes.INVOKESTATIC, "TWSeed", "random",
                                    "(Ljava/lang/String;)Ljava/util/Random;", false);
                            m.instructions.insertBefore(n, new org.objectweb.asm.tree.LdcInsnNode(c));
                            m.instructions.insertBefore(n, call);
                            m.instructions.remove(dup);
                            m.instructions.remove(init);
                            m.instructions.remove(n);
                            n = call;
                            done = true;
                        }
                    }
                    return done;
                }
            });
        }
        return t;
    }

    /** A class that exists only in the given build's jar. */
    private static String probeEntry(GameVersion v) {
        if (v == GameVersion.INF_20100630_1340) {
            return "fe.class";
        }
        if (v == GameVersion.INF_20100313) {
            return "net/minecraft/a/a/f.class";
        }
        if (v == GameVersion.INF_20100320) {
            return "net/minecraft/a/a/b/aj.class";
        }
        if (v == GameVersion.INF_20100316) {
            return "net/minecraft/a/a/e/e.class";
        }
        return "net/minecraft/a/a/e.class";
    }

    private static HookTransformer hooks630() {
        HookTransformer t = new HookTransformer();
        String hook = "TWServerHook";
        t.add(new Hook("fe", "a", "(IIIII)Z", hook, "onBlockSet", Mode.TAIL_PASS_BOOL));
        t.add(new Hook("fe", "a", "(IIII)Z", hook, "onBlockSet", Mode.TAIL_PASS_BOOL));
        t.add(new Hook("fe", "c", "(IIII)Z", hook, "onBlockSet", Mode.TAIL_PASS_BOOL));
        // The game is single-player: mobs look at the "player in the world" field (fe.f). Before every entity tick
        // that field is set to the nearest player's proxy (the call inside fe.c).
        t.custom("fe", HookTransformer.redirect("c", "(Leq;)V", "eq", "b_", "()V", hook, "tickEntity"));
        t.custom("jw", HookTransformer.silenceMissingEntity());
        t.add(new Hook("ka", "a", "(Leq;IDD)V", hook, "skipZeroKnockback", Mode.HEAD_CANCEL_VOID));
        // Mob spawner block (dungeon): sets fe.f to the nearest player; does nothing if there is no player.
        t.add(new Hook("cs", "a", "()Z", hook, "noPlayerNearby", Mode.HEAD_CANCEL_FALSE));
        return t;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (this) {
            Class<?> c = findLoadedClass(name);
            if (c == null) {
                byte[] b = name.indexOf('.') < 0 ? readBridge(name) : null;
                if (b == null) {
                    b = readGame(name);
                }
                if (b != null) {
                    c = defineClass(name, b, 0, b.length);
                }
            }
            if (c == null) {
                return super.loadClass(name, resolve);
            }
            if (resolve) {
                resolveClass(c);
            }
            return c;
        }
    }

    private byte[] readBridge(String name) {
        InputStream in = getParent().getResourceAsStream("bridge/" + name + ".class");
        if (in == null) {
            return null;
        }
        try {
            return readAll(in);
        } catch (IOException e) {
            return null;
        }
    }

    private byte[] readGame(String name) throws ClassNotFoundException {
        JarEntry e = gameJar.getJarEntry(name.replace('.', '/') + ".class");
        if (e == null) {
            return null;
        }
        try {
            byte[] raw = readAll(gameJar.getInputStream(e));
            return transformer == null ? raw : transformer.transform(name.replace('.', '/'), raw);
        } catch (IOException ex) {
            throw new ClassNotFoundException(name, ex);
        }
    }

    /** Checks that the game classes load and that the hooks were applied correctly (inf-20100630-1340). */
    public void verifyHooks() throws IOException {
        try {
            if (version != GameVersion.INF_20100630_1340) {
                loadClass(version == GameVersion.INF_20100313 || version == GameVersion.INF_20100320
                        ? "net.minecraft.a.a.f" : "net.minecraft.a.a.e");
                for (String c : seededClasses(version)) {
                    loadClass(c); // the generator classes are patched when they load
                }
            } else {
                loadClass("fe");
                loadClass("cs");
                loadClass("jw");
            }
        } catch (ClassNotFoundException e) {
            throw new IOException("Game classes could not be loaded: " + e.getMessage());
        }
        if (transformer != null && !transformer.missing().isEmpty()) {
            throw new IOException("Server hooks could not be applied (wrong game version?): " + transformer.missing());
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            bo.write(buf, 0, n);
        }
        in.close();
        return bo.toByteArray();
    }

    /** Creates the bridge of this build. */
    public GameBridge createBridge() throws Exception {
        Class<?> c = loadClass(version.bridgeClass);
        return (GameBridge) c.newInstance();
    }
}
