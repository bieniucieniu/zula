import "@/global.css"

import { NAV_THEME } from "@/lib/theme"
import { PowerSyncAuthBridge } from "@/lib/powersync"
import { ThemeProvider } from "@react-navigation/native"
import { PortalHost } from "@rn-primitives/portal"
import { Stack } from "expo-router"
import { StatusBar } from "expo-status-bar"
import { useUniwind } from "uniwind"

export {
  // Catch any errors thrown by the Layout component.
  ErrorBoundary,
} from "expo-router"

export default function RootLayout() {
  const { theme } = useUniwind()

  return (
    <ThemeProvider value={NAV_THEME[theme ?? "light"]}>
      <PowerSyncAuthBridge>
        <StatusBar style={theme === "dark" ? "light" : "dark"} />
        <Stack />
        <PortalHost />
      </PowerSyncAuthBridge>
    </ThemeProvider>
  )
}
