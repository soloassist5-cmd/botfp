package dev.lscity.citylife.phone;

import java.util.UUID;

/**
 * Голос для звонков: приватная группа Simple Voice Chat на двоих.
 *
 * Здесь нет ни одного типа из API голосового мода — они живут в VoiceImpl,
 * который создаёт только плагин (а плагин загружает только сам голосовой
 * мод). Без мода impl остаётся null и звонки идут без голоса.
 */
public final class Voice {

    /** Реализация поверх API голосового мода. */
    interface Impl {
        boolean join(UUID a, UUID b, String name);

        void leave(UUID player);
    }

    private static Impl impl;

    private Voice() {
    }

    static void connect(Impl implementation) {
        impl = implementation;
    }

    public static boolean available() {
        return impl != null;
    }

    /** Собрать разговор на двоих. false — у кого-то нет голосового мода. */
    public static boolean join(UUID a, UUID b, String name) {
        return impl != null && impl.join(a, b, name);
    }

    public static void leave(UUID player) {
        if (impl != null) {
            impl.leave(player);
        }
    }
}
