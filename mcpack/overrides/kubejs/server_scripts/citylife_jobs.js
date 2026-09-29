// ============================================================================
//  Подработка: команда /work.
//
//  Даёт небольшую сумму на банковский счёт (мод citylife) раз в 15 минут.
//  Это подушка на первый час, а не основной доход: за час выходит около
//  1 200 ₽ — на еду, канистры и первую SIM-карту, но не на машину за вечер.
//  Деньги начисляются командой /citylife money give — она же используется
//  магазинами и админами, так что экономика остаётся одна.
// ============================================================================

const COOLDOWN_MS = 15 * 60 * 1000
const LAST_WORK = 'citylife_last_work'

const JOBS = [
  { text: 'Разнёс заказы по центру', min: 200, max: 350 },
  { text: 'Подменил курьера на смене', min: 220, max: 380 },
  { text: 'Помог на стройке', min: 280, max: 450 },
  { text: 'Развёз пассажиров на такси', min: 250, max: 420 },
  { text: 'Разгрузил фуру на складе', min: 300, max: 500 },
  { text: 'Отработал смену в закусочной', min: 180, max: 320 },
  { text: 'Помыл витрины в торговом центре', min: 150, max: 280 },
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
      player.tell(Text.green(`${job.text}. На счёт зачислено ${pay} ₽.`))
      player.tell(Text.gray('Баланс — в приложении «Банк» или командой /citylife balance.'))
      return 1
    })
  )
})
