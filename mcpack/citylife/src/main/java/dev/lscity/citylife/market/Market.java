package dev.lscity.citylife.market;

import dev.lscity.citylife.device.DeviceModel;
import dev.lscity.citylife.device.Devices;
import dev.lscity.citylife.pc.PcPart;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Каталог маркетплейса.
 *
 * Один и тот же на сервере и клиенте: клиент рисует витрину, сервер по
 * идентификатору предложения проверяет цену и выдаёт товар. Цена всегда
 * берётся отсюда, а не из пакета, — подделать её с клиента нельзя.
 */
public final class Market {

    /** Категории в порядке вкладок. */
    public static final List<String> CATEGORIES =
            List.of("gpu", "parts", "periphery", "phones", "devices", "other");

    public record Offer(String id, String category, String item, int count, int price) {
    }

    public static final Map<String, Offer> BY_ID = new LinkedHashMap<>();

    static {
        List<Offer> offers = new ArrayList<>();
        for (PcPart part : PcPart.ALL) {
            String category = part.type() == PcPart.Type.GPU ? "gpu" : "parts";
            offers.add(new Offer(part.id(), category, "citylife:" + part.id(), 1, part.price()));
        }
        offers.add(new Offer("pc_case", "parts", "citylife:pc_case", 1, 2500));
        offers.add(new Offer("monitor", "periphery", "citylife:monitor", 1, 5000));
        offers.add(new Offer("keyboard", "periphery", "citylife:keyboard", 1, 1000));
        offers.add(new Offer("mouse", "periphery", "citylife:mouse", 1, 600));
        offers.add(new Offer("headset", "periphery", "citylife:headset", 1, 1500));
        for (DeviceModel model : Devices.items()) {
            if (model.price() <= 0) {
                continue; // телефон Старка не продаётся, его печатают
            }
            String category = model.kind() == DeviceModel.Kind.PHONE ? "phones" : "devices";
            offers.add(new Offer(model.id(), category, "citylife:" + model.id(), 1,
                    model.price()));
        }
        offers.add(new Offer("sim_card", "other", "citylife:sim_card", 1, 300));
        offers.add(new Offer("fuel_canister", "other", "citylife:fuel_canister", 4, 700));
        offers.add(new Offer("smart_lock", "other", "citylife:smart_lock", 1, 1500));
        for (Offer offer : offers) {
            BY_ID.put(offer.id(), offer);
        }
    }

    public static List<Offer> in(String category) {
        return BY_ID.values().stream().filter(o -> o.category().equals(category)).toList();
    }

    private Market() {
    }
}
