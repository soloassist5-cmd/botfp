// ============================================================================
//  Стартовый набор новичка.
//
//  При первом входе игрок получает телефон, немного наличных и книгу-гид.
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
  player.tell(Text.gray('В инвентаре: телефон, 580 ₽ наличными и книга-гид.'))
  player.tell(Text.gray('Цели города — в меню достижений. Подработка — команда /work.'))
})
