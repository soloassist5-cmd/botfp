package dev.lscity.citylife.client.controls;

import com.mojang.blaze3d.platform.InputConstants;
import dev.lscity.citylife.CityLife;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Раскладка управления сборки: чтобы моды не мешали друг другу.
 *
 * В сборке за одни и те же клавиши дерутся десяток модов: на V по
 * умолчанию висели и колесо брони Palladium, и меню голосового чата, и
 * удар прикладом TaCZ, и обзор дрона; на M — карта Xaero, микрофон и
 * способность костюма; «/» — и команды, и снаряжение Palladium. Здесь
 * две ступени:
 *
 * 1. LAYOUT — продуманная раскладка для важных модов: частые действия на
 *    свободных буквах, редкие (меню голосового чата, настройки миникарты) —
 *    на Alt+буква, клавиши дронов — на блоке Home/End/PgUp, у ванильных
 *    «панелей быстрого доступа» (только для творческого) клавиши сняты.
 * 2. resolve() — всё, что после этого ещё совпадает (модов семьдесят, всех
 *    не предусмотреть), разводится само: позже зарегистрированное действие
 *    переезжает на Alt+ту же клавишу, потом на Ctrl+, и только если всё
 *    занято — остаётся без клавиши. Кнопки мыши не трогаем: выстрел TaCZ
 *    на ЛКМ и прицел на ПКМ совпадают с ванильными нарочно.
 *
 * Применяется один раз на версию раскладки (config/citylife-controls.txt);
 * клавиши, которые игрок уже поменял сам, не трогаются. Вернуть раскладку
 * сборки целиком — кнопка в справочнике управления (F10).
 */
@OnlyIn(Dist.CLIENT)
public final class Controls {

    /** Новая версия — раскладка применится ещё раз у всех. */
    public static final int VERSION = 1;

    /** Действие → клавиша («alt+key.keyboard.v», «none» — снять). */
    public static final Map<String, String> LAYOUT = new LinkedHashMap<>();

    static {
        // Ванильные «сохранить/загрузить панель» нужны только в творческом, а C и X нужны модам.
        put("key.saveToolbarActivator", "none");
        put("key.loadToolbarActivator", "none");
        // Костюм Железного человека (Palladium): способности по порядку панели.
        put("key.palladium.ability_1", "key.keyboard.v");
        put("key.palladium.ability_2", "key.keyboard.b");
        put("key.palladium.ability_3", "key.keyboard.n");
        put("key.palladium.ability_4", "key.keyboard.period");
        put("key.palladium.ability_5", "key.keyboard.comma");
        put("key.palladium.switch_ability_list", "key.keyboard.x");
        put("key.palladium.open_equipment", "alt+key.keyboard.i");
        // Слоты Curios (туда же надевается костюм) — I, как инвентарь.
        put("key.curios.open.desc", "key.keyboard.i");
        // Оружие TaCZ.
        put("key.tacz.reload.desc", "key.keyboard.r");
        put("key.tacz.fire_select.desc", "key.keyboard.g");
        put("key.tacz.inspect.desc", "key.keyboard.y");
        put("key.tacz.interact.desc", "key.keyboard.o");
        put("key.tacz.melee.desc", "key.keyboard.z");
        put("key.tacz.refit.desc", "alt+key.keyboard.z");
        put("key.tacz.crawl.desc", "key.keyboard.c");
        put("key.tacz.zoom.desc", "key.keyboard.j");
        put("key.tacz.open_config.desc", "alt+key.keyboard.t");
        // Машины.
        put("key.vehicle.horn", "key.keyboard.h");
        put("key.vehicle.cycle_seats", "alt+key.keyboard.c");
        put("key.citylife.vehicle", "key.keyboard.k");
        // Голосовой чат: говорить — Caps Lock, остальное на Alt.
        put("key.push_to_talk", "key.keyboard.caps.lock");
        put("key.voice_chat", "alt+key.keyboard.v");
        put("key.voice_chat_group", "alt+key.keyboard.g");
        put("key.mute_microphone", "alt+key.keyboard.m");
        put("key.disable_voice_chat", "alt+key.keyboard.n");
        put("key.hide_icons", "alt+key.keyboard.h");
        // Карты Xaero: M — карта мира, настройки миникарты — Alt+Y.
        put("gui.xaero_open_map", "key.keyboard.m");
        put("gui.xaero_minimap_settings", "alt+key.keyboard.y");
        // FPV-дрон: блок клавиш над стрелками, взвести моторы — Enter.
        put("key.fpvdrone.arm_motors", "key.keyboard.enter");
        put("key.fpvdrone.thermal_toggle", "key.keyboard.end");
        put("key.fpvdrone.thermal_focus_mode", "key.keyboard.home");
        put("key.fpvdrone.thermal_nuc", "key.keyboard.insert");
        put("key.fpvdrone.thermal_agc_mode", "key.keyboard.page.up");
        put("key.fpvdrone.thermal_cycle_palette", "key.keyboard.page.down");
        put("key.fpvdrone.cycle_resolution", "key.keyboard.f7");
        // Дрон-камера Diligent Stalker.
        put("key.diligentstalker.control.desc", "alt+key.keyboard.x");
        put("key.diligentstalker.view.desc", "alt+key.keyboard.b");
        put("key.diligentstalker.disconnect.desc", "key.keyboard.delete");
        // Служебное прочих модов — с дороги.
        put("key.tctcore.tc_tcore_key", "alt+key.keyboard.o");
        put("key.corpse.death_history", "alt+key.keyboard.u");
        // Наш справочник управления.
        put("key.citylife.controls", "key.keyboard.f10");
    }

    private static void put(String action, String key) {
        LAYOUT.put(action, key);
    }

    private static boolean checked;

    private Controls() {
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("citylife-controls.txt");
    }

    private static int appliedVersion() {
        try {
            return Integer.parseInt(Files.readString(file(), StandardCharsets.UTF_8).trim());
        } catch (IOException | NumberFormatException e) {
            return 0;
        }
    }

    /** Один раз при запуске: применить новую версию раскладки. */
    public static void onStart(Minecraft mc) {
        if (checked || mc.options == null) {
            return;
        }
        checked = true;
        if (appliedVersion() >= VERSION) {
            return;
        }
        int changed = apply(mc, false);
        CityLife.LOG.info("City Life: раскладка управления v{} — изменено клавиш: {}", VERSION, changed);
        try {
            Files.writeString(file(), String.valueOf(VERSION), StandardCharsets.UTF_8);
        } catch (IOException e) {
            CityLife.LOG.warn("City Life: не записать {}", file(), e);
        }
    }

    /**
     * Применить раскладку; force — и к клавишам, которые игрок поменял сам
     * (кнопка «Раскладка сборки» в справочнике). Возвращает, сколько изменено.
     */
    public static int apply(Minecraft mc, boolean force) {
        int changed = 0;
        for (KeyMapping km : mc.options.keyMappings) {
            String want = LAYOUT.get(km.getName());
            if (want == null || !force && !km.isDefault()) {
                continue;
            }
            if (set(km, want)) {
                changed++;
            }
        }
        changed += resolve(mc);
        KeyMapping.resetMapping();
        mc.options.save();
        return changed;
    }

    private static boolean set(KeyMapping km, String spec) {
        KeyModifier mod = KeyModifier.NONE;
        String key = spec;
        if (spec.startsWith("alt+")) {
            mod = KeyModifier.ALT;
            key = spec.substring(4);
        } else if (spec.startsWith("ctrl+")) {
            mod = KeyModifier.CONTROL;
            key = spec.substring(5);
        } else if (spec.startsWith("shift+")) {
            mod = KeyModifier.SHIFT;
            key = spec.substring(6);
        }
        InputConstants.Key code = "none".equals(key) ? InputConstants.UNKNOWN : InputConstants.getKey(key);
        if (code.equals(km.getKey()) && mod == km.getKeyModifier()) {
            return false;
        }
        km.setKeyModifierAndCode(mod, code);
        return true;
    }

    /** Два действия срабатывают от одного нажатия в одной обстановке. */
    public static boolean clash(KeyMapping a, KeyMapping b) {
        if (a == b || a.isUnbound() || b.isUnbound()) {
            return false;
        }
        return a.getKey().equals(b.getKey()) && a.getKeyModifier() == b.getKeyModifier()
                && a.getKeyConflictContext().conflicts(b.getKeyConflictContext())
                && b.getKeyConflictContext().conflicts(a.getKeyConflictContext());
    }

    /** Все пары конфликтующих действий (для справочника). */
    public static List<KeyMapping[]> clashes(Minecraft mc) {
        List<KeyMapping[]> out = new ArrayList<>();
        KeyMapping[] all = mc.options.keyMappings;
        for (int i = 0; i < all.length; i++) {
            for (int j = i + 1; j < all.length; j++) {
                if (clash(all[i], all[j])) {
                    out.add(new KeyMapping[]{all[i], all[j]});
                }
            }
        }
        return out;
    }

    /** Действие из раскладки сборки или ванильное — его не двигаем. */
    private static boolean anchored(KeyMapping km) {
        return LAYOUT.containsKey(km.getName()) || vanilla(km);
    }

    /** Ванильные действия Minecraft. */
    public static final java.util.Set<String> VANILLA = new java.util.HashSet<>(List.of(
            "key.attack", "key.use", "key.forward", "key.left", "key.back", "key.right", "key.jump",
            "key.sneak", "key.sprint", "key.drop", "key.inventory", "key.chat", "key.playerlist",
            "key.pickItem", "key.command", "key.socialInteractions", "key.screenshot",
            "key.togglePerspective", "key.smoothCamera", "key.fullscreen", "key.spectatorOutlines",
            "key.swapOffhand", "key.saveToolbarActivator", "key.loadToolbarActivator", "key.advancements",
            "key.hotbar.1", "key.hotbar.2", "key.hotbar.3", "key.hotbar.4", "key.hotbar.5", "key.hotbar.6",
            "key.hotbar.7", "key.hotbar.8", "key.hotbar.9"));

    public static boolean vanilla(KeyMapping km) {
        return VANILLA.contains(km.getName());
    }

    /** Развести оставшиеся конфликты клавиатуры: Alt+, затем Ctrl+, иначе снять. */
    private static int resolve(Minecraft mc) {
        int moved = 0;
        KeyMapping[] all = mc.options.keyMappings;
        for (KeyMapping km : all) {
            if (km.isUnbound() || km.getKey().getType() != InputConstants.Type.KEYSYM || anchored(km)) {
                continue;
            }
            KeyMapping other = null;
            for (KeyMapping o : all) {
                if (clash(km, o) && (anchored(o) || indexOf(all, o) < indexOf(all, km))) {
                    other = o;
                    break;
                }
            }
            if (other == null) {
                continue;
            }
            InputConstants.Key key = km.getKey();
            boolean done = false;
            for (KeyModifier mod : new KeyModifier[]{KeyModifier.ALT, KeyModifier.CONTROL}) {
                km.setKeyModifierAndCode(mod, key);
                if (!taken(all, km)) {
                    done = true;
                    break;
                }
            }
            if (!done) {
                km.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.UNKNOWN);
            }
            CityLife.LOG.info("City Life: «{}» мешало «{}» — теперь {}", km.getName(), other.getName(),
                    km.getTranslatedKeyMessage().getString());
            moved++;
        }
        return moved;
    }

    private static boolean taken(KeyMapping[] all, KeyMapping km) {
        for (KeyMapping o : all) {
            if (clash(km, o)) {
                return true;
            }
        }
        return false;
    }

    private static int indexOf(KeyMapping[] all, KeyMapping km) {
        for (int i = 0; i < all.length; i++) {
            if (all[i] == km) {
                return i;
            }
        }
        return -1;
    }
}
