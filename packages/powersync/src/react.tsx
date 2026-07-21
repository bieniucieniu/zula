import type { AbstractPowerSyncDatabase } from "@powersync/common"
import { createContext, use, useEffect, useState, type ReactNode } from "react"
import { connectAppPowerSync, disconnectAppPowerSync } from "./client"
import type { GetAppPowerSyncCredentials } from "./connector"

type PowerSyncContextValue<TDb extends AbstractPowerSyncDatabase> = {
  db: TDb
  ready: boolean
}

const PowerSyncContext =
  createContext<PowerSyncContextValue<AbstractPowerSyncDatabase> | null>(null)

export function PowerSyncProvider<TDb extends AbstractPowerSyncDatabase>({
  db,
  getCredentials,
  children,
}: {
  db: TDb
  getCredentials: GetAppPowerSyncCredentials
  children: ReactNode
}) {
  const [ready, setReady] = useState(false)

  useEffect(() => {
    let cancelled = false

    async function connect() {
      const credentials = await getCredentials()
      if (!credentials) {
        if (!cancelled) setReady(false)
        return
      }

      await connectAppPowerSync(db, getCredentials)
      if (!cancelled) setReady(true)
    }

    void connect()

    return () => {
      cancelled = true
      void disconnectAppPowerSync(db)
      setReady(false)
    }
  }, [db, getCredentials])

  return <PowerSyncContext value={{ db, ready }}>{children}</PowerSyncContext>
}

export function usePowerSync<
  TDb extends AbstractPowerSyncDatabase = AbstractPowerSyncDatabase,
>() {
  const ctx = use(PowerSyncContext)
  if (!ctx) throw new Error("usePowerSync must be used within PowerSyncProvider")
  return ctx as PowerSyncContextValue<TDb>
}
