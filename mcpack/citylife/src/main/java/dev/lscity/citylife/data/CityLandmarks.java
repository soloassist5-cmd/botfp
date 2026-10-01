package dev.lscity.citylife.data;

import java.util.List;

/**
 * Городские метки для навигатора.
 *
 * Файл собран из плана генератора города (citylife/tools/gen_landmarks.py),
 * править руками не нужно: при изменении города достаточно перегенерировать.
 */
public final class CityLandmarks {

    public static final List<Waypoint> ALL = List.of(
            new Waypoint("Склад №1", -418, 69, 566, "pin", true),
            new Waypoint("Склад №2", -418, 69, 586, "pin", true),
            new Waypoint("Логистика", -351, 69, 566, "pin", true),
            new Waypoint("Пляжный бар", -287, 69, 182, "club", true),
            new Waypoint("Метро «пляж»", -288, 69, 313, "metro", true),
            new Waypoint("Центральный парк", -202, 69, 96, "park", true),
            new Waypoint("АЗС", -226, 69, 394, "gas", true),
            new Waypoint("Парк на холмах", -159, 69, -202, "park", true),
            new Waypoint("Спортплощадка", -159, 69, 586, "park", true),
            new Waypoint("Закусочная", -95, 69, -10, "food", true),
            new Waypoint("Салон связи", -97, 69, 10, "shop", true),
            new Waypoint("Оружейный магазин", -95, 69, 121, "shop", true),
            new Waypoint("Жилой дом", -77, 69, 181, "pin", true),
            new Waypoint("Ночной клуб", -97, 69, 202, "club", true),
            new Waypoint("Парковка", -34, 69, -10, "pin", true),
            new Waypoint("Мэрия", -34, 69, 10, "civic", true),
            new Waypoint("Городской банк", -10, 69, 95, "bank", true),
            new Waypoint("Продукты", -33, 69, 182, "pin", true),
            new Waypoint("Кофейня", -34, 69, 202, "food", true),
            new Waypoint("Офисы", 34, 69, -10, "pin", true),
            new Waypoint("STARK TOWER", 34, 69, 11, "tower", true),
            new Waypoint("SUNSET PLAZA", 11, 69, 96, "tower", true),
            new Waypoint("Метро «центр»", 33, 69, 182, "metro", true),
            new Waypoint("Аптека", 33, 69, 202, "pin", true),
            new Waypoint("IT-центр", 97, 69, -10, "pin", true),
            new Waypoint("MERIDIAN", 96, 69, 11, "tower", true),
            new Waypoint("Торговый центр", 96, 69, 121, "mall", true),
            new Waypoint("Электроника", 97, 69, 182, "pin", true),
            new Waypoint("Жилой дом", 77, 69, 203, "pin", true),
            new Waypoint("Доходный дом", 176, 69, -11, "pin", true),
            new Waypoint("Полиция", 158, 69, 10, "police", true),
            new Waypoint("Больница", 182, 69, 95, "hospital", true),
            new Waypoint("Городской парк", 158, 69, 182, "park", true),
            new Waypoint("Сквер", 158, 69, 202, "park", true),
            new Waypoint("Бизнес-центр", 226, 69, -10, "pin", true),
            new Waypoint("Автосалон", 225, 69, 10, "pin", true),
            new Waypoint("Пожарная часть", 202, 69, 97, "pin", true),
            new Waypoint("Мини-маркет", 226, 69, 182, "pin", true),
            new Waypoint("Страховая", 225, 69, 202, "pin", true),
            new Waypoint("Фастфуд", 289, 69, 121, "food", true),
            new Waypoint("Стройматериалы", 289, 69, 566, "pin", true),
            new Waypoint("Метро «восток»", 374, 69, -96, "metro", true),
            new Waypoint("АЗС", 350, 69, 202, "gas", true),
            new Waypoint("Парк «дубрава»", 350, 69, 394, "park", true),
            new Waypoint("Смотровая площадка", 417, 69, -202, "park", true),
            new Waypoint("Стройплощадка", 417, 69, 182, "pin", true),
            new Waypoint("Новый квартал", 566, 69, 288, "pin", true),
            new Waypoint("Район «Береговой»", -519, 69, -475, "pin", true),
            new Waypoint("Район «Приморский»", -519, 69, 827, "pin", true),
            new Waypoint("Банкомат — Метро «пляж»", -285, 69, 312, "atm", true),
            new Waypoint("Банкомат — АЗС", -223, 69, 395, "atm", true),
            new Waypoint("Банкомат — Мэрия", -36, 69, 12, "atm", true),
            new Waypoint("Банкомат — Городской банк", -12, 69, 93, "atm", true),
            new Waypoint("Банкомат — Метро «центр»", 36, 69, 181, "atm", true),
            new Waypoint("Банкомат — Торговый центр", 99, 69, 120, "atm", true),
            new Waypoint("Банкомат — АЗС", 353, 69, 203, "atm", true),
            new Waypoint("Банкомат — Метро «восток»", 373, 69, -93, "atm", true),
            new Waypoint("Пункт выдачи — Пальмовый б-р", -355, 69, 202, "pickup", true),
            new Waypoint("Пункт выдачи — 3-й проезд", -277, 69, -313, "pickup", true),
            new Waypoint("Пункт выдачи — 5-я улица", -249, 69, 482, "pickup", true),
            new Waypoint("Пункт выдачи — Бульвар звёзд", -150, 69, 10, "pickup", true),
            new Waypoint("Пункт выдачи — 8-я улица", -57, 69, 291, "pickup", true),
            new Waypoint("Пункт выдачи — Центральный пр.", 10, 69, -84, "pickup", true),
            new Waypoint("Пункт выдачи — Торговый центр", 96, 69, 121, "pickup", true),
            new Waypoint("Пункт выдачи — 13-й проезд", 225, 69, 327, "pickup", true),
            new Waypoint("Пункт выдачи — Бульвар звёзд", 299, 69, 10, "pickup", true),
            new Waypoint("Пункт выдачи — 6-й проезд", 483, 69, -121, "pickup", true),
            new Waypoint("Пункт выдачи — 17-я улица", 519, 69, 477, "pickup", true)
    );

    private CityLandmarks() {
    }
}
