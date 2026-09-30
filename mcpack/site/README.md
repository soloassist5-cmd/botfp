# Сайт сборки LS City Life

Одностраничный сайт с описанием сборки. Собирается скриптом из данных самой
сборки, поэтому числа на странице не могут разойтись с паком.

```bash
python3 build/build_site.py     # -> dist/
```

Что откуда берётся:

| Раздел страницы | Источник данных |
|---|---|
| Счётчики в шапке, число модов | `../mods.lock.json`, `../pack.toml` |
| Карта города, адреса объектов | `../world/generator/citygen/plan.py` |
| Состав застройки | подсчёт участков в плане города |
| Таблица жителей и ассортимент | `../build/gen_datapack.py` |
| Список модов со ссылками | `../mods.lock.json` |

Файлы:

- `build/build_site.py` — сборка `dist/index.html`
- `build/city_map.py` — карта Лос-Сантоса в SVG из плана города
- `build/skyline.py` — силуэт города в шапке, рисуется кодом
- `styles.css`, `app.js` — оформление и поведение
- окно выбора лаунчера (`.dl-overlay` в разметке, `.dl-*` в стилях,
  блок «окно выбора лаунчера» в `app.js`): кнопки скачивания сначала
  спрашивают, официальный лаунчер или пиратский, и дают нужный файл.
  Без JS ссылка работает как обычная — окно только уточняет формат
- `vercel.json` — заголовки и настройки хостинга

Деплой на Vercel идёт из каталога `mcpack/site/dist` этого репозитория.

Зеркало — GitHub Pages, <https://soloassist5-cmd.github.io/botfp/>, из ветки
`gh-pages`. В ней только содержимое `dist/` (плюс `.nojekyll`), без истории;
все ссылки на странице относительные, поэтому она работает и из подкаталога.
Обновить зеркало после пересборки:

```bash
export GIT_INDEX_FILE=$(mktemp -u)
git read-tree HEAD:mcpack/site/dist && git rm -q --cached vercel.json
git update-index --add --cacheinfo 100644,$(git hash-object -w --stdin </dev/null),.nojekyll
C=$(echo "Site mirror" | git commit-tree $(git write-tree)); unset GIT_INDEX_FILE
git push -f origin $C:refs/heads/gh-pages
```
