import { PowerSyncProvider, usePowerSync } from "@zula/powersync/react"
import { useCallback, type ReactNode } from "react"
import { useAuth } from "@/lib/auth"
import { getAppPowerSyncCredentials } from "./credentials"
import { powerSyncDb } from "./db"

export function PowerSyncAuthBridge({ children }: { children: ReactNode }) {
  const { session } = useAuth()

  const getCredentials = useCallback(
    () => getAppPowerSyncCredentials(session?.accessToken),
    [session?.accessToken]
  )

  return (
    <PowerSyncProvider db={powerSyncDb} getCredentials={getCredentials}>
      {children}
    </PowerSyncProvider>
  )
}

export { usePowerSync }
