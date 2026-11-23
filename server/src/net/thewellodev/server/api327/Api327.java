package net.thewellodev.server.api327;

import net.thewellodev.api.Location;
import net.thewellodev.api.Server;
import net.thewellodev.api.World;
import net.thewellodev.api.block.Block;
import net.thewellodev.api.entity.Player;
import net.thewellodev.api327.Block327;
import net.thewellodev.api327.Location327;
import net.thewellodev.api327.Player327;
import net.thewellodev.api327.World327;

import java.util.HashMap;
import java.util.Map;

/**
 * The inf-20100327 API is a view of the server's own objects: this class makes (and keeps) the wrappers
 * and converts locations. The server itself works with the types of net.thewellodev.api.
 */
public final class Api327 {

    private final Server server;
    private final Map<Player, Player327Impl> players = new HashMap<Player, Player327Impl>();
    private World327Impl world;

    public Api327(Server server) {
        this.server = server;
    }

    public synchronized Player327 player(Player p) {
        if (p == null) {
            return null;
        }
        Player327Impl w = players.get(p);
        if (w == null) {
            w = new Player327Impl(this, p);
            players.put(p, w);
        }
        return w;
    }

    /** Forgets the wrapper of a player who left. */
    public synchronized void forget(Player p) {
        players.remove(p);
    }

    public synchronized World327 world(World w) {
        if (w == null) {
            return null;
        }
        if (world == null || world.handle != w) {
            world = new World327Impl(this, w);
        }
        return world;
    }

    public Block327 block(Block b) {
        return b == null ? null : new Block327Impl(this, b);
    }

    public Location327 location(Location l) {
        return l == null ? null : new Location327(world(l.getWorld()), l.getX(), l.getY(), l.getZ(), l.getYaw(),
                l.getPitch());
    }

    public Location toServer(Location327 l) {
        if (l == null) {
            return null;
        }
        World w = l.getWorld() instanceof World327Impl ? ((World327Impl) l.getWorld()).handle : server.getWorld();
        return new Location(w, l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    /** The server's player behind a wrapper (or the object itself). */
    public static Player unwrap(Player327 p) {
        return p instanceof Player327Impl ? ((Player327Impl) p).handle : null;
    }
}
