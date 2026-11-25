import { checkDatabase } from "./db";

export interface Env {
  ALLOWED_ORIGINS?: string;
  DATABASE_URL?: string;
}

const jsonHeaders = { "content-type": "application/json; charset=utf-8" };

function response(
  body: Record<string, unknown>,
  status: number,
  requestId: string,
  corsHeaders: HeadersInit = {},
): Response {
  return new Response(JSON.stringify({ ...body, requestId }), {
    status,
    headers: { ...jsonHeaders, "cache-control": "no-store", ...corsHeaders },
  });
}

function allowedOrigin(request: Request, env: Env): string | null {
  const origin = request.headers.get("Origin");
  if (!origin) return null;
  const allowed = (env.ALLOWED_ORIGINS ?? "")
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
  return allowed.includes(origin) ? origin : null;
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const requestId = crypto.randomUUID();
    const origin = allowedOrigin(request, env);
    const corsHeaders: Record<string, string> = {
      "vary": "Origin",
      ...(origin ? {
        "access-control-allow-origin": origin,
        "access-control-allow-methods": "GET, OPTIONS",
        "access-control-allow-headers": "content-type, authorization, x-request-id",
      } : {}),
    };

    if (request.method === "OPTIONS") {
      if (request.headers.has("Origin") && !origin) {
        return response({ error: "origin_not_allowed" }, 403, requestId);
      }
      return new Response(null, { status: 204, headers: corsHeaders });
    }

    if (request.method !== "GET") {
      return response({ error: "method_not_allowed" }, 405, requestId, corsHeaders);
    }

    const url = new URL(request.url);
    if (url.pathname === "/health") {
      return response({ status: "ok", service: "sehati-backend-api" }, 200, requestId, corsHeaders);
    }

    if (url.pathname === "/ready") {
      const databaseReady = await checkDatabase(env);
      return response(
        {
          status: databaseReady ? "ready" : "not_ready",
          dependencies: { postgres: databaseReady ? "connected" : "unavailable" },
        },
        databaseReady ? 200 : 503,
        requestId,
        corsHeaders,
      );
    }

    if (url.pathname === "/v1") {
      return response(
        { service: "sehati-backend-api", apiVersion: "v1", status: "scaffold" },
        200,
        requestId,
        corsHeaders,
      );
    }

    return response({ error: "not_found" }, 404, requestId, corsHeaders);
  },
};
