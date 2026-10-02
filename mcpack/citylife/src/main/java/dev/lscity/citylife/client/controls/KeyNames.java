package dev.lscity.citylife.client.controls;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.resources.language.I18n;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * Понятные подписи для справочника управления.
 *
 * Названия действий у модов длинные и разношёрстные («Ability 1»,
 * «key.tacz.reload.desc», «Open Curios Inventory»), а на клавишу влезает
 * пара слов. Поэтому у известных действий есть короткая подпись
 * (citylife.keyname.<имя>), а действия собраны в понятные группы с
 * постоянным цветом: «Костюм», «Оружие», «Транспорт», «Голосовой чат»…
 * Всё остальное подписывается названием из мода без лишних «Open».
 */
@OnlyIn(Dist.CLIENT)
public final class KeyNames {

    /** Группа: id для подписи (citylife.keygroup.<id>), цвет, признаки категорий. */
    public record Group(String id, int colour, List<String> match) {
    }

    public static final List<Group> GROUPS = List.of(
            new Group("move", 0xFF4F7CAC, List.of("key.categories.movement")),
            new Group("suit", 0xFFC9473F, List.of("palladium", "satsu", "curios", "corewithstuff")),
            new Group("weapon", 0xFFD08A2E, List.of("tacz")),
            new Group("transport", 0xFF2F9C95, List.of("vehicle", "citylife")),
            new Group("voice", 0xFF3A8FD6, List.of("voicechat")),
            new Group("map", 0xFF4CAF7A, List.of("xaero", "minimap", "worldmap")),
            new Group("drone", 0xFF9B59B6, List.of("fpvdrone", "diligentstalker")),
            new Group("action", 0xFF6A9A5B, List.of("key.categories.gameplay")),
            new Group("inventory", 0xFFA68A4E, List.of("key.categories.inventory", "key.categories.creative")),
            new Group("chat", 0xFF8A6BB0, List.of("key.categories.multiplayer")),
            new Group("recipes", 0xFF5C8F9E, List.of("jei", "jade")),
            new Group("interface", 0xFF6B7280, List.of("key.categories.misc", "key.categories.ui")));

    private static final Group OTHER = new Group("other", 0xFF5E6573, List.of());

    private KeyNames() {
    }

    public static Group group(KeyMapping km) {
        if ("key.citylife.controls".equals(km.getName())) {
            return GROUPS.get(GROUPS.size() - 1);
        }
        String cat = km.getCategory().toLowerCase(java.util.Locale.ROOT);
        String name = km.getName().toLowerCase(java.util.Locale.ROOT);
        for (Group g : GROUPS) {
            for (String m : g.match()) {
                if (cat.equals(m) || !m.startsWith("key.categories") && (cat.contains(m) || name.contains(m))) {
                    return g;
                }
            }
        }
        return OTHER;
    }

    public static String groupTitle(Group g) {
        return I18n.get("citylife.keygroup." + g.id());
    }

    /** Короткая подпись для клавиши. */
    public static String shortName(KeyMapping km) {
        String key = "citylife.keyname." + km.getName();
        if (I18n.exists(key)) {
            return I18n.get(key);
        }
        String full = I18n.get(km.getName());
        // Обычный мусор в названиях модов: «Open …», «(hold)», «Key: …».
        full = full.replaceAll("\\s*\\([^)]*\\)", "").replaceAll("^(Open|Открыть|Toggle|Key:)\\s+", "").trim();
        return full.isEmpty() ? km.getName() : full;
    }

    /** Полное название действия из мода. */
    public static String fullName(KeyMapping km) {
        return I18n.get(km.getName());
    }

    /** Главные клавиши сборки — для панели «Главное» в справочнике. */
    public static final List<String> ESSENTIALS = List.of(
            "key.citylife.controls", "key.palladium.ability_1", "key.curios.open.desc", "key.tacz.reload.desc",
            "gui.xaero_open_map", "key.push_to_talk", "key.citylife.vehicle", "key.inventory", "key.chat",
            "key.vehicle.horn", "key.fpvdrone.arm_motors");
}
