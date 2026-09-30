package dev.lscity.citylife.estate;

import dev.lscity.citylife.CityConfig;
import dev.lscity.citylife.CityLife;
import dev.lscity.citylife.data.CityData;
import dev.lscity.citylife.data.LifeData;
import dev.lscity.citylife.data.Mail;
import dev.lscity.citylife.data.Texts;
import dev.lscity.citylife.economy.Money;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

/**
 * Раз в игровые сутки: коммуналка за жильё и плата за аренду.
 *
 * Коммуналка — небольшой процент от цены дома (по умолчанию 0,3% в сутки).
 * Не хватило денег — копится долг, хозяину приходит письмо. Долг больше
 * недели коммуналки — дом отходит городу и снова продаётся.
 *
 * Аренда: арендатор платит хозяину каждые сутки. Нечем платить — аренда
 * заканчивается, обоим приходит письмо.
 */
@Mod.EventBusSubscriber(modid = CityLife.MOD_ID)
public final class EstateBills {

    private static final String BILLING = "Городские службы";
    private static final long DAY = 24000L;

    private EstateBills() {
    }

    /** Коммуналка за сутки для объекта. */
    public static long upkeep(Estate.Unit unit) {
        if (unit.business()) {
            return 0;
        }
        long perMille = CityConfig.CONFIG.homeUpkeepPerMille.get();
        return perMille <= 0 ? 0 : Math.max(10, unit.price() * perMille / 1000);
    }

    /** Доход бизнеса за сутки. */
    public static long income(Estate.Unit unit) {
        long perMille = CityConfig.CONFIG.businessIncomePerMille.get();
        return unit.business() && perMille > 0 ? unit.price() * perMille / 1000 : 0;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer() == null
                || event.getServer().overworld().getGameTime() % 200 != 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        long day = server.overworld().getGameTime() / DAY;
        LifeData life = LifeData.get(server);
        if (life.billedDay() < 0) {
            life.setBilledDay(day);
            return;
        }
        if (day <= life.billedDay()) {
            return;
        }
        life.setBilledDay(day);
        bill(server, life);
    }

    /** Взять плату за сутки со всех владельцев и арендаторов. */
    public static void bill(MinecraftServer server, LifeData life) {
        CityData bank = CityData.get(server);
        long now = server.overworld().getGameTime();
        for (Map.Entry<String, LifeData.Owner> entry : new ArrayList<>(life.owners().entrySet())) {
            Estate.Unit unit = Estate.get(entry.getKey());
            if (unit == null) {
                continue;
            }
            LifeData.Owner owner = entry.getValue();
            if (unit.business()) {
                bank.deposit(owner.id(), income(unit), Texts.ru("citylife.statement.business_income",
                        unit.title()), now);
                continue;
            }
            rent(bank, life, unit, owner, now);

            long due = upkeep(unit) + life.debt(unit.id());
            if (due <= 0) {
                continue;
            }
            if (bank.withdraw(owner.id(), due, Texts.ru("citylife.statement.upkeep", unit.address()),
                    now)) {
                life.setDebt(unit.id(), 0);
                continue;
            }
            long debt = due;
            life.setDebt(unit.id(), debt);
            if (debt >= upkeep(unit) * CityConfig.CONFIG.homeDebtDays.get()) {
                life.clearOwner(unit.id());
                mail(bank, owner.id(), now, Texts.ru("citylife.bills.repossessed",
                        unit.label(), Money.format(debt)));
            } else {
                mail(bank, owner.id(), now, Texts.ru("citylife.bills.debt", unit.label(),
                        Money.format(debt)));
            }
        }
    }

    private static void rent(CityData bank, LifeData life, Estate.Unit unit, LifeData.Owner owner,
                             long now) {
        LifeData.Owner tenant = life.tenant(unit.id());
        long price = life.rent(unit.id());
        if (tenant == null || price <= 0) {
            return;
        }
        if (bank.transfer(tenant.id(), owner.id(), price)) {
            bank.record(tenant.id(), -price, Texts.ru("citylife.statement.rent_paid", unit.address()),
                    now);
            bank.record(owner.id(), price, Texts.ru("citylife.statement.rent_got", unit.address()), now);
            return;
        }
        life.setTenant(unit.id(), null, "", 0);
        mail(bank, tenant.id(), now, Texts.ru("citylife.bills.lease_ended_tenant", unit.label()));
        mail(bank, owner.id(), now, Texts.ru("citylife.bills.lease_ended_owner", unit.label(),
                tenant.name()));
    }

    private static void mail(CityData bank, UUID to, long now, String text) {
        bank.deliverMail(to, new Mail(new UUID(0L, 0L), BILLING, text, now, false));
    }
}
