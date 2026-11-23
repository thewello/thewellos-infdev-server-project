package net.thewellodev.api330;

/** A position and look direction in the world. Y is the feet level. */
public class Location330 implements Cloneable {

    private World330 world;
    private double x, y, z;
    private float yaw, pitch;

    public Location330(World330 world, double x, double y, double z) {
        this(world, x, y, z, 0F, 0F);
    }

    public Location330(World330 world, double x, double y, double z, float yaw, float pitch) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public World330 getWorld() {
        return world;
    }

    public void setWorld(World330 w) {
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
    public Block330 getBlock() {
        return world.getBlockAt(getBlockX(), getBlockY(), getBlockZ());
    }

    public Location330 add(double dx, double dy, double dz) {
        x += dx;
        y += dy;
        z += dz;
        return this;
    }

    public double distanceSquared(Location330 o) {
        double dx = x - o.x, dy = y - o.y, dz = z - o.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public double distance(Location330 o) {
        return Math.sqrt(distanceSquared(o));
    }

    public static int floor(double d) {
        int i = (int) d;
        return d < i ? i - 1 : i;
    }

    @Override
    public Location330 clone() {
        return new Location330(world, x, y, z, yaw, pitch);
    }

    @Override
    public String toString() {
        return String.format("Location330{x=%.2f,y=%.2f,z=%.2f,yaw=%.1f,pitch=%.1f}", x, y, z, yaw, pitch);
    }

    /** Adds the position of another location (the world and the look direction stay as they are). */
    public Location330 add(Location330 o) {
        x += o.x;
        y += o.y;
        z += o.z;
        return this;
    }

    public Location330 subtract(Location330 o) {
        x -= o.x;
        y -= o.y;
        z -= o.z;
        return this;
    }

    public Location330 subtract(double dx, double dy, double dz) {
        return add(-dx, -dy, -dz);
    }

    public Location330 multiply(double m) {
        x *= m;
        y *= m;
        z *= m;
        return this;
    }

    public Location330 zero() {
        x = 0;
        y = 0;
        z = 0;
        return this;
    }

    public double lengthSquared() {
        return x * x + y * y + z * z;
    }

    public double length() {
        return Math.sqrt(lengthSquared());
    }

    public net.thewellodev.api.util.Vector toVector() {
        return new net.thewellodev.api.util.Vector(x, y, z);
    }

    /** The direction that the yaw and the pitch point to (a vector of length 1). */
    public net.thewellodev.api.util.Vector getDirection() {
        double yawRad = Math.toRadians(yaw), pitchRad = Math.toRadians(pitch);
        double xz = Math.cos(pitchRad);
        return new net.thewellodev.api.util.Vector(-xz * Math.sin(yawRad), -Math.sin(pitchRad), xz * Math.cos(yawRad));
    }

    /** The block coordinate of a position. */
    public static int locToBlock(double d) {
        return floor(d);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Location330)) {
            return false;
        }
        Location330 l = (Location330) o;
        return world == l.world && Double.compare(x, l.x) == 0 && Double.compare(y, l.y) == 0 && Double.compare(z, l.z) == 0
                && Float.compare(yaw, l.yaw) == 0 && Float.compare(pitch, l.pitch) == 0;
    }

    @Override
    public int hashCode() {
        int h = 7;
        h = 19 * h + (world == null ? 0 : System.identityHashCode(world));
        h = 19 * h + (int) (Double.doubleToLongBits(x) ^ (Double.doubleToLongBits(x) >>> 32));
        h = 19 * h + (int) (Double.doubleToLongBits(y) ^ (Double.doubleToLongBits(y) >>> 32));
        h = 19 * h + (int) (Double.doubleToLongBits(z) ^ (Double.doubleToLongBits(z) >>> 32));
        h = 19 * h + Float.floatToIntBits(yaw);
        h = 19 * h + Float.floatToIntBits(pitch);
        return h;
    }
}
