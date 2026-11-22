package net.thewellodev.protocol;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * The network protocol (client <-> server).
 *
 * Each packet is [byte id][body]. Body fields are written with DataOutputStream.
 * Strings are written with writeUTF.
 */
public final class Protocol {

    /** Protocol number of inf-20100630-1340 clients. Each build has its own number (see GameVersion). */
    public static final int VERSION = 11;
    public static final int DEFAULT_PORT = 25565;
    /** Magic number the client sends at the start of a connection ("TWLD"). */
    public static final int MAGIC = 0x54574C44;

    // ---- Packet ids ----
    /** C->S: int magic, int version, String username */
    public static final int HANDSHAKE = 0x01;
    /** S->C: int entityId, long seed, double x,y,z, float yaw,pitch, long time, boolean mobs, String motd */
    public static final int LOGIN = 0x02;
    /** C->S / S->C: String message */
    public static final int CHAT = 0x03;
    /** S->C: long worldTime */
    public static final int TIME = 0x04;
    /** C->S: double x,y,z, float yaw,pitch, boolean onGround */
    public static final int POSITION = 0x05;
    /** S->C: int id, String name, double x,y,z, float yaw,pitch */
    public static final int SPAWN_PLAYER = 0x06;
    /** S->C: int id, double x,y,z, float yaw,pitch */
    public static final int MOVE_PLAYER = 0x07;
    /** S->C: int id */
    public static final int REMOVE_PLAYER = 0x08;
    /** S->C: int cx, int cz, int length, byte[length] (deflate) */
    public static final int CHUNK = 0x09;
    /** S->C: int cx, int cz */
    public static final int UNLOAD_CHUNK = 0x0A;
    /** C->S / S->C: int x, short y, int z, byte id, byte meta */
    public static final int BLOCK_CHANGE = 0x0B;
    /** S->C / C->S: String reason */
    public static final int DISCONNECT = 0x0C;
    /** S->C: double x,y,z, float yaw,pitch */
    public static final int TELEPORT = 0x0D;
    /** both: int token */
    public static final int KEEP_ALIVE = 0x0E;
    /** C->S: (empty) / S->C: int id -- arm swing animation */
    public static final int SWING = 0x0F;
    /** C->S: double x,y,z, double mx,my,mz, short itemId, byte count, short damage -- item dropped on the ground */
    public static final int DROP_ITEM = 0x10;
    /** S->C: int netId, double x,y,z, double mx,my,mz, short itemId, byte count, short damage */
    public static final int SPAWN_ITEM = 0x11;
    /** C->S: int netId -- request to pick up an item */
    public static final int PICKUP_ITEM = 0x12;
    /** S->C: int netId, int collectorEntityId (-1 = vanished) */
    public static final int REMOVE_ITEM = 0x13;
    /** C->S: int targetEntityId, byte damage -- attack another player */
    public static final int ATTACK = 0x14;
    /** S->C: int attackerEntityId, byte damage -- you were hit */
    public static final int DAMAGE = 0x15;
    /** S->C: int entityId -- player was hurt (red flash) */
    public static final int HURT_ANIMATION = 0x16;
    /** C->S: short health (changed) / S->C: short health (set health) */
    public static final int HEALTH = 0x17;
    /** C->S: (empty) -- the player died */
    public static final int DEATH = 0x18;
    /** C->S: (empty) -- client respawned */
    public static final int RESPAWN = 0x19;
    /** S->C: short itemId, byte count, short damage -- give an item to the inventory */
    public static final int GIVE_ITEM = 0x1A;
    /** S->C: byte mode (0 survival, 1 creative) */
    public static final int GAME_MODE = 0x1B;
    /** C->S: short itemId (-1 = empty) / S->C: int entityId, short itemId -- held item */
    public static final int HELD_ITEM = 0x1C;
    /** S->C: String serverId -- request Mojang session verification */
    public static final int AUTH_REQUEST = 0x1D;
    /** C->S: boolean ok, String error -- whether the client joined the session server */
    public static final int AUTH_RESPONSE = 0x1E;
    /**
     * C->S: int x, short y, int z, byte stage (-1 = released, 0-9) -- block-breaking progress
     * S->C: int entityId, int x, short y, int z, byte stage
     */
    public static final int DIG_PROGRESS = 0x1F;
    /** S->C: int netId, byte type, int x32, int y32, int z32, byte yaw, byte pitch, byte state -- mob or arrow spawned */
    public static final int SPAWN_MOB = 0x20;
    /** S->C: int netId, int x32, int y32, int z32, byte yaw, byte pitch, byte state */
    public static final int MOVE_MOB = 0x21;
    /** S->C: int netId, boolean dead */
    public static final int REMOVE_MOB = 0x22;
    /** Both directions: int x, short y, int z, short length, byte[] NBT -- chest/furnace/sign contents */
    public static final int TILE_DATA = 0x24;
    /** C->S: int x, short y, int z, byte direction, byte motif -- a painting was placed */
    public static final int PLACE_PAINTING = 0x25;
    /** S->C: String name, int x32, int y32, int z32, float volume, float pitch -- world sound (explosion, mob, arrow...) */
    public static final int SOUND = 0x26;
    /** S->C: String name, float x,y,z, float vx,vy,vz -- particle (smoke, explosion...) */
    public static final int PARTICLE = 0x27;
    /** C->S: (empty) -- bow shot (the arrow is created on the server) */
    public static final int FIRE_ARROW = 0x28;
    /** C->S: int x, short y, int z -- a minecart was placed on a rail */
    public static final int PLACE_CART = 0x29;
    /** C->S: int netId (-1 = got off) -- got on or off a minecart */
    public static final int RIDE = 0x2A;
    /** C->S: float mx, float mz -- movement input of a rider (pushes the minecart) */
    public static final int RIDE_INPUT = 0x2B;
    /** C->S: int netId -- a saddle was put on a pig */
    public static final int SADDLE = 0x2C;
    /** S->C: int playerEntityId, int vehicleNetId (-1 = dismounted) -- another player mounted a vehicle/pig */
    public static final int MOUNT = 0x2D;
    /** C->S: byte count, count x (short id (-1 = empty), byte amount, short damage) -- inventory summary (for validation) */
    public static final int INVENTORY = 0x2E;
    /** S->C: short slot, short itemId (-1 = empty), byte amount, short damage -- set an inventory slot */
    public static final int SET_SLOT = 0x32;
    /** C->S: int x, short y, int z -- request a lock to open a chest/furnace (one player at a time) */
    public static final int LOCK_REQUEST = 0x2F;
    /** S->C: int x, short y, int z, boolean given */
    public static final int LOCK_RESULT = 0x30;
    /** C->S: int x, short y, int z -- chest/furnace closed, release the lock */
    public static final int LOCK_RELEASE = 0x31;
    /**
     * Server to client (builds 227 and 313, whose stacks hold more than 255): the inventory the server saved for the
     * player, sent once after the login. Layout: ubyte n; n times (short id (-1: empty), int count, short damage).
     * n = 0 means nothing is saved (the client keeps its own and starts reporting).
     */
    public static final int INVENTORY_SYNC = 0x33;
    /** Client to server (same layout as INVENTORY_SYNC): the inventory changed; the server saves it. */
    public static final int INVENTORY_REPORT = 0x34;
    /**
     * Builds 316 and 320, both directions: an item that appeared in a player's own game (a dropped block, a thrown item).
     * Body: int number (see {@link #SLOT}), item id, count, damage (short, short, short), position and motion (6 doubles).
     * The server passes it to the players near it, who show it in their own game.
     */
    public static final int DROP_SPAWN = 0x35;
    /**
     * The item entity is gone (picked up, burnt, too old). Client to server: int number, byte (unused); the server accepts it
     * from the player who dropped the item or who was given it by DROP_CLAIM_RESULT. Server to client: int number. The other
     * players remove their copy.
     */
    public static final int DROP_REMOVE = 0x36;
    /**
     * Client to server (build 320): an explosion happened in the player's own game; the blocks it destroys arrive as block
     * changes in a burst, which the server accepts (only removals inside the blast radius) although it is above the
     * normal rate. Body: float power, 3 doubles (the centre).
     */
    public static final int EXPLOSION = 0x37;
    /**
     * Builds 316 and 320, both directions: a mob that an arrow made in a player's own game, and where it is now (sent when it
     * appears and a few times a second). Both directions: int number, byte kind, 3 doubles position, floats yaw, pitch, body yaw, limb swing amount and
     * limb swing position, byte hurt time, byte death time, int (fire flag | health << 8). The number is the one of
     * {@link #SLOT}. The server passes it to the other players, whose clients draw a copy that is moved and animated from it.
     * The mob itself lives in the game of the player whose arrow made it (the server hands it to another player when that
     * player leaves).
     */
    public static final int MOB_STATE = 0x38;
    /**
     * The mob of a player's own game is gone. Client to server: int number, byte own (must be 1). Server to client: int
     * number; a number whose lower 20 bits are zero means all mobs of that player (he left the server).
     */
    public static final int MOB_REMOVE = 0x39;
    /**
     * A player hit the copy of a mob of another player's game (melee or arrow). Client to server: int number, byte damage.
     * Server to client (the owner of the mob): int number, byte damage, int entity id of the attacker.
     */
    public static final int MOB_HIT = 0x3A;
    /**
     * Server to client: the owner of this mob left and this client takes the mob over. Same body as MOB_STATE: the client
     * replaces its copy by a real mob and from then on reports it under the same number.
     */
    public static final int MOB_ADOPT = 0x3B;
    /** Client to server: this game wants to pick up the shared item with this number. Body: int number. */
    public static final int DROP_CLAIM = 0x3C;
    /** Server to client: answer to DROP_CLAIM. Body: int number, byte: 0 = somebody else has it, 1 = the item is yours (the first one asked gets it), 2 = refused for now (a plugin said no; the item stays and the game may ask again). */
    public static final int DROP_CLAIM_RESULT = 0x3D;
    /**
     * Server to client (builds 316 and 320), after the login: int slot, int first number. The numbers of the items and mobs
     * of a game are {@code slot << 20 | n}; n counts up from the given start and is never 0.
     */
    public static final int SLOT = 0x3E;
    /**
     * Server to client: this game no longer runs the mob with this number (another player is nearer to it and takes it
     * over, see MOB_ADOPT). Body: int number. The real mob is removed without a death; the game draws a copy again
     * from the states of the new owner.
     */
    public static final int MOB_RELEASE = 0x3F;
    /**
     * Server to client (builds 316 and later), no body: send DROP_SPAWN again for every item of your own game that is still
     * lying about. The server asks when a player has joined (he saw none of the items that were already there) and
     * once a minute (players walk into range of items that were made when they were far away). A game that already has
     * the item ignores the copy.
     */
    public static final int DROP_RESYNC = 0x40;
    /** S->C: byte difficulty (0 peaceful, 1 easy, 2 normal, 3 hard) */
    public static final int DIFFICULTY = 0x23;

    /** Synchronized entity classes (obfuscated game names); the index is the mob type. */
    public static final String[] MOB_CLASSES = {"gw", "fo", "fy", "ci", "dn", "ib", "eo", "ac", "db", "ij", "cy", "jp"};
    /** English names (death messages, commands). */
    public static final String[] MOB_NAMES = {"Zombie", "Skeleton", "Creeper", "Spider", "Sheep", "Pig", "Arrow", "Giant", "TNT", "Sand", "Painting", "Minecart"};
    /** Mob network ids start at this value so they do not clash with player ids. */
    public static final int MOB_ID_BASE = 1000000;
    /** State bits: 1 = burning, 2 = sheared sheep, 4.. = creeper fuse (value << 2) */
    public static final int STATE_BURNING = 1;
    public static final int STATE_SHEARED = 2;

    public static int toFixed(double v) {
        return (int) Math.floor(v * 32.0D);
    }

    public static double fromFixed(int v) {
        return v / 32.0D;
    }

    public static byte toAngle(float deg) {
        return (byte) (int) Math.floor(deg * 256.0F / 360.0F);
    }

    public static float fromAngle(byte b) {
        return b * 360.0F / 256.0F;
    }

    /**
     * Minecraft Java "Handshake" packet (length + packet 0 + protocol + address + port + next state = login).
     * Tunnel services read this packet to route connections.
     * The server recognizes this packet and skips it.
     */
    public static byte[] vanillaHandshake(String host, int port, String username) {
        byte[] hs = vanillaHandshake(host, port);
        try {
            // Then "Login Start" (1.20.1 format: name, whether a UUID is present, UUID). Tunnel services verify this too.
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            writeVarInt(body, 0x00);
            byte[] n = (username == null || username.length() == 0 ? "Player" : username).getBytes("UTF-8");
            if (n.length > 16) {
                n = java.util.Arrays.copyOf(n, 16);
            }
            writeVarInt(body, n.length);
            body.write(n);
            body.write(1);
            body.write(new byte[16]);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(hs);
            writeVarInt(out, body.size());
            out.write(body.toByteArray());
            return out.toByteArray();
        } catch (IOException e) {
            return hs;
        }
    }

    public static byte[] vanillaHandshake(String host, int port) {
        try {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            writeVarInt(body, 0x00);
            writeVarInt(body, 763);
            byte[] h = (host == null ? "" : host).getBytes("UTF-8");
            if (h.length > 255) {
                h = new byte[0];
            }
            writeVarInt(body, h.length);
            body.write(h);
            body.write((port >> 8) & 0xFF);
            body.write(port & 0xFF);
            writeVarInt(body, 2);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            writeVarInt(out, body.size());
            out.write(body.toByteArray());
            return out.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }

    private static void writeVarInt(ByteArrayOutputStream b, int v) {
        while ((v & ~0x7F) != 0) {
            b.write((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        b.write(v);
    }

    /**
     * If the first byte is not the HANDSHAKE, a Minecraft Java handshake comes first;
     * reads and skips it. @return the first byte of the handshake
     */
    public static int skipVanillaHandshake(DataInputStream in, int first) throws IOException {
        // Skip at most 2 Minecraft packets (Handshake + Login Start); each is at least 3 bytes long,
        // so a length byte never collides with the HANDSHAKE (1).
        int b0 = first;
        for (int frames = 0; b0 != HANDSHAKE; frames++) {
            if (frames >= 2) {
                throw new IOException("Invalid first packet (not a client of this project?)");
            }
            int len = b0 & 0x7F;
            int shift = 7;
            int b = b0;
            while ((b & 0x80) != 0) {
                if (shift > 21) {
                    throw new IOException("Invalid first packet");
                }
                b = in.readUnsignedByte();
                len |= (b & 0x7F) << shift;
                shift += 7;
            }
            if (len < 3 || len > 300) {
                throw new IOException("Invalid first packet (not a client of this project?)");
            }
            in.readFully(new byte[len]);
            b0 = in.readUnsignedByte();
        }
        return b0;
    }

    /** Mojang session server (can be replaced for testing with -Dthewellodev.sessionserver). */
    public static String sessionServer() {
        return System.getProperty("thewellodev.sessionserver", "https://sessionserver.mojang.com");
    }

    /** Minecraft profile service (can be replaced for testing with -Dthewellodev.profileserver). */
    public static String profileServer() {
        return System.getProperty("thewellodev.profileserver", "https://api.minecraftservices.com");
    }

    /** Reads a simple JSON field, for flat objects such as {"id":"...","name":"..."}. */
    public static String jsonField(String json, String field) {
        if (json == null) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"" + java.util.regex.Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    /** Chunk size (infdev: 16x128x16). */
    public static final int CHUNK_BLOCKS = 16 * 16 * 128;
    public static final int CHUNK_NIBBLES = CHUNK_BLOCKS / 2;
    /** Raw chunk data: blocks + metadata + sky light + block light + height map */
    public static final int CHUNK_RAW_SIZE = CHUNK_BLOCKS + CHUNK_NIBBLES * 3 + 256;

    private Protocol() {
    }

    public static byte[] deflate(byte[] data) {
        Deflater d = new Deflater(Deflater.BEST_SPEED);
        d.setInput(data);
        d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length / 4);
        byte[] buf = new byte[8192];
        while (!d.finished()) {
            int n = d.deflate(buf);
            out.write(buf, 0, n);
        }
        d.end();
        return out.toByteArray();
    }

    public static byte[] inflate(byte[] data, int expectedSize) throws IOException {
        Inflater inf = new Inflater();
        inf.setInput(data);
        byte[] out = new byte[expectedSize];
        try {
            int off = 0;
            while (off < expectedSize && !inf.finished()) {
                int n = inf.inflate(out, off, expectedSize - off);
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) {
                    break;
                }
                off += n;
            }
            if (off != expectedSize) {
                throw new IOException("Chunk data incomplete: " + off + "/" + expectedSize);
            }
        } catch (DataFormatException e) {
            throw new IOException("Corrupt chunk data: " + e.getMessage());
        } finally {
            inf.end();
        }
        return out;
    }

    /** Reads a string with a length limit. */
    public static String readString(DataInputStream in, int maxLen) throws IOException {
        String s = in.readUTF();
        if (s.length() > maxLen) {
            throw new IOException("String too long (" + s.length() + " > " + maxLen + ")");
        }
        return s;
    }

    public static void writeString(DataOutputStream out, String s) throws IOException {
        out.writeUTF(s == null ? "" : s);
    }
}
