import { createAppBackendConnector, typedStreams } from "@zula/powersync"
import {
  createContext,
  use,
  useCallback,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react"
import { powersyncConfig } from "./config"
import { powerSyncDb } from "./db"

type PowerSyncContextValue = {
  db: typeof powerSyncDb
  ready: boolean
}

const PowerSyncContext = createContext<PowerSyncContextValue | null>(null)

export function PowerSyncProvider({
  children,
  getAccessToken,
}: {
  children: ReactNode
  getAccessToken: () => Promise<string | null>
}) {
  const [ready, setReady] = useState(false)

  const connector = useMemo(
    () =>
      createAppBackendConnector({
        powersyncUrl: powersyncConfig.powersyncUrl,
        syncBatchUrl: powersyncConfig.syncBatchUrl,
        getAccessToken,
      }),
    [getAccessToken]
  )

  useEffect(() => {
    let cancelled = false

    async function connect() {
      const token = await getAccessToken()
      if (!token) {
        if (!cancelled) setReady(false)
        return
      }

      await powerSyncDb.connect(connector)
      await typedStreams(powerSyncDb).me().subscribe()

      if (!cancelled) setReady(true)
    }

    void connect()

    return () => {
      cancelled = true
      void powerSyncDb.disconnect()
      setReady(false)
    }
  }, [connector, getAccessToken])

  return <PowerSyncContext value={{ db: powerSyncDb, ready }}>{children}</PowerSyncContext>
}

/** Pass a JWT getter when auth is wired; until then PowerSync stays local-only. */
export function PowerSyncAuthBridge({
  children,
  getAccessToken,
}: {
  children: ReactNode
  getAccessToken?: () => Promise<string | null>
}) {
  const resolveAccessToken = useCallback(
    async () => (getAccessToken ? getAccessToken() : null),
    [getAccessToken]
  )

  return <PowerSyncProvider getAccessToken={resolveAccessToken}>{children}</PowerSyncProvider>
}

export function usePowerSync() {
  const ctx = use(PowerSyncContext)
  if (!ctx) throw new Error("usePowerSync must be used within PowerSyncProvider")
  return ctx
}
