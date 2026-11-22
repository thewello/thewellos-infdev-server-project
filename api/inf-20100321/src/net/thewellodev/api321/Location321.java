package net.thewellodev.api321;

/** A position and look direction in the world. Y is the feet level. */
public class Location321 implements Cloneable {

    private World321 world;
    private double x, y, z;
    private float yaw, pitch;

    public Location321(World321 world, double x, double y, double z) {
        this(world, x, y, z, 0F, 0F);
    }

    public Location321(World321 world, double x, double y, double z, float yaw, float pitch) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public World321 getWorld() {
        return world;
    }

    public void setWorld(World321 w) {
        this.world = w;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public void setX(double v) {
        x = v;
    }

    public void setY(double v) {
        y = v;
    }

    public void setZ(double v) {
        z = v;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setYaw(float v) {
        yaw = v;
    }

    public void setPitch(float v) {
        pitch = v;
    }

    public int getBlockX() {
        return floor(x);
    }

    public int getBlockY() {
        return floor(y);
    }

    public int getBlockZ() {
        return floor(z);
    }

    /** The block at this location. */
    public Block321 getBlock() {
        return world.getBlockAt(getBlockX(), getBlockY(), getBlockZ());
    }

    public Location321 add(double dx, double dy, double dz) {
        x += dx;
        y += dy;
        z += dz;
        return this;
    }

    public double distanceSquared(Location321 o) {
        double dx = x - o.x, dy = y - o.y, dz = z - o.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public double distance(Location321 o) {
        return Math.sqrt(distanceSquared(o));
    }

    public static int floor(double d) {
        int i = (int) d;
        return d < i ? i - 1 : i;
    }

    @Override
    public Location321 clone() {
        return new Location321(world, x, y, z, yaw, pitch);
    }

    @Override
    public String toString() {
        return String.format("Location321{x=%.2f,y=%.2f,z=%.2f,yaw=%.1f,pitch=%.1f}", x, y, z, yaw, pitch);
    }
}
