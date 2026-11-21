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

/**
 * Server connection. Reading happens on a separate thread; packets are queued and
 * is processed on the game thread (TWClient.preTick).
 */
public class TWConnection {

    /** A decoded packet. The fields used depend on the packet type. */
    public static class Pkt {
        public int id;
        public int i1, i2, i3, i4, i5;
        public long l, l2;
        public double x, y, z;
        public double mx, my, mz;
        public float yaw, pitch;
        public boolean b;
        public String s;
        public byte[] data;
        /** INVENTORY_SYNC: n times (id, count, damage). */
        public int[] inv;
    }

    private final String host;
    private final int port;
    private final String username;
    /** Protocol number of the game build this client belongs to (sent in the handshake). */
    private final int protocol;
    private Socket socket;
    private DataOutputStream out;
    private final ConcurrentLinkedQueue<Pkt> queue = new ConcurrentLinkedQueue<Pkt>();
    private volatile boolean closed;
    private volatile String closeReason = "Connection closed";
    public volatile String status = "Connecting...";
    public volatile boolean loggedIn;

    public TWConnection(String host, int port, String username) {
        this(host, port, username, Protocol.VERSION);
    }

    public TWConnection(String host, int port, String username, int protocol) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.protocol = protocol;
    }

    public void start() {
        Thread t = new Thread("Infdev MP reader") {
            public void run() {
                readLoop();
            }
        };
        t.setDaemon(true);
        t.start();
    }

    private void readLoop() {
        try {
            String connectHost = host;
            int connectPort = port;
            if (port <= 0) {
                // If no port was written, look up the DNS SRV record (_minecraft._tcp.host) like normal Minecraft
                status = "Resolving address: " + host;
                String[] srv = lookupSrv(host);
                if (srv != null) {
                    connectHost = srv[0];
                    connectPort = Integer.parseInt(srv[1]);
                } else {
                    connectPort = Protocol.DEFAULT_PORT;
                }
            }
            status = "Connecting to server: " + host + (port > 0 ? ":" + port : "");
            Socket s = new Socket();
            s.connect(new InetSocketAddress(connectHost, connectPort), 10000);
            s.setTcpNoDelay(true);
            s.setSoTimeout(30000);
            synchronized (this) {
                if (closed) {
                    s.close();
                    return;
                }
                socket = s;
                out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream(), 16384));
            }
            status = "Logging in...";
            synchronized (out) {
                // Tunnel services read the first packet as a real Minecraft login: first send a compatible
                // Minecraft Java handshake (the server skips it), then our own handshake.
                out.write(Protocol.vanillaHandshake(host, connectPort, username));
                out.writeByte(Protocol.HANDSHAKE);
                out.writeInt(Protocol.MAGIC);
                out.writeInt(protocol);
                Protocol.writeString(out, username);
                out.flush();
            }
            DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream(), 65536));
            while (!closed) {
                Pkt p = read(in);
                if (p.id == Protocol.LOGIN) {
                    loggedIn = true;
                    status = "Loading world...";
                }
                queue.add(p);
            }
        } catch (SocketTimeoutException e) {
            close("Timed out (server not responding)");
        } catch (IOException e) {
            if (!closed) {
                close(loggedIn ? "Connection lost: " + e.getMessage() : "Could not connect: " + e.getMessage());
            }
        } catch (Throwable t) {
            t.printStackTrace();
            close("Error: " + t);
        }
    }

    /** _minecraft._tcp SRV record: {host, port}, or null if there is none. IP addresses are not looked up. */
    static String[] lookupSrv(String host) {
        if (host.matches("[0-9.]+") || host.indexOf(':') >= 0 || host.indexOf('.') < 0) {
            return null;
        }
        try {
            java.util.Hashtable<String, String> env = new java.util.Hashtable<String, String>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            env.put("java.naming.provider.url", "dns:");
            env.put("com.sun.jndi.dns.timeout.initial", "2000");
            env.put("com.sun.jndi.dns.timeout.retries", "1");
            javax.naming.directory.DirContext ctx = new javax.naming.directory.InitialDirContext(env);
            try {
                javax.naming.directory.Attribute a = ctx.getAttributes("_minecraft._tcp." + host, new String[]{"SRV"})
                        .get("SRV");
                if (a == null || a.size() == 0) {
                    return null;
                }
                // "priority weight port target."
                String[] parts = a.get(0).toString().trim().split(" +");
                if (parts.length < 4) {
                    return null;
                }
                String target = parts[3].endsWith(".") ? parts[3].substring(0, parts[3].length() - 1) : parts[3];
                int p = Integer.parseInt(parts[2]);
                if (p <= 0 || p > 65535 || target.length() == 0) {
                    return null;
                }
                return new String[]{target, String.valueOf(p)};
            } finally {
                ctx.close();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private Pkt read(DataInputStream in) throws IOException {
        Pkt p = new Pkt();
        p.id = in.readUnsignedByte();
        switch (p.id) {
            case Protocol.LOGIN:
                p.i1 = in.readInt();
                p.l2 = in.readLong();
                p.x = in.readDouble();
                p.y = in.readDouble();
                p.z = in.readDouble();
                p.yaw = in.readFloat();
                p.pitch = in.readFloat();
                p.l = in.readLong();
                p.i2 = in.readUnsignedByte(); // difficulty
                p.s = Protocol.readString(in, 1024);
                break;
            case Protocol.CHAT:
            case Protocol.DISCONNECT:
                p.s = Protocol.readString(in, 4096);
                break;
            case Protocol.TIME:
                p.l = in.readLong();
                break;
            case Protocol.SPAWN_PLAYER:
                p.i1 = in.readInt();
                p.s = Protocol.readString(in, 64);
                p.x = in.readDouble();
                p.y = in.readDouble();
                p.z = in.readDouble();
                p.yaw = in.readFloat();
                p.pitch = in.readFloat();
                break;
            case Protocol.MOVE_PLAYER:
                p.i1 = in.readInt();
                p.x = in.readDouble();
                p.y = in.readDouble();
                p.z = in.readDouble();
                p.yaw = in.readFloat();
                p.pitch = in.readFloat();
                break;
            case Protocol.REMOVE_PLAYER:
            case Protocol.KEEP_ALIVE:
            case Protocol.SWING:
                p.i1 = in.readInt();
                break;
            case Protocol.CHUNK: {
                p.i1 = in.readInt();
                p.i2 = in.readInt();
                int len = in.readInt();
                if (len < 0 || len > 4 * 1024 * 1024) {
                    throw new IOException("Invalid chunk size " + len);
                }
                p.data = new byte[len];
                in.readFully(p.data);
                break;
            }
            case Protocol.UNLOAD_CHUNK:
                p.i1 = in.readInt();
                p.i2 = in.readInt();
                break;
            case Protocol.BLOCK_CHANGE:
                p.i1 = in.readInt();
                p.i2 = in.readShort();
                p.i3 = in.readInt();
                p.i4 = in.readUnsignedByte();
                p.i5 = in.readUnsignedByte();
                break;
            case Protocol.TELEPORT:
                p.x = in.readDouble();
                p.y = in.readDouble();
                p.z = in.readDouble();
                p.yaw = in.readFloat();
                p.pitch = in.readFloat();
                break;
            case Protocol.SPAWN_ITEM:
                p.i1 = in.readInt();
                p.x = in.readDouble();
                p.y = in.readDouble();
                p.z = in.readDouble();
                p.mx = in.readDouble();
                p.my = in.readDouble();
                p.mz = in.readDouble();
                p.i2 = in.readShort();
                p.i3 = in.readUnsignedByte();
                p.i4 = in.readShort();
                break;
            case Protocol.REMOVE_ITEM:
                p.i1 = in.readInt();
                p.i2 = in.readInt();
                break;
            case Protocol.DAMAGE:
                p.i1 = in.readInt();
                p.i2 = in.readUnsignedByte();
                break;
            case Protocol.HURT_ANIMATION:
                p.i1 = in.readInt();
                break;
            case Protocol.HEALTH:
                p.i1 = in.readShort();
                break;
            case Protocol.GIVE_ITEM:
                p.i1 = in.readShort();
                p.i2 = in.readUnsignedByte();
                p.i3 = in.readShort();
                break;
            case Protocol.GAME_MODE:
                p.i1 = in.readUnsignedByte();
                break;
            case Protocol.HELD_ITEM:
                p.i1 = in.readInt();
                p.i2 = in.readShort();
                break;
            case Protocol.AUTH_REQUEST:
                p.s = Protocol.readString(in, 64);
                status = "Verifying account...";
                break;
            case Protocol.DIG_PROGRESS:
                p.i1 = in.readInt();
                p.i2 = in.readInt();
                p.i3 = in.readShort();
                p.i4 = in.readInt();
                p.i5 = in.readByte();
                break;
            case Protocol.SPAWN_MOB:
                p.i1 = in.readInt();
                p.i2 = in.readUnsignedByte();
                p.x = Protocol.fromFixed(in.readInt());
                p.y = Protocol.fromFixed(in.readInt());
                p.z = Protocol.fromFixed(in.readInt());
                p.yaw = Protocol.fromAngle(in.readByte());
                p.pitch = Protocol.fromAngle(in.readByte());
                p.i3 = in.readUnsignedByte();
                break;
            case Protocol.MOVE_MOB:
                p.i1 = in.readInt();
                p.x = Protocol.fromFixed(in.readInt());
                p.y = Protocol.fromFixed(in.readInt());
                p.z = Protocol.fromFixed(in.readInt());
                p.yaw = Protocol.fromAngle(in.readByte());
                p.pitch = Protocol.fromAngle(in.readByte());
                p.i3 = in.readUnsignedByte();
                break;
            case Protocol.REMOVE_MOB:
                p.i1 = in.readInt();
                p.b = in.readBoolean();
                break;
            case Protocol.DIFFICULTY:
                p.i1 = in.readUnsignedByte();
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
                break;
            case Protocol.MOB_STATE:
            case Protocol.MOB_ADOPT:
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
                p.i3 = in.readInt();
                break;
            case Protocol.MOB_RELEASE:
                p.i1 = in.readInt();
                break;
            case Protocol.DROP_CLAIM_RESULT:
                p.i1 = in.readInt();
                p.i2 = in.readUnsignedByte();
                break;
            case Protocol.SLOT:
                p.i1 = in.readInt();
                p.i2 = in.readInt();
                break;
            case Protocol.MOB_REMOVE:
                p.i1 = in.readInt();
                break;
            case Protocol.INVENTORY_SYNC: {
                int n = in.readUnsignedByte();
                p.inv = new int[n * 3];
                for (int i = 0; i < n; i++) {
                    p.inv[i * 3] = in.readShort();
                    p.inv[i * 3 + 1] = in.readInt();
                    p.inv[i * 3 + 2] = in.readShort();
                }
                break;
            }
            case Protocol.SET_SLOT:
                p.i1 = in.readShort();
                p.i2 = in.readShort();
                p.i3 = in.readUnsignedByte();
                p.i4 = in.readShort();
                break;
            case Protocol.LOCK_RESULT:
                p.i1 = in.readInt();
                p.i2 = in.readShort();
                p.i3 = in.readInt();
                p.b = in.readBoolean();
                break;
            case Protocol.MOUNT:
                p.i1 = in.readInt();
                p.i2 = in.readInt();
                break;
            case Protocol.SOUND:
                p.s = Protocol.readString(in, 64);
                p.x = Protocol.fromFixed(in.readInt());
                p.y = Protocol.fromFixed(in.readInt());
                p.z = Protocol.fromFixed(in.readInt());
                p.yaw = in.readFloat();
                p.pitch = in.readFloat();
                break;
            case Protocol.PARTICLE:
                p.s = Protocol.readString(in, 32);
                p.x = in.readFloat();
                p.y = in.readFloat();
                p.z = in.readFloat();
                p.mx = in.readFloat();
                p.my = in.readFloat();
                p.mz = in.readFloat();
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
            default:
                throw new IOException("Unknown packet 0x" + Integer.toHexString(p.id));
        }
        return p;
    }

    public Pkt poll() {
        return queue.poll();
    }

    public boolean isClosed() {
        return closed && queue.isEmpty();
    }

    public String getCloseReason() {
        return closeReason;
    }

    public synchronized void close(String reason) {
        if (closed) {
            return;
        }
        closeReason = reason;
        closed = true;
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }

    // ---- Sending ----

    private interface Writer {
        void write(DataOutputStream o) throws IOException;
    }

    /** While true the packets are collected and sent together (one tick can make hundreds of block changes). */
    private volatile boolean batching;

    public void beginBatch() {
        batching = true;
    }

    /** Sends what was collected since {@link #beginBatch()}. */
    public void endBatch() {
        batching = false;
        DataOutputStream o = out;
        if (o == null || closed) {
            return;
        }
        try {
            synchronized (o) {
                o.flush();
            }
        } catch (IOException e) {
            close("Send error: " + e.getMessage());
        }
    }

    private void send(Writer w) {
        DataOutputStream o = out;
        if (o == null || closed) {
            return;
        }
        try {
            synchronized (o) {
                w.write(o);
                if (!batching) {
                    o.flush();
                }
            }
        } catch (IOException e) {
            close("Send error: " + e.getMessage());
        }
    }

    public void sendChat(final String msg) {
        send(new Writer() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.CHAT);
                Protocol.writeString(o, msg.length() > 256 ? msg.substring(0, 256) : msg);
            }
        });
    }

    public void sendPosition(final double x, final double y, final double z, final float yaw, final float pitch,
                             final boolean onGround) {
        send(new Writer() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.POSITION);
                o.writeDouble(x);
                o.writeDouble(y);
                o.writeDouble(z);
                o.writeFloat(yaw);
                o.writeFloat(pitch);
                o.writeBoolean(onGround);
            }
        });
    }

    public void sendBlockChange(final int x, final int y, final int z, final int id, final int meta) {
        send(new Writer() {
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

    public void sendKeepAlive(final int token) {
        send(new Writer() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.KEEP_ALIVE);
                o.writeInt(token);
            }
        });
    }

    public void sendDropItem(final double x, final double y, final double z, final double mx, final double my,
                             final double mz, final int id, final int count, final int damage) {
        send(new Writer() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.DROP_ITEM);
                o.writeDouble(x);
                o.writeDouble(y);
                o.writeDouble(z);
                o.writeDouble(mx);
                o.writeDouble(my);
                o.writeDouble(mz);
                o.writeShort(id);
                o.writeByte(Math.min(255, count));
                o.writeShort(damage);
            }
        });
    }

    /** Only for packets with int arguments or no arguments. */
    public void sendSimple(final int id, final int... ints) {
        send(new Writer() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(id);
                for (int v : ints) {
                    o.writeInt(v);
                }
            }
        });
    }

    public void sendAttack(final int target, final int damage) {
        send(new Writer() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.ATTACK);
                o.writeInt(target);
                o.writeByte(Math.max(0, Math.min(255, damage)));
            }
        });
    }

    public void sendShort(final int id, final int value) {
        send(new Writer() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(id);
                o.writeShort(value);
            }
        });
    }

    public void sendAuthResponse(final boolean ok, final String error) {
        send(new Writer() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.AUTH_RESPONSE);
                o.writeBoolean(ok);
                Protocol.writeString(o, error == null ? "" : (error.length() > 200 ? error.substring(0, 200) : error));
            }
        });
    }

    public void sendInventory(final int[] triples) {
        send(new Writer() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.INVENTORY);
                int n = triples.length / 3;
                o.writeByte(n);
                for (int i = 0; i < n; i++) {
                    o.writeShort(triples[i * 3]);
                    o.writeByte(triples[i * 3 + 1]);
                    o.writeShort(triples[i * 3 + 2]);
                }
            }
        });
    }

    /** Build 316: an item appeared in this player's game (own number, id, count, damage, position, motion). */
    public void sendDropSpawn(final int seq, final int id, final int count, final int damage, final double[] v) {
        send(new Writer() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.DROP_SPAWN);
                o.writeInt(seq);
                o.writeShort(id);
                o.writeShort(count);
                o.writeShort(damage);
                for (int i = 0; i < 6; i++) {
                    o.writeDouble(v[i]);
                }
            }
        });
    }

    /** Build 320: an explosion happened in this game (the server accepts the burst of block changes). */
    public void sendExplosion(final float power, final double x, final double y, final double z) {
        send(new Writer() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.EXPLOSION);
                o.writeFloat(power);
                o.writeDouble(x);
                o.writeDouble(y);
                o.writeDouble(z);
            }
        });
    }

    /** Builds 316 and 320: a mob of this game (own number, kind, position, rotation). */
    public void sendMobState(final int number, final int kind, final double x, final double y, final double z,
                             final float yaw, final float pitch, final float body, final float limbAmount,
                             final float limbPosition, final int hurt, final int death, final int fireAndHealth) {
        send(new Writer() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.MOB_STATE);
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

    /** Builds 316 and 320: this game hit the copy of a mob of another player's game. */
    public void sendMobHit(final int number, final int damage) {
        send(new Writer() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.MOB_HIT);
                o.writeInt(number);
                o.writeByte(damage);
            }
        });
    }

    /** Builds 316 and 320: this game wants to pick up the shared item with this number. */
    public void sendDropClaim(final int number) {
        send(new Writer() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.DROP_CLAIM);
                o.writeInt(number);
            }
        });
    }

    /** Builds 316 and 320: a mob of this game is gone (own: the number is this client's own number). */
    public void sendMobRemove(final int number, final boolean own) {
        send(new Writer() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.MOB_REMOVE);
                o.writeInt(number);
                o.writeByte(own ? 1 : 0);
            }
        });
    }

    /** Build 316: an item entity is gone (own: the number is this client's own number). */
    public void sendDropRemove(final int number, final boolean own) {
        send(new Writer() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.DROP_REMOVE);
                o.writeInt(number);
                o.writeByte(own ? 1 : 0);
            }
        });
    }

    /** The inventory with 32-bit counts (builds 227 and 313): n times (id, count, damage). */
    public void sendInventoryReport(final int[] triples) {
        send(new Writer() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.INVENTORY_REPORT);
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

    public void sendXYZ(final int packet, final int x, final int y, final int z) {
        send(new Writer() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(packet);
                o.writeInt(x);
                o.writeShort(y);
                o.writeInt(z);
            }
        });
    }

    public void sendInt(final int packet, final int v) {
        send(new Writer() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(packet);
                o.writeInt(v);
            }
        });
    }

    public void sendCart(final int x, final int y, final int z) {
        send(new Writer() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.PLACE_CART);
                o.writeInt(x);
                o.writeShort(y);
                o.writeInt(z);
            }
        });
    }

    public void sendRideInput(final float mx, final float mz) {
        send(new Writer() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.RIDE_INPUT);
                o.writeFloat(mx);
                o.writeFloat(mz);
            }
        });
    }

    public void sendPainting(final int x, final int y, final int z, final int dir, final int motive) {
        send(new Writer() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.PLACE_PAINTING);
                o.writeInt(x);
                o.writeShort(y);
                o.writeInt(z);
                o.writeByte(dir);
                o.writeByte(motive);
            }
        });
    }

    public void sendTile(final int x, final int y, final int z, final byte[] data) {
        send(new Writer() {
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

    public void sendDig(final int x, final int y, final int z, final int stage) {
        send(new Writer() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.DIG_PROGRESS);
                o.writeInt(x);
                o.writeShort(y);
                o.writeInt(z);
                o.writeByte(stage);
            }
        });
    }

    public void sendDisconnect(final String reason) {
        send(new Writer() {
            public void write(DataOutputStream o) throws IOException {
                o.writeByte(Protocol.DISCONNECT);
                Protocol.writeString(o, reason);
            }
        });
    }
}
