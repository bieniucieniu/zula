import "@/global.css"

import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { NAV_THEME } from "@/lib/theme"
import { ThemeProvider } from "@react-navigation/native"
import { PortalHost } from "@rn-primitives/portal"
import { Stack } from "expo-router"
import { StatusBar } from "expo-status-bar"
import { useUniwind } from "uniwind"
import { AuthProvider } from "@/lib/auth"
import { usePowerSyncAuth } from "@/lib/powersync"

export {
  // Catch any errors thrown by the Layout component.
  ErrorBoundary,
} from "expo-router"

const queryClient = new QueryClient()

function PowerSyncConnector() {
  usePowerSyncAuth()
  return null
}

export default function RootLayout() {
  const { theme } = useUniwind()

  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <PowerSyncConnector />
        <ThemeProvider value={NAV_THEME[theme ?? "light"]}>
          <StatusBar style={theme === "dark" ? "light" : "dark"} />
          <Stack />
          <PortalHost />
        </ThemeProvider>
      </AuthProvider>
    </QueryClientProvider>
  )
}
