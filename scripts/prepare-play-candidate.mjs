// Точный проверенный кандидат 27: upload в временный edit, БЕЗ track update/commit.
// Скачанный APK затем обязательно проверяется отдельным verify-play-apk.ps1.
import { readFile, writeFile, realpath } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { dirname, resolve, relative, isAbsolute } from 'node:path';
import { fileURLToPath } from 'node:url';
import { PACKAGE, CERTIFICATE, PlayApiError, getAccessToken, loadCredentials,
    normalizeSigningFingerprint, requestJson } from './play-api.mjs';

export const VERSION_CODE = 27;
export const AAB_SHA256 = 'b8f0a7e09e1ae4e1742972df769a3134c654a0943890e11b4f78234d07c62fb9';
const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const BASE = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${PACKAGE}`;
const AAB = resolve(ROOT, '.test-tools/runs/20261009-103501-release27-historical-json/candidate/intime-27-2.0.0-rc3.aab');
const MAX_APK_BYTES = 10 * 1024 * 1024;
export const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');

export function assertCandidate(bytes) {
    if (sha256(bytes) !== AAB_SHA256) throw new PlayApiError('CANDIDATE_AAB_HASH_MISMATCH');
}

export function assertBundle(bundle) {
    if (bundle?.versionCode !== VERSION_CODE || typeof bundle.sha256 !== 'string'
        || bundle.sha256.toLowerCase() !== AAB_SHA256) throw new PlayApiError('REMOTE_BUNDLE_IDENTITY_MISMATCH');
}

// Checkpoint сохраняет только разрешённые поля состояния, ни token, ни server body.
// Upload не повторяется автоматически при таймауте: результат может быть неопределён.
export async function prepareCandidate(token, bytes, transport = fetch, checkpoint = async () => {}) {
    assertCandidate(bytes);
    const headers = { Authorization: `Bearer ${token}` };
    const edit = await requestJson(`${BASE}/edits`, {
        method: 'POST', headers: { ...headers, 'Content-Type': 'application/json' }, body: '{}',
    }, transport);
    if (typeof edit.id !== 'string' || !/^[a-zA-Z0-9_-]+$/.test(edit.id)) throw new PlayApiError('INVALID_EDIT_ID');
    const state = { editId: edit.id, editExpiryTime: edit.expiryTimeSeconds,
        versionCode: VERSION_CODE, aabSha256: AAB_SHA256, uploadAttempted: false,
        uploaded: false, reusedExistingBundle: false, committed: false, published: false };
    await checkpoint(state);
    const url = `${BASE}/edits/${encodeURIComponent(edit.id)}/bundles`;
    const existing = await requestJson(url, { method: 'GET', headers }, transport);
    const bundles = existing.bundles ?? [];
    if (!Array.isArray(bundles)) throw new PlayApiError('INVALID_BUNDLE_LIST');
    if (bundles.some(bundle => bundle.versionCode > VERSION_CODE)) throw new PlayApiError('NEWER_REMOTE_VERSION_ALREADY_EXISTS');
    const matches = bundles.filter(bundle => bundle.versionCode === VERSION_CODE);
    if (matches.length > 1) throw new PlayApiError('AMBIGUOUS_REMOTE_BUNDLE');
    if (matches.length === 1) {
        assertBundle(matches[0]);
        state.reusedExistingBundle = true;
    } else {
        state.uploadAttempted = true;
        await checkpoint(state);
        const uploadUrl = `https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/${PACKAGE}/edits/${encodeURIComponent(edit.id)}/bundles?uploadType=media`;
        const uploaded = await requestJson(uploadUrl, {
            method: 'POST', headers: { ...headers, 'Content-Type': 'application/octet-stream' }, body: bytes,
        }, transport, 300000);
        // HTTP успех уже означает upload; при неверном ответе не теряем факт загрузки.
        state.uploaded = true;
        await checkpoint(state);
        assertBundle(uploaded);
    }
    await checkpoint(state);
    return state;
}

export function selectUniversal(body) {
    if (!Array.isArray(body?.generatedApks)) throw new PlayApiError('INVALID_APK_LIST');
    const groups = body.generatedApks.filter(group => normalizeSigningFingerprint(group.certificateSha256Hash) === CERTIFICATE);
    if (groups.length !== 1) throw new PlayApiError('EXPECTED_APP_SIGNING_CERTIFICATE_MISSING_OR_AMBIGUOUS');
    const id = groups[0].generatedUniversalApk?.downloadId;
    if (typeof id !== 'string' || !id) throw new PlayApiError('UNIVERSAL_APK_NOT_READY');
    return id;
}

// Размер ограничен до записи на диск; неправильный HTTP статус не сохраняется как APK.
// Redirect запрещён, Authorization не отправляется по адресу из API body.
export async function downloadCandidate(token, transport = fetch) {
    const headers = { Authorization: `Bearer ${token}` };
    const body = await requestJson(`${BASE}/generatedApks/${VERSION_CODE}`, { method: 'GET', headers }, transport);
    const id = selectUniversal(body);
    let response;
    try {
        response = await transport(`${BASE}/generatedApks/${VERSION_CODE}/downloads/${encodeURIComponent(id)}:download?alt=media`, {
            method: 'GET', headers, redirect: 'error', signal: AbortSignal.timeout(60000),
        });
    } catch { throw new PlayApiError('APK_DOWNLOAD_NETWORK_OR_TIMEOUT'); }
    if (!response.ok) throw new PlayApiError(`APK_DOWNLOAD_HTTP_${response.status}`);
    if (Number(response.headers.get('content-length')) > MAX_APK_BYTES) throw new PlayApiError('APK_EXCEEDS_10_MIB_LIMIT');
    const chunks = []; let size = 0;
    try {
        for await (const part of response.body) {
            size += part.length;
            if (size > MAX_APK_BYTES) throw new PlayApiError('APK_EXCEEDS_10_MIB_LIMIT');
            chunks.push(Buffer.from(part));
        }
    } catch (error) {
        if (error instanceof PlayApiError) throw error;
        throw new PlayApiError('APK_DOWNLOAD_STREAM_FAILED');
    }
    const bytes = Buffer.concat(chunks);
    if (bytes.length < 4 || bytes.readUInt32LE(0) !== 0x04034b50) throw new PlayApiError('DOWNLOADED_FILE_IS_NOT_ZIP_APK');
    return bytes;
}

async function main() {
    const [mode, keyPath, runPath] = process.argv.slice(2);
    if (!['prepare', 'download'].includes(mode) || !keyPath || !runPath || process.argv.length !== 5) throw new PlayApiError('INVALID_ARGUMENTS');
    const run = await realpath(runPath);
    const part = relative(await realpath(resolve(ROOT, '.test-tools/runs')), run);
    if (isAbsolute(part) || !part || part === '..' || part.startsWith('..\\') || part.startsWith('../')) throw new PlayApiError('INVALID_RUN_DIRECTORY');
    const summary = { status: 'RUNNING', mode, versionCode: VERSION_CODE, versionName: '2.0.0-rc3',
        uploaded: false, committed: false, published: false, phoneUsed: false, stage: 'LOCAL_PREFLIGHT' };
    const save = async extra => {
        Object.assign(summary, extra);
        await writeFile(resolve(run, 'summary.json'), JSON.stringify(summary, null, 2) + '\n');
    };
    await save({});
    try {
        // Проверенный payload нужен даже для download: не путаем candidate с чужой сборкой.
        const bytes = await readFile(AAB); assertCandidate(bytes);
        const credentials = await loadCredentials(keyPath);
        await save({ stage: 'OAUTH' });
        const token = await getAccessToken(credentials);
        if (mode === 'prepare') {
            await save({ stage: 'PREPARE_EDIT' });
            await prepareCandidate(token, bytes, fetch, async state => save({ ...state, stage: state.uploadAttempted ? 'UPLOAD_AAB' : 'LIST_BUNDLES' }));
        }
        await save({ stage: 'DOWNLOAD_APK' });
        const apk = await downloadCandidate(token);
        const apkPath = resolve(run, 'intime-play-27-2.0.0-rc3.apk');
        await writeFile(apkPath, apk, { flag: 'wx' });
        await save({ status: 'DOWNLOADED_NOT_VERIFIED', stage: 'DONE', apk: apkPath, apkBytes: apk.length, apkSha256: sha256(apk) });
    } catch (error) {
        await save({ status: 'FAILED', code: error instanceof PlayApiError ? error.code : 'LOCAL_SETUP_ERROR' });
        process.exitCode = 1;
    }
    console.log(JSON.stringify(summary));
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    main().catch(error => { console.error(error instanceof PlayApiError ? error.code : 'LOCAL_SETUP_ERROR'); process.exitCode = 1; });
}
