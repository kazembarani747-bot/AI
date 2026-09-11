import http from "node:http";
import OpenAI from "openai";
import { nextPlan } from "./autonomous-loop.mjs";

const port = Number(process.env.PORT || 8787);
const apiKey = process.env.OPENAI_API_KEY;
if (!apiKey) throw new Error("OPENAI_API_KEY is required on the server.");

const client = new OpenAI({ apiKey });
const MODEL = process.env.OPENAI_MODEL || "gpt-5.6-luna";

function sendJson(res, status, body) {
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store",
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "Content-Type",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
  });
  res.end(JSON.stringify(body));
}

async function readBody(req) {
  let raw = "";
  for await (const chunk of req) raw += chunk;
  return JSON.parse(raw || "{}");
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

const server = http.createServer(async (req, res) => {
  if (req.method === "OPTIONS") return sendJson(res, 204, {});
  if (req.method !== "POST") return sendJson(res, 404, { error: "Not found" });

  try {
    const body = await readBody(req);

    if (req.url === "/v1/autonomous-plan") {
      const request = typeof body.request === "string" ? body.request.trim() : "";
      const feedback = typeof body.feedback === "string" ? body.feedback.trim() : null;
      if (!request) return sendJson(res, 400, { error: "request is required" });
      const plan = await nextPlan({ request, feedback });
      return sendJson(res, 200, { plan });
    }

    const message = typeof body.message === "string" ? body.message.trim() : "";
    if (!message) return sendJson(res, 400, { error: "message is required" });

    if (req.url === "/v1/chat") {
      return sendJson(res, 200, { text: await normalChat(message) });
    }
    if (req.url === "/v1/code") {
      return sendJson(res, 200, { text: await codingAssistant(message) });
    }
    if (req.url === "/v1/android-project") {
      return sendJson(res, 200, await androidProject(message));
    }
    return sendJson(res, 404, { error: "Not found" });
  } catch (error) {
    console.error(error);
    sendJson(res, 500, { error: "AI request failed" });
  }
});

server.listen(port, "0.0.0.0", () => {
  console.log(`AI backend listening on ${port}`);
});
