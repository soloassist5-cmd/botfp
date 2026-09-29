// ============================================================================
//  Городские мелочи.
//
//  1. Телефон и умный замок можно купить, но и скрафтить: подсказываем
//     рецепты в чате по команде /phonehelp, чтобы не искать в JEI.
//  2. Ночной город не должен зарастать мобами: уборкой занимается датапак
//     (функция citylife:city/mob_clean), здесь только подсказка админу.
// ============================================================================

ServerEvents.commandRegistry(event => {
  const { commands: Commands } = event

  event.register(
    Commands.literal('phonehelp').executes(context => {
      const player = context.source.player
      if (!player) {
        return 0
      }
      player.tell(Text.gold('Смартфон LS'))
      player.tell(Text.gray('  3 стеклянных панели / железо + золото + железо / железо + редстоун + железо'))
      player.tell(Text.gold('SIM-карта'))
      player.tell(Text.gray('  2 золотых самородка + редстоун (даёт 2 штуки, номер выдаётся сам)'))
      player.tell(Text.gray('  Вставить: перетащить SIM на телефон в инвентаре.'))
      player.tell(Text.gold('Другие телефоны, планшет, ноутбук, детали ПК'))
      player.tell(Text.gray('  в салоне связи, у продавца техники или в маркетплейсе'))
      player.tell(Text.gold('Умный замок'))
      player.tell(Text.gray('  железо вокруг, редстоун в центре, компаратор снизу'))
      player.tell(Text.gold('Отмычка'))
      player.tell(Text.gray('  2 железных самородка по диагонали и палка'))
      player.tell(Text.gray('Привязать замок к телефону: кликнуть телефоном по замку.'))
      return 1
    })
  )
})
