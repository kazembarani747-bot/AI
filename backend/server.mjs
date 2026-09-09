import http from "node:http";
import OpenAI from "openai";

const port = Number(process.env.PORT || 8787);
const apiKey = process.env.OPENAI_API_KEY;

if (!apiKey) {
  throw new Error("OPENAI_API_KEY is required on the server.");
}

const client = new OpenAI({ apiKey });

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

const server = http.createServer(async (req, res) => {
  if (req.method === "OPTIONS") {
    sendJson(res, 204, {});
    return;
  }

  if (req.method !== "POST" || req.url !== "/v1/chat") {
    sendJson(res, 404, { error: "Not found" });
    return;
  }

  try {
    let raw = "";
    for await (const chunk of req) raw += chunk;
    const body = JSON.parse(raw || "{}");
    const message = typeof body.message === "string" ? body.message.trim() : "";

    if (!message) {
      sendJson(res, 400, { error: "message is required" });
      return;
    }

    const response = await client.responses.create({
      model: "gpt-5.6-luna",
      instructions: "پاسخ را به فارسی روان و طبیعی بده. اگر سؤال به اطلاعات جدید یا قابل‌تغییر نیاز دارد، از جست‌وجوی وب استفاده کن و نتیجه را دقیق و خلاصه توضیح بده.",
      tools: [{ type: "web_search_preview" }],
      input: message,
    });

    sendJson(res, 200, { text: response.output_text || "پاسخی دریافت نشد." });
  } catch (error) {
    console.error(error);
    sendJson(res, 500, { error: "AI request failed" });
  }
});

server.listen(port, "0.0.0.0", () => {
  console.log(`AI backend listening on ${port}`);
});
