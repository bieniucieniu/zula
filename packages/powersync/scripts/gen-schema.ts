/**
 * Generate AppSchema from the running PowerSync instance + sync-config.yaml.
 *
 * Requires: `devenv --profile powersync up` (or equivalent) so
 * POWERSYNC_URL (default http://127.0.0.1:8081) answers.
 */
import { $ } from "bun"
import { resolve } from "node:path"

const root = resolve(import.meta.dir, "../../..")
const powersyncDir = resolve(root, "powersync")
const out = resolve(import.meta.dir, "../src/generated/schema.ts")
const apiUrl = process.env.POWERSYNC_URL ?? "http://127.0.0.1:8081"

const probe = await fetch(`${apiUrl.replace(/\/$/, "")}/probes/readiness`).catch(
  () => null,
)
if (!probe?.ok) {
  console.error(
    `PowerSync not ready at ${apiUrl}. Start: devenv --profile powersync up`,
  )
  process.exit(1)
}

const result = await $`bunx powersync generate schema \
  --output=ts \
  --output-path=${out} \
  --api-url=${apiUrl} \
  --directory=${powersyncDir} \
  --sync-config-file-path=${powersyncDir}/sync-config.yaml`.nothrow()

if (result.exitCode !== 0) {
  console.error(result.stderr.toString() || result.stdout.toString())
  process.exit(result.exitCode ?? 1)
}

console.log(`wrote ${out}`)
