import { PowerSyncProvider, usePowerSync } from "@zula/powersync/react"
import { useCallback, type ReactNode } from "react"
import { getAppPowerSyncCredentials } from "./credentials"
import { powerSyncDb } from "./db"

/** Pass `getAccessToken` when auth is wired; until then PowerSync stays local-only. */
export function PowerSyncAuthBridge({
  children,
  getAccessToken,
}: {
  children: ReactNode
  getAccessToken?: () => Promise<string | null>
}) {
  const getCredentials = useCallback(async () => {
    const accessToken = getAccessToken ? await getAccessToken() : null
    return getAppPowerSyncCredentials(accessToken)
  }, [getAccessToken])

  return (
    <PowerSyncProvider db={powerSyncDb} getCredentials={getCredentials}>
      {children}
    </PowerSyncProvider>
  )
}

export { usePowerSync }
