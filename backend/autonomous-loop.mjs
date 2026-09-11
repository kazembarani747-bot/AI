import OpenAI from "openai";

const MODEL = process.env.OPENAI_MODEL || "gpt-5.6-luna";
const client = new OpenAI({ apiKey: process.env.OPENAI_API_KEY });
const BUDGETS = new Set([10, 20, 30, 60]);
const MAX_FILES = 2000;
const MAX_FILE_CHARS = 2_000_000;
const TASK = /^[A-Za-z0-9:_-]{1,80}$/;
const PROJECT = /^[A-Za-z0-9._-]{1,80}$/;

export function normalizeBudget(value) {
  const minutes = Number(value);
  return BUDGETS.has(minutes) ? minutes : 10;
}

export function validatePlan(plan) {
  if (!plan || typeof plan !== "object") throw new Error("Planner returned no plan.");
  if (typeof plan.projectName !== "string" || !PROJECT.test(plan.projectName)) throw new Error("Planner returned an invalid projectName.");
  if (!plan.files || typeof plan.files !== "object" || Array.isArray(plan.files)) throw new Error("Planner returned invalid files.");
  const entries = Object.entries(plan.files);
  if (entries.length === 0 || entries.length > MAX_FILES) throw new Error("Planner returned an invalid file count.");
  for (const [file, content] of entries) {
    if (!file || file.startsWith("/") || file.includes("..") || typeof content !== "string" || content.length > MAX_FILE_CHARS) {
      throw new Error(`Planner returned an invalid file: ${file}`);
    }
  }
  for (const listName of ["buildTasks", "testTasks"]) {
    const tasks = plan[listName];
    if (!Array.isArray(tasks) || tasks.length > 16 || tasks.some(task => typeof task !== "string" || !TASK.test(task))) {
      throw new Error(`Planner returned invalid ${listName}.`);
    }
  }
  return plan;
}

export async function nextPlan({ request, feedback = null, budget = 10 }) {
  const minutes = normalizeBudget(budget);
  const response = await client.responses.create({
    model: MODEL,
    instructions:
      "تو موتور برنامه‌نویسی خودکار AI هستی. یک BuildPlan دقیق برای Android تولید کن. " +
      `بودجه کاری انتخاب‌شده ${minutes} دقیقه است؛ در این مرحله یک برنامه قابل build و قابل تست تولید کن، نه توضیح متنی. ` +
      "اگر feedback خطای build/test/logcat دارد، علت را تشخیص بده و فقط فایل‌های لازم را اصلاح کن. " +
      "اگر تست Android قابل اجراست، testTasks مناسب مثل connectedDebugAndroidTest را قرار بده؛ اگر تست دستگاه لازم نیست، testTasks را خالی نگه دار. " +
      "buildTasks و testTasks فقط باید نام taskهای Gradle ساده باشند و هیچ shell command یا آرگومان آزاد در آن‌ها قرار نده. " +
      "نام پروژه فقط حروف انگلیسی، عدد، نقطه، خط تیره یا زیرخط داشته باشد. مسیر فایل‌ها نسبی باشند. " +
      "هیچ secret، API key یا credential را داخل فایل‌های پروژه قرار نده. خروجی فقط JSON مطابق schema باشد.",
    input: [
      { role: "user", content: request },
      ...(feedback ? [{ role: "user", content: `Runtime feedback from the previous attempt:\n${feedback}` }] : [])
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

  const plan = JSON.parse(response.output_text || "{}");
  return validatePlan(plan);
}
