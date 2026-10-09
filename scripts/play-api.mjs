// Проверка доступа к одному приложению. Ключ и OAuth token остаются в памяти;
// в отчёт попадают только статус доступа и сведения о подписанных APK.
import { readFile, realpath, writeFile } from 'node:fs/promises';
import { createPrivateKey, sign } from 'node:crypto';
import { dirname, relative, resolve, isAbsolute } from 'node:path';
import { fileURLToPath } from 'node:url';

export const PACKAGE = 'com.vpe_soft.intime.intime';
export const CERTIFICATE = '4a37905063f6896ec53f3aee47757c0541ce8c5094d12f9bfb21a7348f2ef17c';
const TOKEN_URL = 'https://oauth2.googleapis.com/token';
const SCOPE = 'https://www.googleapis.com/auth/androidpublisher';
const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');

// Собственные коды ошибок не содержат серверные ответы, ключи или токены.
export class PlayApiError extends Error {
    constructor(code) { super(code); this.code = code; }
}

export function validateVersionCode(value) {
    if (!/^\d+$/.test(String(value))) throw new PlayApiError('INVALID_VERSION_CODE');
    const code = Number(value);
    if (!Number.isSafeInteger(code) || code < 25 || code > 2100000000) {
        throw new PlayApiError('INVALID_VERSION_CODE');
    }
    return code;
}

export function validateCredentialLocation(path, root = ROOT) {
    const part = relative(root, path);
    if (part === '' || (!isAbsolute(part) && part !== '..' && !part.startsWith('..\\') && !part.startsWith('../'))) {
        throw new PlayApiError('CREDENTIALS_MUST_BE_OUTSIDE_REPOSITORY');
    }
}

export function validateCredentials(credentials) {
    if (credentials?.type !== 'service_account'
        || typeof credentials.client_email !== 'string'
        || !/^[^\s@]+@[^\s@]+\.gserviceaccount\.com$/.test(credentials.client_email)
        || typeof credentials.private_key !== 'string'
        || !credentials.private_key.startsWith('-----BEGIN PRIVATE KEY-----')
        || credentials.token_uri !== TOKEN_URL) {
        throw new PlayApiError('INVALID_SERVICE_ACCOUNT_FILE');
    }
}

// RS256 выполняет стандартная криптобиблиотека Node. Адрес обмена фиксирован:
// token_uri из JSON не может перенаправить подписанное утверждение на чужой сервер.
export function createAssertion(credentials, now = Math.floor(Date.now() / 1000)) {
    validateCredentials(credentials);
    const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url');
    const content = encode({ alg: 'RS256', typ: 'JWT' }) + '.' + encode({
        iss: credentials.client_email, scope: SCOPE, aud: TOKEN_URL,
        iat: now, exp: now + 300,
    });
    let key;
    try { key = createPrivateKey(credentials.private_key); }
    catch { throw new PlayApiError('INVALID_PRIVATE_KEY'); }
    if (key.asymmetricKeyType !== 'rsa') throw new PlayApiError('PRIVATE_KEY_MUST_BE_RSA');
    return content + '.' + sign('RSA-SHA256', Buffer.from(content), key).toString('base64url');
}

export async function requestJson(url, options, transport, timeoutMs = 60000) {
    let response;
    try {
        response = await transport(url, { ...options, redirect: 'error', signal: AbortSignal.timeout(timeoutMs) });
    } catch { throw new PlayApiError('NETWORK_OR_TIMEOUT'); }
    if (!response.ok) {
        const phase = url === TOKEN_URL ? 'OAUTH' : 'PLAY';
        throw new PlayApiError(`${phase}_HTTP_${response.status}`);
    }
    try { return await response.json(); }
    catch { throw new PlayApiError('INVALID_API_RESPONSE'); }
}

// Минимальный безопасный отчёт: не сохраняем полный HTTP response/downloadId.
// Сверка указанного API сертификата не заменяет apksigner verify скачанного файла.
export function normalizeSigningFingerprint(value) {
    if (typeof value !== 'string') return null;
    if (/^[0-9a-fA-F]{64}$/.test(value)) return value.toLowerCase();
    // Реальный Play ответ содержит 32 байта с двоеточиями, как Console/keytool.
    // Принимаем только точную форму; произвольные символы не удаляем.
    if (/^(?:[0-9a-fA-F]{2}:){31}[0-9a-fA-F]{2}$/.test(value)) return value.replaceAll(':', '').toLowerCase();
    return null;
}

export function summarizeGeneratedApks(body, versionCode) {
    if (!Array.isArray(body?.generatedApks)) throw new PlayApiError('INVALID_APK_LIST');
    const matched = body.generatedApks.filter(item =>
        normalizeSigningFingerprint(item?.certificateSha256Hash) === CERTIFICATE);
    if (matched.length !== 1) throw new PlayApiError('EXPECTED_APP_SIGNING_CERTIFICATE_MISSING_OR_AMBIGUOUS');
    const group = matched[0];
    return {
        status: 'PASSED', packageName: PACKAGE, versionCode,
        appSigningCertificateSha256: CERTIFICATE,
        universalApkAvailable: typeof group.generatedUniversalApk?.downloadId === 'string'
            && group.generatedUniversalApk.downloadId.length > 0,
        splitApkCount: Array.isArray(group.generatedSplitApks) ? group.generatedSplitApks.length : 0,
        standaloneApkCount: Array.isArray(group.generatedStandaloneApks) ? group.generatedStandaloneApks.length : 0,
        uploaded: false, published: false, phoneUsed: false,
        scope: 'OAuth token exchange and generatedapks.list only',
    };
}

export async function getAccessToken(credentials, transport = fetch) {
    const assertion = createAssertion(credentials);
    const token = await requestJson(TOKEN_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion }),
    }, transport);
    if (token.token_type !== 'Bearer' || typeof token.access_token !== 'string'
        || !token.access_token || /[\r\n]/.test(token.access_token)) {
        throw new PlayApiError('INVALID_OAUTH_RESPONSE');
    }
    return token.access_token;
}

export async function checkAccess(credentials, version, transport = fetch) {
    const code = validateVersionCode(version);
    const token = await getAccessToken(credentials, transport);
    const body = await requestJson(
        `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${PACKAGE}/generatedApks/${code}`,
        { method: 'GET', headers: { Authorization: `Bearer ${token}` } }, transport);
    return summarizeGeneratedApks(body, code);
}

export async function loadCredentials(keyFile) {
    let path;
    try { path = await realpath(keyFile); }
    catch { throw new PlayApiError('CREDENTIAL_FILE_MISSING_OR_UNREADABLE'); }
    validateCredentialLocation(path, await realpath(ROOT));
    let credentials;
    try { credentials = JSON.parse((await readFile(path, 'utf8')).replace(/^\uFEFF/, '')); }
    catch { throw new PlayApiError('INVALID_SERVICE_ACCOUNT_FILE'); }
    validateCredentials(credentials);
    return credentials;
}

async function main() {
    const args = process.argv.slice(2);
    const parsed = {};
    for (let index = 0; index < args.length; index += 2) {
        const name = args[index];
        if (!['--key-file', '--version-code', '--report'].includes(name)
            || !args[index + 1] || parsed[name]) throw new PlayApiError('INVALID_ARGUMENTS');
        parsed[name] = args[index + 1];
    }
    if (!parsed['--key-file'] || !parsed['--version-code'] || !parsed['--report']) {
        throw new PlayApiError('INVALID_ARGUMENTS');
    }
    const reportPath = resolve(parsed['--report']);
    const reportPart = relative(resolve(ROOT, '.test-tools', 'runs'), reportPath);
    if (isAbsolute(reportPart) || reportPart === '..' || reportPart.startsWith('..\\') || reportPart.startsWith('../') || !reportPart) {
        throw new PlayApiError('REPORT_MUST_BE_IN_PROJECT_RUNS');
    }
    let result;
    try {
        const code = validateVersionCode(parsed['--version-code']);
        const credentials = await loadCredentials(parsed['--key-file']);
        result = await checkAccess(credentials, code);
    } catch (error) {
        result = { status: 'FAILED', code: error instanceof PlayApiError ? error.code : 'LOCAL_SETUP_ERROR',
            uploaded: false, published: false, phoneUsed: false };
        process.exitCode = 1;
    }
    await writeFile(reportPath, JSON.stringify(result, null, 2) + '\n', { flag: 'wx' });
    console.log(JSON.stringify(result));
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    main().catch(error => {
        console.error(error instanceof PlayApiError ? error.code : 'LOCAL_SETUP_ERROR');
        process.exitCode = 1;
    });
}
