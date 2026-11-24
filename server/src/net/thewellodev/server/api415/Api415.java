package net.thewellodev.server.api415;

import net.thewellodev.api.Location;
import net.thewellodev.api.Server;
import net.thewellodev.api.World;
import net.thewellodev.api.block.Block;
import net.thewellodev.api.entity.Player;
import net.thewellodev.api415.Block415;
import net.thewellodev.api415.Location415;
import net.thewellodev.api415.Player415;
import net.thewellodev.api415.World415;

import java.util.HashMap;
import java.util.Map;

/**
 * The inf-20100415 API is a view of the server's own objects: this class makes (and keeps) the wrappers
 * and converts locations. The server itself works with the types of net.thewellodev.api.
 */
public final class Api415 {

    private final Server server;
    private final Map<Player, Player415Impl> players = new HashMap<Player, Player415Impl>();
    private World415Impl world;

    public Api415(Server server) {
        this.server = server;
    }

    public synchronized Player415 player(Player p) {
        if (p == null) {
            return null;
        }
        Player415Impl w = players.get(p);
        if (w == null) {
            w = new Player415Impl(this, p);
            players.put(p, w);
        }
        return w;
    }

    /** Forgets the wrapper of a player who left. */
    public synchronized void forget(Player p) {
        players.remove(p);
    }

    public synchronized World415 world(World w) {
        if (w == null) {
            return null;
        }
        if (world == null || world.handle != w) {
            world = new World415Impl(this, w);
        }
        return world;
    }

    public Block415 block(Block b) {
        return b == null ? null : new Block415Impl(this, b);
    }

    public Location415 location(Location l) {
        return l == null ? null : new Location415(world(l.getWorld()), l.getX(), l.getY(), l.getZ(), l.getYaw(),
                l.getPitch());
    }

    public Location toServer(Location415 l) {
        if (l == null) {
            return null;
        }
        World w = l.getWorld() instanceof World415Impl ? ((World415Impl) l.getWorld()).handle : server.getWorld();
        return new Location(w, l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    /** The server's player behind a wrapper (or the object itself). */
    public static Player unwrap(Player415 p) {
        return p instanceof Player415Impl ? ((Player415Impl) p).handle : null;
    }
}
