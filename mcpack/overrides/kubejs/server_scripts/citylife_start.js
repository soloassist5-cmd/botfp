// ============================================================================
//  Стартовый набор новичка.
//
//  При первом входе игрок получает телефон, SIM-карту, немного наличных
//  и книгу-гид. SIM нужно вставить в телефон самому — так сразу понятно,
//  зачем она: без неё не работают связь, банк и навигатор.
//  Отметка о выдаче лежит в данных игрока, поэтому набор не дублируется
//  после смерти или перезахода.
// ============================================================================

const STARTER_FLAG = 'citylife_starter_given'

PlayerEvents.loggedIn(event => {
  const player = event.player
  if (player.persistentData.getBoolean(STARTER_FLAG)) {
    return
  }
  player.persistentData.putBoolean(STARTER_FLAG, true)

  player.give('citylife:smartphone')
  player.give('citylife:sim_card')
  player.give('citylife:banknote_100 5')
  player.give('citylife:coin_10 8')
  player.give('minecraft:cooked_beef 8')

  // Книга-гид лежит в датапаке города, чтобы текст был в одном месте.
  player.server.runCommandSilent(
    `execute as ${player.username} at ${player.username} run function citylife:city/guide`
  )

  player.tell(Text.gold('Добро пожаловать в Лос-Сантос.'))
  player.tell(Text.gray('В инвентаре: телефон, SIM-карта, 580 ₽ наличными и книга-гид.'))
  player.tell(Text.aqua('Вставь SIM: возьми её курсором и нажми ПКМ по телефону в инвентаре'))
  player.tell(Text.aqua('(или открой телефон и нажми «Нет SIM-карты»).'))
  player.tell(Text.gray('Цели города — в меню достижений. Подработка — команда /work.'))
})
