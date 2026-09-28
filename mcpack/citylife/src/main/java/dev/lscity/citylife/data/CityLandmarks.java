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
            new Waypoint("Склад №1", -418, 69, 542, "pin", true),
            new Waypoint("Склад №2", -418, 69, 609, "pin", true),
            new Waypoint("Логистика", -351, 69, 542, "pin", true),
            new Waypoint("Пляжный бар", -288, 69, 158, "club", true),
            new Waypoint("Метро «пляж»", -288, 69, 288, "metro", true),
            new Waypoint("АЗС", -226, 69, 417, "gas", true),
            new Waypoint("Спортплощадка", -159, 69, 609, "park", true),
            new Waypoint("Закусочная", -96, 69, -34, "food", true),
            new Waypoint("Салон связи", -96, 69, 33, "shop", true),
            new Waypoint("Оружейный магазин", -96, 69, 96, "shop", true),
            new Waypoint("Жилой дом", -96, 69, 158, "pin", true),
            new Waypoint("Ночной клуб", -96, 69, 225, "club", true),
            new Waypoint("Парковка", -34, 69, -34, "pin", true),
            new Waypoint("Мэрия", -34, 69, 33, "civic", true),
            new Waypoint("Городской банк", -34, 69, 96, "bank", true),
            new Waypoint("Продукты", -34, 69, 158, "pin", true),
            new Waypoint("Кофейня", -34, 69, 225, "food", true),
            new Waypoint("Офисы", 33, 69, -34, "pin", true),
            new Waypoint("LS TOWER", 33, 69, 33, "tower", true),
            new Waypoint("SUNSET PLAZA", 33, 69, 96, "tower", true),
            new Waypoint("Метро «центр»", 33, 69, 158, "metro", true),
            new Waypoint("Аптека", 33, 69, 225, "pin", true),
            new Waypoint("IT-центр", 96, 69, -34, "pin", true),
            new Waypoint("MERIDIAN", 96, 69, 33, "tower", true),
            new Waypoint("Торговый центр", 96, 69, 96, "mall", true),
            new Waypoint("Электроника", 96, 69, 158, "pin", true),
            new Waypoint("Жилой дом", 96, 69, 225, "pin", true),
            new Waypoint("Доходный дом", 158, 69, -34, "pin", true),
            new Waypoint("Полиция", 158, 69, 33, "police", true),
            new Waypoint("Больница", 158, 69, 96, "hospital", true),
            new Waypoint("Городской парк", 158, 69, 158, "park", true),
            new Waypoint("Сквер", 158, 69, 225, "park", true),
            new Waypoint("Бизнес-центр", 225, 69, -34, "pin", true),
            new Waypoint("Автосалон", 225, 69, 33, "pin", true),
            new Waypoint("Пожарная часть", 225, 69, 96, "pin", true),
            new Waypoint("Мини-маркет", 225, 69, 158, "pin", true),
            new Waypoint("Страховая", 225, 69, 225, "pin", true),
            new Waypoint("Фастфуд", 288, 69, 96, "food", true),
            new Waypoint("Стройматериалы", 288, 69, 542, "pin", true),
            new Waypoint("Метро «восток»", 350, 69, -96, "metro", true),
            new Waypoint("АЗС", 350, 69, 225, "gas", true),
            new Waypoint("Смотровая площадка", 417, 69, -226, "park", true),
            new Waypoint("Стройплощадка", 417, 69, 158, "pin", true),
            new Waypoint("Новый квартал", 542, 69, 288, "pin", true),
            new Waypoint("Банкомат — Метро «пляж»", -285, 69, 311, "atm", true),
            new Waypoint("Банкомат — АЗС", -223, 69, 396, "atm", true),
            new Waypoint("Банкомат — Мэрия", -31, 69, 12, "atm", true),
            new Waypoint("Банкомат — Городской банк", -12, 69, 99, "atm", true),
            new Waypoint("Банкомат — Метро «центр»", 36, 69, 180, "atm", true),
            new Waypoint("Банкомат — Торговый центр", 99, 69, 119, "atm", true),
            new Waypoint("Банкомат — АЗС", 353, 69, 204, "atm", true),
            new Waypoint("Банкомат — Метро «восток»", 372, 69, -93, "atm", true)
    );

    private CityLandmarks() {
    }
}
