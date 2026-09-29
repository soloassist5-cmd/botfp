package dev.lscity.citylife.pc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Комплектующие компьютера.
 *
 * Сборка проверяется по трём правилам, как в жизни: процессор встаёт только
 * в плату со своим сокетом, блок питания тянет суммарное потребление с
 * запасом, а горячему процессору нужна водянка. Производительность —
 * сумма очков процессора, видеокарты и памяти.
 *
 * @param id     идентификатор предмета
 * @param type   куда ставится
 * @param socket сокет платы и процессора (у остального пусто)
 * @param watts  потребление, а у блока питания — мощность
 * @param score  вклад в производительность
 * @param price  цена в маркетплейсе, ₽
 */
public record PcPart(String id, Type type, String socket, int watts, int score, int price) {

    public enum Type {
        BOARD, CPU, COOLER, RAM, GPU, PSU, STORAGE
    }

    /** Процессор горячее этого требует водяного охлаждения. */
    public static final int HOT_CPU = 100;

    public static final Map<String, PcPart> BY_ID = new LinkedHashMap<>();

    public static final List<PcPart> ALL = List.of(
            new PcPart("mb_k1", Type.BOARD, "K1", 30, 0, 4000),
            new PcPart("mb_r5", Type.BOARD, "R5", 35, 0, 5000),
            new PcPart("cpu_k1_i5", Type.CPU, "K1", 65, 40, 7000),
            new PcPart("cpu_k1_i9", Type.CPU, "K1", 125, 85, 20000),
            new PcPart("cpu_r5_r7", Type.CPU, "R5", 105, 70, 14000),
            new PcPart("cooler_air", Type.COOLER, "", 3, 0, 900),
            new PcPart("cooler_water", Type.COOLER, "", 8, 0, 3000),
            new PcPart("ram_8", Type.RAM, "", 4, 8, 1500),
            new PcPart("ram_16", Type.RAM, "", 5, 15, 2500),
            new PcPart("gpu_gx1650", Type.GPU, "", 75, 20, 6000),
            new PcPart("gpu_gx3060", Type.GPU, "", 170, 50, 15000),
            new PcPart("gpu_gx4070", Type.GPU, "", 200, 80, 28000),
            new PcPart("gpu_gx4090", Type.GPU, "", 450, 130, 60000),
            new PcPart("psu_450", Type.PSU, "", 450, 0, 1800),
            new PcPart("psu_750", Type.PSU, "", 750, 0, 3000),
            new PcPart("psu_1000", Type.PSU, "", 1000, 0, 5000),
            new PcPart("ssd_512", Type.STORAGE, "", 5, 5, 2000),
            new PcPart("hdd_1tb", Type.STORAGE, "", 8, 1, 1500));

    static {
        for (PcPart part : ALL) {
            BY_ID.put(part.id(), part);
        }
    }
}
