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
type SqlClient = { query: (query: string, params?: any[]) => Promise<any> };

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
function toHex(bytes: Uint8Array): string {
  return [...bytes].map(b => b.toString(16).padStart(2, "0")).join("");
}
function fromHex(value: string): Uint8Array {
  const bytes = new Uint8Array(value.length / 2);
  for (let i = 0; i < bytes.length; i++) bytes[i] = Number.parseInt(value.slice(i * 2, i * 2 + 2), 16);
  return bytes;
}
function constantTimeEqual(a: Uint8Array, b: Uint8Array): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a[i] ^ b[i];
  return diff === 0;
}
async function verifyPassword(password: string, encoded: string): Promise<boolean> {
  const parts = encoded.split("$");
  if (parts.length !== 4 || parts[0] !== "pbkdf2_sha256") return false;
  const iterations = Number(parts[1]);
  if (!Number.isInteger(iterations) || iterations < 100_000 || iterations > 1_000_000) return false;
  try {
    const salt = fromHex(parts[2]);
    const expected = fromHex(parts[3]);
    if (salt.length !== 16 || expected.length !== 32) return false;
    const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(password), "PBKDF2", false, ["deriveBits"]);
    const bits = await crypto.subtle.deriveBits({ name: "PBKDF2", hash: "SHA-256", salt, iterations }, key, 256);
    return constantTimeEqual(new Uint8Array(bits), expected);
  } catch { return false; }
}
function randomToken(bytes = 32): string {
  const raw = crypto.getRandomValues(new Uint8Array(bytes));
  let binary = "";
  for (const value of raw) binary += String.fromCharCode(value);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}
function bearerToken(request: Request): string | null {
  const header = request.headers.get("Authorization") ?? "";
  const match = /^Bearer ([A-Za-z0-9_-]{32,128})$/.exec(header);
  return match?.[1] ?? null;
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
        text: "رمز التحقق الخاص بك: " + code + "\nصلاحية الرمز 10 دقائق. لا تشاركه مع أي شخص.",
        html: "<div dir=\"rtl\" style=\"font-family:Arial,sans-serif\"><h2>الطريق الصحي</h2><p>رمز التحقق الخاص بك:</p><div style=\"font-size:32px;font-weight:bold;letter-spacing:8px\">" + code + "</div><p>صلاحية الرمز 10 دقائق. لا تشاركه مع أي شخص.</p></div>",
      }),
    });
    if (!result.ok) {
      console.error("Resend rejected verification email", result.status);
      return false;
    }
    return true;
  } catch {
    console.error("Resend request failed");
    return false;
  }
}
async function releaseResendCooldown(sql: SqlClient, userId: string): Promise<void> {
  await sql.query("UPDATE email_verification_codes SET resend_after = now(), expires_at = LEAST(expires_at, now()) WHERE user_id = $1::uuid AND consumed_at IS NULL", [userId]);
}
async function allowRateLimit(sql: SqlClient, env: Env, key: string, limit: number, windowSeconds: number): Promise<boolean> {
  const bucket = await digest(env.VERIFICATION_CODE_PEPPER + ":rate:" + key);
  const rows = await sql.query(
    "INSERT INTO auth_rate_limits (bucket_key, window_started_at, request_count) VALUES ($1, now(), 1) ON CONFLICT (bucket_key) DO UPDATE SET window_started_at = CASE WHEN auth_rate_limits.window_started_at <= now() - ($2 * interval '1 second') THEN now() ELSE auth_rate_limits.window_started_at END, request_count = CASE WHEN auth_rate_limits.window_started_at <= now() - ($2 * interval '1 second') THEN 1 ELSE auth_rate_limits.request_count + 1 END RETURNING request_count",
    [bucket, windowSeconds],
  );
  return Number(rows[0]?.request_count ?? limit + 1) <= limit;
}
function clientIp(request: Request): string {
  return request.headers.get("CF-Connecting-IP")?.slice(0, 64) || "unknown";
}
async function login(request: Request, env: Env): Promise<{ body: Record<string, unknown>; status: number }> {
  let input: Record<string, unknown>;
  try {
    const raw = await request.text();
    if (raw.length > 16_384) return { body: { error: "request_too_large" }, status: 413 };
    input = JSON.parse(raw) as Record<string, unknown>;
    if (!input || typeof input !== "object" || Array.isArray(input)) throw new Error("invalid_object");
  } catch { return { body: { error: "invalid_json" }, status: 400 }; }
  const email = normalizeEmail(input.email);
  const password = input.password;
  if (!email || typeof password !== "string" || password.length < 1 || password.length > 128) {
    return { body: { error: "invalid_credentials" }, status: 400 };
  }
  if (!env.DATABASE_URL || !env.VERIFICATION_CODE_PEPPER || env.VERIFICATION_CODE_PEPPER.length < 32) {
    return { body: { error: "authentication_not_configured" }, status: 503 };
  }
  const sql = neon(env.DATABASE_URL);
  const ip = clientIp(request);
  if (!await allowRateLimit(sql, env, "login:ip:" + ip, 20, 900) ||
      !await allowRateLimit(sql, env, "login:email:" + email, 10, 900)) {
    return { body: { error: "too_many_requests" }, status: 429 };
  }
  const rows = await sql.query(
    "SELECT id, password_hash, role FROM users WHERE lower(email) = $1 AND account_status = 'ACTIVE' AND email_verified_at IS NOT NULL LIMIT 1",
    [email],
  );
  if (!rows.length || !await verifyPassword(password, String(rows[0].password_hash))) {
    return { body: { error: "invalid_credentials" }, status: 401 };
  }
  const accessToken = randomToken(32);
  const refreshToken = randomToken(48);
  const sessionId = crypto.randomUUID();
  await sql.query(
    "INSERT INTO auth_sessions (id, user_id, access_token_hash, refresh_token_hash, access_expires_at, refresh_expires_at) VALUES ($1::uuid, $2::uuid, $3, $4, now() + interval '15 minutes', now() + interval '30 days')",
    [sessionId, String(rows[0].id), await digest(accessToken), await digest(refreshToken)],
  );
  return { body: { accessToken, refreshToken, tokenType: "Bearer", expiresIn: 900, refreshExpiresIn: 2592000, user: { id: String(rows[0].id), role: String(rows[0].role), email } }, status: 200 };
}
async function refreshSession(request: Request, env: Env): Promise<{ body: Record<string, unknown>; status: number }> {
  let input: Record<string, unknown>;
  try {
    const raw = await request.text();
    if (raw.length > 16_384) return { body: { error: "request_too_large" }, status: 413 };
    input = JSON.parse(raw) as Record<string, unknown>;
    if (!input || typeof input !== "object" || Array.isArray(input)) throw new Error("invalid_object");
  } catch { return { body: { error: "invalid_json" }, status: 400 }; }
  const refreshToken = typeof input.refreshToken === "string" ? input.refreshToken : "";
  if (!/^[A-Za-z0-9_-]{60,100}$/.test(refreshToken)) return { body: { error: "invalid_refresh_token" }, status: 400 };
  if (!env.DATABASE_URL || !env.VERIFICATION_CODE_PEPPER || env.VERIFICATION_CODE_PEPPER.length < 32) return { body: { error: "authentication_not_configured" }, status: 503 };
  const sql = neon(env.DATABASE_URL);
  if (!await allowRateLimit(sql, env, "refresh:ip:" + clientIp(request), 30, 900)) return { body: { error: "too_many_requests" }, status: 429 };
  const accessToken = randomToken(32);
  const nextRefreshToken = randomToken(48);
  const sessionId = crypto.randomUUID();
  const rotated = await sql.query(
    "WITH old_session AS (UPDATE auth_sessions SET revoked_at = now(), replaced_by_session_id = $2::uuid WHERE refresh_token_hash = $1 AND revoked_at IS NULL AND refresh_expires_at > now() RETURNING user_id), active_user AS (SELECT u.id FROM users u JOIN old_session s ON s.user_id = u.id WHERE u.account_status = 'ACTIVE' AND u.email_verified_at IS NOT NULL), new_session AS (INSERT INTO auth_sessions (id, user_id, access_token_hash, refresh_token_hash, access_expires_at, refresh_expires_at) SELECT $2::uuid, id, $3, $4, now() + interval '15 minutes', now() + interval '30 days' FROM active_user RETURNING user_id) SELECT user_id FROM new_session",
    [await digest(refreshToken), sessionId, await digest(accessToken), await digest(nextRefreshToken)],
  );
  if (!rotated.length) return { body: { error: "invalid_refresh_token" }, status: 401 };
  return { body: { accessToken, refreshToken: nextRefreshToken, tokenType: "Bearer", expiresIn: 900, refreshExpiresIn: 2592000 }, status: 200 };
}
async function logout(request: Request, env: Env): Promise<{ body: Record<string, unknown>; status: number }> {
  let input: Record<string, unknown>;
  try {
    const raw = await request.text();
    if (raw.length > 16_384) return { body: { error: "request_too_large" }, status: 413 };
    input = JSON.parse(raw) as Record<string, unknown>;
    if (!input || typeof input !== "object" || Array.isArray(input)) throw new Error("invalid_object");
  } catch { return { body: { error: "invalid_json" }, status: 400 }; }
  const refreshToken = typeof input.refreshToken === "string" ? input.refreshToken : "";
  if (!/^[A-Za-z0-9_-]{60,100}$/.test(refreshToken)) return { body: { error: "invalid_refresh_token" }, status: 400 };
  if (!env.DATABASE_URL) return { body: { error: "authentication_not_configured" }, status: 503 };
  const sql = neon(env.DATABASE_URL);
  await sql.query("UPDATE auth_sessions SET revoked_at = now() WHERE refresh_token_hash = $1 AND revoked_at IS NULL", [await digest(refreshToken)]);
  return { body: { status: "logged_out" }, status: 200 };
}
async function currentUser(request: Request, env: Env): Promise<{ body: Record<string, unknown>; status: number }> {
  const token = bearerToken(request);
  if (!token) return { body: { error: "unauthorized" }, status: 401 };
  if (!env.DATABASE_URL) return { body: { error: "authentication_not_configured" }, status: 503 };
  const sql = neon(env.DATABASE_URL);
  const rows = await sql.query(
    "SELECT u.id, u.email, u.role FROM auth_sessions s JOIN users u ON u.id = s.user_id WHERE s.access_token_hash = $1 AND s.revoked_at IS NULL AND s.access_expires_at > now() AND u.account_status = 'ACTIVE' AND u.email_verified_at IS NOT NULL LIMIT 1",
    [await digest(token)],
  );
  if (!rows.length) return { body: { error: "unauthorized" }, status: 401 };
  return { body: { user: { id: String(rows[0].id), email: String(rows[0].email), role: String(rows[0].role) } }, status: 200 };
}

async function register(request: Request, env: Env): Promise<{ body: Record<string, unknown>; status: number }> {
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
  if (!env.DATABASE_URL || (!env.VERIFICATION_CODE_PEPPER || env.VERIFICATION_CODE_PEPPER.length < 32) || !env.RESEND_API_KEY || !env.EMAIL_FROM) return { body: { error: "registration_not_configured" }, status: 503 };
  const sql = neon(env.DATABASE_URL);
  const ip = clientIp(request);
  if (!await allowRateLimit(sql, env, "register:ip:" + ip, 8, 900) ||
      !await allowRateLimit(sql, env, "register:email:" + email, 3, 3600)) {
    return { body: { error: "too_many_requests" }, status: 429 };
  }
  const hash = await passwordHash(password);
  const code = randomCode();
  const codeHash = await digest(env.VERIFICATION_CODE_PEPPER + ":" + email + ":" + code);
  // Create the identity, role profile, and verification challenge in one SQL
  // statement. A profile/code failure rolls back the pending user as well.
  let created: Record<string, unknown>[];
  if (role === "PATIENT") {
    created = await sql.query(
      "WITH created AS (INSERT INTO users (email, password_hash, role, account_status) VALUES ($1, $2, $3, 'PENDING_VERIFICATION') ON CONFLICT DO NOTHING RETURNING id), profile AS (INSERT INTO patient_profiles (user_id, display_name) SELECT id, $4 FROM created RETURNING user_id), verification AS (INSERT INTO email_verification_codes (user_id, code_hash, expires_at, resend_after) SELECT user_id, $5, now() + ($6 * interval '1 minute'), now() + ($7 * interval '1 second') FROM profile RETURNING user_id) SELECT user_id FROM verification",
      [email, hash, role, displayName, codeHash, TTL_MINUTES, RESEND_SECONDS],
    );
  } else if (role === "DOCTOR") {
    created = await sql.query(
      "WITH created AS (INSERT INTO users (email, password_hash, role, account_status) VALUES ($1, $2, $3, 'PENDING_VERIFICATION') ON CONFLICT DO NOTHING RETURNING id), profile AS (INSERT INTO doctor_profiles (user_id, display_name, specialty_name, city) SELECT id, $4, $5, $6 FROM created RETURNING user_id), verification AS (INSERT INTO email_verification_codes (user_id, code_hash, expires_at, resend_after) SELECT user_id, $7, now() + ($8 * interval '1 minute'), now() + ($9 * interval '1 second') FROM profile RETURNING user_id) SELECT user_id FROM verification",
      [email, hash, role, displayName, specialty, city, codeHash, TTL_MINUTES, RESEND_SECONDS],
    );
  } else {
    created = await sql.query(
      "WITH created AS (INSERT INTO users (email, password_hash, role, account_status) VALUES ($1, $2, $3, 'PENDING_VERIFICATION') ON CONFLICT DO NOTHING RETURNING id), profile AS (INSERT INTO clinic_profiles (user_id, clinic_name, city) SELECT id, $4, $5 FROM created RETURNING user_id), verification AS (INSERT INTO email_verification_codes (user_id, code_hash, expires_at, resend_after) SELECT user_id, $6, now() + ($7 * interval '1 minute'), now() + ($8 * interval '1 second') FROM profile RETURNING user_id) SELECT user_id FROM verification",
      [email, hash, role, displayName, city, codeHash, TTL_MINUTES, RESEND_SECONDS],
    );
  }
  if (!created.length) return { body: { error: "email_already_registered" }, status: 409 };
  const userId = String(created[0].user_id);
  if (!await sendEmail(env, email, code)) {
    await releaseResendCooldown(sql, userId);
    return { body: { error: "email_delivery_failed" }, status: 502 };
  }
  return { body: { status: "verification_required", message: "تم إنشاء الحساب وإرسال رمز التحقق إلى بريدك الإلكتروني." }, status: 202 };
}
async function resend(request: Request, env: Env): Promise<{ body: Record<string, unknown>; status: number }> {
  let input: Record<string, unknown>;
  try {
    const raw = await request.text();
    if (raw.length > 16_384) return { body: { error: "request_too_large" }, status: 413 };
    input = JSON.parse(raw) as Record<string, unknown>;
    if (!input || typeof input !== "object" || Array.isArray(input)) throw new Error("invalid_object");
  } catch { return { body: { error: "invalid_json" }, status: 400 }; }
  const email = normalizeEmail(input.email);
  if (!email) return { body: { error: "invalid_email" }, status: 400 };
  if (!env.DATABASE_URL || !env.VERIFICATION_CODE_PEPPER || env.VERIFICATION_CODE_PEPPER.length < 32 || !env.RESEND_API_KEY || !env.EMAIL_FROM) return { body: { error: "email_verification_not_configured" }, status: 503 };
  const sql = neon(env.DATABASE_URL);
  const ip = clientIp(request);
  if (!await allowRateLimit(sql, env, "resend:ip:" + ip, 10, 3600) ||
      !await allowRateLimit(sql, env, "resend:email:" + email, 5, 3600)) {
    return { body: { error: "too_many_requests" }, status: 429 };
  }
  const code = randomCode();
  const codeHash = await digest(env.VERIFICATION_CODE_PEPPER + ":" + email + ":" + code);
  // Conditional UPDATE obtains the row lock and enforces cooldown atomically;
  // concurrent resend requests cannot both replace the active code.
  const claimed = await sql.query(
    "UPDATE email_verification_codes ev SET code_hash = $2, expires_at = now() + ($3 * interval '1 minute'), resend_after = now() + ($4 * interval '1 second'), attempts = 0, consumed_at = NULL, created_at = now() FROM users u WHERE ev.user_id = u.id AND lower(u.email) = $1 AND u.email_verified_at IS NULL AND u.account_status = 'PENDING_VERIFICATION' AND ev.resend_after <= now() AND ev.consumed_at IS NULL RETURNING ev.user_id",
    [email, codeHash, TTL_MINUTES, RESEND_SECONDS],
  );
  if (!claimed.length) {
    // Keep the response identical for unknown accounts and active cooldowns
    // to avoid disclosing whether an email has a pending account.
    return { body: { status: "if_pending_account_exists_code_sent" }, status: 202 };
  }
  const userId = String(claimed[0].user_id);
  if (!await sendEmail(env, email, code)) {
    await releaseResendCooldown(sql, userId);
    return { body: { status: "if_pending_account_exists_code_sent" }, status: 202 };
  }
  return { body: { status: "if_pending_account_exists_code_sent" }, status: 202 };
}
async function verify(request: Request, env: Env): Promise<{ body: Record<string, unknown>; status: number }> {
  let input: Record<string, unknown>;
  try {
    const raw = await request.text();
    if (raw.length > 16_384) return { body: { error: "request_too_large" }, status: 413 };
    input = JSON.parse(raw) as Record<string, unknown>;
    if (!input || typeof input !== "object" || Array.isArray(input)) throw new Error("invalid_object");
  } catch { return { body: { error: "invalid_json" }, status: 400 }; }
  const email = normalizeEmail(input.email);
  const code = typeof input.code === "string" ? input.code.trim() : "";
  if (!email || !/^\d{6}$/.test(code)) return { body: { error: "invalid_verification_data" }, status: 400 };
  if (!env.DATABASE_URL || !env.VERIFICATION_CODE_PEPPER || env.VERIFICATION_CODE_PEPPER.length < 32) return { body: { error: "email_verification_not_configured" }, status: 503 };
  const sql = neon(env.DATABASE_URL);
  const ip = clientIp(request);
  if (!await allowRateLimit(sql, env, "verify:ip:" + ip, 30, 900) ||
      !await allowRateLimit(sql, env, "verify:email:" + email, 10, 900)) {
    return { body: { error: "too_many_requests" }, status: 429 };
  }
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
    if (url.pathname === "/v1" && request.method === "GET") return response({ service: "sehati-backend-api", apiVersion: "v1", status: "auth-preview" }, 200, requestId, cors);
    try {
      if (request.method === "POST" && url.pathname === "/v1/auth/login") {
        const result = await login(request, env); return response(result.body, result.status, requestId, cors);
      }
      if (request.method === "POST" && url.pathname === "/v1/auth/refresh") {
        const result = await refreshSession(request, env); return response(result.body, result.status, requestId, cors);
      }
      if (request.method === "POST" && url.pathname === "/v1/auth/logout") {
        const result = await logout(request, env); return response(result.body, result.status, requestId, cors);
      }
      if (request.method === "GET" && url.pathname === "/v1/auth/me") {
        const result = await currentUser(request, env); return response(result.body, result.status, requestId, cors);
      }
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
  async scheduled(_controller: ScheduledController, env: Env, ctx: ExecutionContext): Promise<void> {
    if (!env.DATABASE_URL) return;
    ctx.waitUntil((async () => {
      const sql = neon(env.DATABASE_URL!);
      await sql.query("DELETE FROM auth_rate_limits WHERE window_started_at < now() - interval '2 days'");
      await sql.query("DELETE FROM auth_sessions WHERE refresh_expires_at < now() OR (revoked_at IS NOT NULL AND revoked_at < now() - interval '30 days')");
    })());
  },
};
