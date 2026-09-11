import http from "node:http";
import fs from "node:fs/promises";
import path from "node:path";
import crypto from "node:crypto";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";

const HOST = process.env.RUNTIME_HOST || "127.0.0.1";
const PORT = Number(process.env.RUNTIME_PORT || 8787);
const ROOT = path.resolve(process.env.RUNTIME_WORKSPACE || path.join(path.dirname(fileURLToPath(import.meta.url)), "runtime-workspace"));
const SESSION_ROOT = path.join(ROOT, ".sessions");
const ADB = process.env.ADB_PATH || "adb";
const GRADLE = process.env.GRADLE_PATH || "gradle";
const RUNTIME_TOKEN = (process.env.RUNTIME_TOKEN || "").trim();
const MAX_BODY = 4 * 1024 * 1024;
const MAX_FILES = 2000;
const MAX_FILE_BYTES = 2_000_000_000;
const MAX_PROJECT_BYTES = 2_000_000_000;
const MAX_CHUNK_BYTES = 1_000_000;
const TASK = /^[A-Za-z0-9:_-]{1,80}$/;
const PROJECT = /^[A-Za-z0-9._-]+$/;

if (!RUNTIME_TOKEN) throw new Error("RUNTIME_TOKEN is required.");
async function ensureRoot() { await fs.mkdir(ROOT, { recursive: true }); await fs.mkdir(SESSION_ROOT, { recursive: true }); }
function json(res, code, value) { const body = JSON.stringify(value); res.writeHead(code, { "content-type": "application/json" }); res.end(body); }
function body(req) { return new Promise((resolve, reject) => { let size = 0; const chunks = []; let failed = false; req.on("data", c => { if (failed) return; size += c.length; if (size > MAX_BODY) { failed = true; reject(new Error("request too large; use chunked transfer")); req.destroy(); } else chunks.push(c); }); req.on("end", () => { if (failed) return; try { resolve(JSON.parse(Buffer.concat(chunks).toString("utf8"))); } catch (e) { reject(e); } }); req.on("error", reject); }); }
function run(file, args, cwd, timeoutMs = 20 * 60 * 1000) { return new Promise(resolve => { const p = spawn(file, args, { cwd, shell: false }); let out = ""; p.stdout.on("data", d => out += d); p.stderr.on("data", d => out += d); const timer = setTimeout(() => { p.kill("SIGKILL"); resolve({ exitCode: 124, output: out + "\nTIMEOUT" }); }, timeoutMs); p.on("error", e => { clearTimeout(timer); resolve({ exitCode: 127, output: String(e?.message || e) }); }); p.on("close", code => { clearTimeout(timer); resolve({ exitCode: code ?? 1, output: out.slice(-120000) }); }); }); }
function runBinary(file, args, cwd, timeoutMs = 60000) { return new Promise(resolve => { const p = spawn(file, args, { cwd, shell: false }); const chunks = []; const errors = []; p.stdout.on("data", d => chunks.push(d)); p.stderr.on("data", d => errors.push(d)); const timer = setTimeout(() => { p.kill("SIGKILL"); resolve({ exitCode: 124, bytes: Buffer.concat(chunks), output: Buffer.concat(errors).toString("utf8") + "\nTIMEOUT" }); }, timeoutMs); p.on("error", e => { clearTimeout(timer); resolve({ exitCode: 127, bytes: Buffer.alloc(0), output: String(e?.message || e) }); }); p.on("close", code => { clearTimeout(timer); resolve({ exitCode: code ?? 1, bytes: Buffer.concat(chunks), output: Buffer.concat(errors).toString("utf8") }); }); }); }
function safeTaskList(tasks = []) { if (!Array.isArray(tasks) || tasks.length > 16 || tasks.some(t => typeof t !== "string" || !TASK.test(t))) throw new Error("invalid task list"); return tasks; }
function safeProjectName(projectName) { if (typeof projectName !== "string" || !PROJECT.test(projectName)) throw new Error("invalid project name"); return projectName; }
function projectPath(projectName) { const name = safeProjectName(projectName); const dir = path.resolve(ROOT, name); if (!dir.startsWith(ROOT + path.sep)) throw new Error("path escape"); return dir; }
function sessionPath(id) { if (typeof id !== "string" || !/^[a-f0-9-]{36}$/.test(id)) throw new Error("invalid session id"); return path.resolve(SESSION_ROOT, id); }
function relativeSafe(rel) { if (typeof rel !== "string" || !rel || rel.startsWith("/") || rel.includes("..") || rel.includes("\\")) throw new Error("invalid relative path"); return rel; }
async function writeProject(projectName, files) { const name = safeProjectName(projectName); if (!files || typeof files !== "object" || Array.isArray(files) || Object.keys(files).length > MAX_FILES) throw new Error("invalid files"); const dir = projectPath(name); await fs.rm(dir, { recursive: true, force: true }); await fs.mkdir(dir, { recursive: true }); let total = 0; for (const [rel, content] of Object.entries(files)) { relativeSafe(rel); if (typeof content !== "string" || content.length > MAX_FILE_BYTES) throw new Error("invalid file: " + rel); total += Buffer.byteLength(content, "utf8"); if (total > MAX_PROJECT_BYTES) throw new Error("project exceeds maximum size"); const target = path.resolve(dir, rel); if (!target.startsWith(dir + path.sep)) throw new Error("path escape"); await fs.mkdir(path.dirname(target), { recursive: true }); await fs.writeFile(target, content, "utf8"); } await makeGradleExecutable(dir); return dir; }
async function makeGradleExecutable(project) { const wrapper = path.join(project, "gradlew"); try { await fs.chmod(wrapper, 0o755); } catch {} }
async function findApk(project) { const apk = path.join(project, "app/build/outputs/apk/debug/app-debug.apk"); try { await fs.access(apk); return apk; } catch { return null; } }
async function adbDevices() { const r = await run(ADB, ["devices"], ROOT, 10000); const connected = r.exitCode === 0 ? r.output.split("\n").slice(1).filter(line => /\tdevice\s*$/.test(line)).length : 0; return { result: r, connected }; }
async function readSession(id) { const dir = sessionPath(id); const meta = JSON.parse(await fs.readFile(path.join(dir, "session.json"), "utf8")); return { dir, meta }; }
async function startSession(data) {
  const name = safeProjectName(data.projectName);
  if (!Array.isArray(data.files) || data.files.length === 0 || data.files.length > MAX_FILES) throw new Error("invalid file manifest");
  let total = 0;
  const files = data.files.map(item => {
    const rel = relativeSafe(item?.path);
    const bytes = Number(item?.bytes);
    if (!Number.isSafeInteger(bytes) || bytes < 0 || bytes > MAX_FILE_BYTES) throw new Error("invalid file size: " + rel);
    total += bytes;
    return { path: rel, bytes };
  });
  if (total > MAX_PROJECT_BYTES) throw new Error("project exceeds maximum size");
  const id = crypto.randomUUID();
  const dir = sessionPath(id);
  await fs.mkdir(dir, { recursive: true });
  await fs.writeFile(path.join(dir, "session.json"), JSON.stringify({ id, projectName: name, files, totalBytes: total, receivedBytes: 0, finalized: false }), "utf8");
  return { sessionId: id, maxChunkBytes: MAX_CHUNK_BYTES, totalBytes: total };
}
async function appendChunk(data) {
  const { dir, meta } = await readSession(data.sessionId);
  if (meta.finalized) throw new Error("session already finalized");
  const rel = relativeSafe(data.path);
  const expected = meta.files.find(f => f.path === rel);
  if (!expected) throw new Error("file is not in manifest");
  const offset = Number(data.offset);
  if (!Number.isSafeInteger(offset) || offset < 0 || offset > expected.bytes) throw new Error("invalid chunk offset");
  const bytes = Buffer.from(String(data.base64 || ""), "base64");
  if (bytes.length === 0 && expected.bytes > 0) throw new Error("empty chunk");
  if (bytes.length > MAX_CHUNK_BYTES || offset + bytes.length > expected.bytes) throw new Error("invalid chunk size");
  const target = path.resolve(dir, "project", rel);
  if (!target.startsWith(path.resolve(dir, "project") + path.sep)) throw new Error("path escape");
  await fs.mkdir(path.dirname(target), { recursive: true });
  let current = 0;
  try { current = (await fs.stat(target)).size; } catch {}
  if (current !== offset) throw new Error(`offset mismatch: expected ${current}, got ${offset}`);
  await fs.appendFile(target, bytes);
  meta.receivedBytes += bytes.length;
  await fs.writeFile(path.join(dir, "session.json"), JSON.stringify(meta), "utf8");
  return { received: bytes.length, fileReceived: offset + bytes.length, fileTotal: expected.bytes, totalReceived: meta.receivedBytes, totalBytes: meta.totalBytes };
}
async function finalizeSession(id) {
  const { dir, meta } = await readSession(id);
  for (const file of meta.files) {
    const target = path.join(dir, "project", file.path);
    let size = 0; try { size = (await fs.stat(target)).size; } catch { throw new Error("missing file: " + file.path); }
    if (size !== file.bytes) throw new Error(`incomplete file: ${file.path}`);
  }
  await makeGradleExecutable(path.join(dir, "project"));
  meta.finalized = true;
  await fs.writeFile(path.join(dir, "session.json"), JSON.stringify(meta), "utf8");
  return { sessionId: id, projectName: meta.projectName, project: path.join(dir, "project"), totalBytes: meta.totalBytes };
}
async function gradleCommand(project, tasks, timeoutMs) {
  const wrapper = path.join(project, "gradlew");
  try { await fs.access(wrapper); await fs.chmod(wrapper, 0o755); return await run(wrapper, tasks, project, timeoutMs); }
  catch { return await run(GRADLE, tasks, project, timeoutMs); }
}

const server = http.createServer(async (req, res) => {
  try {
    if (req.headers.authorization !== `Bearer ${RUNTIME_TOKEN}`) return json(res, 401, { error: "unauthorized" });
    if (req.method === "GET" && req.url === "/health") {
      const adb = await adbDevices();
      const gradle = await run(GRADLE, ["--version"], ROOT, 15000);
      return json(res, 200, { ok: true, runtime: "companion-runtime", build: gradle.exitCode === 0, test: adb.connected > 0 && gradle.exitCode === 0, install: adb.connected > 0, logcat: adb.connected > 0, screenshot: adb.connected > 0, adb: adb.result.exitCode === 0, gradle: gradle.exitCode === 0, connectedDevices: adb.connected, chunkedTransfer: true, maxProjectBytes: MAX_PROJECT_BYTES });
    }
    if (req.method !== "POST") return json(res, 405, { error: "method not allowed" });
    const data = await body(req);
    if (req.url === "/transfer/start") return json(res, 200, await startSession(data));
    if (req.url === "/transfer/chunk") return json(res, 200, await appendChunk(data));
    if (req.url === "/transfer/finalize") return json(res, 200, await finalizeSession(data.sessionId));
    if (req.url === "/build") { const project = await writeProject(data.projectName, data.files); const tasks = safeTaskList(data.tasks?.length ? data.tasks : ["assembleDebug"]); const r = await gradleCommand(project, tasks, 20 * 60 * 1000); const apk = r.exitCode === 0 ? await findApk(project) : null; return json(res, 200, { ...r, artifact: apk }); }
    if (req.url === "/build-session") { const { dir, meta } = await readSession(data.sessionId); if (!meta.finalized) throw new Error("session is not finalized"); const tasks = safeTaskList(data.tasks?.length ? data.tasks : ["assembleDebug"]); const project = path.join(dir, "project"); const r = await gradleCommand(project, tasks, 20 * 60 * 1000); const apk = r.exitCode === 0 ? await findApk(project) : null; return json(res, 200, { ...r, artifact: apk }); }
    if (req.url === "/test") { const adb = await adbDevices(); if (adb.connected === 0) return json(res, 200, { exitCode: 125, output: "No Android device connected for tests." }); const project = projectPath(data.projectName); const tasks = safeTaskList(data.tasks); if (tasks.length === 0) return json(res, 200, { exitCode: 0, output: "No test tasks requested." }); return json(res, 200, await gradleCommand(project, tasks, 20 * 60 * 1000)); }
    if (req.url === "/test-session") { const adb = await adbDevices(); if (adb.connected === 0) return json(res, 200, { exitCode: 125, output: "No Android device connected for tests." }); const { dir, meta } = await readSession(data.sessionId); if (!meta.finalized) throw new Error("session is not finalized"); const tasks = safeTaskList(data.tasks); if (tasks.length === 0) return json(res, 200, { exitCode: 0, output: "No test tasks requested." }); return json(res, 200, await gradleCommand(path.join(dir, "project"), tasks, 20 * 60 * 1000)); }
    if (req.url === "/install") { const adb = await adbDevices(); if (adb.connected === 0) return json(res, 200, { exitCode: 125, output: "No Android device connected for install." }); if (typeof data.base64 !== "string" || !data.base64) throw new Error("missing apk"); const apk = path.join(ROOT, "runtime-upload.apk"); await fs.writeFile(apk, Buffer.from(data.base64, "base64")); const r = await run(ADB, ["install", "-r", apk], ROOT, 5 * 60 * 1000); await fs.rm(apk, { force: true }); return json(res, 200, r); }
    if (req.url === "/logcat") return json(res, 200, await run(ADB, ["logcat", "-d", "-t", "500"], ROOT, 60 * 1000));
    if (req.url === "/screenshot") { const r = await runBinary(ADB, ["exec-out", "screencap", "-p"], ROOT); return json(res, 200, { exitCode: r.exitCode, output: r.output, base64: r.exitCode === 0 ? r.bytes.toString("base64") : "" }); }
    if (req.url === "/artifact") { if (typeof data.path !== "string") throw new Error("missing artifact path"); const artifact = path.resolve(ROOT, data.path); if (!artifact.startsWith(ROOT + path.sep)) throw new Error("path escape"); const bytes = await fs.readFile(artifact); res.writeHead(200, { "content-type": "application/vnd.android.package-archive", "content-length": bytes.length }); return res.end(bytes); }
    return json(res, 404, { error: "unknown endpoint" });
  } catch (e) { return json(res, 400, { exitCode: 2, output: e?.message || String(e) }); }
});
await ensureRoot();
server.listen(PORT, HOST, () => console.log(`Companion Runtime listening on http://${HOST}:${PORT}`));