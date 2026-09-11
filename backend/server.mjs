import http from "node:http";
import OpenAI from "openai";
import { nextPlan, normalizeBudget } from "./autonomous-loop.mjs";

const port = Number(process.env.PORT || 8787);
const apiKey = process.env.OPENAI_API_KEY;
if (!apiKey) throw new Error("OPENAI_API_KEY is required on the server.");

const client = new OpenAI({ apiKey });
const MODEL = process.env.OPENAI_MODEL || "gpt-5.6-luna";
const RUNTIME_URL = (process.env.RUNTIME_URL || "").trim().replace(/\/$/, "");
const MAX_BODY = 128 * 1024 * 1024;
const MAX_PROXY_BODY = 4 * 1024 * 1024;

function corsHeaders() {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "Content-Type",
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  };
}

function sendJson(res, status, body) {
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store",
    ...corsHeaders(),
  });
  res.end(JSON.stringify(body));
}

async function readBody(req, maxBytes = MAX_BODY) {
  let size = 0;
  const chunks = [];
  for await (const chunk of req) {
    size += chunk.length;
    if (size > maxBytes) throw new Error("request body is too large");
    chunks.push(chunk);
  }
  return Buffer.concat(chunks);
}

async function readJsonBody(req, maxBytes = MAX_BODY) {
  const bytes = await readBody(req, maxBytes);
  return JSON.parse(bytes.toString("utf8") || "{}");
}

async function normalChat(message) {
  const response = await client.responses.create({
    model: MODEL,
    instructions:
      "تو دستیار فارسی برنامه AI هستی. پاسخ را روان و دقیق بده. " +
      "برای اطلاعات جدید یا قابل‌تغییر از جست‌وجوی وب استفاده کن. " +
      "در پرسش‌های برنامه‌نویسی، کد قابل اجرا و توضیح کوتاه ارائه بده.",
    tools: [{ type: "web_search_preview" }],
    input: message,
  });
  return response.output_text || "پاسخی دریافت نشد.";
}

async function codingAssistant(message) {
  const response = await client.responses.create({
    model: MODEL,
    instructions:
      "تو یک مهندس نرم‌افزار ارشد و دستیار کدنویسی فارسی هستی. " +
      "می‌توانی Kotlin، Java، Python، JavaScript/TypeScript، Gradle و Android را توضیح بدهی و کد تولید کنی. " +
      "کد را کامل، قابل اجرا و با مسیر فایل مشخص ارائه کن. اگر کاربر درخواست ساخت اپ اندروید کرد، " +
      "معماری مناسب Android/Jetpack Compose، فایل‌های لازم، Gradle و Manifest را در نظر بگیر. " +
      "هیچ API key یا secret را داخل کد کلاینت قرار نده.",
    input: message,
  });
  return response.output_text || "کدی تولید نشد.";
}

async function androidProject(message) {
  const response = await client.responses.create({
    model: MODEL,
    instructions:
      "یک سازنده پروژه Android هستی. بر اساس درخواست کاربر یک پروژه Android قابل ساخت با Gradle تولید کن. " +
      "پیش‌فرض: Kotlin + Jetpack Compose + AndroidX، minSdk 26، target/compile SDK مناسب و Java 17. " +
      "خروجی را فقط به صورت JSON مطابق schema بده. هر فایل باید path و content کامل داشته باشد. " +
      "پروژه باید بدون کلید مخفی داخل APK باشد و تا حد ممکن با یک اجرای Gradle build شود.",
    input: message,
    text: {
      format: {
        type: "json_schema",
        name: "android_project",
        strict: true,
        schema: {
          type: "object",
          additionalProperties: false,
          properties: {
            name: { type: "string" },
            summary: { type: "string" },
            files: {
              type: "array",
              items: {
                type: "object",
                additionalProperties: false,
                properties: {
                  path: { type: "string" },
                  content: { type: "string" }
                },
                required: ["path", "content"]
              }
            }
          },
          required: ["name", "summary", "files"]
        }
      }
    }
  });
  return JSON.parse(response.output_text || "{}");
}

async function runtimeHealth() {
  if (!RUNTIME_URL) {
    return { configured: false, ok: false, build: false, test: false, install: false, logcat: false, screenshot: false };
  }
  try {
    const response = await fetch(`${RUNTIME_URL}/health`, { signal: AbortSignal.timeout(15000) });
    const body = await response.json();
    return { configured: true, ...body };
  } catch (error) {
    return { configured: true, ok: false, build: false, test: false, install: false, logcat: false, screenshot: false, error: error?.message || "runtime unavailable" };
  }
}

async function proxyRuntime(req, res) {
  if (!RUNTIME_URL) return sendJson(res, 503, { error: "Companion Runtime is not configured." });
  const allowed = new Set([
    "/transfer/start", "/transfer/chunk", "/transfer/finalize",
    "/build-session", "/test-session", "/install", "/logcat", "/screenshot", "/artifact"
  ]);
  if (!allowed.has(req.url)) return sendJson(res, 404, { error: "Runtime endpoint not allowed." });

  const body = await readBody(req, MAX_PROXY_BODY);
  const response = await fetch(`${RUNTIME_URL}${req.url}`, {
    method: "POST",
    headers: { "Content-Type": req.headers["content-type"] || "application/json" },
    body,
    signal: AbortSignal.timeout(25 * 60 * 1000),
  });
  const contentType = response.headers.get("content-type") || "application/octet-stream";
  const data = Buffer.from(await response.arrayBuffer());
  res.writeHead(response.status, {
    "Content-Type": contentType,
    "Cache-Control": "no-store",
    ...corsHeaders(),
  });
  res.end(data);
}

const server = http.createServer(async (req, res) => {
  if (req.method === "OPTIONS") return sendJson(res, 204, {});

  if (req.method === "GET" && req.url === "/health") {
    const runtime = await runtimeHealth();
    return sendJson(res, 200, {
      ok: true,
      service: "ai-backend",
      model: MODEL,
      planner: true,
      runtime: runtime.ok,
      runtimeConfigured: runtime.configured,
      runtimeDetails: runtime,
      message: runtime.ok
        ? "AI backend and Companion Runtime are online."
        : "AI backend is online; Companion Runtime is not ready."
    });
  }

  if (req.method === "POST" && [
    "/transfer/start", "/transfer/chunk", "/transfer/finalize",
    "/build-session", "/test-session", "/install", "/logcat", "/screenshot", "/artifact"
  ].includes(req.url)) {
    try {
      return await proxyRuntime(req, res);
    } catch (error) {
      console.error(error);
      return sendJson(res, 502, { error: error?.message || "Companion Runtime request failed" });
    }
  }

  if (req.method !== "POST") return sendJson(res, 404, { error: "Not found" });

  try {
    const body = await readJsonBody(req);

    if (req.url === "/v1/autonomous-plan") {
      const request = typeof body.request === "string" ? body.request.trim() : "";
      const feedback = typeof body.feedback === "string" ? body.feedback.trim() : null;
      if (!request) return sendJson(res, 400, { error: "request is required" });
      const budget = normalizeBudget(body.budget);
      const plan = await nextPlan({ request, feedback, budget });
      return sendJson(res, 200, { budget, plan });
    }

    const message = typeof body.message === "string" ? body.message.trim() : "";
    if (!message) return sendJson(res, 400, { error: "message is required" });

    if (req.url === "/v1/chat") return sendJson(res, 200, { text: await normalChat(message) });
    if (req.url === "/v1/code") return sendJson(res, 200, { text: await codingAssistant(message) });
    if (req.url === "/v1/android-project") return sendJson(res, 200, await androidProject(message));
    return sendJson(res, 404, { error: "Not found" });
  } catch (error) {
    console.error(error);
    sendJson(res, 500, { error: error?.message || "AI request failed" });
  }
});

server.listen(port, "0.0.0.0", () => {
  console.log(`AI backend listening on ${port}`);
  console.log(`Companion Runtime proxy: ${RUNTIME_URL || "not configured"}`);
});
