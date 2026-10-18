# Автоматическое обновление документации

После слияния PR в develop или main GitHub Actions запускает
[Update project documentation](../.github/workflows/docs-sync.yml).
Агент анализирует изменения Kotlin/Gradle/release.yml, сам читает определения API,
места использования, README и docs. Он сохраняет нужные правки и создаёт коммит
прямо в ветке, куда влили PR. Отдельный PR документации и ручное слияние не нужны.

До коммита workflow запускает /sync --apply закреплённого runtime агента.
В нём [docs-verifier.js](https://github.com/Flobsterable/cosplay-dev-assistant/blob/b8a24ccabc398f9c2d6ba6e9774b2831ecd4ef1e/tools/dev-assistant/src/docs-verifier.js)
проверяет новые утверждения, параметры, API, примеры команд, относительные ссылки
и якоря. [sync-docs.js](https://github.com/Flobsterable/cosplay-dev-assistant/blob/b8a24ccabc398f9c2d6ba6e9774b2831ecd4ef1e/tools/dev-assistant/src/sync-docs.js)
отклоняет неполный контекст и проверяет результат до записи. Ошибка CLI останавливает
workflow до шага git commit благодаря set -euo pipefail. При отклонении коммита не будет;
причина доступна в Actions. Проверка статическая: команды приложения не исполняются.
Если документация уже актуальна или в диапазоне нет поддерживаемых изменений,
новый коммит не создаётся. Работа занимает время на запуск Actions и запросы модели.

Workflow использует существующие Secret LLM_API_KEY и Variables LLM_MODEL/LLM_BASE_URL,
как AI-ревью. Runtime закреплён на коммите b8a24ccabc398f9c2d6ba6e9774b2831ecd4ef1e
репозитория Flobsterable/cosplay-dev-assistant. При обновлении агента нужно обновлять ref.
Токен GitHub Actions должен иметь contents:write; ограничения защиты ветки не обходятся.

В коммит включаются только README.md и Markdown в docs/. Код приложения, Gradle,
релизные теги и настройки workflow агентом не изменяются. Событие — закрытие
слитого PR, поэтому собственный push с документацией не запускает новый цикл.
Уже обработанный диапазон определяется по Docs-Sync-Source в сообщении коммита.
Если целевая ветка изменилась во время генерации, workflow повторно анализирует
свежий checkout и заново генерирует правки; force push не применяется.
Если push отклонён при неизменной ветке (например, из-за защиты или прав), workflow
сразу завершается ошибкой, без повторных платных запросов генерации.

Если запуск завершился ошибкой до публикации коммита, выберите Actions → Update
project documentation → Re-run jobs. Уже успешно обработанный диапазон пропускается.
Также доступен Run workflow на develop/main с полными base/head SHA из лога.
Он тоже сохраняет идемпотентность: ранее опубликованный диапазон заново не генерируется.
Отчёт генерации и проверки сохраняется как Actions artifact на 14 дней.
Локальную копию проекта на VPS нужно обновлять через git pull, чтобы /help читал свежие docs.
