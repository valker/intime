import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { CERTIFICATE } from './play-api.mjs';
import { AAB_SHA256, VERSION_CODE, assertBundle, prepareCandidate, downloadCandidate } from './prepare-play-candidate.mjs';

// Читается только подписанный локальный тестовый кандидат, не ключи/личные базы.
const bytes = await readFile(new URL('../.test-tools/runs/20261009-103501-release27-historical-json/candidate/intime-27-2.0.0-rc3.aab', import.meta.url));
const bundle = { versionCode: VERSION_CODE, sha256: AAB_SHA256 };
const json = body => ({ ok: true, json: async () => body });
const apkList = { generatedApks: [{ certificateSha256Hash: CERTIFICATE, generatedUniversalApk: { downloadId: 'test/id' } }] };

/**
 * Исходные условия: проверенный AAB и mock пустого списка bundles. Выполняем
 * prepare; требуем edit insert/list/upload, точные code/hash и uploadType=media,
 * отсутствие track/commit, token в checkpoint. Сеть Google не вызывается.
 */
test('prepare uploads exact candidate without tracks or commit', async () => {
    const calls = [], states = [];
    const result = await prepareCandidate('fake-token', bytes, async (url, options) => {
        calls.push({ url, options });
        return json(calls.length === 1 ? { id: 'test-edit', expiryTimeSeconds: '123' } : calls.length === 2 ? { bundles: [] } : bundle);
    }, async state => states.push(structuredClone(state)));
    assert.equal(calls.length, 3);
    assert.equal(calls[0].options.method, 'POST');
    assert.equal(calls[1].options.method, 'GET');
    assert.ok(calls[2].url.endsWith('/bundles?uploadType=media'));
    assert.equal(calls[2].options.body, bytes);
    for (const call of calls) { assert.equal(call.options.redirect, 'error'); assert.equal(/tracks|:commit/.test(call.url), false); }
    assert.equal(result.uploaded, true);
    assert.equal(result.committed, false);
    assert.equal(result.published, false);
    assert.equal(JSON.stringify(states).includes('fake-token'), false);
});

/**
 * Исходные условия: другой payload вместо архивного AAB. prepare должен
 * остановиться по хешу до создания edit и любого сетевого вызова. Проверяется
 * привязка к точному артефакту; Android manifest этот тест не разбирает.
 */
test('different AAB is rejected before any request', async () => {
    let called = false;
    await assert.rejects(prepareCandidate('fake', Buffer.from('wrong'), async () => { called = true; }), { code: 'CANDIDATE_AAB_HASH_MISMATCH' });
    assert.equal(called, false);
});

/**
 * Исходные условия: bundle 27 с тем же SHA уже присутствует. prepare использует
 * его без повторного upload. При чужом SHA или более высокой версии должен
 * отказать после чтения списка и не отправлять файл/commit.
 */
test('existing bundle is reused only with exact hash and no newer code', async () => {
    for (const existing of [[bundle], [{ ...bundle, sha256: '0'.repeat(64) }], [{ ...bundle, versionCode: 28 }]]) {
        let calls = 0;
        const action = prepareCandidate('fake', bytes, async () => json(++calls === 1 ? { id: 'test-edit' } : { bundles: existing }));
        if (existing[0] === bundle) { assert.equal((await action).reusedExistingBundle, true); }
        else { await assert.rejects(action); }
        assert.equal(calls, 2);
    }
    assert.throws(() => assertBundle({ ...bundle, versionCode: 26 }), { code: 'REMOTE_BUNDLE_IDENTITY_MISMATCH' });
});

/**
 * Исходные условия: create/list успешны, upload возвращает 403. Проверяем один
 * uploadAttempted checkpoint, uploaded=false и отказ без retry/commit. Тело
 * сервера не читается и в состояние не попадает; реальные ACL не проверяются.
 */
test('upload refusal keeps checkpoint and does not retry', async () => {
    let calls = 0; const states = [];
    await assert.rejects(prepareCandidate('fake', bytes, async () => {
        calls++;
        return calls === 1 ? json({ id: 'test-edit' }) : calls === 2 ? json({ bundles: [] }) : { ok: false, status: 403 };
    }, async state => states.push(structuredClone(state))), { code: 'PLAY_HTTP_403' });
    assert.equal(calls, 3);
    assert.equal(states.at(-1).uploadAttempted, true);
    assert.equal(states.at(-1).uploaded, false);
});

/**
 * Исходные условия: API upload отвечает HTTP 200, но код ответа не равен 27.
 * Проверяем отказ по identity с сохранённым uploaded=true: успешную передачу
 * нельзя скрывать за ошибкой проверки и автоматически повторять.
 */
test('wrong successful upload identity preserves upload fact', async () => {
    let calls = 0; const states = [];
    await assert.rejects(prepareCandidate('fake', bytes, async () => json(++calls === 1 ? { id: 'test-edit' } : calls === 2 ? { bundles: [] } : { ...bundle, versionCode: 26 }),
        async state => states.push(structuredClone(state))), { code: 'REMOTE_BUNDLE_IDENTITY_MISMATCH' });
    assert.equal(states.at(-1).uploaded, true);
});

/**
 * Исходные условия: metadata содержит ожидаемый сертификат и downloadId с '/'.
 * Скачиваем синтетические ZIP bytes по фиксированному GET endpoint с percent
 * encoding ID и запретом redirect. Это контроль транспорта, не действительный APK.
 */
test('download uses fixed endpoint and encoded ID', async () => {
    const calls = [];
    const expected = Buffer.from([0x50, 0x4b, 0x03, 0x04, 1, 2]);
    const result = await downloadCandidate('fake', async (url, options) => {
        calls.push({ url, options });
        if (calls.length === 1) return json(apkList);
        return { ok: true, headers: new Headers(), body: (async function* () { yield expected; })() };
    });
    assert.deepEqual(result, expected);
    assert.ok(calls[1].url.endsWith('/downloads/test%2Fid:download?alt=media'));
    assert.equal(calls[1].options.redirect, 'error');
    assert.equal(calls[1].options.method, 'GET');
});

/**
 * Исходные условия: metadata с чужим сертификатом либо без universal APK.
 * Скачивание не должно начинаться. HTTP success чтения не подтверждает наличие
 * файла с нужной подписью; сеть и apksigner в этом тесте не используются.
 */
test('download rejects wrong signing group and absent universal', async () => {
    for (const body of [{ generatedApks: [{ certificateSha256Hash: '0'.repeat(64) }] }, { generatedApks: [{ certificateSha256Hash: CERTIFICATE }] }]) {
        let calls = 0;
        await assert.rejects(downloadCandidate('fake', async () => { calls++; return json(body); }));
        assert.equal(calls, 1);
    }
});

/**
 * Исходные условия: download HTTP 403, чрезмерный Content-Length, чрезмерный
 * chunk без Content-Length и не-ZIP файл. Все случаи отклоняются до записи
 * на диск. Ограничение 10 MiB тестируется по размеру, а не свободному месту диска.
 */
test('download rejects refusal oversized content and invalid bytes', async () => {
    for (const result of [
        { ok: false, status: 403 },
        { ok: true, headers: new Headers({ 'content-length': String(11 * 1024 * 1024) }) },
        { ok: true, headers: new Headers(), body: (async function* () { yield Buffer.alloc(11 * 1024 * 1024); })() },
        { ok: true, headers: new Headers(), body: (async function* () { yield Buffer.from('not-a-zip'); })() },
    ]) {
        let calls = 0;
        await assert.rejects(downloadCandidate('fake', async () => ++calls === 1 ? json(apkList) : result));
    }
});
