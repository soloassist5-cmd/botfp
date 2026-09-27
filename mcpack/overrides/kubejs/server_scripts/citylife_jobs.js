// ============================================================================
//  Подработка: команда /work.
//
//  Даёт небольшую сумму на банковский счёт (мод citylife) с перерывом,
//  чтобы в городе было чем заняться с первых минут и без гринда.
//  Деньги начисляются командой /citylife money give — она же используется
//  магазинами и админами, так что экономика остаётся одна.
// ============================================================================

const COOLDOWN_MS = 5 * 60 * 1000
const LAST_WORK = 'citylife_last_work'

const JOBS = [
  { text: 'Разнёс заказы по центру', min: 40, max: 70 },
  { text: 'Подменил курьера на смене', min: 45, max: 80 },
  { text: 'Помог на стройке', min: 60, max: 110 },
  { text: 'Развёз пассажиров на такси', min: 55, max: 95 },
  { text: 'Разгрузил фуру на складе', min: 65, max: 120 },
  { text: 'Отработал смену в закусочной', min: 35, max: 65 },
  { text: 'Помыл витрины в торговом центре', min: 30, max: 60 },
]

ServerEvents.commandRegistry(event => {
  const { commands: Commands } = event

  event.register(
    Commands.literal('work').executes(context => {
      const player = context.source.player
      if (!player) {
        return 0
      }
      const now = Date.now()
      const last = player.persistentData.getLong(LAST_WORK)
      const passed = now - last

      if (last > 0 && passed < COOLDOWN_MS) {
        const left = Math.ceil((COOLDOWN_MS - passed) / 60000)
        player.tell(Text.red(`Смена ещё не началась. Подожди ~${left} мин.`))
        return 0
      }

      const job = JOBS[Math.floor(Math.random() * JOBS.length)]
      const pay = job.min + Math.floor(Math.random() * (job.max - job.min + 1))

      player.persistentData.putLong(LAST_WORK, now)
      player.server.runCommandSilent(`citylife money give ${player.username} ${pay}`)
      player.tell(Text.green(`${job.text}. На счёт зачислено ${pay}.`))
      player.tell(Text.gray('Баланс — в приложении «Банк» или командой /citylife balance.'))
      return 1
    })
  )
})
