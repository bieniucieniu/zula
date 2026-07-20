import { describe, expect, test } from "bun:test"
import { resolveDataSourceConfig } from "../src/data-source"

describe("resolveDataSourceConfig", () => {
  test("api-only when POWERSYNC_URL missing", () => {
    const cfg = resolveDataSourceConfig({ API_URL: "http://localhost:8080/api/v1" })
    expect(cfg.mode).toBe("api")
    expect(cfg.powersyncUrl).toBeNull()
  })

  test("fallback mode when POWERSYNC_URL set", () => {
    const cfg = resolveDataSourceConfig({
      POWERSYNC_URL: "http://127.0.0.1:8081",
      API_URL: "http://localhost:8080/api/v1",
    })
    expect(cfg.mode).toBe("powersync-with-api-fallback")
    expect(cfg.powersyncUrl).toBe("http://127.0.0.1:8081")
  })
})
