import "@/global.css"
import "react-native-random-uuid"

import { ThemeProvider } from "@react-navigation/native"
import { PortalHost } from "@rn-primitives/portal"
import { PersistQueryClientProvider } from "@tanstack/react-query-persist-client"
import { Stack } from "expo-router"
import { StatusBar } from "expo-status-bar"
import { useUniwind } from "uniwind"
import { AuthProvider } from "@/lib/auth"
import { persistOptions, queryClient } from "@/lib/query-client"
import { NAV_THEME } from "@/lib/theme"

export {
  // Catch any errors thrown by the Layout component.
  ErrorBoundary,
} from "expo-router"

export default function RootLayout() {
  const { theme } = useUniwind()

  return (
    <PersistQueryClientProvider client={queryClient} persistOptions={persistOptions}>
      <AuthProvider>
        <ThemeProvider value={NAV_THEME[theme ?? "light"]}>
          <StatusBar style={theme === "dark" ? "light" : "dark"} />
          <Stack />
          <PortalHost />
        </ThemeProvider>
      </AuthProvider>
    </PersistQueryClientProvider>
  )
}
