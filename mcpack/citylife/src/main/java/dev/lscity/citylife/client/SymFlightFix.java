package dev.lscity.citylife.client;

import dev.lscity.citylife.CityLife;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;

/**
 * Полёт в костюмах Sym's Armored Industries: каждое нажатие A или D в полёте
 * мод превращает в «бочку» — костюм кувыркается вбок, а шагнуть влево-вправо
 * нельзя. На бегу-полёте это выглядит так, будто игрока швыряет из стороны в
 * сторону. Мод запоминает, что клавиша была отпущена (поля aDown/dDown), и
 * на следующем нажатии крутит бочку; мы каждый тик, до его обработчика,
 * сбрасываем эти пометки — бочки нет, A/D снова двигают вбок.
 */
@Mod.EventBusSubscriber(modid = CityLife.MOD_ID, value = Dist.CLIENT)
public final class SymFlightFix {

    private static Field aDown;
    private static Field dDown;
    private static boolean missing;

    private SymFlightFix() {
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || missing) {
            return;
        }
        try {
            if (aDown == null) {
                Class<?> mod = Class.forName("com.symbiotespidey.sym_industries.forge.ClientMod");
                aDown = mod.getDeclaredField("aDown");
                dDown = mod.getDeclaredField("dDown");
                aDown.setAccessible(true);
                dDown.setAccessible(true);
            }
            aDown.setBoolean(null, false);
            dDown.setBoolean(null, false);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            missing = true;   // мода нет или он другой версии — трогать нечего
        }
    }
}
