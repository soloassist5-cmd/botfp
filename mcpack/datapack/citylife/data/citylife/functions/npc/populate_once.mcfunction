# Однократное заселение города. Вызывается достижением citylife:hidden/populate.
# Расставить заново вручную: /function citylife:npc/spawn_all
execute unless score #populated citylife_state matches 1 run function citylife:npc/spawn_all
scoreboard players set #populated citylife_state 1
