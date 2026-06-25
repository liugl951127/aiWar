package com.openclaw.wargame.core.coord;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PositionTest {

    @Test
    void distanceTo() {
        Position a = new Position(0, 0);
        Position b = new Position(3, 4);
        assertEquals(5.0, a.distanceTo(b), 1e-9);
    }

    @Test
    void moveTowards() {
        Position a = new Position(0, 0);
        Position b = new Position(10, 0);
        Position mid = a.moveTowards(b, 5);
        assertEquals(5.0, mid.x(), 1e-9);
        assertEquals(0.0, mid.y(), 1e-9);
    }

    @Test
    void moveTowardsOverflowClamp() {
        Position a = new Position(0, 0);
        Position b = new Position(10, 0);
        Position end = a.moveTowards(b, 100);
        assertEquals(10.0, end.x(), 1e-9);
    }

    @Test
    void moveTowardsNegative() {
        Position a = new Position(0, 0);
        Position b = new Position(10, 0);
        Position back = a.moveTowards(b, -5);
        assertEquals(-5.0, back.x(), 1e-9);
    }

    @Test
    void bearingTo() {
        Position a = new Position(0, 0);
        Position b = new Position(1, 1);
        assertEquals(Math.PI / 4, a.bearingTo(b), 1e-9);
    }

    @Test
    void clamp() {
        Position p = new Position(-10, 9000);
        Position c = p.clamp(8000, 8000);
        assertEquals(0, c.x(), 1e-9);
        assertEquals(8000, c.y(), 1e-9);
    }

    @Test
    void equalsAndHashCode() {
        Position a = new Position(1.0, 2.0);
        Position b = new Position(1.0, 2.0);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
