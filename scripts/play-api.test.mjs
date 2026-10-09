import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, verify } from 'node:crypto';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { CERTIFICATE, PACKAGE, checkAccess, createAssertion, summarizeGeneratedApks,
    normalizeSigningFingerprint, validateCredentialLocation, validateCredentials, validateVersionCode } from './play-api.mjs';

// Только временный тестовый ключ в памяти; ключ пользователя не читается.
const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
const credentials = {
    type: 'service_account', client_email: 'test@example.iam.gserviceaccount.com',
    private_key: privateKey.export({ type: 'pkcs8', format: 'pem' }),
    token_uri: 'https://oauth2.googleapis.com/token',
};
const generated = () => ({ generatedApks: [{ certificateSha256Hash: CERTIFICATE,
    generatedUniversalApk: { downloadId: 'synthetic-download-id' }, generatedSplitApks: [{}, {}] }] });
const response = body => ({ ok: true, json: async () => body });

/**
 * Исходные условия: временный RSA-ключ и фиксированное время. Создаём JWT,
 * проверяем RS256-подпись публичным ключом, адрес/token scope, issuer и срок
 * 300 секунд. Проверяется реальная локальная криптография, не принятие JWT Google.
 */
test('JWT signature and fixed OAuth claims', () => {
    const jwt = createAssertion(credentials, 1700000000);
    const [header, payload, signature] = jwt.split('.');
    assert.equal(verify('RSA-SHA256', Buffer.from(header + '.' + payload), publicKey, Buffer.from(signature, 'base64url')), true);
    assert.deepEqual(JSON.parse(Buffer.from(header, 'base64url')), { alg: 'RS256', typ: 'JWT' });
    assert.deepEqual(JSON.parse(Buffer.from(payload, 'base64url')), {
        iss: credentials.client_email, scope: 'https://www.googleapis.com/auth/androidpublisher',
        aud: credentials.token_uri, iat: 1700000000, exp: 1700000300,
    });
});

/**
 * Исходные условия: транспорт возвращает синтетический token и метаданные APK
 * с ожидаемым сертификатом. Выполняем checkAccess; требуем только OAuth POST
 * и Play GET для production-пакета/26, запрет redirect и безопасный summary
 * без token, JWT, private key и downloadId. Google и устройства не вызываются.
 */
test('access uses only token exchange and fixed read endpoint', async () => {
    const calls = [];
    const transport = async (url, options) => {
        calls.push({ url, options });
        return calls.length === 1 ? response({ token_type: 'Bearer', access_token: 'synthetic-token' }) : response(generated());
    };
    const result = await checkAccess(credentials, 26, transport);
    assert.equal(calls.length, 2);
    assert.equal(calls[0].url, credentials.token_uri);
    assert.equal(calls[0].options.method, 'POST');
    assert.equal(calls[0].options.body.get('grant_type'), 'urn:ietf:params:oauth:grant-type:jwt-bearer');
    assert.equal(calls[1].url, `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${PACKAGE}/generatedApks/26`);
    assert.equal(calls[1].options.method, 'GET');
    assert.equal(calls[1].options.headers.Authorization, 'Bearer synthetic-token');
    for (const call of calls) { assert.equal(call.options.redirect, 'error'); assert.ok(call.options.signal instanceof AbortSignal); }
    assert.equal(result.status, 'PASSED');
    assert.equal(result.universalApkAvailable, true);
    assert.equal(result.splitApkCount, 2);
    assert.equal(result.uploaded, false);
    assert.equal(result.published, false);
    for (const secret of ['synthetic-token', 'synthetic-download-id', credentials.private_key, calls[0].options.body.get('assertion')]) {
        assert.equal(JSON.stringify(result).includes(secret), false);
    }
});

/**
 * Исходные условия: token endpoint заменён посторонним адресом в JSON ключа.
 * Проверка должна отвергнуть credentials до первого сетевого вызова: assertion
 * не отправляется по адресу из файла. Это контроль маршрутизации, не TLS-тест.
 */
test('foreign token endpoint is rejected before network', async () => {
    let called = false;
    await assert.rejects(checkAccess({ ...credentials, token_uri: 'https://example.com/token' }, 26,
        async () => { called = true; }), { code: 'INVALID_SERVICE_ACCOUNT_FILE' });
    assert.equal(called, false);
});

/**
 * Исходные условия: пути внутри проекта, его корень и внешние соседние пути.
 * Проверяем запрет расположения credentials в репозитории без запрета внешних
 * файлов с похожим префиксом. Файлы и симлинки тест не создаёт/не читает.
 */
test('credentials location must be outside repository', () => {
    const root = resolve(fileURLToPath(new URL('..', import.meta.url)));
    for (const path of [root, resolve(root, '.test-tools', 'key.json'), resolve(root, 'scripts', 'key.json')]) {
        assert.throws(() => validateCredentialLocation(path, root), { code: 'CREDENTIALS_MUST_BE_OUTSIDE_REPOSITORY' });
    }
    validateCredentialLocation(resolve(root, '..', 'external-key.json'), root);
    validateCredentialLocation(resolve(root + '-sibling', 'key.json'), root);
});

/**
 * Исходные условия: пустой/неверного типа JSON, невалидный email и обычный
 * текст вместо ключа. validateCredentials отклоняет структуру; malformed PEM
 * отдельно отвергается криптобиблиотекой с безопасным кодом без исходного текста.
 */
test('malformed credentials fail without exposing their content', () => {
    for (const value of [null, {}, { ...credentials, type: 'authorized_user' }, { ...credentials, client_email: 'wrong' }, { ...credentials, private_key: 'private-secret' }]) {
        assert.throws(() => validateCredentials(value), { code: 'INVALID_SERVICE_ACCOUNT_FILE' });
    }
    assert.throws(() => createAssertion({ ...credentials, private_key: '-----BEGIN PRIVATE KEY-----\nsecret-invalid-pem' }), { message: 'INVALID_PRIVATE_KEY' });
});

/**
 * Исходные условия: корректный PKCS8 EC-ключ вместо RSA. Создание assertion
 * должно отклонить тип до RS256 sign. Проверяется ошибка выбора алгоритма,
 * а не разрешения сервисного аккаунта и не действительность credentials Google.
 */
test('non-RSA private key is rejected', () => {
    const pair = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
    assert.throws(() => createAssertion({ ...credentials, private_key: pair.privateKey.export({ type: 'pkcs8', format: 'pem' }) }), { code: 'PRIVATE_KEY_MUST_BE_RSA' });
});

/**
 * Исходные условия: допустимые границы кодов и строки с дробью, экспонентой,
 * выходом за диапазон. Проверяем целый диапазон 25..2100000000; при неверном
 * коде checkAccess не должен получать token или вызывать Play.
 */
test('version range rejects invalid values before network', async () => {
    assert.equal(validateVersionCode('25'), 25);
    assert.equal(validateVersionCode(2100000000), 2100000000);
    for (const value of ['24', '2100000001', '27.0', '1e2', 'abc']) {
        let called = false;
        await assert.rejects(checkAccess(credentials, value, async () => { called = true; }), { code: 'INVALID_VERSION_CODE' });
        assert.equal(called, false);
    }
});

/**
 * Исходные условия: после token обмена Play возвращает HTTP 403 и потенциально
 * чувствительное тело. Требуем безопасный PLAY_HTTP_403, отсутствие чтения
 * тела и повторов/других API. Проверяется обработка отказа, не настоящий доступ.
 */
test('Play refusal hides response body and performs no retry', async () => {
    let calls = 0;
    let bodyRead = false;
    await assert.rejects(checkAccess(credentials, 26, async () => {
        if (++calls === 1) return response({ token_type: 'Bearer', access_token: 'synthetic-token' });
        return { ok: false, status: 403, json: async () => { bodyRead = true; return { secret: 'hidden' }; } };
    }), { code: 'PLAY_HTTP_403' });
    assert.equal(calls, 2);
    assert.equal(bodyRead, false);
});

/**
 * Исходные условия: OAuth отвечает HTTP 400 либо транспорт выбрасывает ошибку
 * с секретным текстом. Требуем только безопасные коды; Play после отказа не
 * вызывается. Timeout/сеть здесь имитируются, реальный срок ожидания не измеряется.
 */
test('OAuth and transport failures stop before Play and hide details', async () => {
    let calls = 0;
    await assert.rejects(checkAccess(credentials, 26, async () => { calls++; return { ok: false, status: 400 }; }), { code: 'OAUTH_HTTP_400' });
    assert.equal(calls, 1);
    await assert.rejects(checkAccess(credentials, 26, async () => { throw new Error('secret-request-body'); }), { message: 'NETWORK_OR_TIMEOUT' });
});

/**
 * Исходные условия: успешный HTTP OAuth с отсутствующим токеном, неверным
 * token_type или CR/LF в токене. Требуем отказ до Play GET, исключая формирование
 * неправильного Authorization header. Все значения синтетические.
 */
test('invalid OAuth payload is rejected before authorized GET', async () => {
    for (const token of [{}, { token_type: 'Basic', access_token: 'fake' }, { token_type: 'Bearer', access_token: 'fake\r\nheader' }]) {
        let calls = 0;
        await assert.rejects(checkAccess(credentials, 26, async () => { calls++; return response(token); }), { code: 'INVALID_OAUTH_RESPONSE' });
        assert.equal(calls, 1);
    }
});

/**
 * Исходные условия: успешные метаданные с неверным сертификатом либо двумя
 * одинаковыми ожидаемыми группами. PASSED запрещён в обоих случаях; Upload key
 * и неоднозначность не считаются подтверждённой App signing группой.
 */
test('wrong or ambiguous app signing metadata is rejected', () => {
    for (const body of [{ generatedApks: [{ certificateSha256Hash: '97b60110a8e894791b4770826a8d214dc5e3a551cd24bd41d646c29f64a1dd26' }] },
        { generatedApks: [generated().generatedApks[0], generated().generatedApks[0]] }]) {
        assert.throws(() => summarizeGeneratedApks(body, 26), { code: 'EXPECTED_APP_SIGNING_CERTIFICATE_MISSING_OR_AMBIGUOUS' });
    }
    assert.throws(() => summarizeGeneratedApks({}, 26), { code: 'INVALID_APK_LIST' });
});

/**
 * Исходные условия: публичный отпечаток из настоящего ответа Play записан
 * 32 hex-байтами через двоеточия; имеются также неверные разделители/длина.
 * Проверяем точное равенство ранее подтверждённому SHA-256 после нормализации,
 * PASSED для этой группы и отказ при неверной форме или двух эквивалентных
 * группах. Это регрессия разбора метаданных, не apksigner и не сетевой запрос.
 */
test('Play colon-separated fingerprint matches confirmed SHA256 strictly', () => {
    const fingerprint = '4A:37:90:50:63:F6:89:6E:C5:3F:3A:EE:47:75:7C:05:41:CE:8C:50:94:D1:2F:9B:FB:21:A7:34:8F:2E:F1:7C';
    assert.equal(normalizeSigningFingerprint(fingerprint), CERTIFICATE);
    assert.equal(summarizeGeneratedApks({ generatedApks: [{ certificateSha256Hash: fingerprint }] }, 26).status, 'PASSED');
    for (const invalid of [fingerprint.replaceAll(':', '-'), fingerprint.slice(3), ':' + fingerprint, fingerprint + ':', fingerprint + 'junk', null]) {
        assert.equal(normalizeSigningFingerprint(invalid), null);
    }
    assert.throws(() => summarizeGeneratedApks({ generatedApks: [{ certificateSha256Hash: fingerprint }, { certificateSha256Hash: CERTIFICATE }] }, 26), { code: 'EXPECTED_APP_SIGNING_CERTIFICATE_MISSING_OR_AMBIGUOUS' });
});

/**
 * Исходные условия: ожидаемая подписывающая группа есть, универсального APK нет.
 * Отчёт подтверждает доступ к метаданным, но universalApkAvailable=false:
 * доступ к API не равен наличию файла или успешному скачиванию/установке.
 */
test('API connection does not imply universal APK availability', () => {
    assert.equal(summarizeGeneratedApks({ generatedApks: [{ certificateSha256Hash: CERTIFICATE.toUpperCase() }] }, 26).universalApkAvailable, false);
});
