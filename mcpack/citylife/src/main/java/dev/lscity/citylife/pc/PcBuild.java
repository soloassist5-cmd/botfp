package dev.lscity.citylife.pc;

import dev.lscity.citylife.pc.PcPart.Type;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Проверка сборки системного блока.
 *
 * Слоты корпуса идут в порядке SLOTS. Результат — список того, что мешает
 * запуску, плюс итоговые очки и потребление: всё это видно прямо в окне
 * корпуса, поэтому собирать можно без гадания.
 */
public final class PcBuild {

    /** Порядок слотов корпуса. */
    public static final Type[] SLOTS = {Type.BOARD, Type.CPU, Type.COOLER, Type.RAM, Type.RAM,
            Type.GPU, Type.PSU, Type.STORAGE};

    private PcBuild() {
    }

    public record Result(boolean works, List<Component> problems, int score, int draw,
                         int supply) {
    }

    public static PcPart part(ItemStack stack) {
        return stack.getItem() instanceof PcPartItem item ? item.part() : null;
    }

    public static Result check(List<ItemStack> slots) {
        List<Component> problems = new ArrayList<>();
        PcPart board = part(slots.get(0));
        PcPart cpu = part(slots.get(1));
        PcPart cooler = part(slots.get(2));
        PcPart gpu = part(slots.get(5));
        PcPart psu = part(slots.get(6));
        PcPart storage = part(slots.get(7));

        int draw = 0;
        int score = 0;
        int ramSticks = 0;
        for (int i = 0; i < slots.size(); i++) {
            PcPart part = part(slots.get(i));
            if (part == null || part.type() == Type.PSU) {
                continue;
            }
            draw += part.watts();
            score += part.score();
            if (part.type() == Type.RAM) {
                ramSticks++;
            }
        }
        // Две планки работают в двухканальном режиме — это заметная прибавка.
        if (ramSticks == 2) {
            score += 10;
        }

        if (board == null) {
            problems.add(Component.translatable("citylife.pc.need_board"));
        }
        if (cpu == null) {
            problems.add(Component.translatable("citylife.pc.need_cpu"));
        } else if (board != null && !board.socket().equals(cpu.socket())) {
            problems.add(Component.translatable("citylife.pc.socket", cpu.socket(),
                    board.socket()));
        }
        if (cooler == null) {
            problems.add(Component.translatable("citylife.pc.need_cooler"));
        } else if (cpu != null && cpu.watts() > PcPart.HOT_CPU && !cooler.id().equals("cooler_water")) {
            problems.add(Component.translatable("citylife.pc.too_hot", cpu.watts()));
        }
        if (ramSticks == 0) {
            problems.add(Component.translatable("citylife.pc.need_ram"));
        }
        if (storage == null) {
            problems.add(Component.translatable("citylife.pc.need_storage"));
        }
        int supply = psu == null ? 0 : psu.watts();
        if (psu == null) {
            problems.add(Component.translatable("citylife.pc.need_psu"));
        } else if (draw * 5 / 4 > supply) {
            // Запас в четверть мощности: блок впритык в жизни не живёт.
            problems.add(Component.translatable("citylife.pc.weak_psu", supply, draw * 5 / 4));
        }
        if (gpu == null) {
            // Без видеокарты работает на встроенной графике — медленно, но работает.
            score += 5;
        }
        return new Result(problems.isEmpty(), problems, score, draw, supply);
    }
}
