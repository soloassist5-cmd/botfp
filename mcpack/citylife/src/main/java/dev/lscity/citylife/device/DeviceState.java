package dev.lscity.citylife.device;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * Состояние конкретного гаджета: вставленная SIM, доставленные приложения,
 * обои и заметки.
 *
 * Всё это живёт в NBT самого предмета (или блока компьютера), а не в данных
 * игрока: игра на одном телефоне не появляется на другом, а телефон с SIM
 * можно передать другу вместе с номером.
 */
public final class DeviceState {

    public static final String SIM = "Sim";
    public static final String APPS = "Apps";
    public static final String WALLPAPER = "Wallpaper";
    public static final String NOTES = "Notes";

    private DeviceState() {
    }

    /** Номер вставленной SIM или 0, если слот пуст. */
    public static int sim(CompoundTag tag) {
        return tag == null ? 0 : tag.getInt(SIM);
    }

    public static void setSim(CompoundTag tag, int number) {
        if (number <= 0) {
            tag.remove(SIM);
        } else {
            tag.putInt(SIM, number);
        }
    }

    public static List<String> installed(CompoundTag tag) {
        List<String> out = new ArrayList<>();
        if (tag == null) {
            return out;
        }
        ListTag list = tag.getList(APPS, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            out.add(list.getString(i));
        }
        return out;
    }

    /** Значки рабочего стола: прошивка, потом доставленное из магазина. */
    public static List<String> apps(DeviceModel model, CompoundTag tag) {
        List<String> out = new ArrayList<>(model.apps());
        for (String app : installed(tag)) {
            if (!out.contains(app)) {
                // Настройки держим последними: так их проще найти.
                int at = out.indexOf("settings");
                out.add(at < 0 ? out.size() : at, app);
            }
        }
        return out;
    }

    public static boolean install(DeviceModel model, CompoundTag tag, String app) {
        List<String> have = installed(tag);
        if (!Devices.STORE_APPS.contains(app) || model.has(app) || have.contains(app)
                || have.size() >= model.storeSlots()) {
            return false;
        }
        ListTag list = tag.getList(APPS, Tag.TAG_STRING);
        list.add(StringTag.valueOf(app));
        tag.put(APPS, list);
        return true;
    }

    public static boolean remove(CompoundTag tag, String app) {
        ListTag list = tag.getList(APPS, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            if (list.getString(i).equals(app)) {
                list.remove(i);
                tag.put(APPS, list);
                return true;
            }
        }
        return false;
    }

    public static String wallpaper(CompoundTag tag) {
        return tag == null || !tag.contains(WALLPAPER) ? "sunset" : tag.getString(WALLPAPER);
    }

    public static String notes(CompoundTag tag) {
        return tag == null ? "" : tag.getString(NOTES);
    }
}
