package net.thewellodev.api.util;

/** A point or a direction in the world, with the usual vector arithmetic. */
public class Vector implements Cloneable {

    private double x, y, z;

    public Vector() {
    }

    public Vector(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
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

    public int getBlockX() {
        return (int) Math.floor(x);
    }

    public int getBlockY() {
        return (int) Math.floor(y);
    }

    public int getBlockZ() {
        return (int) Math.floor(z);
    }

    public Vector setX(double x) {
        this.x = x;
        return this;
    }

    public Vector setY(double y) {
        this.y = y;
        return this;
    }

    public Vector setZ(double z) {
        this.z = z;
        return this;
    }

    public Vector add(Vector o) {
        x += o.x;
        y += o.y;
        z += o.z;
        return this;
    }

    public Vector subtract(Vector o) {
        x -= o.x;
        y -= o.y;
        z -= o.z;
        return this;
    }

    public Vector multiply(double m) {
        x *= m;
        y *= m;
        z *= m;
        return this;
    }

    public Vector zero() {
        x = 0;
        y = 0;
        z = 0;
        return this;
    }

    public double length() {
        return Math.sqrt(lengthSquared());
    }

    public double lengthSquared() {
        return x * x + y * y + z * z;
    }

    public double distance(Vector o) {
        return Math.sqrt(distanceSquared(o));
    }

    public double distanceSquared(Vector o) {
        double dx = x - o.x, dy = y - o.y, dz = z - o.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public double dot(Vector o) {
        return x * o.x + y * o.y + z * o.z;
    }

    public Vector crossProduct(Vector o) {
        double nx = y * o.z - o.y * z, ny = z * o.x - o.z * x, nz = x * o.y - o.x * y;
        x = nx;
        y = ny;
        z = nz;
        return this;
    }

    /** Makes the length 1 (a vector of length 0 stays as it is). */
    public Vector normalize() {
        double len = length();
        if (len > 0) {
            x /= len;
            y /= len;
            z /= len;
        }
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Vector)) {
            return false;
        }
        Vector v = (Vector) o;
        return Double.compare(x, v.x) == 0 && Double.compare(y, v.y) == 0 && Double.compare(z, v.z) == 0;
    }

    @Override
    public int hashCode() {
        long h = Double.doubleToLongBits(x);
        h = h * 31 + Double.doubleToLongBits(y);
        h = h * 31 + Double.doubleToLongBits(z);
        return (int) (h ^ (h >>> 32));
    }

    @Override
    public Vector clone() {
        return new Vector(x, y, z);
    }

    @Override
    public String toString() {
        return x + "," + y + "," + z;
    }
}
