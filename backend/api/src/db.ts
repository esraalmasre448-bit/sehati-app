import { neon } from "@neondatabase/serverless";

export interface DatabaseEnv {
  DATABASE_URL?: string;
}

export type DatabaseCheck = {
  ok: boolean;
  diagnostic?: string;
};

function safeDiagnostic(error: unknown): string {
  const value = error as { code?: unknown; name?: unknown };
  const code = typeof value?.code === "string" ? value.code : "";
  if (code === "28P01" || code === "28000") return "database_auth_failed";
  if (code === "3D000") return "database_not_found";
  if (code === "57P03") return "database_unavailable";
  if (code.startsWith("08")) return "database_connection_failed";
  if (code === "ETIMEDOUT" || code === "ESOCKETTIMEDOUT") return "database_timeout";
  if (code === "ENOTFOUND" || code === "EAI_AGAIN") return "database_dns_failed";
  if (code === "CERT_HAS_EXPIRED" || code === "ERR_TLS_CERT_ALTNAME_INVALID") return "database_tls_failed";
  return "database_connection_error";
}

export async function checkDatabase(env: DatabaseEnv): Promise<DatabaseCheck> {
  if (!env.DATABASE_URL?.trim()) return { ok: false, diagnostic: "database_url_secret_missing" };
  try {
    const sql = neon(env.DATABASE_URL);
    const rows = await sql`select 1 as ok`;
    return rows.length === 1 && rows[0]?.ok === 1
      ? { ok: true }
      : { ok: false, diagnostic: "database_unexpected_response" };
  } catch (error) {
    const diagnostic = safeDiagnostic(error);
    // Never log the raw exception: it may contain connection details.
    console.error("Database readiness check failed", diagnostic);
    return { ok: false, diagnostic };
  }
}
