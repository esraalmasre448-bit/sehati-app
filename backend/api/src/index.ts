import { neon } from "@neondatabase/serverless";
import { checkDatabase } from "./db";

export interface Env {
  ALLOWED_ORIGINS?: string;
  DATABASE_URL?: string;
  RESEND_API_KEY?: string;
  EMAIL_FROM?: string;
  VERIFICATION_CODE_PEPPER?: string;
}

const jsonHeaders = { "content-type": "application/json; charset=utf-8" };
const TTL_MINUTES = 10;
const RESEND_SECONDS = 60;
const MAX_ATTEMPTS = 5;
const PBKDF2_ITERATIONS = 310_000;

function response(body: Record<string, unknown>, status: number, requestId: string, cors: HeadersInit = {}): Response {
  return new Response(JSON.stringify({ ...body, requestId }), {
    status, headers: { ...jsonHeaders, "cache-control": "no-store", ...cors },
  });
}
function allowedOrigin(request: Request, env: Env): string | null {
  const origin = request.headers.get("Origin");
  if (!origin) return null;
  return (env.ALLOWED_ORIGINS ?? "").split(",").map(x => x.trim()).includes(origin) ? origin : null;
}
function normalizeEmail(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const email = value.trim().toLowerCase();
  return email.length <= 254 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) ? email : null;
}
function randomCode(): string {
  // Rejection sampling avoids modulo bias across the six-digit code space.
  const limit = Math.floor(0x1_0000_0000 / 1_000_000) * 1_000_000;
  const value = new Uint32Array(1);
  do { crypto.getRandomValues(value); } while (value[0] >= limit);
  return String(value[0] % 1_000_000).padStart(6, "0");
}
async function digest(value: string): Promise<string> {
  const bytes = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return [...new Uint8Array(bytes)].map(x => x.toString(16).padStart(2, "0")).join("");
}
async function passwordHash(password: string): Promise<string> {
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(password), "PBKDF2", false, ["deriveBits"]);
  const bits = await crypto.subtle.deriveBits({ name: "PBKDF2", hash: "SHA-256", salt, iterations: PBKDF2_ITERATIONS }, key, 256);
  const hex = (x: Uint8Array) => [...x].map(b => b.toString(16).padStart(2, "0")).join("");
  return "pbkdf2_sha256$" + PBKDF2_ITERATIONS + "$" + hex(salt) + "$" + hex(new Uint8Array(bits));
}
async function sendEmail(env: Env, to: string, code: string): Promise<boolean> {
  if (!env.RESEND_API_KEY || !env.EMAIL_FROM) return false;
  try {
    const result = await fetch("https://api.resend.com/emails", {
      method: "POST",
      headers: { authorization: "Bearer " + env.RESEND_API_KEY, "content-type": "application/json" },
      signal: AbortSignal.timeout(8_000),
      body: JSON.stringify({
        from: env.EMAIL_FROM, to: [to], subject: "رمز التحقق - الطريق الصحي",
        text: "رمز التحقق الخاص بك: " + code + "\\nصلاحية الرمز 10 دقائق. لا تشاركه مع أي شخص.",
        html: "<div dir=\"rtl\" style=\"font-family:Arial,sans-serif\"><h2>الطريق الصحي</h2><p>رمز التحقق الخاص بك:</p><div style=\"font-size:32px;font-weight:bold;letter-spacing:8px\">" + code + "</div><p>صلاحية الرمز 10 دقائق. لا تشاركه مع أي شخص.</p></div>",
      }),
    });
    return result.ok;
  } catch {
    return false;
  }
}
async function releaseResendCooldown(sql: ReturnType<typeof neon>, userId: string): Promise<void> {
  await sql.query("UPDATE email_verification_codes SET resend_after = now(), expires_at = LEAST(expires_at, now()) WHERE user_id = $1::uuid AND consumed_at IS NULL", [userId]);
}
async function storeCode(sql: ReturnType<typeof neon>, userId: string, email: string, env: Env): Promise<string> {
  const code = randomCode();
  const codeHash = await digest(env.VERIFICATION_CODE_PEPPER + ":" + email + ":" + code);
  await sql.query(
    "INSERT INTO email_verification_codes (user_id, code_hash, expires_at, resend_after) VALUES ($1::uuid, $2, now() + ($3 * interval '1 minute'), now() + ($4 * interval '1 second')) ON CONFLICT (user_id) DO UPDATE SET code_hash = EXCLUDED.code_hash, expires_at = EXCLUDED.expires_at, resend_after = EXCLUDED.resend_after, attempts = 0, consumed_at = NULL, created_at = now()",
    [userId, codeHash, TTL_MINUTES, RESEND_SECONDS],
  );
  return code;
}
async function register(request: Request, env: Env): Promise<{ body: Record<string, unknown>; status: number }> {
  if (!env.DATABASE_URL || !env.VERIFICATION_CODE_PEPPER || !env.RESEND_API_KEY || !env.EMAIL_FROM) return { body: { error: "registration_not_configured" }, status: 503 };
  let input: Record<string, unknown>;
  try {
    const raw = await request.text();
    if (raw.length > 16_384) return { body: { error: "request_too_large" }, status: 413 };
    input = JSON.parse(raw) as Record<string, unknown>;
    if (!input || typeof input !== "object" || Array.isArray(input)) throw new Error("invalid_object");
  } catch { return { body: { error: "invalid_json" }, status: 400 }; }
  const email = normalizeEmail(input.email);
  const displayName = typeof input.displayName === "string" ? input.displayName.trim() : "";
  const password = input.password;
  const role = String(input.role ?? "PATIENT");
  const specialty = typeof input.specialtyName === "string" ? input.specialtyName.trim() : "";
  const city = typeof input.city === "string" ? input.city.trim() : "";
  if (!email || typeof password !== "string" || password.length < 10 || password.length > 128 || displayName.length < 2 || displayName.length > 100 || !["PATIENT", "DOCTOR", "CLINIC"].includes(role)) {
    return { body: { error: "invalid_registration_data" }, status: 400 };
  }
  if (role === "DOCTOR" && (!specialty || specialty.length > 100 || !city || city.length > 100)) return { body: { error: "doctor_profile_required" }, status: 400 };
  if (role === "CLINIC" && (!city || city.length > 100)) return { body: { error: "clinic_profile_required" }, status: 400 };
  const sql = neon(env.DATABASE_URL);
  const hash = await passwordHash(password);
  // The unique index is the source of truth; this also closes the concurrent
  // check-then-insert race that could otherwise surface as an uncaught 500.
  const created = await sql.query("INSERT INTO users (email, password_hash, role, account_status) VALUES ($1, $2, $3, 'PENDING_VERIFICATION') ON CONFLICT DO NOTHING RETURNING id", [email, hash, role]);
  if (!created.length) return { body: { error: "email_already_registered" }, status: 409 };
  const userId = String(created[0].id);
  if (role === "PATIENT") {
    await sql.query("INSERT INTO patient_profiles (user_id, display_name) VALUES ($1::uuid, $2)", [userId, displayName]);
  } else if (role === "DOCTOR") {
    await sql.query("INSERT INTO doctor_profiles (user_id, display_name, specialty_name, city) VALUES ($1::uuid, $2, $3, $4)", [userId, displayName, specialty, city]);
  } else {
    await sql.query("INSERT INTO clinic_profiles (user_id, clinic_name, city) VALUES ($1::uuid, $2, $3)", [userId, displayName, city]);
  }
  const code = await storeCode(sql, userId, email, env);
  if (!await sendEmail(env, email, code)) {
    await releaseResendCooldown(sql, userId);
    return { body: { error: "email_delivery_failed" }, status: 502 };
  }
  return { body: { status: "verification_required", message: "تم إنشاء الحساب وإرسال رمز التحقق إلى بريدك الإلكتروني." }, status: 202 };
}
async function resend(request: Request, env: Env): Promise<{ body: Record<string, unknown>; status: number }> {
  if (!env.DATABASE_URL || !env.VERIFICATION_CODE_PEPPER || !env.RESEND_API_KEY || !env.EMAIL_FROM) return { body: { error: "email_verification_not_configured" }, status: 503 };
  let input: Record<string, unknown>;
  try { input = await request.json() as Record<string, unknown>; } catch { return { body: { error: "invalid_json" }, status: 400 }; }
  const email = normalizeEmail(input.email);
  if (!email) return { body: { error: "invalid_email" }, status: 400 };
  const sql = neon(env.DATABASE_URL);
  const rows = await sql.query("SELECT u.id, ev.resend_after FROM users u LEFT JOIN email_verification_codes ev ON ev.user_id = u.id WHERE lower(u.email) = $1 AND u.email_verified_at IS NULL AND u.account_status = 'PENDING_VERIFICATION' LIMIT 1", [email]);
  if (!rows.length) return { body: { status: "if_pending_account_exists_code_sent" }, status: 202 };
  if (rows[0].resend_after && new Date(String(rows[0].resend_after)).getTime() > Date.now()) return { body: { error: "resend_too_soon" }, status: 429 };
  const code = await storeCode(sql, String(rows[0].id), email, env);
  if (!await sendEmail(env, email, code)) {
    await releaseResendCooldown(sql, String(rows[0].id));
    return { body: { error: "email_delivery_failed" }, status: 502 };
  }
  return { body: { status: "verification_code_sent" }, status: 202 };
}
async function verify(request: Request, env: Env): Promise<{ body: Record<string, unknown>; status: number }> {
  if (!env.DATABASE_URL || !env.VERIFICATION_CODE_PEPPER) return { body: { error: "email_verification_not_configured" }, status: 503 };
  let input: Record<string, unknown>;
  try { input = await request.json() as Record<string, unknown>; } catch { return { body: { error: "invalid_json" }, status: 400 }; }
  const email = normalizeEmail(input.email);
  const code = typeof input.code === "string" ? input.code.trim() : "";
  if (!email || !/^\d{6}$/.test(code)) return { body: { error: "invalid_verification_data" }, status: 400 };
  const sql = neon(env.DATABASE_URL);
  const submitted = await digest(env.VERIFICATION_CODE_PEPPER + ":" + email + ":" + code);
  // Consume the matching, unexpired code and activate the account in one
  // atomic PostgreSQL statement. This prevents concurrent requests reusing it.
  const activated = await sql.query(
    "WITH consumed AS (UPDATE email_verification_codes ev SET consumed_at = now(), code_hash = 'consumed' FROM users u WHERE ev.user_id = u.id AND u.email = $1 AND u.email_verified_at IS NULL AND u.account_status = 'PENDING_VERIFICATION' AND ev.code_hash = $2 AND ev.expires_at > now() AND ev.consumed_at IS NULL AND ev.attempts < $3 RETURNING ev.user_id), activated AS (UPDATE users SET email_verified_at = now(), account_status = 'ACTIVE', updated_at = now() WHERE id IN (SELECT user_id FROM consumed) AND email_verified_at IS NULL AND account_status = 'PENDING_VERIFICATION' RETURNING id) SELECT id FROM activated",
    [email, submitted, MAX_ATTEMPTS],
  );
  if (activated.length) return { body: { status: "email_verified", message: "تم تأكيد البريد الإلكتروني بنجاح." }, status: 200 };

  // A wrong code increments attempts atomically and can never exceed the limit.
  await sql.query(
    "UPDATE email_verification_codes ev SET attempts = attempts + 1 FROM users u WHERE ev.user_id = u.id AND u.email = $1 AND u.email_verified_at IS NULL AND u.account_status = 'PENDING_VERIFICATION' AND ev.code_hash <> $2 AND ev.expires_at > now() AND ev.consumed_at IS NULL AND ev.attempts < $3",
    [email, submitted, MAX_ATTEMPTS],
  );
  const state = await sql.query(
    "SELECT ev.attempts, ev.expires_at, ev.consumed_at FROM email_verification_codes ev JOIN users u ON u.id = ev.user_id WHERE u.email = $1 AND u.email_verified_at IS NULL AND u.account_status = 'PENDING_VERIFICATION' LIMIT 1",
    [email],
  );
  if (state.length && Number(state[0].attempts) >= MAX_ATTEMPTS) return { body: { error: "too_many_attempts" }, status: 429 };
  return { body: { error: "code_invalid_or_expired" }, status: 400 };
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const requestId = crypto.randomUUID();
    const origin = allowedOrigin(request, env);
    if (request.headers.has("Origin") && !origin) return response({ error: "origin_not_allowed" }, 403, requestId);
    const cors: Record<string, string> = { vary: "Origin", ...(origin ? {
      "access-control-allow-origin": origin, "access-control-allow-methods": "GET, POST, OPTIONS",
      "access-control-allow-headers": "content-type, authorization, x-request-id",
    } : {}) };
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: cors });
    const url = new URL(request.url);
    if (url.pathname === "/health" && request.method === "GET") return response({ status: "ok", service: "sehati-backend-api" }, 200, requestId, cors);
    if (url.pathname === "/ready" && request.method === "GET") {
      const ready = await checkDatabase(env);
      return response({ status: ready ? "ready" : "not_ready", dependencies: { postgres: ready ? "connected" : "unavailable" } }, ready ? 200 : 503, requestId, cors);
    }
    if (url.pathname === "/v1" && request.method === "GET") return response({ service: "sehati-backend-api", apiVersion: "v1", status: "email-verification-preview" }, 200, requestId, cors);
    try {
      if (request.method === "POST" && url.pathname === "/v1/auth/register") {
        const result = await register(request, env); return response(result.body, result.status, requestId, cors);
      }
      if (request.method === "POST" && url.pathname === "/v1/auth/email/resend") {
        const result = await resend(request, env); return response(result.body, result.status, requestId, cors);
      }
      if (request.method === "POST" && url.pathname === "/v1/auth/email/verify") {
        const result = await verify(request, env); return response(result.body, result.status, requestId, cors);
      }
    } catch {
      return response({ error: "internal_error" }, 500, requestId, cors);
    }
    if (request.method !== "GET" && request.method !== "POST") return response({ error: "method_not_allowed" }, 405, requestId, cors);
    return response({ error: "not_found" }, 404, requestId, cors);
  },
};
