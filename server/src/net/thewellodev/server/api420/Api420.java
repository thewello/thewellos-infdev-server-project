package net.thewellodev.server.api420;

import net.thewellodev.api.Location;
import net.thewellodev.api.Server;
import net.thewellodev.api.World;
import net.thewellodev.api.block.Block;
import net.thewellodev.api.entity.Player;
import net.thewellodev.api420.Block420;
import net.thewellodev.api420.Location420;
import net.thewellodev.api420.Player420;
import net.thewellodev.api420.World420;

import java.util.HashMap;
import java.util.Map;

/**
 * The inf-20100420 API is a view of the server's own objects: this class makes (and keeps) the wrappers
 * and converts locations. The server itself works with the types of net.thewellodev.api.
 */
public final class Api420 {

    private final Server server;
    private final Map<Player, Player420Impl> players = new HashMap<Player, Player420Impl>();
    private World420Impl world;

    public Api420(Server server) {
        this.server = server;
    }

    public synchronized Player420 player(Player p) {
        if (p == null) {
            return null;
        }
        Player420Impl w = players.get(p);
        if (w == null) {
            w = new Player420Impl(this, p);
            players.put(p, w);
        }
        return w;
    }

    /** Forgets the wrapper of a player who left. */
    public synchronized void forget(Player p) {
        players.remove(p);
    }

    public synchronized World420 world(World w) {
        if (w == null) {
            return null;
        }
        if (world == null || world.handle != w) {
            world = new World420Impl(this, w);
        }
        return world;
    }

    public Block420 block(Block b) {
        return b == null ? null : new Block420Impl(this, b);
    }

    public Location420 location(Location l) {
        return l == null ? null : new Location420(world(l.getWorld()), l.getX(), l.getY(), l.getZ(), l.getYaw(),
                l.getPitch());
    }

    public Location toServer(Location420 l) {
        if (l == null) {
            return null;
        }
        World w = l.getWorld() instanceof World420Impl ? ((World420Impl) l.getWorld()).handle : server.getWorld();
        return new Location(w, l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    /** The server's player behind a wrapper (or the object itself). */
    public static Player unwrap(Player420 p) {
        return p instanceof Player420Impl ? ((Player420Impl) p).handle : null;
    }
}
