import { Link, Outlet, useMatchRoute, useNavigate } from "@tanstack/react-router"
import { useLogout } from "@zula/api/endpoints"
import {
  ArrowLeftRightIcon,
  LogOutIcon,
  NewspaperIcon,
  UserIcon,
  UsersIcon,
} from "lucide-react"
import { useEffect, useState, type ReactNode } from "react"
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarHeader,
  SidebarInset,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarProvider,
  SidebarRail,
  SidebarTrigger,
} from "@/components/ui/sidebar"
import { useAuth } from "@/lib/auth"

const navItems = [
  { to: "/feed" as const, label: "Feed", icon: NewspaperIcon, match: "feed" as const },
  { to: "/groups" as const, label: "Groups", icon: UsersIcon, match: "groups" as const },
  { to: "/profile" as const, label: "Profile", icon: UserIcon, match: "profile" as const },
  { to: "/feed" as const, label: "Trades", icon: ArrowLeftRightIcon, match: "none" as const },
]

export function AppShell({ children }: { children?: ReactNode }) {
  const { session, refresh } = useAuth()
  const logout = useLogout()
  const navigate = useNavigate()
  const matchRoute = useMatchRoute()
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
    <SidebarProvider>
      <Sidebar collapsible="icon">
        <SidebarHeader>
          <SidebarMenu>
            <SidebarMenuItem>
              <SidebarMenuButton
                size="lg"
                tooltip="Zula"
                render={<Link to="/feed" />}
              >
                <span className="font-heading text-sm font-semibold">Zula</span>
              </SidebarMenuButton>
            </SidebarMenuItem>
          </SidebarMenu>
        </SidebarHeader>

        <SidebarContent>
          <SidebarGroup>
            <SidebarGroupContent>
              <SidebarMenu>
                {navItems.map((item) => {
                  const Icon = item.icon
                  const isActive =
                    item.match === "none"
                      ? false
                      : !!matchRoute({ to: `/${item.match}`, fuzzy: true })
                  return (
                    <SidebarMenuItem key={item.label}>
                      <SidebarMenuButton
                        isActive={isActive}
                        tooltip={item.label}
                        render={<Link to={item.to} />}
                      >
                        <Icon />
                        <span>{item.label}</span>
                      </SidebarMenuButton>
                    </SidebarMenuItem>
                  )
                })}
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
        </SidebarContent>

        <SidebarFooter>
          <SidebarMenu>
            {session?.email ? (
              <SidebarMenuItem>
                <SidebarMenuButton tooltip={session.email} className="pointer-events-none">
                  <UserIcon />
                  <span className="truncate">{session.email}</span>
                </SidebarMenuButton>
              </SidebarMenuItem>
            ) : null}
            <SidebarMenuItem>
              <SidebarMenuButton
                tooltip="Logout"
                disabled={pending}
                onClick={() => void onLogout()}
              >
                <LogOutIcon />
                <span>{pending ? "Logging out…" : "Logout"}</span>
              </SidebarMenuButton>
            </SidebarMenuItem>
          </SidebarMenu>
        </SidebarFooter>
        <SidebarRail />
      </Sidebar>

      <SidebarInset>
        <header className="flex h-12 shrink-0 items-center gap-2 border-b px-3">
          <SidebarTrigger />
        </header>
        <div className="mx-auto w-full max-w-2xl space-y-4 px-4 py-6">
          {children ?? <Outlet />}
        </div>
      </SidebarInset>
    </SidebarProvider>
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
