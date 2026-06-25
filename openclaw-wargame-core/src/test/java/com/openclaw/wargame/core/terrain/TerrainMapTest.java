package com.openclaw.wargame.core.terrain;

import com.openclaw.wargame.core.coord.Position;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerrainMapTest {

    @Test
    void defaultIsPlain() {
        TerrainMap map = new TerrainMap(1000, 1000, 100);
        assertEquals(Terrain.PLAIN, map.terrainAt(new Position(500, 500)));
    }

    @Test
    void setAndGet() {
        TerrainMap map = new TerrainMap(1000, 1000, 100);
        map.setTerrain(new Position(500, 500), Terrain.FOREST);
        assertEquals(Terrain.FOREST, map.terrainAt(new Position(505, 505)));
    }

    @Test
    void worstMovementFactorWithForest() {
        TerrainMap map = new TerrainMap(1000, 1000, 100);
        for (double x = 0; x < 500; x += 50) {
            map.setTerrain(new Position(x, 500), Terrain.FOREST);
        }
        double factor = map.worstMovementFactor(new Position(0, 500), new Position(1000, 500), 10);
        // worst case should be FOREST (0.60)
        assertEquals(0.60, factor, 1e-9);
    }

    @Test
    void outOfBoundsClamps() {
        TerrainMap map = new TerrainMap(1000, 1000, 100);
        // 查询越界点，不抛异常
        assertDoesNotThrow(() -> map.terrainAt(new Position(-100, -100)));
        assertDoesNotThrow(() -> map.terrainAt(new Position(9999, 9999)));
    }
}
