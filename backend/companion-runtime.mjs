import http from "node:http";
import fs from "node:fs/promises";
import path from "node:path";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";

const HOST = process.env.RUNTIME_HOST || "127.0.0.1";
const PORT = Number(process.env.RUNTIME_PORT || 8787);
const ROOT = path.resolve(process.env.RUNTIME_WORKSPACE || path.join(path.dirname(fileURLToPath(import.meta.url)), "runtime-workspace"));
const ADB = process.env.ADB_PATH || "adb";
const GRADLE = process.env.GRADLE_PATH || "./gradlew";
const MAX_BODY = 20 * 1024 * 1024;
const TASK = /^[A-Za-z0-9:_-]{1,80}$/;
const PROJECT = /^[A-Za-z0-9._-]+$/;

async function ensureRoot() { await fs.mkdir(ROOT, { recursive: true }); }
function json(res, code, value) { const body = JSON.stringify(value); res.writeHead(code, { "content-type": "application/json" }); res.end(body); }
function body(req) { return new Promise((resolve, reject) => { let size = 0; const chunks = []; req.on("data", c => { size += c.length; if (size > MAX_BODY) { reject(new Error("request too large")); req.destroy(); } else chunks.push(c); }); req.on("end", () => { try { resolve(JSON.parse(Buffer.concat(chunks).toString("utf8"))); } catch (e) { reject(e); } }); req.on("error", reject); }); }
function run(file, args, cwd, timeoutMs = 20 * 60 * 1000) { return new Promise(resolve => { const p = spawn(file, args, { cwd, shell: false }); let out = ""; p.stdout.on("data", d => out += d); p.stderr.on("data", d => out += d); const timer = setTimeout(() => { p.kill("SIGKILL"); resolve({ exitCode: 124, output: out + "\nTIMEOUT" }); }, timeoutMs); p.on("close", code => { clearTimeout(timer); resolve({ exitCode: code ?? 1, output: out.slice(-120000) }); }); }); }
function safeTaskList(tasks = []) { if (!Array.isArray(tasks) || tasks.length > 16 || tasks.some(t => typeof t !== "string" || !TASK.test(t))) throw new Error("invalid task list"); return tasks; }
function safeProjectName(projectName) { if (typeof projectName !== "string" || !PROJECT.test(projectName)) throw new Error("invalid project name"); return projectName; }
function projectPath(projectName) { const name = safeProjectName(projectName); const dir = path.resolve(ROOT, name); if (!dir.startsWith(ROOT + path.sep)) throw new Error("path escape"); return dir; }
async function writeProject(projectName, files) { const name = safeProjectName(projectName); if (!files || typeof files !== "object" || Array.isArray(files) || Object.keys(files).length > 2000) throw new Error("invalid files"); const dir = projectPath(name); await fs.rm(dir, { recursive: true, force: true }); await fs.mkdir(dir, { recursive: true }); for (const [rel, content] of Object.entries(files)) { if (!rel || rel.startsWith("/") || rel.includes("..") || typeof content !== "string" || content.length > 2000000) throw new Error("invalid file: " + rel); const target = path.resolve(dir, rel); if (!target.startsWith(dir + path.sep)) throw new Error("path escape"); await fs.mkdir(path.dirname(target), { recursive: true }); await fs.writeFile(target, content, "utf8"); } return dir; }
async function findApk(project) { const apk = path.join(project, "app/build/outputs/apk/debug/app-debug.apk"); try { await fs.access(apk); return apk; } catch { return null; } }
function runBinary(file, args, cwd, timeoutMs = 60000) { return new Promise(resolve => { const p = spawn(file, args, { cwd, shell: false }); const chunks = []; const errors = []; p.stdout.on("data", d => chunks.push(d)); p.stderr.on("data", d => errors.push(d)); const timer = setTimeout(() => { p.kill("SIGKILL"); resolve({ exitCode: 124, bytes: Buffer.concat(chunks), output: Buffer.concat(errors).toString("utf8") + "\nTIMEOUT" }); }, timeoutMs); p.on("close", code => { clearTimeout(timer); resolve({ exitCode: code ?? 1, bytes: Buffer.concat(chunks), output: Buffer.concat(errors).toString("utf8") }); }); }); }

const server = http.createServer(async (req, res) => {
  try {
    if (req.method === "GET" && req.url === "/health") {
      const adbState = await run(ADB, ["devices"], ROOT, 10000);
      const devices = adbState.exitCode === 0 ? adbState.output.split("\n").slice(1).filter(line => /\tdevice\s*$/.test(line)).length : 0;
      return json(res, 200, { ok: true, runtime: "companion-runtime", build: true, test: true, install: true, logcat: true, screenshot: true, adb: adbState.exitCode === 0, connectedDevices: devices });
    }
    if (req.method !== "POST") return json(res, 405, { error: "method not allowed" });
    const data = await body(req);
    if (req.url === "/build") { const project = await writeProject(data.projectName, data.files); const tasks = safeTaskList(data.tasks?.length ? data.tasks : ["assembleDebug"]); const r = await run(GRADLE, tasks, project); const apk = r.exitCode === 0 ? await findApk(project) : null; return json(res, 200, { ...r, artifact: apk }); }
    if (req.url === "/test") { const project = projectPath(data.projectName); const tasks = safeTaskList(data.tasks); const r = await run(GRADLE, tasks, project); return json(res, 200, r); }
    if (req.url === "/install") { if (typeof data.base64 !== "string" || !data.base64) throw new Error("missing apk"); const apk = path.join(ROOT, "runtime-upload.apk"); await fs.writeFile(apk, Buffer.from(data.base64, "base64")); const r = await run(ADB, ["install", "-r", apk], ROOT, 5 * 60 * 1000); await fs.rm(apk, { force: true }); return json(res, 200, r); }
    if (req.url === "/logcat") { const r = await run(ADB, ["logcat", "-d", "-t", "500"], ROOT, 60 * 1000); return json(res, 200, r); }
    if (req.url === "/screenshot") { const r = await runBinary(ADB, ["exec-out", "screencap", "-p"], ROOT); return json(res, 200, { exitCode: r.exitCode, output: r.output, base64: r.exitCode === 0 ? r.bytes.toString("base64") : "" }); }
    if (req.url === "/artifact") { if (typeof data.path !== "string") throw new Error("missing artifact path"); const artifact = path.resolve(ROOT, data.path); if (!artifact.startsWith(ROOT + path.sep)) throw new Error("path escape"); const bytes = await fs.readFile(artifact); res.writeHead(200, { "content-type": "application/vnd.android.package-archive", "content-length": bytes.length }); return res.end(bytes); }
    return json(res, 404, { error: "unknown endpoint" });
  } catch (e) { return json(res, 400, { exitCode: 2, output: e?.message || String(e) }); }
});
await ensureRoot();
server.listen(PORT, HOST, () => console.log(`Companion Runtime listening on http://${HOST}:${PORT}`));
