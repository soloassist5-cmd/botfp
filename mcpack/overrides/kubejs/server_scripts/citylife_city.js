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
      player.tell(Text.gold('Смартфон'))
      player.tell(Text.gray('  3 стеклянных панели / железо + SIM + железо / железо + редстоун'))
      player.tell(Text.gold('SIM-карта'))
      player.tell(Text.gray('  2 золотых самородка + редстоун (даёт 2 штуки)'))
      player.tell(Text.gold('Умный замок'))
      player.tell(Text.gray('  железо вокруг, редстоун в центре, компаратор снизу'))
      player.tell(Text.gold('Отмычка'))
      player.tell(Text.gray('  2 железных самородка по диагонали и палка'))
      player.tell(Text.gray('Привязать замок к телефону: кликнуть телефоном по замку.'))
      return 1
    })
  )
})
