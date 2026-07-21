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
import { useAuth } from "@/lib/auth"
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

export function PowerSyncAuthBridge({ children }: { children: ReactNode }) {
  const { session } = useAuth()

  const getAccessToken = useCallback(
    async () => session?.accessToken ?? null,
    [session?.accessToken]
  )

  return <PowerSyncProvider getAccessToken={getAccessToken}>{children}</PowerSyncProvider>
}

export function usePowerSync() {
  const ctx = use(PowerSyncContext)
  if (!ctx) throw new Error("usePowerSync must be used within PowerSyncProvider")
  return ctx
}
