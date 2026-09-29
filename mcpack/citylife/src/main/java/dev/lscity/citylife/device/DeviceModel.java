package dev.lscity.citylife.device;

import java.util.List;

/**
 * Модель гаджета: что это за устройство и что в нём умеет работать.
 *
 * Телефоны отличаются не только корпусом: у кнопочной «Нокты» нет интернета
 * и магазина приложений, у защищённого «Полюса» есть компас и фонарик, у
 * флагмана — всё сразу. Экран один на все устройства и рисуется по этой
 * записи: размер корпуса, число колонок значков, цвета.
 *
 * @param id         идентификатор предмета (citylife:&lt;id&gt;)
 * @param kind       телефон, планшет, ноутбук или компьютер
 * @param simSlot    есть ли слот SIM: без него связь только по Wi‑Fi
 * @param storeSlots сколько приложений можно доставить из магазина
 * @param apps       встроенные приложения, в порядке значков на столе
 * @param price      цена в маркетплейсе, ₽
 * @param frame      цвет корпуса
 */
public record DeviceModel(String id, Kind kind, boolean simSlot, int storeSlots,
                          List<String> apps, int price, int frame) {

    public enum Kind {
        PHONE, TABLET, LAPTOP, COMPUTER;

        /** Ноутбук и компьютер в сети всегда: Wi‑Fi или провод, SIM им не нужна. */
        public boolean wired() {
            return this == LAPTOP || this == COMPUTER;
        }
    }

    public boolean has(String app) {
        return apps.contains(app);
    }
}
