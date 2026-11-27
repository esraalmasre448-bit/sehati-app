import { describe, expect, it } from "vitest";
import worker from "../src/index";

const env = { ALLOWED_ORIGINS: "https://app.example.test" };

describe("Sehaty backend API", () => {
  it("returns liveness without exposing configuration", async () => {
    const result = await worker.fetch(new Request("https://api.example.test/health"), env);
    expect(result.status).toBe(200);
    const body = await result.json() as Record<string, unknown>;
    expect(body.status).toBe("ok");
    expect(body.requestId).toBeTypeOf("string");
    expect(JSON.stringify(body)).not.toContain("DATABASE_URL");
  });

  it("does not claim database readiness without a configured database", async () => {
    const result = await worker.fetch(new Request("https://api.example.test/ready"), env);
    expect(result.status).toBe(503);
    const body = await result.json() as { status: string; dependencies: { postgres: string } };
    expect(body.status).toBe("not_ready");
    expect(body.dependencies.postgres).toBe("unavailable");
  });

  it("serves versioned API metadata", async () => {
    const result = await worker.fetch(new Request("https://api.example.test/v1"), env);
    expect(result.status).toBe(200);
    const body = await result.json() as Record<string, unknown>;
    expect(body.apiVersion).toBe("v1");
  });

  it("requires server and email provider secrets before registration", async () => {
    const result = await worker.fetch(new Request("https://api.example.test/v1/auth/register", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ email: "person@example.com", password: "long-enough-password", displayName: "Test" }),
    }), env);
    expect(result.status).toBe(503);
    const body = await result.json() as { error: string };
    expect(body.error).toBe("registration_not_configured");
  });

  it("rejects oversized registration payloads before requiring service secrets", async () => {
    const result = await worker.fetch(new Request("https://api.example.test/v1/auth/register", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ email: "person@example.com", padding: "x".repeat(17_000) }),
    }), env);
    expect(result.status).toBe(413);
    const body = await result.json() as { error: string };
    expect(body.error).toBe("request_too_large");
  });

  it("rejects malformed verification JSON without touching the database", async () => {
    const result = await worker.fetch(new Request("https://api.example.test/v1/auth/email/verify", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: "{not-json",
    }), env);
    expect(result.status).toBe(400);
    const body = await result.json() as { error: string };
    expect(body.error).toBe("invalid_json");
  });

  it("rejects weak verification pepper configuration", async () => {
    const result = await worker.fetch(new Request("https://api.example.test/v1/auth/register", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ email: "person@example.com", password: "long-enough-password", displayName: "Test" }),
    }), {
      ...env,
      DATABASE_URL: "postgres://placeholder",
      VERIFICATION_CODE_PEPPER: "too-short",
      RESEND_API_KEY: "placeholder",
      EMAIL_FROM: "Sehaty <no-reply@example.test>",
    });
    expect(result.status).toBe(503);
    const body = await result.json() as { error: string };
    expect(body.error).toBe("registration_not_configured");
  });

  it("rejects malformed login requests before accessing the database", async () => {
    const result = await worker.fetch(new Request("https://api.example.test/v1/auth/login", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: "{broken",
    }), env);
    expect(result.status).toBe(400);
    const body = await result.json() as { error: string };
    expect(body.error).toBe("invalid_json");
  });

  it("rejects refresh requests with malformed tokens", async () => {
    const result = await worker.fetch(new Request("https://api.example.test/v1/auth/refresh", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ refreshToken: "bad" }),
    }), env);
    expect(result.status).toBe(400);
    const body = await result.json() as { error: string };
    expect(body.error).toBe("invalid_refresh_token");
  });

  it("requires a bearer token for the current-user endpoint", async () => {
    const result = await worker.fetch(new Request("https://api.example.test/v1/auth/me"), env);
    expect(result.status).toBe(401);
    const body = await result.json() as { error: string };
    expect(body.error).toBe("unauthorized");
  });

  it("rejects unsupported methods", async () => {
    const result = await worker.fetch(new Request("https://api.example.test/health", { method: "PATCH" }), env);
    expect(result.status).toBe(405);
  });

  it("rejects unapproved browser origins", async () => {
    const result = await worker.fetch(new Request("https://api.example.test/health", {
      headers: { Origin: "https://attacker.example" },
    }), env);
    expect(result.status).toBe(403);
    const body = await result.json() as { error: string };
    expect(body.error).toBe("origin_not_allowed");
  });

  it("answers preflight for approved origins", async () => {
    const result = await worker.fetch(new Request("https://api.example.test/v1/auth/register", {
      method: "OPTIONS", headers: { Origin: "https://app.example.test" },
    }), env);
    expect(result.status).toBe(204);
    expect(result.headers.get("access-control-allow-origin")).toBe("https://app.example.test");
  });

  it("returns 404 for unknown routes", async () => {
    const result = await worker.fetch(new Request("https://api.example.test/unknown"), env);
    expect(result.status).toBe(404);
  });
});
