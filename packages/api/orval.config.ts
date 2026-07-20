import { defineConfig } from "orval"

export default defineConfig({
  api: {
    input: {
      target: "http://localhost:8000/swagger/documentation.yaml",
    },
    output: {
      mode: "tags-split",
      target: "src/generated/endpoints.ts",
      schemas: "src/generated/model",
      client: "react-query",
      clean: true,
      override: {
        mutator: {
          path: "./src/mutator.ts",
          name: "customInstance",
        },
      },
    },
  },
})
