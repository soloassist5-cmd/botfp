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

    /** Костюм для приложения «Джарвис»: номер марки, подпись, иконка. */
    public record Suit(int mark, String name, ItemStack stack) {
    }

    /** Марки, которые Джарвис может прислать, по порядку номеров. */
    public static List<Suit> suitItems() {
        if (items == null) {
            List<Suit> out = new ArrayList<>();
            for (var m : dev.lscity.citylife.stark.StarkSuits.MARKS) {
                if (!m.playable()) {
                    continue;
                }
                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(m.icon()));
                if (item != null && item != net.minecraft.world.item.Items.AIR) {
                    out.add(new Suit(m.id(), m.title(), new ItemStack(item)));
                }
            }
            items = out;
        }
        return items;
    }

    public static String suitName(int mark) {
        var m = dev.lscity.citylife.stark.StarkSuits.mark(mark);
        return m == null ? "Mark " + mark : m.title();
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

    /** Марки Зала брони для монитора: «MARK 3», «MARK 33 · SILVER CENTURION»… */
    public static List<String> suits() {
        if (suits == null) {
            List<String> out = new ArrayList<>();
            for (var m : dev.lscity.citylife.stark.StarkSuits.MARKS) {
                out.add(m.title().toUpperCase(java.util.Locale.ROOT));
            }
            suits = out;
        }
        return suits;
    }
}
