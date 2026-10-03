package dev.lscity.citylife.drone;

/**
 * Модели дронов. Скорости — блоков за тик (1 блок/тик = 20 м/с), батарея —
 * секунд полёта, дальность — блоков от пилота до дрона.
 */
public enum DroneType {
    /** «Сокол»: дрон-камера. Плавный, висит на месте, долго летает, далеко видит. */
    CAMERA("camera", 0.45F, 0.12F, 0.30F, 720, 224, 8.0F, 1.25F),
    /** «Стриж»: гоночный FPV. Инерция, крен в поворотах, форсаж до 100 км/ч. */
    RACER("racer", 1.10F, 0.0F, 0.32F, 300, 176, 6.0F, 1.0F),
    /** «Пеликан»: курьер. Медленный, но поднимает посылку или любой предмет. */
    COURIER("courier", 0.36F, 0.08F, 0.22F, 900, 256, 14.0F, 1.6F);

    public final String id;
    /** Предельная горизонтальная скорость (у «Стрижа» — без форсажа). */
    public final float maxSpeed;
    /** Сглаживание управления: доля разницы скорости за тик (0 — своя модель). */
    public final float smooth;
    public final float climb;
    public final int batterySeconds;
    public final int range;
    public final float maxHealth;
    /** Масштаб модели. */
    public final float scale;

    DroneType(String id, float maxSpeed, float smooth, float climb, int batterySeconds, int range, float maxHealth,
              float scale) {
        this.id = id;
        this.maxSpeed = maxSpeed;
        this.smooth = smooth;
        this.climb = climb;
        this.batterySeconds = batterySeconds;
        this.range = range;
        this.maxHealth = maxHealth;
        this.scale = scale;
    }

    public static DroneType byId(String id) {
        for (DroneType t : values()) {
            if (t.id.equals(id)) {
                return t;
            }
        }
        return CAMERA;
    }

    /** Расход заряда за тик полёта (0…1). */
    public float drainPerTick() {
        return 1.0F / (batterySeconds * 20.0F);
    }
}
