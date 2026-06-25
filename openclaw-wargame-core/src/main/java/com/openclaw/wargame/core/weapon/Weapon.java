package com.openclaw.wargame.core.weapon;

import java.util.Objects;
import java.util.UUID;

/**
 * 武器载荷。每个单位可以挂载多个 Weapon 实例。
 */
public final class Weapon {
    private final String id;
    private final WeaponType type;
    /** 当前弹药数 */
    private int ammo;
    /** 弹药基数（满载） */
    private final int maxAmmo;
    /** 是否已发射但还在飞行（用于计算抵达时间） */
    private boolean inFlight;

    public Weapon(WeaponType type, int maxAmmo) {
        this.id = UUID.randomUUID().toString();
        this.type = Objects.requireNonNull(type);
        this.maxAmmo = maxAmmo;
        this.ammo = maxAmmo;
    }

    public String id() {
        return id;
    }

    public WeaponType type() {
        return type;
    }

    public int ammo() {
        return ammo;
    }

    public int maxAmmo() {
        return maxAmmo;
    }

    public boolean inFlight() {
        return inFlight;
    }

    public boolean canFire() {
        return ammo > 0 && !inFlight;
    }

    public boolean fire() {
        if (!canFire()) return false;
        ammo--;
        inFlight = true;
        return true;
    }

    public void onHit() {
        inFlight = false;
    }

    public void reload() {
        ammo = maxAmmo;
        inFlight = false;
    }

    /** 按比例补充弹药（用于补给） */
    public void resupply(int amount) {
        ammo = Math.min(maxAmmo, ammo + amount);
    }
}
