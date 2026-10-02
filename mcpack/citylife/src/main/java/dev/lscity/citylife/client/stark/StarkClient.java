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
