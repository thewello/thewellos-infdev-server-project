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
        return t;
    }

    /**
     * Hooks for inf-20100320: the world class is f again; the set-block method that notifies the renderer is c(IIII)Z
     * and the set-metadata method is b(IIII)V. Besides the hooks of 20100316 it reports explosions.
     */
    public static HookTransformer createTransformer320() {
        HookTransformer t = createTransformerInfdev("TW320Client", true, "net/minecraft/a/a/f", "c", "b");
        addEntityAddHook(t, "TW320Client", "net/minecraft/a/a/f");
        addItemPickupHook(t, "TW320Client");
        // Chest and furnace blocks: the click that opens them asks the server for the lock first
        addContainerHook(t, "TW320Client", "net/minecraft/a/a/b/a");
        addContainerHook(t, "TW320Client", "net/minecraft/a/a/b/s");
        // World: start of the explosion method (entity, x, y, z, power): the other players near it are hurt too
        t.custom("net/minecraft/a/a/f", new HookTransformer.Custom() {
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
                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "TW320Client", "onExplosion",
                        "(Ljava/lang/Object;DDDF)V", false));
                m.instructions.insert(il);
                return true;
            }
        });
        return t;
    }

    /**
     * A block's "right-clicked" method (world, x, y, z, player): the mod may cancel the click (it returns true) while it
     * waits for the server.
     */
    private static void addContainerHook(HookTransformer t, final String H, String blockInternal) {
        t.custom(blockInternal, new HookTransformer.Custom() {
            public boolean apply(ClassNode cn) {
                MethodNode m = findMethod(cn, "a", "(Lnet/minecraft/a/a/f;IIILnet/minecraft/a/c/e/a;)Z");
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

    private static HookTransformer createTransformerInfdev(final String H, final boolean v313,
                                                           final String worldInternal, final String setBlockName,
                                                           final String metaName) {
        HookTransformer t = new HookTransformer();
        addMouseHook(t, "net/minecraft/client/a/d");
        // Game tick: the main application's tick method calls the world tick
        t.custom("net/minecraft/client/d", new HookTransformer.Custom() {
            public boolean apply(ClassNode cn) {
                MethodNode m = findMethod(cn, "f", "()V");
                if (m == null) {
                    return false;
                }
                InsnList il = new InsnList();
                il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, H, "tick", "(Ljava/lang/Object;)V", false));
                m.instructions.insert(il);
                return true;
            }
        });
        // Set-world method: remove the old online check at its start. It reads a URL (it can hang for a long time)
        // and keeps the world only if the check passes. Now the world is stored and the check is skipped.
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
        // Title screen: add the multiplayer button (end of init) and handle its click (start of the button handler)
        t.custom("net/minecraft/client/q", new HookTransformer.Custom() {
            public boolean apply(ClassNode cn) {
                MethodNode init = findMethod(cn, "b", "()V");
                MethodNode click = findMethod(cn, "a", "(Lnet/minecraft/client/c/r;)V");
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
        t.custom("net/minecraft/client/a/j", fixCompare);
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
        // Pause menu: no saving or loading online, and a disconnect button (end of init, start of the click handler)
        t.custom("net/minecraft/client/c/b", new HookTransformer.Custom() {
            public boolean apply(ClassNode cn) {
                MethodNode init = findMethod(cn, "b", "()V");
                MethodNode click = findMethod(cn, "a", "(Lnet/minecraft/client/c/r;)V");
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
        t.custom("net/minecraft/client/c/d", new HookTransformer.Custom() {
            public boolean apply(ClassNode cn) {
                MethodNode init = findMethod(cn, "b", "()V");
                MethodNode click = findMethod(cn, "a", "(Lnet/minecraft/client/c/r;)V");
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
        t.custom("net/minecraft/client/c/l", new HookTransformer.Custom() {
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
            System.out.println("Builds: inf-20100630-1340, inf-20100227-1433, inf-20100313, inf-20100316, inf-20100320 (detected from the jar)");
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
            throw new IOException("This file does not look like an inf-20100630-1340, inf-20100227-1433, inf-20100313, inf-20100316 or inf-20100320 client jar: " + in);
        }
        return version;
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
        String modDir = is320 ? "/mod320/" : is316 ? "/mod316/" : is313 ? "/mod313/" : is227 ? "/mod227/" : "/mod/";
        HookTransformer t = publicizeOnly ? new HookTransformer()
                : (is320 ? createTransformer320() : is316 ? createTransformer316() : is313 ? createTransformer313() : is227 ? createTransformer227() : createTransformer());
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
