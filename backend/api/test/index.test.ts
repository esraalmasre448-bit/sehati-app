import { describe, expect, it } from "vitest";
import worker from "../src/index";

const env = { ALLOWED_ORIGINS: "https://app.example.test" };

describe("Sehaty backend API scaffold", () => {
  it("returns a minimal liveness response", async () => {
    const result = await worker.fetch(new Request("https://api.example.test/health"), env);
    expect(result.status).toBe(200);
    const body = await result.json() as Record<string, unknown>;
    expect(body.status).toBe("ok");
    expect(body.requestId).toBeTypeOf("string");
    expect(JSON.stringify(body)).not.toContain("DATABASE_URL");
  });

  it("does not claim database readiness when no connection is configured", async () => {
    const result = await worker.fetch(new Request("https://api.example.test/ready"), env);
    expect(result.status).toBe(503);
    const body = await result.json() as { status: string; dependencies: { postgres: string } };
    expect(body.status).toBe("not_ready");
    expect(body.dependencies.postgres).toBe("unavailable");
  });

  it("serves the versioned API root", async () => {
    const result = await worker.fetch(new Request("https://api.example.test/v1"), env);
    expect(result.status).toBe(200);
    const body = await result.json() as Record<string, unknown>;
    expect(body.apiVersion).toBe("v1");
  });

  it("rejects unapproved browser origins", async () => {
    const result = await worker.fetch(new Request("https://api.example.test/health", {
      headers: { Origin: "https://attacker.example" },
    }), env);
    expect(result.headers.get("access-control-allow-origin")).toBeNull();
  });

  it("rejects non-GET methods", async () => {
    const result = await worker.fetch(new Request("https://api.example.test/health", {
      method: "POST",
    }), env);
    expect(result.status).toBe(405);
  });

  it("returns 404 for unknown routes", async () => {
    const result = await worker.fetch(new Request("https://api.example.test/unknown"), env);
    expect(result.status).toBe(404);
  });
});
