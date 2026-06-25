package com.openclaw.wargame.core.terrain;

import com.openclaw.wargame.core.coord.Position;

import java.util.Objects;

/**
 * 战场地图：记录每个坐标的地形。坐标原点 (0,0) 在地图左下角。
 * <p>
 * 用稀疏表示（数组 + 边界），对中小规模地图（<=10km x 10km）足够。
 */
public final class TerrainMap {
    private final double widthMeters;
    private final double heightMeters;
    private final Terrain[][] grid;
    private final int rows;
    private final int cols;
    private final double cellSize;

    public TerrainMap(double widthMeters, double heightMeters, double cellSize) {
        if (widthMeters <= 0 || heightMeters <= 0 || cellSize <= 0) {
            throw new IllegalArgumentException("dimensions must be positive");
        }
        this.widthMeters = widthMeters;
        this.heightMeters = heightMeters;
        this.cellSize = cellSize;
        this.cols = (int) Math.ceil(widthMeters / cellSize);
        this.rows = (int) Math.ceil(heightMeters / cellSize);
        this.grid = new Terrain[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                grid[r][c] = Terrain.PLAIN;
            }
        }
    }

    public double widthMeters() {
        return widthMeters;
    }

    public double heightMeters() {
        return heightMeters;
    }

    public double cellSize() {
        return cellSize;
    }

    public int rows() {
        return rows;
    }

    public int cols() {
        return cols;
    }

    public void setTerrain(Position p, Terrain terrain) {
        Objects.requireNonNull(terrain, "terrain");
        int[] idx = toIndex(p);
        grid[idx[0]][idx[1]] = terrain;
    }

    public Terrain terrainAt(Position p) {
        int[] idx = toIndex(p);
        return grid[idx[0]][idx[1]];
    }

    /**
     * 在两个位置之间采样 N 段地形，返回最差机动系数。
     */
    public double worstMovementFactor(Position from, Position to, int samples) {
        double worst = Double.MAX_VALUE;
        for (int i = 0; i <= samples; i++) {
            double t = (double) i / samples;
            double x = from.x() + (to.x() - from.x()) * t;
            double y = from.y() + (to.y() - from.y()) * t;
            Terrain t1 = terrainAt(new Position(x, y).clamp(widthMeters, heightMeters));
            worst = Math.min(worst, t1.movementFactor());
        }
        return worst;
    }

    private int[] toIndex(Position p) {
        Position clamped = p.clamp(widthMeters, heightMeters);
        int c = (int) (clamped.x() / cellSize);
        int r = (int) (clamped.y() / cellSize);
        if (r < 0) r = 0;
        if (r >= rows) r = rows - 1;
        if (c < 0) c = 0;
        if (c >= cols) c = cols - 1;
        return new int[]{r, c};
    }
}
