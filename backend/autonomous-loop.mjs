import OpenAI from "openai";

const MODEL = process.env.OPENAI_MODEL || "gpt-5.6-luna";
const client = new OpenAI({ apiKey: process.env.OPENAI_API_KEY });
const BUDGETS = new Set([10, 20, 30, 60]);

export function normalizeBudget(value) {
  const minutes = Number(value);
  return BUDGETS.has(minutes) ? minutes : 10;
}

export async function nextPlan({ request, feedback = null }) {
  const response = await client.responses.create({
    model: MODEL,
    instructions:
      "تو موتور برنامه‌نویسی خودکار AI هستی. یک BuildPlan دقیق برای Android تولید کن. " +
      "اگر feedback خطای build/test دارد، فقط بخش‌های لازم را اصلاح کن و پروژه را قابل build نگه دار. " +
      "هیچ secret یا API key را داخل فایل‌های پروژه قرار نده. خروجی فقط JSON مطابق schema باشد.",
    input: [
      { role: "user", content: request },
      ...(feedback ? [{ role: "user", content: `Runtime feedback:\n${feedback}` }] : [])
    ],
    text: {
      format: {
        type: "json_schema",
        name: "build_plan",
        strict: true,
        schema: {
          type: "object",
          additionalProperties: false,
          properties: {
            projectName: { type: "string" },
            summary: { type: "string" },
            files: { type: "object", additionalProperties: { type: "string" } },
            buildTasks: { type: "array", items: { type: "string" } },
            testTasks: { type: "array", items: { type: "string" } }
          },
          required: ["projectName", "summary", "files", "buildTasks", "testTasks"]
        }
      }
    }
  });
  return JSON.parse(response.output_text || "{}");
}
