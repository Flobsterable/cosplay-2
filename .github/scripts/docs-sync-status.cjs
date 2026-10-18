// Receives only workflow metadata; never publishes LLM output or log contents.
module.exports = async function track({ github, context, core, env }) {
  const { TARGET_BRANCH: branch, BASE_SHA: base, MERGED_SHA: head, SYNC_RESULT: result } = env;
  if (!['develop', 'main'].includes(branch) || !/^[a-f0-9]{40}$/.test(base || '') || !/^[a-f0-9]{40}$/.test(head || '')) {
    throw new Error('Invalid documentation recovery range');
  }
  if (!['success', 'failure', 'cancelled'].includes(result)) throw new Error('Invalid sync result');
  const repo = context.repo;
  const marker = `<!-- docs-sync-recovery:${branch}:${head} -->`;
  const run = `${context.serverUrl}/${repo.owner}/${repo.repo}/actions/runs/${context.runId}`;
  const issues = await github.paginate(github.rest.issues.listForRepo, { ...repo, state: 'open', creator: 'github-actions[bot]', per_page: 100 });
  const existing = issues.find(issue => !issue.pull_request && issue.body?.includes(marker));
  if (result === 'success') {
    if (existing) {
      await github.rest.issues.update({ ...repo, issue_number: existing.number,
        body: `${existing.body}\n\nВосстановлено: [успешный запуск](${run}). Документация обновлена либо проверена как актуальная.`,
        state: 'closed', state_reason: 'completed' });
      core.notice(`Documentation recovery issue #${existing.number} resolved.`);
    }
    return;
  }
  const body = `${marker}
Обновление документации для \`${branch}\` не завершилось (${result}). **Документация может отставать от кода.**

[Запуск и логи](${run}) · попытка ${context.runAttempt}

Диапазон: \`${base}..${head}\`.

### Восстановление
1. Посмотрите ошибку шага Generate, verify and commit documentation и артефакты docs-sync (хранятся 14 дней).
2. Исправьте причину: доступность модели, лимит контекста, проверку фактов, права записи или защиту ветки.
3. Откройте Actions → Update project documentation → Run workflow, выберите \`${branch}\` и введите:
   - base: \`${base}\`
   - head: \`${head}\`

Run workflow использует текущую исправленную версию workflow/runtime; Re-run jobs старого запуска использует старую версию.
Успешный запуск для этого диапазона автоматически закроет эту задачу. Другой мердж её не закрывает.
`;
  const response = existing
    ? await github.rest.issues.update({ ...repo, issue_number: existing.number, body })
    : await github.rest.issues.create({ ...repo, title: `Документация не обновлена: ${branch} ${head.slice(0, 8)}`, body });
  core.warning(`Documentation recovery required: ${response.data.html_url}`);
  await core.summary.addHeading('Документация требует восстановления')
    .addLink('Задача восстановления', response.data.html_url).write();
};
