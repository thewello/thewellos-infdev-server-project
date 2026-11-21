package net.thewellodev.server.api320;

import net.thewellodev.api.Location;
import net.thewellodev.api.Server;
import net.thewellodev.api.World;
import net.thewellodev.api.block.Block;
import net.thewellodev.api.entity.Player;
import net.thewellodev.api320.Block320;
import net.thewellodev.api320.Location320;
import net.thewellodev.api320.Player320;
import net.thewellodev.api320.World320;

import java.util.HashMap;
import java.util.Map;

/**
 * The inf-20100320 API is a view of the server's own objects: this class makes (and keeps) the wrappers
 * and converts locations. The server itself works with the types of net.thewellodev.api.
 */
public final class Api320 {

    private final Server server;
    private final Map<Player, Player320Impl> players = new HashMap<Player, Player320Impl>();
    private World320Impl world;

    public Api320(Server server) {
        this.server = server;
    }

    public synchronized Player320 player(Player p) {
        if (p == null) {
            return null;
        }
        Player320Impl w = players.get(p);
        if (w == null) {
            w = new Player320Impl(this, p);
            players.put(p, w);
        }
        return w;
    }

    /** Forgets the wrapper of a player who left. */
    public synchronized void forget(Player p) {
        players.remove(p);
    }

    public synchronized World320 world(World w) {
        if (w == null) {
            return null;
        }
        if (world == null || world.handle != w) {
            world = new World320Impl(this, w);
        }
        return world;
    }

    public Block320 block(Block b) {
        return b == null ? null : new Block320Impl(this, b);
    }

    public Location320 location(Location l) {
        return l == null ? null : new Location320(world(l.getWorld()), l.getX(), l.getY(), l.getZ(), l.getYaw(),
                l.getPitch());
    }

    public Location toServer(Location320 l) {
        if (l == null) {
            return null;
        }
        World w = l.getWorld() instanceof World320Impl ? ((World320Impl) l.getWorld()).handle : server.getWorld();
        return new Location(w, l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    /** The server's player behind a wrapper (or the object itself). */
    public static Player unwrap(Player320 p) {
        return p instanceof Player320Impl ? ((Player320Impl) p).handle : null;
    }
}
