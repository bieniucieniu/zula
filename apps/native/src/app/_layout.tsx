import "@/global.css"
import "react-native-random-uuid"

import { ThemeProvider } from "@react-navigation/native"
import { PortalHost } from "@rn-primitives/portal"
import { PersistQueryClientProvider } from "@tanstack/react-query-persist-client"
import { Stack } from "expo-router"
import { StatusBar } from "expo-status-bar"
import * as SystemUI from "expo-system-ui"
import { useEffect } from "react"
import { Uniwind, useUniwind } from "uniwind"
import { configureApiClient } from "@/lib/api"
import { AuthProvider } from "@/lib/auth"
import { persistOptions, queryClient } from "@/lib/query-client"
import { useNavTheme } from "@/lib/theme"

configureApiClient()

// RN 0.86 can leave UniwindStore theme as "unspecified" while Uniwind.currentTheme is light/dark.
// Flip once so runtime.currentThemeName syncs, then restore adaptive system theme.
{
  const boot = Uniwind.currentTheme
  Uniwind.setTheme(boot === "dark" ? "light" : "dark")
  Uniwind.setTheme("system")
}

export {
  // Catch any errors thrown by the Layout component.
  ErrorBoundary,
} from "expo-router"

export default function RootLayout() {
  const { theme } = useUniwind()
  const navTheme = useNavTheme()

  useEffect(() => {
    void SystemUI.setBackgroundColorAsync(navTheme.colors.background)
  }, [navTheme.colors.background])

  return (
    <PersistQueryClientProvider client={queryClient} persistOptions={persistOptions}>
      <AuthProvider>
        <ThemeProvider value={navTheme}>
          <StatusBar style={theme === "dark" ? "light" : "dark"} />
          <Stack
            screenOptions={{
              contentStyle: { backgroundColor: navTheme.colors.background },
              headerStyle: { backgroundColor: navTheme.colors.card },
              headerTintColor: navTheme.colors.text,
            }}
          />
          <PortalHost />
        </ThemeProvider>
      </AuthProvider>
    </PersistQueryClientProvider>
  )
}
