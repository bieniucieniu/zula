import { Link, Outlet, useNavigate } from "@tanstack/react-router"
import { useLogout } from "@zula/api/endpoints"
import { useEffect, useState, type ReactNode } from "react"
import { Button, buttonVariants } from "@/components/ui/button"
import { useAuth } from "@/lib/auth"
import { cn } from "@/lib/utils"

const navLinkClass = cn(buttonVariants({ variant: "ghost", size: "sm" }))

export function AppShell({ children }: { children?: ReactNode }) {
  const { session, refresh } = useAuth()
  const logout = useLogout()
  const navigate = useNavigate()
  const [pending, setPending] = useState(false)

  async function onLogout() {
    setPending(true)
    try {
      await logout.mutateAsync({})
    } catch {
      // still clear local session
    } finally {
      await refresh()
      setPending(false)
      await navigate({ to: "/login" })
    }
  }

  return (
    <div className="min-h-svh">
      <header className="border-b">
        <div className="mx-auto flex max-w-2xl items-center gap-1 px-4 py-2">
          <Link to="/feed" className={navLinkClass}>
            Feed
          </Link>
          <Link to="/groups" className={navLinkClass}>
            Groups
          </Link>
          <Link to="/profile" className={navLinkClass}>
            Profile
          </Link>
          <Link to="/feed" className={navLinkClass}>
            Trades
          </Link>
          <div className="ml-auto flex items-center gap-2">
            {session?.email ? (
              <span className="hidden text-xs text-muted-foreground sm:inline">
                {session.email}
              </span>
            ) : null}
            <Button variant="outline" size="sm" disabled={pending} onClick={() => void onLogout()}>
              {pending ? "Logging out…" : "Logout"}
            </Button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-2xl space-y-4 px-4 py-6">{children ?? <Outlet />}</main>
    </div>
  )
}

export function RequireAuth({ children }: { children: ReactNode }) {
  const { session, ready } = useAuth()
  const navigate = useNavigate()

  useEffect(() => {
    if (ready && !session) {
      void navigate({ to: "/login" })
    }
  }, [ready, session, navigate])

  if (!ready || !session) {
    return (
      <div className="flex min-h-svh items-center justify-center p-6">
        <p className="text-sm text-muted-foreground">Loading…</p>
      </div>
    )
  }

  return children
}
