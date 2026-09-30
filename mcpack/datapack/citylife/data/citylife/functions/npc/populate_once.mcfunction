# Заселение города жителями поколения 2. Вызывается достижением
# citylife:hidden/populate_2: у нового поколения новое достижение, поэтому
# оно срабатывает и в старых мирах, где прежнее уже получено.
# Расставить заново вручную: /function citylife:npc/spawn_all
execute unless score #populated citylife_state matches 2 run function citylife:npc/spawn_all
scoreboard players set #populated citylife_state 2
