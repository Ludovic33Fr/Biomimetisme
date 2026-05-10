// Logger structuré minimal — JSON par défaut, "pretty" si LOG_FORMAT=pretty.
// Niveau filtré par LOG_LEVEL (debug|info|warn|error), défaut info.

type Level = "debug" | "info" | "warn" | "error";

const LEVELS: Record<Level, number> = { debug: 0, info: 1, warn: 2, error: 3 };
const MIN = LEVELS[(process.env.LOG_LEVEL as Level)] ?? LEVELS.info;
const PRETTY = process.env.LOG_FORMAT === "pretty";
const SERVICE = "traffic";

function serialize(v: unknown): unknown {
  if (v instanceof Error) return { name: v.name, message: v.message, stack: v.stack };
  return v;
}

function emit(level: Level, msg: string, ctx?: Record<string, unknown>) {
  if (LEVELS[level] < MIN) return;
  const ts = new Date().toISOString();
  const ctxSerialized = ctx
    ? Object.fromEntries(Object.entries(ctx).map(([k, v]) => [k, serialize(v)]))
    : undefined;

  if (PRETTY) {
    const tail = ctxSerialized
      ? " " + Object.entries(ctxSerialized).map(([k, v]) => `${k}=${typeof v === "string" ? v : JSON.stringify(v)}`).join(" ")
      : "";
    const stream = level === "error" || level === "warn" ? process.stderr : process.stdout;
    stream.write(`${ts} ${level.toUpperCase().padEnd(5)} [${SERVICE}] ${msg}${tail}\n`);
  } else {
    const entry = { ts, level, service: SERVICE, msg, ...(ctxSerialized || {}) };
    const stream = level === "error" || level === "warn" ? process.stderr : process.stdout;
    stream.write(JSON.stringify(entry) + "\n");
  }
}

export const log = {
  debug: (msg: string, ctx?: Record<string, unknown>) => emit("debug", msg, ctx),
  info:  (msg: string, ctx?: Record<string, unknown>) => emit("info", msg, ctx),
  warn:  (msg: string, ctx?: Record<string, unknown>) => emit("warn", msg, ctx),
  error: (msg: string, ctx?: Record<string, unknown>) => emit("error", msg, ctx),
};
