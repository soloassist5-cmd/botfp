package dev.lscity.citylife.client;

import dev.lscity.citylife.data.Waypoint;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Активный маршрут на клиенте: одна цель, живёт между открытиями телефона. */
@OnlyIn(Dist.CLIENT)
public final class NavClient {

    private static Waypoint target;

    private NavClient() {
    }

    public static Waypoint target() {
        return target;
    }

    public static void set(Waypoint point) {
        target = point;
    }

    public static void clear() {
        target = null;
    }
}
