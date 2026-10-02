package dev.lscity.citylife.client.stark;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/** Что клиент знает об охране башни STARK: для мониторов, лучей и пульта. */
@OnlyIn(Dist.CLIENT)
public final class StarkClient {

    public static boolean armed = true;
    public static boolean alarm;
    public static String by = "";
    public static int intruders;
    public static int cleared;
    /** Журнал: «ЧЧ:ММ текст», свежие сверху. */
    public static final List<String> LOG = new ArrayList<>();
    public static final List<Boolean> LOG_ALARM = new ArrayList<>();

    private static List<String> suits;
    private static List<Suit> items;

    /** Костюм для приложения «Джарвис»: id предмета, подпись, иконка. */
    public record Suit(String id, String name, ItemStack stack) {
    }

    /** Все марки Satsu по порядку подписи. */
    public static List<Suit> suitItems() {
        if (items == null) {
            List<Suit> out = new ArrayList<>();
            for (Item item : ForgeRegistries.ITEMS.getValues()) {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
                if (dev.lscity.citylife.stark.StarkSuits.isSuit(id)) {
                    ItemStack stack = new ItemStack(item);
                    out.add(new Suit(id.toString(), shortName(stack), stack));
                }
            }
            out.sort(java.util.Comparator.comparing(s -> sortKey(s.name())));
            items = out;
        }
        return items;
    }

    public static String suitName(String id) {
        for (Suit s : suitItems()) {
            if (s.id().equals(id)) {
                return s.name();
            }
        }
        return id;
    }

    /** «Armor bracelet (Mark 42 Prodigal Son)» → «Mark 42 Prodigal Son». */
    private static String shortName(ItemStack stack) {
        String name = stack.getHoverName().getString();
        int open = name.indexOf('(');
        int close = name.lastIndexOf(')');
        if (open < 0 || close <= open) {
            return name;
        }
        String inner = name.substring(open + 1, close);
        String kind = name.substring(0, open).trim();
        // «Браслет», «кейс», «нано-модуль» — одно и то же для игрока, а «Чёрный костюм» — отдельная вещь.
        String low = kind.toLowerCase(java.util.Locale.ROOT);
        boolean generic = low.contains("bracelet") || low.contains("briefcase") || low.contains("modifier")
                || low.contains("reactor") || low.contains("chest") || low.contains("necklace")
                || low.contains("браслет") || low.contains("кейс") || low.contains("модуль");
        return generic || kind.isEmpty() ? inner : inner + " · " + kind;
    }

    /** Mark 7 раньше Mark 10: числа сравниваем как числа. */
    private static String sortKey(String name) {
        var m = java.util.regex.Pattern.compile("(\\d+)").matcher(name);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (m.find()) {
            out.append(name, last, m.start()).append(String.format("%06d", Integer.parseInt(m.group(1))));
            last = m.end();
        }
        return out.append(name.substring(last)).toString().toLowerCase(java.util.Locale.ROOT);
    }

    private StarkClient() {
    }

    public static void update(CompoundTag tag) {
        armed = tag.getBoolean("armed");
        alarm = tag.getBoolean("alarm");
        by = tag.getString("by");
        intruders = tag.getInt("intruders");
        cleared = tag.getInt("cleared");
        LOG.clear();
        LOG_ALARM.clear();
        ListTag log = tag.getList("log", Tag.TAG_COMPOUND);
        for (Tag t : log) {
            CompoundTag line = (CompoundTag) t;
            LOG.add(line.getString("t") + "  " + line.getString("s"));
            LOG_ALARM.add(line.getBoolean("a"));
        }
    }

    /** Имена костюмов Железного человека из реестра предметов: список для монитора Зала брони. */
    public static List<String> suits() {
        if (suits == null) {
            List<String> out = new ArrayList<>();
            for (Item item : ForgeRegistries.ITEMS.getValues()) {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
                if (id == null || !"satsu_iron_man_addon".equals(id.getNamespace())) {
                    continue;
                }
                String path = id.getPath();
                if (path.matches("(war_machine/|iron_heart/)?marks/.+/(bracelet|briefcase|main|.*_black_suit)")) {
                    String name = new ItemStack(item).getHoverName().getString();
                    int open = name.indexOf('(');
                    int close = name.lastIndexOf(')');
                    out.add((open >= 0 && close > open ? name.substring(open + 1, close) : name)
                            .toUpperCase(java.util.Locale.ROOT));
                }
            }
            java.util.Collections.sort(out);
            suits = out;
        }
        return suits;
    }
}
