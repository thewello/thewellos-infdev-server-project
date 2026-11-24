package net.thewellodev.server.api414;

import net.thewellodev.api.Location;
import net.thewellodev.api.Server;
import net.thewellodev.api.World;
import net.thewellodev.api.block.Block;
import net.thewellodev.api.entity.Player;
import net.thewellodev.api414.Block414;
import net.thewellodev.api414.Location414;
import net.thewellodev.api414.Player414;
import net.thewellodev.api414.World414;

import java.util.HashMap;
import java.util.Map;

/**
 * The inf-20100414 API is a view of the server's own objects: this class makes (and keeps) the wrappers
 * and converts locations. The server itself works with the types of net.thewellodev.api.
 */
public final class Api414 {

    private final Server server;
    private final Map<Player, Player414Impl> players = new HashMap<Player, Player414Impl>();
    private World414Impl world;

    public Api414(Server server) {
        this.server = server;
    }

    public synchronized Player414 player(Player p) {
        if (p == null) {
            return null;
        }
        Player414Impl w = players.get(p);
        if (w == null) {
            w = new Player414Impl(this, p);
            players.put(p, w);
        }
        return w;
    }

    /** Forgets the wrapper of a player who left. */
    public synchronized void forget(Player p) {
        players.remove(p);
    }

    public synchronized World414 world(World w) {
        if (w == null) {
            return null;
        }
        if (world == null || world.handle != w) {
            world = new World414Impl(this, w);
        }
        return world;
    }

    public Block414 block(Block b) {
        return b == null ? null : new Block414Impl(this, b);
    }

    public Location414 location(Location l) {
        return l == null ? null : new Location414(world(l.getWorld()), l.getX(), l.getY(), l.getZ(), l.getYaw(),
                l.getPitch());
    }

    public Location toServer(Location414 l) {
        if (l == null) {
            return null;
        }
        World w = l.getWorld() instanceof World414Impl ? ((World414Impl) l.getWorld()).handle : server.getWorld();
        return new Location(w, l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    /** The server's player behind a wrapper (or the object itself). */
    public static Player unwrap(Player414 p) {
        return p instanceof Player414Impl ? ((Player414Impl) p).handle : null;
    }
}
