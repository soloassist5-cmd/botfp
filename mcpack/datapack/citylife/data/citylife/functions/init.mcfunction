# Выполняется при загрузке мира: граница карты и правила города.
worldborder center 0 0
worldborder set 2048
worldborder warning distance 8
gamerule doFireTick false
gamerule mobGriefing false
gamerule doMobSpawning false
gamerule doWardenSpawning false
gamerule doInsomnia false
gamerule doPatrolSpawning false
gamerule doTraderSpawning false
gamerule announceAdvancements true
gamerule spawnRadius 2
setworldspawn 11 69 20
scoreboard objectives add citylife_jobs dummy "Выполненные работы"
scoreboard objectives add citylife_state dummy "Состояние города"
# Запасная уборка мобов (основное правило — в моде citylife).
schedule function citylife:city/mob_clean 15s replace
