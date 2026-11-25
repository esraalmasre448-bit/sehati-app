import { neon } from "@neondatabase/serverless";

export interface DatabaseEnv {
  DATABASE_URL?: string;
}

export async function checkDatabase(env: DatabaseEnv): Promise<boolean> {
  if (!env.DATABASE_URL) return false;
  try {
    const sql = neon(env.DATABASE_URL);
    const rows = await sql`select 1 as ok`;
    return rows.length === 1 && rows[0]?.ok === 1;
  } catch {
    return false;
  }
}
