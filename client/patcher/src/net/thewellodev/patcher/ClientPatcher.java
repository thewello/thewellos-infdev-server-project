package net.thewellodev.patcher;

import net.thewellodev.asm.HookTransformer;
import net.thewellodev.asm.HookTransformer.Hook;
import net.thewellodev.asm.HookTransformer.Mode;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * Patches the inf-20100630-1340 client jar with multiplayer support.
 *
 * Usage:
 *   java -jar TheWellosInfdevClientPatcher.jar inf-20100630-1340.jar inf-20100630-1340-mp.jar
 */
public class ClientPatcher {

    public static final String HOOK = "TWClient";

    public static HookTransformer createTransformer() {
        HookTransformer t = new HookTransformer();
        addMouseHook(t, "cb");
        // Minecraft (aw)
        t.add(new Hook("aw", "i", "()V", HOOK, "preTick", Mode.HEAD_CALL));
        t.add(new Hook("aw", "a", "(I)V", HOOK, "actionBegin", Mode.HEAD_CALL));
        t.add(new Hook("aw", "a", "(I)V", HOOK, "actionEnd", Mode.TAIL_CALL));
        t.add(new Hook("aw", "a", "(IZ)V", HOOK, "actionBegin", Mode.HEAD_CALL));
        t.add(new Hook("aw", "a", "(IZ)V", HOOK, "actionEnd", Mode.TAIL_CALL));
        t.add(new Hook("aw", "a", "(Lfe;Ljava/lang/String;)V", HOOK, "onChangeWorld", Mode.HEAD_CALL));
        // World (fe)
        t.add(new Hook("fe", "a", "(IIIII)Z", HOOK, "onBlockSet", Mode.TAIL_PASS_BOOL));
        t.add(new Hook("fe", "a", "(IIII)Z", HOOK, "onBlockSet", Mode.TAIL_PASS_BOOL));
        t.add(new Hook("fe", "c", "(IIII)Z", HOOK, "onBlockSet", Mode.TAIL_PASS_BOOL));
        t.add(new Hook("fe", "a", "(Ljava/io/File;)Lch;", HOOK, "createChunkProvider", Mode.HEAD_RETURN_IF_NONNULL));
        t.add(new Hook("fe", "a", "(Z)Z", HOOK, "skipTickUpdates", Mode.HEAD_CANCEL_FALSE));
        t.add(new Hook("fe", "i", "()V", HOOK, "worldTick", Mode.HEAD_CANCEL_VOID));
        // Entity spawning: share dropped items through the server
        t.add(new Hook("fe", "a", "(Leq;)V", HOOK, "onSpawnEntity", Mode.HEAD_CANCEL_VOID));
        // Spawn-point search: in multiplayer chunks arrive from the server, so it could loop forever
        t.add(new Hook("fe", "a", "()V", HOOK, "skipSpawnSearch", Mode.HEAD_CANCEL_VOID));
        // Picking up an item from the ground: ask the server first
        t.add(new Hook("gx", "a", "(Lgi;)V", HOOK, "onItemCollide", Mode.HEAD_CANCEL_VOID));
        // Respawn after death
        t.add(new Hook("aw", "o", "()V", HOOK, "onRespawn", Mode.TAIL_CALL));
        // Server mobs: position from the server instead of AI/physics; damage is sent to the server
        final String MOBS = "TWMobs";
        t.custom("fe", HookTransformer.redirect("c", "(Leq;)V", "eq", "b_", "()V", MOBS, "tickEntity"));
        t.custom("ka", HookTransformer.redirect("b_", "()V", "ka", "e", "()V", MOBS, "livingUpdate"));
        t.add(new Hook("ka", "a", "(Leq;I)Z", MOBS, "netDamage", Mode.HEAD_CANCEL_FALSE));
        t.add(new Hook("gr", "a", "(Leq;I)Z", MOBS, "netDamage", Mode.HEAD_CANCEL_FALSE));
        t.add(new Hook("dn", "a", "(Leq;I)Z", MOBS, "netDamage", Mode.HEAD_CANCEL_FALSE));
        t.add(new Hook("cy", "a", "(Leq;I)Z", MOBS, "netDamage", Mode.HEAD_CANCEL_FALSE));
        t.add(new Hook("fe", "a", "(Leq;Ljava/lang/String;FF)V", MOBS, "skipSound", Mode.HEAD_CANCEL_VOID));
        t.add(new Hook("jp", "a", "(Leq;I)Z", MOBS, "netDamage", Mode.HEAD_CANCEL_FALSE));
        t.add(new Hook("ea", "b", "(Lhw;Lka;)V", MOBS, "onSaddle", Mode.HEAD_CALL));
        // Difficulty: in multiplayer the server's difficulty is used, not the one in the options (aw.i: fe.g = ja.x)
        t.custom("aw", new HookTransformer.Custom() {
            public boolean apply(ClassNode cn) {
                for (MethodNode m : cn.methods) {
                    if (!m.name.equals("i") || !m.desc.equals("()V")) {
                        continue;
                    }
                    for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                        if (n.getOpcode() == Opcodes.GETFIELD && n.getNext() != null
                                && n.getNext().getOpcode() == Opcodes.PUTFIELD) {
                            FieldInsnNode a = (FieldInsnNode) n;
                            FieldInsnNode b = (FieldInsnNode) n.getNext();
                            if (a.owner.equals("ja") && a.name.equals("x") && b.owner.equals("fe") && b.name.equals("g")) {
                                m.instructions.insert(n, new MethodInsnNode(Opcodes.INVOKESTATIC, MOBS,
                                        "filterDifficulty", "(I)I", false));
                                return true;
                            }
                        }
                    }
                }
                return false;
            }
        });
        t.custom("jw", HookTransformer.silenceMissingEntity());
        t.add(new Hook("dd", "a", "(Lkd;)V", HOOK, "openChest", Mode.HEAD_CANCEL_VOID));
        t.add(new Hook("dd", "a", "(Lel;)V", HOOK, "openFurnace", Mode.HEAD_CANCEL_VOID));
        t.custom("c", HookTransformer.fixDistanceComparator(HOOK, "cmpDist"));
        t.custom("if", HookTransformer.fixDistanceComparator(HOOK, "cmpDist"));
        t.add(new Hook("aw", "i", "()V", HOOK, "postTick", Mode.TAIL_CALL));
        t.add(new Hook("ka", "a", "(Leq;IDD)V", HOOK, "skipZeroKnockback", Mode.HEAD_CANCEL_VOID));
        // Mob spawner (ck)
        t.add(new Hook("ck", "a", "(Lfe;)V", HOOK, "skipSpawner", Mode.HEAD_CANCEL_VOID));
        // Mob spawner block (dungeons): no local mob spawning in multiplayer
        t.add(new Hook("cs", "a", "()Z", HOOK, "skipSpawnerBlock", Mode.HEAD_CANCEL_FALSE));
        // Main menu (kb)
        t.add(new Hook("kb", "b", "()V", HOOK, "titleInit", Mode.TAIL_CALL));
        t.add(new Hook("kb", "a", "(Liq;)V", HOOK, "titleAction", Mode.HEAD_CANCEL_VOID));
        // Other players' block-breaking cracks: drawn in cb.b(F) after the particles (dp.a(eq,F))
        t.custom("cb", new HookTransformer.Custom() {
            public boolean apply(ClassNode cn) {
                for (MethodNode m : cn.methods) {
                    if (!m.name.equals("b") || !m.desc.equals("(F)V")) {
                        continue;
                    }
                    for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                        if (n.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                            MethodInsnNode mi = (MethodInsnNode) n;
                            if (mi.owner.equals("dp") && mi.name.equals("a") && mi.desc.equals("(Leq;F)V")) {
                                InsnList il = new InsnList();
                                il.add(new VarInsnNode(Opcodes.ALOAD, 3));
                                il.add(new VarInsnNode(Opcodes.ALOAD, 2));
                                il.add(new VarInsnNode(Opcodes.FLOAD, 1));
                                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "renderDigs", "(Lm;Lgi;F)V", false));
                                m.instructions.insert(n, il);
                                return true;
                            }
                        }
                    }
                }
                return false;
            }
        });
        // Chat key: in the keyboard loop of aw.i(), right before the ESC check
        t.custom("aw", new HookTransformer.Custom() {
            public boolean apply(ClassNode cn) {
                for (MethodNode m : cn.methods) {
                    if (!m.name.equals("i") || !m.desc.equals("()V")) {
                        continue;
                    }
                    for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                        if (n.getOpcode() == Opcodes.INVOKESTATIC) {
                            MethodInsnNode mi = (MethodInsnNode) n;
                            AbstractInsnNode nx = n.getNext();
                            if (mi.owner.equals("org/lwjgl/input/Keyboard") && mi.name.equals("getEventKey")
                                    && nx != null && nx.getOpcode() == Opcodes.ICONST_1
                                    && nx.getNext() != null && nx.getNext().getOpcode() == Opcodes.IF_ICMPNE) {
                                InsnList il = new InsnList();
                                il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "onKey", "(Law;)V", false));
                                m.instructions.insertBefore(n, il);
                                return true;
                            }
                        }
                    }
                }
                return false;
            }
        });
        return t;
    }

    private static MethodNode findMethod(ClassNode cn, String name, String desc) {
        for (MethodNode m : cn.methods) {
            if (m.name.equals(name) && m.desc.equals(desc)) {
                return m;
            }
        }
        return null;
    }

    /**
     * Hooks for inf-20100227-1433. Every hook takes no game types (Object or primitives), because the mod cannot
     * name the game classes in source (see TW227Client).
     */
    public static HookTransformer createTransformer227() {
        return createTransformerInfdev("TW227Client", false, "net/minecraft/a/a/e", "a", "a");
    }

    /** Hooks for inf-20100313 (same as 20100227, but the world class is f and its chunks are the game's own). */
    public static HookTransformer createTransformer313() {
        return createTransformerInfdev("TW313Client", true, "net/minecraft/a/a/f", "b", "a");
    }

    /** Hooks for inf-20100316 (the hooks of 20100313; the world class is called e again). */
    public static HookTransformer createTransformer316() {
        HookTransformer t = createTransformerInfdev("TW316Client", true, "net/minecraft/a/a/e", "b", "a");
        addEntityAddHook(t, "TW316Client", "net/minecraft/a/a/e");
        addItemPickupHook(t, "TW316Client");
        addGameModeHooks(t, "TW316Client");
        return t;
    }

    /**
     * Hooks for inf-20100321: the game world is the code of 20100320, so the hooks are the same. The client changed: the
     * tick method is g, the title screen is r, and the session check of the game is switched off (see
     * createTransformerInfdev).
     */
    public static HookTransformer createTransformer321() {
        return createTransformer320(true);
    }

    public static HookTransformer createTransformer320() {
        return createTransformer320(false);
    }

    /**
     * Hooks for inf-20100320: the world class is f again; the set-block method that notifies the renderer is c(IIII)Z
     * and the set-metadata method is b(IIII)V. Besides the hooks of 20100316 it reports explosions.
     */
    private static HookTransformer createTransformer320(boolean is321) {
        final String H = is321 ? "TW321Client" : "TW320Client";
        HookTransformer t = is321
                ? createTransformerInfdev(H, true, "net/minecraft/a/a/f", "c", "b", "g", "net/minecraft/client/r", true)
                : createTransformerInfdev(H, true, "net/minecraft/a/a/f", "c", "b");
        addLateHooks(t, H, "net/minecraft/a/a/f", "net/minecraft/a/a/b/a", "net/minecraft/a/a/b/s");
        return t;
    }

    /**
     * Hooks for inf-20100325-1640. The obfuscated names moved (world class g, blocks in a/a/c, title screen q) and the
     * world runs block ticks again. The server is the only one that ticks blocks (a leaf that decays, fire that
     * spreads): the client removes the scheduled ticks and the random ticks around the player from the world's tick
     * method, and the server sends what they changed.
     */
    public static HookTransformer createTransformer325() {
        return createTransformer325(false);
    }

    /**
     * Hooks for inf-20100327. The world is saved in the Alpha level format by the game itself now: the world's save method
     * is cut out (the mod's world is never saved), and the obfuscated names moved again (world class f, blocks in a/a/b,
     * title screen p, button class c/o, settings class j).
     */
    public static HookTransformer createTransformer327() {
        return createTransformer325(true);
    }

    /**
     * Hooks for inf-20100330-1611: the same class and member names as 20100327 (the jars have the same class names), so the hooks
     * are the same; the client class of the mod is TW330Client.
     */
    public static HookTransformer createTransformer330() {
        return createTransformer325(true, "TW330Client");
    }

    private static HookTransformer createTransformer325(final boolean is327) {
        return createTransformer325(is327, is327 ? "TW327Client" : "TW325Client");
    }

    /**
     * Hooks for inf-20100413-1953: the world class is g again (as in 325-1640), its tick method is i, and the living entity is
     * a/c/f (the class a/c/e is the entity registry now). The screens, the digging controller, the renderer and the settings class
     * keep their names from 20100327.
     */
    /**
     * inf-20100415: the classes of 20100414 with the client classes moved by a letter (the title screen is client/q, the settings
     * client/k, the game renderer client/a/e, the sorter of the renderer client/a/l, the HUD client/c/n). Mobs can be made again,
     * so the game's mob spawner is left alone.
     */
    public static HookTransformer createTransformer415() {
        renamed415 = true;
        try {
            return createTransformerWith("TW415Client", false);
        } finally {
            renamed415 = false;
        }
    }

    /** inf-20100608 has the class and member names of 20100607: the same hooks. */
    public static HookTransformer createTransformer608() {
        renamed415 = true;
        renamed607 = true;
        try {
            return createTransformerWith("TW608Client", false);
        } finally {
            renamed415 = false;
            renamed607 = false;
        }
    }

    /** inf-20100607: the hooks of 415 with the names that moved again. */
    public static HookTransformer createTransformer607() {
        renamed415 = true;
        renamed607 = true;
        try {
            return createTransformerWith("TW607Client", false);
        } finally {
            renamed415 = false;
            renamed607 = false;
        }
    }

    /** inf-20100420 has the class and member names of 20100415: the same hooks. */
    public static HookTransformer createTransformer420() {
        renamed415 = true;
        try {
            return createTransformerWith("TW420Client", false);
        } finally {
            renamed415 = false;
        }
    }

    /** True while the hooks of inf-20100415 or later are made (the client classes have other names). */
    private static boolean renamed415;
    /** True while the hooks of inf-20100607 are made: the sand and furnace blocks, the biped model and the HUD moved again. */
    private static boolean renamed607;

    /** inf-20100414 has the classes of 20100413-1953 (only the item use code differs), so it gets the same hooks. */
    public static HookTransformer createTransformer414() {
        return createTransformerFor413Family("TW414Client");
    }

    public static HookTransformer createTransformer413() {
        return createTransformerFor413Family("TW413Client");
    }

    private static HookTransformer createTransformerFor413Family(String hookClass) {
        return createTransformerWith(hookClass, true);
    }

    private static HookTransformer createTransformerWith(String hookClass, boolean stubSpawner) {
        HookTransformer t = createTransformer325(true, hookClass, "net/minecraft/a/a/g", "i", "h", "net/minecraft/a/c/f");
        if (!stubSpawner) {
            return t;
        }
        // The game's mob spawner (a/a/b, called by the digging controller every tick) makes a mob by reflection with the mob's
        // constructor, and the mob classes of this build have none: it fails and prints a stack trace again and again. There are
        // no mobs in the multiplayer game, so the spawner does nothing.
        t.custom("net/minecraft/a/a/b", new HookTransformer.Custom() {
            public boolean apply(ClassNode cn) {
                MethodNode m = findMethod(cn, "a", "(Lnet/minecraft/a/a/g;)V");
                if (m == null) {
                    return false;
                }
                m.instructions.clear();
                m.tryCatchBlocks.clear();
                if (m.localVariables != null) {
                    m.localVariables.clear();
                }
                m.instructions.add(new InsnNode(Opcodes.RETURN));
                return true;
            }
        });
        return t;
    }

    private static HookTransformer createTransformer325(final boolean is327, final String hookClass) {
        return createTransformer325(is327, hookClass, is327 ? "net/minecraft/a/a/f" : "net/minecraft/a/a/g", is327 ? "h" : "f",
                is327 ? "h" : "f", "net/minecraft/a/c/e");
    }

    private static HookTransformer createTransformer325(final boolean is327, final String hookClass, final String worldName,
                                                        final String tick, final String clientTick, final String livingBase) {
        final String H = hookClass;
        final String W = worldName;
        final String tickName = tick;
        final String entityTickName = is327 ? "c" : "b";
        Screens screens = is327
                ? new Screens("net/minecraft/client/c/o", "net/minecraft/client/c/c", "net/minecraft/client/c/e",
                renamed415 && !renamed607 ? "net/minecraft/client/c/n" : "net/minecraft/client/c/m")
                : Screens.DEFAULT;
        HookTransformer t = createTransformerInfdev(H, true, W, "c", "b", clientTick,
                is327 ? (renamed415 ? "net/minecraft/client/q" : "net/minecraft/client/p") : "net/minecraft/client/q", false, true,
                screens);
        addLateHooks(t, H, W, is327 ? "net/minecraft/a/a/b/a" : "net/minecraft/a/a/c/a",
                is327 ? (renamed607 ? "net/minecraft/a/a/b/t" : "net/minecraft/a/a/b/s") : "net/minecraft/a/a/c/s", livingBase);
        addSandHook(t, H, W, is327 ? (renamed607 ? "net/minecraft/a/a/b/x" : "net/minecraft/a/a/b/w") : "net/minecraft/a/a/c/w");
        if (is327) {
            // The game saves its world (level.dat and the chunks, every 200 ticks and when the world is closed). This
            // game's world comes from the server and is never saved: the private save method does nothing. The body is
            // replaced as a whole, so that no dead code is left (the old verifier of these class files rejects that).
            t.custom(W, new HookTransformer.Custom() {
                public boolean apply(ClassNode cn) {
                    MethodNode m = findMethod(cn, "a", "(Z)V");
                    if (m == null) {
                        return false;
                    }
                    m.instructions.clear();
                    m.tryCatchBlocks.clear();
                    if (m.localVariables != null) {
                        m.localVariables.clear();
                    }
                    m.instructions.add(new InsnNode(Opcodes.RETURN));
                    return true;
                }
            });
        }
        // The game's settings object (client/k) no longer keeps the session id of the account (it was for the session
        // check): the constructor passes it on to the mod through a system property, so that online-mode servers can still
        // verify the player
        t.custom(is327 ? (renamed415 ? "net/minecraft/client/k" : "net/minecraft/client/j") : "net/minecraft/client/k",
                new HookTransformer.Custom() {
            public boolean apply(ClassNode cn) {
                MethodNode m = findMethod(cn, "<init>", "(Ljava/lang/String;Ljava/lang/String;)V");
                if (m == null) {
                    return false;
                }
                LabelNode skip = new LabelNode();
                InsnList il = new InsnList();
                il.add(new LdcInsnNode("thewellodev.token"));
                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/System", "getProperty",
                        "(Ljava/lang/String;)Ljava/lang/String;", false));
                il.add(new JumpInsnNode(Opcodes.IFNONNULL, skip));
                il.add(new VarInsnNode(Opcodes.ALOAD, 2));
                il.add(new JumpInsnNode(Opcodes.IFNULL, skip));
                il.add(new LdcInsnNode("thewellodev.token"));
                il.add(new VarInsnNode(Opcodes.ALOAD, 2));
                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/System", "setProperty",
                        "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false));
                il.add(new InsnNode(Opcodes.POP));
                il.add(skip);
                m.instructions.insert(il);
                return true;
            }
        });
        // the world's entity tick also ticks the tile entities in its list: the furnaces smelt on the server (see TW325Client.tiles)
        t.custom(W, new HookTransformer.Custom() {
            public boolean apply(ClassNode cn) {
                MethodNode m = findMethod(cn, entityTickName, "()V");
                if (m == null) {
                    return false;
                }
                InsnList il = new InsnList();
                il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, H, "tiles", "(Ljava/lang/Object;)V", false));
                m.instructions.insert(il);
                return true;
            }
        });
        t.custom(W, new HookTransformer.Custom() {
            public boolean apply(ClassNode cn) {
                MethodNode m = findMethod(cn, tickName, "()V");
                if (m == null) {
                    return false;
                }
                // 1. the list of scheduled block ticks (the list that the method takes entries from) is emptied at the
                //    start: blocks that this game schedules are the server's business
                String queue = null;
                for (AbstractInsnNode n = m.instructions.getFirst(); n != null && queue == null; n = n.getNext()) {
                    if (n.getOpcode() == Opcodes.INVOKEINTERFACE && n instanceof MethodInsnNode
                            && "remove".equals(((MethodInsnNode) n).name)
                            && "(I)Ljava/lang/Object;".equals(((MethodInsnNode) n).desc)) {
                        for (AbstractInsnNode p = n.getPrevious(); p != null; p = p.getPrevious()) {
                            if (p instanceof FieldInsnNode && "Ljava/util/List;".equals(((FieldInsnNode) p).desc)) {
                                queue = ((FieldInsnNode) p).name;
                                break;
                            }
                        }
                        break;
                    }
                }
                if (queue == null) {
                    return false;
                }
                // 2. the loop of random block ticks around the player (32000 tries) runs no more
                boolean cut = false;
                for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                    if (n instanceof org.objectweb.asm.tree.IntInsnNode && n.getOpcode() == Opcodes.SIPUSH
                            && ((org.objectweb.asm.tree.IntInsnNode) n).operand == 32000) {
                        m.instructions.set(n, new InsnNode(Opcodes.ICONST_0));
                        cut = true;
                        break;
                    }
                }
                InsnList il = new InsnList();
                il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                il.add(new FieldInsnNode(Opcodes.GETFIELD, W, queue, "Ljava/util/List;"));
                il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/List", "clear", "()V", true));
                m.instructions.insert(il);
                return cut;
            }
        });
        return t;
    }

    /**
     * Game modes 1 (creative) and 2 (spectator), builds 316 and later:
     * <ul>
     * <li>Nothing hurts the player of this game: the damage methods of the living entity and of the player class return at once
     *     (drowning, fire, explosions, falls, mobs; the check is the mod's {@code noDamage}).</li>
     * <li>The digging controller of the survival game (client/d/b) breaks a block at once, the way the controller of the
     *     creative game does (its base class client/d/a), without drops, and does nothing in the spectator mode.</li>
     * </ul>
     */
    private static void addGameModeHooks(HookTransformer t, final String H) {
        addGameModeHooks(t, H, "net/minecraft/a/c/e");
    }

    /** @param livingBase the living entity class (a/c/e before 20100413-1953, a/c/f in it); the player class extends it */
    private static void addGameModeHooks(HookTransformer t, final String H, final String livingBase) {
        final String[] living = {livingBase, "net/minecraft/a/c/e/a"};
        for (String cls : living) {
            t.custom(cls, new HookTransformer.Custom() {
                public boolean apply(ClassNode cn) {
                    boolean done = false;
                    MethodNode hurt = findMethod(cn, "a", "(Lnet/minecraft/a/c/b;I)Z");
                    if (hurt != null) {
                        LabelNode cont = new LabelNode();
                        InsnList il = new InsnList();
                        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, H, "noDamage", "(Ljava/lang/Object;)Z", false));
                        il.add(new JumpInsnNode(Opcodes.IFEQ, cont));
                        il.add(new InsnNode(Opcodes.ICONST_0));
                        il.add(new InsnNode(Opcodes.IRETURN));
                        il.add(cont);
                        hurt.instructions.insert(il);
                        done = true;
                    }
                    MethodNode plain = findMethod(cn, "b", "(I)V");
                    if (plain != null) {
                        LabelNode cont = new LabelNode();
                        InsnList il = new InsnList();
                        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, H, "noDamage", "(Ljava/lang/Object;)Z", false));
                        il.add(new JumpInsnNode(Opcodes.IFEQ, cont));
                        il.add(new InsnNode(Opcodes.RETURN));
                        il.add(cont);
                        plain.instructions.insert(il);
                        done = true;
                    }
                    return done;
                }
            });
        }
        t.custom("net/minecraft/client/d/b", new HookTransformer.Custom() {
            public boolean apply(ClassNode cn) {
                boolean done = false;
                for (int variant = 0; variant < 2; variant++) {
                    boolean withFace = variant == 1;
                    MethodNode m = findMethod(cn, "a", withFace ? "(IIII)V" : "(III)V");
                    if (m == null) {
                        continue;
                    }
                    LabelNode afterSpectator = new LabelNode();
                    LabelNode cont = new LabelNode();
                    InsnList il = new InsnList();
                    il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, H, "spectator", "()Z", false));
                    il.add(new JumpInsnNode(Opcodes.IFEQ, afterSpectator));
                    il.add(new InsnNode(Opcodes.RETURN)); // the spectator breaks nothing
                    il.add(afterSpectator);
                    il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, H, "creative", "()Z", false));
                    il.add(new JumpInsnNode(Opcodes.IFEQ, cont));
                    il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    il.add(new VarInsnNode(Opcodes.ILOAD, 1));
                    il.add(new VarInsnNode(Opcodes.ILOAD, 2));
                    il.add(new VarInsnNode(Opcodes.ILOAD, 3));
                    // the destroy method of the base class (client/d/a): the block goes at once, nothing drops
                    il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "net/minecraft/client/d/a", "b", "(III)Z", false));
                    il.add(new InsnNode(Opcodes.POP));
                    if (withFace) {
                        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        il.add(new InsnNode(Opcodes.ICONST_5)); // a short pause before the next block, as the survival game has
                        il.add(new FieldInsnNode(Opcodes.PUTFIELD, "net/minecraft/client/d/b", "i", "I"));
                    }
                    il.add(new InsnNode(Opcodes.RETURN));
                    il.add(cont);
                    m.instructions.insert(il);
                    done = true;
                }
                return done;
            }
        });
    }

    /**
     * The first-person arm (builds 20100227 to 20100330-1611): the player renderer draws the arm of its shared model as the model last was
     * posed. In multiplayer the model was last posed for another player, so the own arm swung with the walking of somebody else.
     * The arm is put at rest first (the model's own pose method with all values 0).
     */
    private static void addFirstPersonArmHook(HookTransformer t) {
        final String model = renamed607 ? "net/minecraft/client/b/i" : "net/minecraft/client/b/h"; // the biped model
        t.custom("net/minecraft/client/a/a/g", new HookTransformer.Custom() {
            public boolean apply(ClassNode cn) {
                MethodNode m = findMethod(cn, "a", "()V");
                if (m == null) {
                    return false;
                }
                InsnList il = new InsnList();
                il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                il.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraft/client/a/a/g", "e", "L" + model + ";"));
                for (int i = 0; i < 5; i++) {
                    il.add(new InsnNode(Opcodes.FCONST_0));
                }
                il.add(new LdcInsnNode(Float.valueOf(0.0625f)));
                il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, model, "b", "(FFFFFF)V", false));
                m.instructions.insert(il);
                return true;
            }
        });
    }

    /**
     * The game renderer draws the water after the entities and then the crack of the block under the own crosshair (only when
     * there is one within reach). The cracks of the blocks that other players dig at are drawn there too, in every frame: after
     * the water pass, with the same camera. Build 20100227 has no such water pass; its hook is at the end of the terrain method
     * of the world renderer.
     */
    private static void addDigRenderHook(HookTransformer t, final String H, String gameRenderer) {
        if (!H.equals("TW227Client")) {
            addDigAfterWater(t, H, gameRenderer);
            return;
        }
        addDigInTerrain(t, H);
    }

    private static void addDigAfterWater(HookTransformer t, final String H, String gameRenderer) {
        t.custom(gameRenderer, new HookTransformer.Custom() {
            public boolean apply(ClassNode cn) {
                for (MethodNode m : cn.methods) {
                    for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                        if (!isCall(n, "net/minecraft/client/a/b", "a", "(ID)V")) {
                            continue;
                        }
                        // the local that holds the world renderer: the last object load before the call's arguments
                        int renderer = -1;
                        for (AbstractInsnNode b = n.getPrevious(); b != null; b = b.getPrevious()) {
                            if (b.getOpcode() == Opcodes.ALOAD) {
                                renderer = ((VarInsnNode) b).var;
                                break;
                            }
                        }
                        for (AbstractInsnNode a = n.getNext(); a != null && renderer >= 0; a = a.getNext()) {
                            if (isCall(a, "org/lwjgl/opengl/GL11", "glDepthMask", "(Z)V")) {
                                InsnList il = new InsnList();
                                il.add(new VarInsnNode(Opcodes.ALOAD, renderer));
                                il.add(new VarInsnNode(Opcodes.FLOAD, 1)); // the partial tick (the method's argument)
                                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, H, "renderDigsPass", "(Ljava/lang/Object;F)V",
                                        false));
                                m.instructions.insert(a, il);
                                return true;
                            }
                        }
                    }
                }
                return false;
            }
        });
    }

    private static void addDigInTerrain(HookTransformer t, final String H) {
        t.custom("net/minecraft/client/a/b", new HookTransformer.Custom() {
            public boolean apply(ClassNode cn) {
                MethodNode m = findMethod(cn, "a", "(Lnet/minecraft/a/c/e/a;I)I");
                if (m == null) {
                    return false;
                }
                for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                    if (n.getOpcode() == Opcodes.IRETURN) {
                        InsnList il = new InsnList();
                        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        il.add(new InsnNode(Opcodes.FCONST_1));
                        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, H, "renderDigsPass", "(Ljava/lang/Object;F)V", false));
                        m.instructions.insertBefore(n, il);
                    }
                }
                return true;
            }
        });
    }

    private static boolean isCall(AbstractInsnNode n, String owner, String name, String desc) {
        if (!(n instanceof MethodInsnNode)) {
            return false;
        }
        MethodInsnNode c = (MethodInsnNode) n;
        return c.owner.equals(owner) && c.name.equals(name) && c.desc.equals(desc);
    }

    /** The hooks that builds 320 and later share: entities, item pick-ups, chests and furnaces, explosions. */
    private static void addLateHooks(HookTransformer t, final String H, final String W, String chestBlock,
                                     String furnaceBlock) {
        addLateHooks(t, H, W, chestBlock, furnaceBlock, "net/minecraft/a/c/e");
    }

    private static void addLateHooks(HookTransformer t, final String H, final String W, String chestBlock,
                                     String furnaceBlock, String livingBase) {
        addEntityAddHook(t, H, W);
        addItemPickupHook(t, H);
        addGameModeHooks(t, H, livingBase);
        // Chest and furnace blocks: the click that opens them asks the server for the lock first
        addContainerHook(t, H, W, chestBlock);
        addContainerHook(t, H, W, furnaceBlock);
        // World: start of the explosion method (entity, x, y, z, power): the other players near it are hurt too
        t.custom(W, new HookTransformer.Custom() {
            public boolean apply(ClassNode cn) {
                MethodNode m = findMethod(cn, "a", "(Lnet/minecraft/a/c/b;DDDF)V");
                if (m == null) {
                    return false;
                }
                InsnList il = new InsnList();
                il.add(new VarInsnNode(Opcodes.ALOAD, 1));
                il.add(new VarInsnNode(Opcodes.DLOAD, 2));
                il.add(new VarInsnNode(Opcodes.DLOAD, 4));
                il.add(new VarInsnNode(Opcodes.DLOAD, 6));
                il.add(new VarInsnNode(Opcodes.FLOAD, 8));
                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, H, "onExplosion",
                        "(Ljava/lang/Object;DDDF)V", false));
                m.instructions.insert(il);
                return true;
            }
        });
    }

    /**
     * A block's "right-clicked" method (world, x, y, z, player): the mod may cancel the click (it returns true) while it
     * waits for the server.
     */
    private static void addContainerHook(HookTransformer t, final String H, final String worldInternal, String blockInternal) {
        t.custom(blockInternal, new HookTransformer.Custom() {
            public boolean apply(ClassNode cn) {
                MethodNode m = findMethod(cn, "a", "(L" + worldInternal + ";IIILnet/minecraft/a/c/e/a;)Z");
                if (m == null) {
                    return false;
                }
                LabelNode cont = new LabelNode();
                InsnList il = new InsnList();
                il.add(new VarInsnNode(Opcodes.ALOAD, 1));
                il.add(new VarInsnNode(Opcodes.ILOAD, 2));
                il.add(new VarInsnNode(Opcodes.ILOAD, 3));
                il.add(new VarInsnNode(Opcodes.ILOAD, 4));
                il.add(new VarInsnNode(Opcodes.ALOAD, 5));
                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, H, "onContainerUse",
                        "(Ljava/lang/Object;IIILjava/lang/Object;)Z", false));
                il.add(new JumpInsnNode(Opcodes.IFEQ, cont));
                il.add(new InsnNode(Opcodes.ICONST_1));
                il.add(new InsnNode(Opcodes.IRETURN));
                il.add(cont);
                m.instructions.insert(il);
                return true;
            }
        });
    }

    /**
     * The game's renderer reads the mouse movement for the camera with Mouse.getDX() and Mouse.getDY(): the calls are
     * replaced by TWMouse.dx() and TWMouse.dy(), which return nothing while the window has no focus (two windows on
     * one computer share the mouse).
     */
    private static void addMouseHook(HookTransformer t, String rendererInternal) {
        t.custom(rendererInternal, new HookTransformer.Custom() {
            public boolean apply(ClassNode cn) {
                boolean done = false;
                for (MethodNode m : cn.methods) {
                    for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                        if (n.getOpcode() == Opcodes.INVOKESTATIC && n instanceof MethodInsnNode) {
                            MethodInsnNode call = (MethodInsnNode) n;
                            if ("org/lwjgl/input/Mouse".equals(call.owner) && "()I".equals(call.desc)
                                    && ("getDX".equals(call.name) || "getDY".equals(call.name))) {
                                call.owner = "TWMouse";
                                call.name = "getDX".equals(call.name) ? "dx" : "dy";
                                done = true;
                            }
                        }
                    }
                }
                return done;
            }
        });
    }

    /**
     * The item entity's "touched by a player" method: the mod may cancel the pick-up (it returns true) until the server
     * has said that this game gets the item.
     */
    /**
     * Sand and gravel: the block moves itself to the lowest free cell at once (a "teleport"). In a multiplayer game the hook
     * takes the fall over (see sandFall in the mods), so that the block falls by cells and every player sees it.
     */
    private static void addSandHook(HookTransformer t, final String H, final String world, String sandBlock) {
        final String desc = "(L" + world + ";III)V";
        // the sand block's own fall method (it moved to the name h in inf-20100607)
        final String renamed607Name = renamed607 ? "h" : "e";
        t.custom(sandBlock, new HookTransformer.Custom() {
            public boolean apply(ClassNode cn) {
                MethodNode m = findMethod(cn, renamed607Name, desc);
                if (m == null) {
                    return false;
                }
                LabelNode cont = new LabelNode();
                InsnList il = new InsnList();
                il.add(new VarInsnNode(Opcodes.ALOAD, 1));
                il.add(new VarInsnNode(Opcodes.ILOAD, 2));
                il.add(new VarInsnNode(Opcodes.ILOAD, 3));
                il.add(new VarInsnNode(Opcodes.ILOAD, 4));
                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, H, "sandFall", "(Ljava/lang/Object;III)Z", false));
                il.add(new JumpInsnNode(Opcodes.IFEQ, cont));
                il.add(new InsnNode(Opcodes.RETURN));
                il.add(cont);
                m.instructions.insert(il);
                return true;
            }
        });
    }

    private static void addItemPickupHook(HookTransformer t, final String H) {
        t.custom("net/minecraft/a/c/c/b", new HookTransformer.Custom() {
            public boolean apply(ClassNode cn) {
                MethodNode m = findMethod(cn, "a", "(Lnet/minecraft/a/c/e/a;)V");
                if (m == null) {
                    return false;
                }
                LabelNode cont = new LabelNode();
                InsnList il = new InsnList();
                il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, H, "onItemPickup", "(Ljava/lang/Object;)Z", false));
                il.add(new JumpInsnNode(Opcodes.IFEQ, cont));
                il.add(new InsnNode(Opcodes.RETURN));
                il.add(cont);
                m.instructions.insert(il);
                return true;
            }
        });
    }

    /** World: start of the add-entity method (the mods pass on the items that fall and follow the arrows). */
    private static void addEntityAddHook(HookTransformer t, final String H, String worldInternal) {
        t.custom(worldInternal, new HookTransformer.Custom() {
            public boolean apply(ClassNode cn) {
                MethodNode m = findMethod(cn, "a", "(Lnet/minecraft/a/c/b;)V");
                if (m == null) {
                    return false;
                }
                InsnList il = new InsnList();
                il.add(new VarInsnNode(Opcodes.ALOAD, 1));
                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, H, "onEntityAdd", "(Ljava/lang/Object;)V", false));
                m.instructions.insert(il);
                return true;
            }
        });
    }

    /** The classes of the screens that the hooks need (their names moved in 20100327). */
    private static final class Screens {
        static final Screens DEFAULT = new Screens("net/minecraft/client/c/r", "net/minecraft/client/c/b",
                "net/minecraft/client/c/d", "net/minecraft/client/c/l");
        final String button, pause, death, hud;

        Screens(String button, String pause, String death, String hud) {
            this.button = button;
            this.pause = pause;
            this.death = death;
            this.hud = hud;
        }
    }

    private static HookTransformer createTransformerInfdev(final String H, final boolean v313,
                                                           final String worldInternal, final String setBlockName,
                                                           final String metaName) {
        return createTransformerInfdev(H, v313, worldInternal, setBlockName, metaName, "f", "net/minecraft/client/q", false);
    }

    /**
     * @param tickName     the main application's tick method (it changed its name in 20100321)
     * @param titleInternal the title screen class (its name moved in 20100321)
     * @param sessionCheck true for 20100321 and later: the game has a thread that asks a server of the time (long gone)
     *                     whether the account is logged in twice, and a screen "Session conflict!" that opens when the
     *                     answer does not come. The thread is stopped and the check always passes.
     */
    private static HookTransformer createTransformerInfdev(final String H, final boolean v313,
                                                           final String worldInternal, final String setBlockName,
                                                           final String metaName, final String tickName,
                                                           final String titleInternal, final boolean sessionCheck) {
        return createTransformerInfdev(H, v313, worldInternal, setBlockName, metaName, tickName, titleInternal,
                sessionCheck, false);
    }

    /** @param noClientChecks true for 20100325 and later: the game has neither the online check nor the session check */
    private static HookTransformer createTransformerInfdev(final String H, final boolean v313,
                                                           final String worldInternal, final String setBlockName,
                                                           final String metaName, final String tickName,
                                                           final String titleInternal, final boolean sessionCheck,
                                                           final boolean noClientChecks) {
        return createTransformerInfdev(H, v313, worldInternal, setBlockName, metaName, tickName, titleInternal,
                sessionCheck, noClientChecks, Screens.DEFAULT);
    }

    private static HookTransformer createTransformerInfdev(final String H, final boolean v313,
                                                           final String worldInternal, final String setBlockName,
                                                           final String metaName, final String tickName,
                                                           final String titleInternal, final boolean sessionCheck,
                                                           final boolean noClientChecks, final Screens screens) {
        HookTransformer t = new HookTransformer();
        addMouseHook(t, renamed415 ? "net/minecraft/client/a/e" : "net/minecraft/client/a/d");
        addFirstPersonArmHook(t);
        if (sessionCheck) {
            t.custom("net/minecraft/client/d", new HookTransformer.Custom() {
                public boolean apply(ClassNode cn) {
                    // the poll that reads a URL: it does nothing now (and the game contacts no server of the old days)
                    MethodNode poll = findMethod(cn, "b", "(Lnet/minecraft/client/d;)V");
                    if (poll == null) {
                        return false;
                    }
                    // the body is replaced as a whole (code and exception handlers): a bare "return" in front of it would
                    // leave dead code that the old verifier of these class files rejects
                    poll.instructions.clear();
                    poll.tryCatchBlocks.clear();
                    if (poll.localVariables != null) {
                        poll.localVariables.clear();
                    }
                    poll.instructions.add(new InsnNode(Opcodes.RETURN));
                    return true;
                }
            });
        }
        // Game tick: the main application's tick method calls the world tick
        t.custom("net/minecraft/client/d", new HookTransformer.Custom() {
            public boolean apply(ClassNode cn) {
                MethodNode m = findMethod(cn, tickName, "()V");
                if (m == null) {
                    return false;
                }
                InsnList il = new InsnList();
                if (sessionCheck) {
                    // E = K: the time of the last answer is now, so the "Session conflict!" screen never opens
                    il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    il.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraft/client/d", "K", "I"));
                    il.add(new InsnNode(Opcodes.I2L));
                    il.add(new FieldInsnNode(Opcodes.PUTSTATIC, "net/minecraft/client/d", "E", "J"));
                }
                il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, H, "tick", "(Ljava/lang/Object;)V", false));
                m.instructions.insert(il);
                return true;
            }
        });
        // Set-world method: remove the old online check at its start. It reads a URL (it can hang for a long time)
        // and keeps the world only if the check passes. Now the world is stored and the check is skipped.
        // (20100321 and later have no such check in this method)
        if (!sessionCheck && !noClientChecks) {
            t.custom("net/minecraft/client/d", new HookTransformer.Custom() {
                public boolean apply(ClassNode cn) {
                    MethodNode m = findMethod(cn, "a", "(L" + worldInternal + ";)V");
                    if (m == null) {
                        return false;
                    }
                    // The check ends with "goto L; pop; L: aload_1; ifnull ..." (the catch handler's pop, then the label)
                    LabelNode target = null;
                    for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                        if (n.getOpcode() == Opcodes.POP && n.getNext() instanceof LabelNode) {
                            AbstractInsnNode after = n.getNext().getNext();
                            while (after != null && after.getOpcode() < 0) {
                                after = after.getNext();
                            }
                            if (after instanceof VarInsnNode && after.getOpcode() == Opcodes.ALOAD
                                    && ((VarInsnNode) after).var == 1) {
                                target = (LabelNode) n.getNext();
                                break;
                            }
                        }
                    }
                    if (target == null) {
                        return false;
                    }
                    InsnList il = new InsnList();
                    il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    il.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    il.add(new FieldInsnNode(Opcodes.PUTFIELD, "net/minecraft/client/d", "d", "L" + worldInternal + ";"));
                    il.add(new JumpInsnNode(Opcodes.GOTO, target));
                    m.instructions.insert(il);
                    return true;
                }
            });
        }
        // Title screen: add the multiplayer button (end of init) and handle its click (start of the button handler)
        t.custom(titleInternal, new HookTransformer.Custom() {
            public boolean apply(ClassNode cn) {
                MethodNode init = findMethod(cn, "b", "()V");
                MethodNode click = findMethod(cn, "a", "(L" + screens.button + ";)V");
                if (init == null || click == null) {
                    return false;
                }
                java.util.List<AbstractInsnNode> rets = new java.util.ArrayList<AbstractInsnNode>();
                for (AbstractInsnNode n = init.instructions.getFirst(); n != null; n = n.getNext()) {
                    if (n.getOpcode() == Opcodes.RETURN) {
                        rets.add(n);
                    }
                }
                for (AbstractInsnNode r : rets) {
                    InsnList il = new InsnList();
                    il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, H, "titleInit", "(Ljava/lang/Object;)V", false));
                    init.instructions.insertBefore(r, il);
                }
                InsnList il = new InsnList();
                LabelNode cont = new LabelNode();
                il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                il.add(new VarInsnNode(Opcodes.ALOAD, 1));
                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, H, "titleAction",
                        "(Ljava/lang/Object;Ljava/lang/Object;)Z", false));
                il.add(new JumpInsnNode(Opcodes.IFEQ, cont));
                il.add(new InsnNode(Opcodes.RETURN));
                il.add(cont);
                click.instructions.insert(il);
                return true;
            }
        });
        // Render-chunk sorting: the two comparators return 1 for equal distances, which makes Java 7 and later throw
        // "Comparison method violates its general contract" and crash the game. Equal distances now return 0.
        HookTransformer.Custom fixCompare = new HookTransformer.Custom() {
            public boolean apply(ClassNode cn) {
                MethodNode m = findMethod(cn, "compare", "(Ljava/lang/Object;Ljava/lang/Object;)I");
                if (m == null) {
                    return false;
                }
                for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                    if (n.getOpcode() == Opcodes.FCMPG) {
                        LabelNode notEqual = new LabelNode();
                        InsnList il = new InsnList();
                        il.add(new InsnNode(Opcodes.DUP));
                        il.add(new JumpInsnNode(Opcodes.IFNE, notEqual));
                        il.add(new InsnNode(Opcodes.POP));
                        il.add(new InsnNode(Opcodes.ICONST_0));
                        il.add(new InsnNode(Opcodes.IRETURN));
                        il.add(notEqual);
                        m.instructions.insert(n, il);
                        return true;
                    }
                }
                return false;
            }
        };
        t.custom("net/minecraft/client/a/a", fixCompare);
        t.custom(renamed415 ? "net/minecraft/client/a/l" : "net/minecraft/client/a/j", fixCompare);
        // Renderer: draw the other players at the end of the entity pass (the camera is set up there)
        t.custom("net/minecraft/client/a/b", new HookTransformer.Custom() {
            public boolean apply(ClassNode cn) {
                MethodNode m = findMethod(cn, "a", "(Lnet/minecraft/a/d/a;Lnet/minecraft/client/a/b/c;F)V");
                if (m == null) {
                    return false;
                }
                java.util.List<AbstractInsnNode> rets = new java.util.ArrayList<AbstractInsnNode>();
                for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                    if (n.getOpcode() == Opcodes.RETURN) {
                        rets.add(n);
                    }
                }
                for (AbstractInsnNode r : rets) {
                    InsnList il = new InsnList();
                    il.add(new VarInsnNode(Opcodes.FLOAD, 3));
                    il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, H, "renderPlayers", "(F)V", false));
                    m.instructions.insertBefore(r, il);
                }
                // the camera matrices of the frame, for the name tags (taken before the pass changes them)
                InsnList head = new InsnList();
                head.add(new MethodInsnNode(Opcodes.INVOKESTATIC, H, "captureMatrices", "()V", false));
                m.instructions.insert(head);
                return !rets.isEmpty();
            }
        });
        // The cracks of the blocks that other players dig at (see addDigRenderHook)
        addDigRenderHook(t, H, renamed415 ? "net/minecraft/client/a/e" : "net/minecraft/client/a/d");
        // Pause menu: no saving or loading online, and a disconnect button (end of init, start of the click handler)
        t.custom(screens.pause, new HookTransformer.Custom() {
            public boolean apply(ClassNode cn) {
                MethodNode init = findMethod(cn, "b", "()V");
                MethodNode click = findMethod(cn, "a", "(L" + screens.button + ";)V");
                if (init == null || click == null) {
                    return false;
                }
                java.util.List<AbstractInsnNode> rets = new java.util.ArrayList<AbstractInsnNode>();
                for (AbstractInsnNode n = init.instructions.getFirst(); n != null; n = n.getNext()) {
                    if (n.getOpcode() == Opcodes.RETURN) {
                        rets.add(n);
                    }
                }
                for (AbstractInsnNode r : rets) {
                    InsnList il = new InsnList();
                    il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, H, "pauseInit", "(Ljava/lang/Object;)V", false));
                    init.instructions.insertBefore(r, il);
                }
                InsnList il = new InsnList();
                LabelNode cont = new LabelNode();
                il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                il.add(new VarInsnNode(Opcodes.ALOAD, 1));
                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, H, "pauseAction",
                        "(Ljava/lang/Object;Ljava/lang/Object;)Z", false));
                il.add(new JumpInsnNode(Opcodes.IFEQ, cont));
                il.add(new InsnNode(Opcodes.RETURN));
                il.add(cont);
                click.instructions.insert(il);
                return true;
            }
        });
        // Death screen: Respawn instead of generating a new level
        t.custom(screens.death, new HookTransformer.Custom() {
            public boolean apply(ClassNode cn) {
                MethodNode init = findMethod(cn, "b", "()V");
                MethodNode click = findMethod(cn, "a", "(L" + screens.button + ";)V");
                if (init == null || click == null) {
                    return false;
                }
                java.util.List<AbstractInsnNode> rets = new java.util.ArrayList<AbstractInsnNode>();
                for (AbstractInsnNode n = init.instructions.getFirst(); n != null; n = n.getNext()) {
                    if (n.getOpcode() == Opcodes.RETURN) {
                        rets.add(n);
                    }
                }
                for (AbstractInsnNode r : rets) {
                    InsnList il = new InsnList();
                    il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, H, "deathInit", "(Ljava/lang/Object;)V", false));
                    init.instructions.insertBefore(r, il);
                }
                InsnList il = new InsnList();
                LabelNode cont = new LabelNode();
                il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                il.add(new VarInsnNode(Opcodes.ALOAD, 1));
                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, H, "deathAction",
                        "(Ljava/lang/Object;Ljava/lang/Object;)Z", false));
                il.add(new JumpInsnNode(Opcodes.IFEQ, cont));
                il.add(new InsnNode(Opcodes.RETURN));
                il.add(cont);
                click.instructions.insert(il);
                return true;
            }
        });
        // In-game HUD: draw the chat lines right after the "Minecraft Infdev" label, where the HUD still has its 2D
        // drawing state (at the end of the method the state is already back to 3D and text is not visible)
        t.custom(screens.hud, new HookTransformer.Custom() {
            public boolean apply(ClassNode cn) {
                MethodNode m = findMethod(cn, "a", "(F)V");
                if (m == null) {
                    return false;
                }
                for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                    if (n instanceof org.objectweb.asm.tree.LdcInsnNode
                            && "Minecraft Infdev".equals(((org.objectweb.asm.tree.LdcInsnNode) n).cst)) {
                        // the label draw is followed by its call; the code after it is where both branches meet
                        AbstractInsnNode call = n.getNext();
                        while (call != null && call.getOpcode() != Opcodes.INVOKEVIRTUAL) {
                            call = call.getNext();
                        }
                        if (call == null || !(call.getNext() instanceof LabelNode)) {
                            return false;
                        }
                        InsnList il = new InsnList();
                        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, H, "drawHud", "(Ljava/lang/Object;)V", false));
                        m.instructions.insert(call.getNext(), il);
                        return true;
                    }
                }
                return false;
            }
        });
        // World: before every return of set-block (x, y, z, id): report the result and the arguments
        t.custom(worldInternal, new HookTransformer.Custom() {
            public boolean apply(ClassNode cn) {
                MethodNode m = findMethod(cn, setBlockName, "(IIII)Z");
                if (m == null) {
                    return false;
                }
                java.util.List<AbstractInsnNode> rets = new java.util.ArrayList<AbstractInsnNode>();
                for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                    if (n.getOpcode() == Opcodes.IRETURN) {
                        rets.add(n);
                    }
                }
                // The method reuses its parameter slots, so the arguments are copied into new local variables first
                int base = Math.max(m.maxLocals, 5);
                InsnList head = new InsnList();
                for (int i = 0; i < 4; i++) {
                    head.add(new VarInsnNode(Opcodes.ILOAD, 1 + i));
                    head.add(new VarInsnNode(Opcodes.ISTORE, base + i));
                }
                m.instructions.insert(head);
                m.maxLocals = base + 4;
                for (AbstractInsnNode r : rets) {
                    InsnList il = new InsnList();
                    il.add(new InsnNode(Opcodes.DUP));
                    for (int i = 0; i < 4; i++) {
                        il.add(new VarInsnNode(Opcodes.ILOAD, base + i));
                    }
                    il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, H, "onBlockSet", "(ZIIII)V", false));
                    m.instructions.insertBefore(r, il);
                }
                return !rets.isEmpty();
            }
        });
        if (v313) {
            // World: block metadata is set after the block (torch direction, cloth colour): report it as well
            t.custom(worldInternal, new HookTransformer.Custom() {
                public boolean apply(ClassNode cn) {
                    MethodNode m = findMethod(cn, metaName, "(IIII)V");
                    if (m == null) {
                        return false;
                    }
                    java.util.List<AbstractInsnNode> rets = new java.util.ArrayList<AbstractInsnNode>();
                    for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                        if (n.getOpcode() == Opcodes.RETURN) {
                            rets.add(n);
                        }
                    }
                    int base = Math.max(m.maxLocals, 5);
                    InsnList head = new InsnList();
                    for (int i = 0; i < 4; i++) {
                        head.add(new VarInsnNode(Opcodes.ILOAD, 1 + i));
                        head.add(new VarInsnNode(Opcodes.ISTORE, base + i));
                    }
                    m.instructions.insert(head);
                    m.maxLocals = base + 4;
                    for (AbstractInsnNode r : rets) {
                        InsnList il = new InsnList();
                        for (int i = 0; i < 4; i++) {
                            il.add(new VarInsnNode(Opcodes.ILOAD, base + i));
                        }
                        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, H, "onMetaSet", "(IIII)V", false));
                        m.instructions.insertBefore(r, il);
                    }
                    return !rets.isEmpty();
                }
            });
            return t;
        }
        // Chunk store: the terrain generator returns the hook's array when it is not null (no local terrain)
        t.custom("net/minecraft/a/a/f", new HookTransformer.Custom() {
            public boolean apply(ClassNode cn) {
                MethodNode m = findMethod(cn, "a", "(Lnet/minecraft/a/a/g;)[B");
                if (m == null) {
                    return false;
                }
                InsnList il = new InsnList();
                LabelNode cont = new LabelNode();
                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, H, "blank", "()[B", false));
                il.add(new InsnNode(Opcodes.DUP));
                il.add(new JumpInsnNode(Opcodes.IFNULL, cont));
                il.add(new InsnNode(Opcodes.ARETURN));
                il.add(cont);
                il.add(new InsnNode(Opcodes.POP));
                m.instructions.insert(il);
                return true;
            }
        });
        return t;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("TheWello's Infdev Client Patcher");
            System.out.println("Usage: java -jar TheWellosInfdevClientPatcher.jar <inf-...jar> <output.jar | folder>");
            System.out.println("With a folder the output is named <build>-mp.jar (for example inf-20100316-mp.jar).");
            System.out.println("Builds: inf-20100630-1340, inf-20100227-1433, inf-20100313, inf-20100316, inf-20100320, inf-20100321, inf-20100325-1640, inf-20100327, inf-20100330-1611, inf-20100413-1953, inf-20100414, inf-20100415, inf-20100420, inf-20100607, inf-20100608 (detected from the jar)");
            System.exit(1);
        }
        boolean publicizeOnly = args.length > 2 && args[2].equals("--publicize-only");
        File in = new File(args[0]);
        File out = new File(args[1]);
        if (out.isDirectory()) {
            out = new File(out, detect(in).id + "-mp.jar");
        }
        patch(in, out, publicizeOnly);
        System.out.println("Done: " + out.getAbsolutePath());
    }

    /** Recognises the Infdev build of a game jar from the classes in it. */
    public static net.thewellodev.protocol.GameVersion detect(File in) throws IOException {
        JarFile jar = new JarFile(in);
        try {
            return detect(jar, in);
        } finally {
            jar.close();
        }
    }

    private static net.thewellodev.protocol.GameVersion detect(JarFile jar, File in) throws IOException {
        net.thewellodev.protocol.GameVersion version;
        if (jar.getEntry("kb.class") != null && jar.getEntry("fe.class") != null && jar.getEntry("aw.class") != null) {
            version = net.thewellodev.protocol.GameVersion.INF_20100630_1340;
        } else if (jar.getEntry("com/a/a/c.class") != null && jar.getEntry("net/minecraft/client/a/d/c.class") != null
                && jar.getEntry("net/minecraft/a/a/b/ak.class") != null && jar.getEntry("net/minecraft/client/q.class") != null) {
            // 20100607 and 20100608 have the same class names: the sign block class tells them apart
            version = "4c30b04efc502dc53a411b5ae8098a90637fc7cb7b8cf1d086c67bd6b7538677".equals(
                    entrySha256(jar, "net/minecraft/a/a/b/aj.class"))
                    ? net.thewellodev.protocol.GameVersion.INF_20100608
                    : net.thewellodev.protocol.GameVersion.INF_20100607; // the sign renderer package is new in 607
        } else if (jar.getEntry("com/a/a/c.class") != null && jar.getEntry("net/minecraft/a/c/f.class") != null
                && jar.getEntry("net/minecraft/a/a/h.class") != null && jar.getEntry("net/minecraft/client/q.class") != null
                && jar.getEntry("net/minecraft/client/a/l.class") != null) {
            // 20100415 and 20100420 have the same class names: the world class tells them apart
            version = "1125297debceff21cc0bdb7566e1c95c121189c81c989547135fa7b1770cea02".equals(
                    entrySha256(jar, "net/minecraft/a/a/g.class"))
                    ? net.thewellodev.protocol.GameVersion.INF_20100420 : net.thewellodev.protocol.GameVersion.INF_20100415;
        } else if (jar.getEntry("com/a/a/c.class") != null && jar.getEntry("net/minecraft/a/c/f.class") != null
                && jar.getEntry("net/minecraft/a/a/h.class") != null && jar.getEntry("net/minecraft/client/p.class") != null) {
            // 20100413-1953 and 20100414 have the same class names: the class that uses an item on a block tells them apart
            version = "c842e56a4235105350669a5c58c9464b2e959d6792affb60193bfff97a962381".equals(
                    entrySha256(jar, "net/minecraft/a/b/d.class"))
                    ? net.thewellodev.protocol.GameVersion.INF_20100414 : net.thewellodev.protocol.GameVersion.INF_20100413_1953;
        } else if (jar.getEntry("com/a/a/c.class") != null && jar.getEntry("net/minecraft/a/a/f.class") != null
                && jar.getEntry("net/minecraft/a/a/c/a.class") != null && jar.getEntry("net/minecraft/client/p.class") != null) {
            // 20100327 and 20100330-1611 have the same class names: the chunk class of the later one knows the tag TerrainPopulated
            version = classHasText(jar, "net/minecraft/a/a/e/d.class", "TerrainPopulated")
                    ? net.thewellodev.protocol.GameVersion.INF_20100330_1611 : net.thewellodev.protocol.GameVersion.INF_20100327;
        } else if (jar.getEntry("net/minecraft/a/a/g.class") != null && jar.getEntry("net/minecraft/a/a/c/x.class") != null
                && jar.getEntry("net/minecraft/a/a/a/b.class") != null && jar.getEntry("net/minecraft/client/q.class") != null) {
            version = net.thewellodev.protocol.GameVersion.INF_20100325_1640;
        } else if (jar.getEntry("net/minecraft/a/a/e/e.class") != null && jar.getEntry("net/minecraft/a/a/f.class") != null
                && jar.getEntry("net/minecraft/a/a/b/aj.class") != null && jar.getEntry("net/minecraft/client/r.class") != null
                && jar.getEntry("net/minecraft/client/c/s.class") != null) {
            version = net.thewellodev.protocol.GameVersion.INF_20100321;
        } else if (jar.getEntry("net/minecraft/a/a/e/e.class") != null && jar.getEntry("net/minecraft/a/a/f.class") != null
                && jar.getEntry("net/minecraft/a/a/b/aj.class") != null && jar.getEntry("net/minecraft/client/d.class") != null) {
            version = net.thewellodev.protocol.GameVersion.INF_20100320;
        } else if (jar.getEntry("net/minecraft/a/a/e/e.class") != null && jar.getEntry("net/minecraft/a/a/f.class") != null
                && jar.getEntry("net/minecraft/client/d.class") != null) {
            version = net.thewellodev.protocol.GameVersion.INF_20100313;
        } else if (jar.getEntry("net/minecraft/a/a/e/e.class") != null && jar.getEntry("net/minecraft/client/d.class") != null) {
            version = net.thewellodev.protocol.GameVersion.INF_20100316;
        } else if (jar.getEntry("net/minecraft/a/a/e.class") != null && jar.getEntry("net/minecraft/client/d.class") != null) {
            version = net.thewellodev.protocol.GameVersion.INF_20100227_1433;
        } else {
            throw new IOException("This file does not look like an inf-20100630-1340, inf-20100227-1433, inf-20100313, inf-20100316, inf-20100320, inf-20100321, inf-20100325-1640, inf-20100327, inf-20100330-1611, inf-20100413-1953, inf-20100414, inf-20100415, inf-20100420, inf-20100607 or inf-20100608 client jar: " + in);
        }
        return version;
    }

    private static String entrySha256(JarFile jar, String entry) throws IOException {
        java.util.zip.ZipEntry e = jar.getEntry(entry);
        if (e == null) {
            return "";
        }
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            InputStream is = jar.getInputStream(e);
            try {
                byte[] buf = new byte[8192];
                int r;
                while ((r = is.read(buf)) > 0) {
                    md.update(buf, 0, r);
                }
            } finally {
                is.close();
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) {
                sb.append(String.format("%02x", b & 255));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IOException(ex);
        }
    }

    private static boolean classHasText(JarFile jar, String entry, String text) throws IOException {
        JarEntry e = jar.getJarEntry(entry);
        if (e == null) {
            return false;
        }
        byte[] data = readAll(jar.getInputStream(e));
        return new String(data, "ISO-8859-1").contains(text);
    }

    public static void patch(File in, File out, boolean publicizeOnly) throws IOException {
        if (in.getCanonicalFile().equals(out.getCanonicalFile())) {
            throw new IOException("The output file must not be the input jar: " + in);
        }
        // written to a temporary file and moved at the end, so that a failed run leaves no broken jar
        File tmp = new File(out.getPath() + ".tmp");
        try {
            patchTo(in, tmp, publicizeOnly);
            java.nio.file.Files.move(tmp.toPath(), out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            tmp.delete();
        }
    }

    private static void patchTo(File in, File out, boolean publicizeOnly) throws IOException {
        JarFile jar = new JarFile(in);
        if (!publicizeOnly && jar.getManifest() != null && jar.getManifest().getMainAttributes()
                .getValue(net.thewellodev.protocol.GameVersion.MANIFEST_KEY) != null) {
            jar.close();
            throw new IOException("This jar is already patched: " + in + ". Patch the original jar of the game.");
        }
        net.thewellodev.protocol.GameVersion version;
        try {
            version = detect(jar, in);
        } catch (IOException e) {
            jar.close();
            throw e;
        }
        boolean is227 = version == net.thewellodev.protocol.GameVersion.INF_20100227_1433;
        boolean is313 = version == net.thewellodev.protocol.GameVersion.INF_20100313;
        // Mod classes of each build are in their own folder inside the patcher
        boolean is316 = version == net.thewellodev.protocol.GameVersion.INF_20100316;
        boolean is320 = version == net.thewellodev.protocol.GameVersion.INF_20100320;
        boolean is321 = version == net.thewellodev.protocol.GameVersion.INF_20100321;
        boolean is325 = version == net.thewellodev.protocol.GameVersion.INF_20100325_1640;
        boolean is327 = version == net.thewellodev.protocol.GameVersion.INF_20100327;
        boolean is330 = version == net.thewellodev.protocol.GameVersion.INF_20100330_1611;
        boolean is413 = version == net.thewellodev.protocol.GameVersion.INF_20100413_1953;
        boolean is414 = version == net.thewellodev.protocol.GameVersion.INF_20100414;
        boolean is415 = version == net.thewellodev.protocol.GameVersion.INF_20100415;
        boolean is420 = version == net.thewellodev.protocol.GameVersion.INF_20100420;
        boolean is607 = version == net.thewellodev.protocol.GameVersion.INF_20100607;
        boolean is608 = version == net.thewellodev.protocol.GameVersion.INF_20100608;
        String modDir = is608 ? "/mod608/" : is607 ? "/mod607/" : is420 ? "/mod420/" : is415 ? "/mod415/" : is414 ? "/mod414/" : is413 ? "/mod413/" : is330 ? "/mod330/" : is327 ? "/mod327/" : is325 ? "/mod325/" : is321 ? "/mod321/" : is320 ? "/mod320/" : is316 ? "/mod316/" : is313 ? "/mod313/" : is227 ? "/mod227/" : "/mod/";
        HookTransformer t = publicizeOnly ? new HookTransformer()
                : (is608 ? createTransformer608() : is607 ? createTransformer607() : is420 ? createTransformer420() : is415 ? createTransformer415() : is414 ? createTransformer414() : is413 ? createTransformer413() : is330 ? createTransformer330() : is327 ? createTransformer327() : is325 ? createTransformer325() : is321 ? createTransformer321() : is320 ? createTransformer320() : is316 ? createTransformer316() : is313 ? createTransformer313() : is227 ? createTransformer227() : createTransformer());
        Manifest mf = new Manifest();
        mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (!publicizeOnly) {
            mf.getMainAttributes().putValue("TheWelloDev-Client", "1.0");
            // Lets the server recognise this jar as a build (see GameVersion)
            mf.getMainAttributes().putValue(net.thewellodev.protocol.GameVersion.MANIFEST_KEY, version.id);
        }
        JarOutputStream jos = new JarOutputStream(new FileOutputStream(out), mf);
        Set<String> written = new HashSet<String>();
        written.add("META-INF/MANIFEST.MF");
        try {
            Enumeration<JarEntry> en = jar.entries();
            while (en.hasMoreElements()) {
                JarEntry e = en.nextElement();
                String name = e.getName();
                if (name.startsWith("META-INF/") || e.isDirectory()) {
                    continue;
                }
                byte[] data = readAll(jar.getInputStream(e));
                if (name.endsWith(".class")) {
                    String internal = name.substring(0, name.length() - 6);
                    data = t.transform(internal, data);
                }
                put(jos, written, name, data);
            }
            if (!publicizeOnly) {
                List<String> miss = t.missing();
                if (!miss.isEmpty()) {
                    throw new IOException("Some hooks could not be applied (wrong version?): " + miss);
                }
                // add the mod classes
                InputStream idx = ClientPatcher.class.getResourceAsStream(modDir + "index.txt");
                if (idx == null) {
                    throw new IOException(modDir.substring(1) + "index.txt not found: this patcher was built without support for "
                            + version.id);
                }
                BufferedReader br = new BufferedReader(new InputStreamReader(idx, "UTF-8"));
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.length() == 0) {
                        continue;
                    }
                    InputStream res = ClientPatcher.class.getResourceAsStream(modDir + line);
                    if (res == null) {
                        throw new IOException("Missing mod file: " + line);
                    }
                    put(jos, written, line, readAll(res));
                }
                br.close();
            }
        } finally {
            jos.close();
            jar.close();
        }
    }

    private static void put(JarOutputStream jos, Set<String> written, String name, byte[] data) throws IOException {
        if (!written.add(name)) {
            return;
        }
        JarEntry je = new JarEntry(name);
        jos.putNextEntry(je);
        jos.write(data);
        jos.closeEntry();
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            bo.write(buf, 0, n);
        }
        in.close();
        return bo.toByteArray();
    }

    @SuppressWarnings("unused")
    private static byte[] readFile(File f) throws IOException {
        return readAll(new FileInputStream(f));
    }
}
