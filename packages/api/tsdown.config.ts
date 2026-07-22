import { defineConfig } from "tsdown"

export default defineConfig({
  entry: {
    index: "src/index.ts",
    model: "src/generated/model/index.ts",
    endpoints: "src/generated/endpoints.ts",
  },
  dts: true,
  exports: true,
})
