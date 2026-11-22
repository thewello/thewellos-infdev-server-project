package net.thewellodev.server.net;

import net.thewellodev.protocol.Protocol;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One client connection. Reading and writing run on separate threads; incoming packets are
 * queued and processed on the main server thread.
 */
public class PlayerConnection {

    /** A decoded packet from the client. */
    public static class InPacket {
        public int id;
        public int i1, i2, i3, i4, i5;
        public double x, y, z;
        public double mx, my, mz;
        public float yaw, pitch;
        public boolean b;
        public String s;
        public byte[] data;
        /** Inventory: [id, amount, damage] triples. */
        public int[] inv;
    }

    /** An outgoing packet; it is serialized on the write thread. */
    public interface OutPacket {
        void write(DataOutputStream out) throws IOException;
    }

    private static final OutPacket POISON = new OutPacket() {
        public void write(DataOutputStream out) {
        }
    };

    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final ConcurrentLinkedQueue<InPacket> incoming = new ConcurrentLinkedQueue<InPacket>();
    private final LinkedBlockingQueue<OutPacket> outgoing = new LinkedBlockingQueue<OutPacket>();
    private final AtomicInteger pendingChunks = new AtomicInteger();
    private final AtomicInteger incomingCount = new AtomicInteger();
    private volatile boolean closed;
    private volatile String closeReason;
    private volatile long lastReceived = System.currentTimeMillis();
    private final long connectedAt = System.currentTimeMillis();
    private volatile long bytesSent;

    /** Information from the handshake. */
    public volatile String username;
    public volatile boolean handshakeDone;

    /** Account verification state (used by the main thread): 0 not started, 1 waiting for the client, 2 checking with Mojang. */
    public int authState;
    public String authServerId;
    public long authStartedAt;
    /** Result of the verification thread: the verified name, or an error. */
    public volatile String authVerifiedName;
    public volatile String authError;

    /** Protocol number of the game build this server runs; clients with another number are refused. */
    private final int expectedProtocol;

    public PlayerConnection(Socket socket, int expectedProtocol) throws IOException {
        this.socket = socket;
        this.expectedProtocol = expectedProtocol;
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(60000);
        socket.setTrafficClass(0x10);
        in = new DataInputStream(new BufferedInputStream(socket.getInputStream(), 16384));
        out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), 65536));
    }

    public void start() {
        Thread r = new Thread("TW reader " + getAddressString()) {
            public void run() {
                readLoop();
            }
        };
        r.setDaemon(true);
        r.start();
        Thread w = new Thread("TW writer " + getAddressString()) {
            public void run() {
                writeLoop();
            }
        };
        w.setDaemon(true);
        w.start();
    }

    public InetSocketAddress getAddress() {
        return (InetSocketAddress) socket.getRemoteSocketAddress();
    }

    public String getAddressString() {
        InetSocketAddress a = getAddress();
        return a == null ? "?" : a.getAddress().getHostAddress() + ":" + a.getPort();
    }

    public long getConnectedAt() {
        return connectedAt;
    }

    public long getLastReceived() {
        return lastReceived;
    }

    public long getBytesSent() {
        return bytesSent;
    }

    public boolean isClosed() {
        return closed;
    }

    public String getCloseReason() {
        return closeReason;
    }

    public InPacket poll() {
        InPacket p = incoming.poll();
        if (p != null) {
            incomingCount.decrementAndGet();
        }
        return p;
    }

    public int getPendingChunks() {
        return pendingChunks.get();
    }

    public int getQueueSize() {
        return outgoing.size();
    }

    // ------------------------------------------------------------------ reading

    private void readLoop() {
        try {
            // Tunnel services may send a Minecraft Java handshake first: skip it
            int first = Protocol.skipVanillaHandshake(in, in.readUnsignedByte());
            if (first != Protocol.HANDSHAKE) {
                throw new IOException("Invalid first packet (not a client of this project?)");
            }
            int magic = in.readInt();
            int version = in.readInt();
            String name = Protocol.readString(in, 32);
            if (magic != Protocol.MAGIC) {
                throw new IOException("Invalid protocol");
            }
            if (version != expectedProtocol) {
                sendDisconnectNow("Protocol version mismatch (server " + expectedProtocol + ", client " + version + ")");
                return;
            }
            username = name;
            handshakeDone = true;
            lastReceived = System.currentTimeMillis();
            while (!closed) {
                InPacket p = read();
                lastReceived = System.currentTimeMillis();
                incoming.add(p);
                // ConcurrentLinkedQueue.size() is O(n), so use a separate counter
                if (incomingCount.incrementAndGet() > 5000) {
                    close("Too many packets");
                }
            }
        } catch (SocketTimeoutException e) {
            close("Timed out");
        } catch (IOException e) {
            close(e.getMessage() == null ? "Connection closed" : e.getMessage());
        } catch (Throwable t) {
            close("Error: " + t);
        }
    }

    private InPacket read() throws IOException {
        InPacket p = new InPacket();
        p.id = in.readUnsignedByte();
        switch (p.id) {
            case Protocol.CHAT:
                p.s = clean(Protocol.readString(in, 256));
                break;
            case Protocol.DISCONNECT:
                p.s = clean(Protocol.readString(in, 256));
                break;
            case Protocol.POSITION:
                p.x = in.readDouble();
                p.y = in.readDouble();
                p.z = in.readDouble();
                p.yaw = in.readFloat();
                p.pitch = in.readFloat();
                p.b = in.readBoolean();
                break;
            case Protocol.BLOCK_CHANGE:
                p.i1 = in.readInt();
                p.i2 = in.readShort();
                p.i3 = in.readInt();
                p.i4 = in.readUnsignedByte();
                p.i5 = in.readUnsignedByte();
                break;
            case Protocol.KEEP_ALIVE:
                p.i1 = in.readInt();
                break;
            case Protocol.SWING:
            case Protocol.DEATH:
            case Protocol.RESPAWN:
                break;
            case Protocol.DROP_ITEM:
                p.x = in.readDouble();
                p.y = in.readDouble();
                p.z = in.readDouble();
                p.mx = in.readDouble();
                p.my = in.readDouble();
                p.mz = in.readDouble();
                p.i1 = in.readShort();
                p.i2 = in.readUnsignedByte();
                p.i3 = in.readShort();
                break;
            case Protocol.PICKUP_ITEM:
                p.i1 = in.readInt();
                break;
            case Protocol.ATTACK:
                p.i1 = in.readInt();
                p.i2 = in.readUnsignedByte();
                break;
            case Protocol.HEALTH:
            case Protocol.HELD_ITEM:
                p.i1 = in.readShort();
                break;
            case Protocol.AUTH_RESPONSE:
                p.b = in.readBoolean();
                p.s = clean(Protocol.readString(in, 256));
                break;
            case Protocol.FIRE_ARROW:
                break;
            case Protocol.DROP_SPAWN:
                p.i1 = in.readInt();
                p.i2 = in.readShort();
                p.i3 = in.readShort();
                p.i4 = in.readShort();
                p.x = in.readDouble();
                p.y = in.readDouble();
                p.z = in.readDouble();
                p.mx = in.readDouble();
                p.my = in.readDouble();
                p.mz = in.readDouble();
                break;
            case Protocol.DROP_REMOVE:
                p.i1 = in.readInt();
                p.i2 = in.readUnsignedByte();
                break;
            case Protocol.EXPLOSION:
                p.yaw = in.readFloat();
                p.x = in.readDouble();
                p.y = in.readDouble();
                p.z = in.readDouble();
                break;
            case Protocol.MOB_STATE:
                p.i1 = in.readInt();
                p.i2 = in.readUnsignedByte();
                p.x = in.readDouble();
                p.y = in.readDouble();
                p.z = in.readDouble();
                p.yaw = in.readFloat();
                p.pitch = in.readFloat();
                p.mx = in.readFloat();
                p.my = in.readFloat();
                p.mz = in.readFloat();
                p.i3 = in.readUnsignedByte();
                p.i4 = in.readUnsignedByte();
                p.i5 = in.readInt();
                break;
            case Protocol.MOB_HIT:
                p.i1 = in.readInt();
                p.i2 = in.readUnsignedByte();
                break;
            case Protocol.DROP_CLAIM:
                p.i1 = in.readInt();
                break;
            case Protocol.MOB_REMOVE:
                p.i1 = in.readInt();
                p.i2 = in.readUnsignedByte();
                break;
            case Protocol.INVENTORY_REPORT: {
                int n = in.readUnsignedByte();
                if (n > 48) {
                    throw new IOException("Invalid inventory packet");
                }
                p.inv = new int[n * 3];
                for (int i = 0; i < n; i++) {
                    p.inv[i * 3] = in.readShort();
                    p.inv[i * 3 + 1] = in.readInt();
                    p.inv[i * 3 + 2] = in.readShort();
                }
                break;
            }
            case Protocol.INVENTORY: {
                int n = in.readUnsignedByte();
                if (n > 48) {
                    throw new IOException("Invalid inventory packet");
                }
                p.inv = new int[n * 3];
                for (int i = 0; i < n; i++) {
                    p.inv[i * 3] = in.readShort();
                    p.inv[i * 3 + 1] = in.readUnsignedByte();
                    p.inv[i * 3 + 2] = in.readShort();
                }
                break;
            }
            case Protocol.LOCK_REQUEST:
            case Protocol.LOCK_RELEASE:
                p.i1 = in.readInt();
                p.i2 = in.readShort();
                p.i3 = in.readInt();
                break;
            case Protocol.PLACE_CART:
                p.i1 = in.readInt();
                p.i2 = in.readShort();
                p.i3 = in.readInt();
                break;
            case Protocol.RIDE:
            case Protocol.SADDLE:
                p.i1 = in.readInt();
                break;
            case Protocol.RIDE_INPUT:
                p.x = in.readFloat();
                p.z = in.readFloat();
                if (Double.isNaN(p.x) || Double.isNaN(p.z)) {
                    p.x = 0;
                    p.z = 0;
                }
                break;
            case Protocol.PLACE_PAINTING:
                p.i1 = in.readInt();
                p.i2 = in.readShort();
                p.i3 = in.readInt();
                p.i4 = in.readUnsignedByte();
                p.i5 = in.readUnsignedByte();
                break;
            case Protocol.TILE_DATA: {
                p.i1 = in.readInt();
                p.i2 = in.readShort();
                p.i3 = in.readInt();
                int len = in.readShort();
                if (len < 1 || len > 4000) {
                    throw new IOException("Invalid tile data");
                }
                p.data = new byte[len];
                in.readFully(p.data);
                break;
            }
            case Protocol.DIG_PROGRESS:
                p.i1 = in.readInt();
                p.i2 = in.readShort();
                p.i3 = in.readInt();
                p.i4 = in.readByte();
                break;
            default:
                throw new IOException("Unknown packet 0x" + Integer.toHexString(p.id));
        }
        return p;
    }

    /** Removes control characters (line breaks etc.) from text that will be written to the log. */
    private static String clean(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append(c < 32 || c == 127 ? ' ' : c);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ writing

    private void writeLoop() {
        try {
            while (true) {
                OutPacket p = outgoing.poll(1, TimeUnit.SECONDS);
                if (p == null) {
                    if (closed) {
                        break;
                    }
                    continue;
                }
                if (p == POISON) {
                    out.flush();
                    break;
                }
                p.write(out);
                if (p instanceof ChunkPacket) {
                    pendingChunks.decrementAndGet();
                }
                if (outgoing.isEmpty()) {
                    out.flush();
                }
            }
        } catch (InterruptedException ignored) {
        } catch (IOException e) {
            close(e.getMessage() == null ? "Write error" : e.getMessage());
        } finally {
            try {
                out.flush();
            } catch (IOException ignored) {
            }
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    public void send(OutPacket p) {
        if (closed) {
            return;
        }
        if (p instanceof ChunkPacket) {
            pendingChunks.incrementAndGet();
        }
        outgoing.add(p);
        if (outgoing.size() > 20000) {
            close("Send queue full (connection too slow)");
        }
    }

    /** Sends the queued packets, then closes the connection. */
    public void disconnect(final String reason) {
        if (closed) {
            return;
        }
        send(new OutPacket() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.DISCONNECT);
                Protocol.writeString(o, reason);
            }
        });
        closeReason = reason;
        closed = true;
        outgoing.add(POISON);
    }

    private void sendDisconnectNow(String reason) throws IOException {
        out.writeByte(Protocol.DISCONNECT);
        Protocol.writeString(out, reason);
        out.flush();
        close(reason);
    }

    /** Closes immediately. */
    public void close(String reason) {
        if (closeReason == null) {
            closeReason = reason;
        }
        if (!closed) {
            closed = true;
            outgoing.add(POISON);
        }
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    // ------------------------------------------------------------------ packets

    /** Raw chunk data; compression happens on the write thread. */
    public static final class ChunkPacket implements OutPacket {
        private final int cx, cz;
        private final byte[] raw;

        public ChunkPacket(int cx, int cz, byte[] raw) {
            this.cx = cx;
            this.cz = cz;
            this.raw = raw;
        }

        public void write(DataOutputStream o) throws IOException {
            byte[] d = Protocol.deflate(raw);
            o.writeByte(Protocol.CHUNK);
            o.writeInt(cx);
            o.writeInt(cz);
            o.writeInt(d.length);
            o.write(d);
        }
    }

    public void sendChat(final String msg) {
        send(new OutPacket() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.CHAT);
                Protocol.writeString(o, msg);
            }
        });
    }

    public void sendLogin(final int entityId, final long seed, final double x, final double y, final double z,
                          final float yaw, final float pitch, final long time, final int difficulty, final String motd) {
        send(new OutPacket() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.LOGIN);
                o.writeInt(entityId);
                o.writeLong(seed);
                o.writeDouble(x);
                o.writeDouble(y);
                o.writeDouble(z);
                o.writeFloat(yaw);
                o.writeFloat(pitch);
                o.writeLong(time);
                o.writeByte(difficulty);
                Protocol.writeString(o, motd);
            }
        });
    }

    public void sendTime(final long time) {
        send(new OutPacket() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.TIME);
                o.writeLong(time);
            }
        });
    }

    public void sendChunk(int cx, int cz, byte[] raw) {
        send(new ChunkPacket(cx, cz, raw));
    }

    public void sendUnloadChunk(final int cx, final int cz) {
        send(new OutPacket() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.UNLOAD_CHUNK);
                o.writeInt(cx);
                o.writeInt(cz);
            }
        });
    }

    public void sendBlockChange(final int x, final int y, final int z, final int id, final int meta) {
        send(new OutPacket() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.BLOCK_CHANGE);
                o.writeInt(x);
                o.writeShort(y);
                o.writeInt(z);
                o.writeByte(id);
                o.writeByte(meta);
            }
        });
    }

    public void sendSpawnPlayer(final int id, final String name, final double x, final double y, final double z,
                                final float yaw, final float pitch) {
        send(new OutPacket() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.SPAWN_PLAYER);
                o.writeInt(id);
                Protocol.writeString(o, name);
                o.writeDouble(x);
                o.writeDouble(y);
                o.writeDouble(z);
                o.writeFloat(yaw);
                o.writeFloat(pitch);
            }
        });
    }

    public void sendMovePlayer(final int id, final double x, final double y, final double z, final float yaw,
                               final float pitch) {
        send(new OutPacket() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.MOVE_PLAYER);
                o.writeInt(id);
                o.writeDouble(x);
                o.writeDouble(y);
                o.writeDouble(z);
                o.writeFloat(yaw);
                o.writeFloat(pitch);
            }
        });
    }

    public void sendRemovePlayer(final int id) {
        send(new OutPacket() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.REMOVE_PLAYER);
                o.writeInt(id);
            }
        });
    }

    public void sendTeleport(final double x, final double y, final double z, final float yaw, final float pitch) {
        send(new OutPacket() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.TELEPORT);
                o.writeDouble(x);
                o.writeDouble(y);
                o.writeDouble(z);
                o.writeFloat(yaw);
                o.writeFloat(pitch);
            }
        });
    }

    public void sendSpawnItem(final int netId, final double x, final double y, final double z, final double mx,
                              final double my, final double mz, final int id, final int count, final int damage) {
        send(new OutPacket() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.SPAWN_ITEM);
                o.writeInt(netId);
                o.writeDouble(x);
                o.writeDouble(y);
                o.writeDouble(z);
                o.writeDouble(mx);
                o.writeDouble(my);
                o.writeDouble(mz);
                o.writeShort(id);
                o.writeByte(count);
                o.writeShort(damage);
            }
        });
    }

    /** Packets with two int fields (REMOVE_ITEM; HELD_ITEM is the exception, its int part). */
    public void sendInts(final int packetId, final int... values) {
        send(new OutPacket() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(packetId);
                for (int v : values) {
                    o.writeInt(v);
                }
            }
        });
    }

    public void sendSpawnMob(final int id, final int type, final int x, final int y, final int z, final int yaw,
                             final int pitch, final int state) {
        send(new OutPacket() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.SPAWN_MOB);
                o.writeInt(id);
                o.writeByte(type);
                o.writeInt(x);
                o.writeInt(y);
                o.writeInt(z);
                o.writeByte(yaw);
                o.writeByte(pitch);
                o.writeByte(state);
            }
        });
    }

    public void sendMoveMob(final int id, final int x, final int y, final int z, final int yaw, final int pitch,
                            final int state) {
        send(new OutPacket() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.MOVE_MOB);
                o.writeInt(id);
                o.writeInt(x);
                o.writeInt(y);
                o.writeInt(z);
                o.writeByte(yaw);
                o.writeByte(pitch);
                o.writeByte(state);
            }
        });
    }

    public void sendRemoveMob(final int id, final boolean died) {
        send(new OutPacket() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.REMOVE_MOB);
                o.writeInt(id);
                o.writeBoolean(died);
            }
        });
    }

    public void sendTile(final int x, final int y, final int z, final byte[] data) {
        send(new OutPacket() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.TILE_DATA);
                o.writeInt(x);
                o.writeShort(y);
                o.writeInt(z);
                o.writeShort(data.length);
                o.write(data);
            }
        });
    }

    public void sendSound(final String name, final int x, final int y, final int z, final float vol, final float pitch) {
        send(new OutPacket() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.SOUND);
                Protocol.writeString(o, name);
                o.writeInt(x);
                o.writeInt(y);
                o.writeInt(z);
                o.writeFloat(vol);
                o.writeFloat(pitch);
            }
        });
    }

    public void sendParticle(final String name, final float x, final float y, final float z, final float vx,
                             final float vy, final float vz) {
        send(new OutPacket() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.PARTICLE);
                Protocol.writeString(o, name);
                o.writeFloat(x);
                o.writeFloat(y);
                o.writeFloat(z);
                o.writeFloat(vx);
                o.writeFloat(vy);
                o.writeFloat(vz);
            }
        });
    }

    public void sendMount(final int playerId, final int vehicleId) {
        send(new OutPacket() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.MOUNT);
                o.writeInt(playerId);
                o.writeInt(vehicleId);
            }
        });
    }

    /** Build 316: an item that appeared in another player's game. */
    public void sendDropSpawn(final int number, final int id, final int count, final int damage, final double[] v) {
        send(new OutPacket() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.DROP_SPAWN);
                o.writeInt(number);
                o.writeShort(id);
                o.writeShort(count);
                o.writeShort(damage);
                for (int i = 0; i < 6; i++) {
                    o.writeDouble(v[i]);
                }
            }
        });
    }

    /** Builds 316 and 320: a mob of another player's game. */
    public void sendMobState(int number, int kind, double x, double y, double z, float yaw, float pitch, float body,
                             float limbAmount, float limbPosition, int hurt, int death, int fireAndHealth) {
        sendMob(Protocol.MOB_STATE, number, kind, x, y, z, yaw, pitch, body, limbAmount, limbPosition, hurt, death,
                fireAndHealth);
    }

    /** Hands a mob to this player (its owner left): the same body as the state. */
    public void sendMobAdopt(int number, int kind, double x, double y, double z, float yaw, float pitch, float body,
                             float limbAmount, float limbPosition, int hurt, int death, int fireAndHealth) {
        sendMob(Protocol.MOB_ADOPT, number, kind, x, y, z, yaw, pitch, body, limbAmount, limbPosition, hurt, death,
                fireAndHealth);
    }

    private void sendMob(final int packet, final int number, final int kind, final double x, final double y,
                         final double z, final float yaw, final float pitch, final float body, final float limbAmount,
                         final float limbPosition, final int hurt, final int death, final int fireAndHealth) {
        send(new OutPacket() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(packet);
                o.writeInt(number);
                o.writeByte(kind);
                o.writeDouble(x);
                o.writeDouble(y);
                o.writeDouble(z);
                o.writeFloat(yaw);
                o.writeFloat(pitch);
                o.writeFloat(body);
                o.writeFloat(limbAmount);
                o.writeFloat(limbPosition);
                o.writeByte(hurt);
                o.writeByte(death);
                o.writeInt(fireAndHealth);
            }
        });
    }

    /** A player hit the mob of this player's game. */
    public void sendMobHit(final int number, final int damage, final int attackerId) {
        send(new OutPacket() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.MOB_HIT);
                o.writeInt(number);
                o.writeByte(damage);
                o.writeInt(attackerId);
            }
        });
    }

    /** This player's game no longer runs the mob with this number. */
    public void sendMobRelease(final int number) {
        send(new OutPacket() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.MOB_RELEASE);
                o.writeInt(number);
            }
        });
    }

    /** Asks the game of this player to announce its items again (see Protocol.DROP_RESYNC). */
    public void sendDropResync() {
        send(new OutPacket() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.DROP_RESYNC);
            }
        });
    }

    public void sendDropClaimResult(final int number, final boolean ok) {
        sendDropClaimResult(number, ok ? 1 : 0);
    }

    /** Code 0: somebody else has the item; 1: it is yours; 2: refused for now (the item stays). */
    public void sendDropClaimResult(final int number, final int code) {
        send(new OutPacket() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.DROP_CLAIM_RESULT);
                o.writeInt(number);
                o.writeByte(code);
            }
        });
    }

    public void sendSlot(final int slot, final int firstNumber) {
        send(new OutPacket() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.SLOT);
                o.writeInt(slot);
                o.writeInt(firstNumber);
            }
        });
    }

    /** Builds 316 and 320: that mob is gone. */
    public void sendMobRemove(final int number) {
        send(new OutPacket() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.MOB_REMOVE);
                o.writeInt(number);
            }
        });
    }

    /** Build 316: that item entity is gone. */
    public void sendDropRemove(final int number) {
        send(new OutPacket() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.DROP_REMOVE);
                o.writeInt(number);
            }
        });
    }

    /** The saved inventory of the player (builds 227 and 313): n times (id, count, damage); n = 0: nothing saved. */
    public void sendInventorySync(final int[] triples) {
        send(new OutPacket() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.INVENTORY_SYNC);
                int n = triples.length / 3;
                o.writeByte(n);
                for (int i = 0; i < n; i++) {
                    o.writeShort(triples[i * 3]);
                    o.writeInt(triples[i * 3 + 1]);
                    o.writeShort(triples[i * 3 + 2]);
                }
            }
        });
    }

    /** Sets a slot in the player's inventory (itemId -1 empties the slot). */
    public void sendSetSlot(final int slot, final int itemId, final int count, final int damage) {
        send(new OutPacket() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.SET_SLOT);
                o.writeShort(slot);
                o.writeShort(itemId);
                o.writeByte(count);
                o.writeShort(damage);
            }
        });
    }

    public void sendLockResult(final int x, final int y, final int z, final boolean granted) {
        send(new OutPacket() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.LOCK_RESULT);
                o.writeInt(x);
                o.writeShort(y);
                o.writeInt(z);
                o.writeBoolean(granted);
            }
        });
    }

    public void sendDifficulty(final int difficulty) {
        send(new OutPacket() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.DIFFICULTY);
                o.writeByte(difficulty);
            }
        });
    }

    public void sendDamage(final int attackerId, final int damage) {
        send(new OutPacket() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.DAMAGE);
                o.writeInt(attackerId);
                o.writeByte(Math.max(0, Math.min(255, damage)));
            }
        });
    }

    public void sendHealth(final int health) {
        send(new OutPacket() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.HEALTH);
                o.writeShort(health);
            }
        });
    }

    public void sendGiveItem(final int id, final int count, final int damage) {
        send(new OutPacket() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.GIVE_ITEM);
                o.writeShort(id);
                o.writeByte(Math.max(1, Math.min(255, count)));
                o.writeShort(damage);
            }
        });
    }

    public void sendGameMode(final int mode) {
        send(new OutPacket() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.GAME_MODE);
                o.writeByte(mode);
            }
        });
    }

    public void sendHeldItem(final int entityId, final int itemId) {
        send(new OutPacket() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.HELD_ITEM);
                o.writeInt(entityId);
                o.writeShort(itemId);
            }
        });
    }

    public void sendAuthRequest(final String serverId) {
        send(new OutPacket() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.AUTH_REQUEST);
                Protocol.writeString(o, serverId);
            }
        });
    }

    public void sendDig(final int entityId, final int x, final int y, final int z, final int stage) {
        send(new OutPacket() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.DIG_PROGRESS);
                o.writeInt(entityId);
                o.writeInt(x);
                o.writeShort(y);
                o.writeInt(z);
                o.writeByte(stage);
            }
        });
    }

    public void sendKeepAlive(final int token) {
        send(new OutPacket() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.KEEP_ALIVE);
                o.writeInt(token);
            }
        });
    }
}
