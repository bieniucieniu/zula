import { Link, Redirect, Stack } from "expo-router"
import { MoonStarIcon, StarIcon, SunIcon } from "lucide-react-native"
import { Image, type ImageStyle, View } from "react-native"
import { Uniwind, useUniwind } from "uniwind"
import { Button } from "@/components/ui/button"
import { Icon } from "@/components/ui/icon"
import { Text } from "@/components/ui/text"
import { useAuth } from "@/lib/auth"
import { usePowerSync } from "@/lib/powersync"

const LOGO = {
  light: require("@assets/images/react-native-reusables-light.png"),
  dark: require("@assets/images/react-native-reusables-dark.png"),
}

const SCREEN_OPTIONS = {
  title: "React Native Reusables",
  headerTransparent: true,
  headerRight: () => <ThemeToggle />,
}

const IMAGE_STYLE: ImageStyle = {
  height: 76,
  width: 76,
}

export default function Screen() {
  const { theme } = useUniwind()
  const { session, ready, signOut } = useAuth()
  const { ready: syncReady } = usePowerSync()

  if (!ready) {
    return (
      <View className="flex-1 items-center justify-center">
        <Text className="text-sm text-muted-foreground">Loading…</Text>
      </View>
    )
  }

  if (!session) {
    return <Redirect href="/login" />
  }

  return (
    <>
      <Stack.Screen options={SCREEN_OPTIONS} />
      <View className="flex-1 items-center justify-center gap-8 p-4">
        <Image source={LOGO[theme ?? "light"]} style={IMAGE_STYLE} resizeMode="contain" />
        <View className="gap-2 p-4">
          <Text className="ios:text-foreground text-center text-sm text-muted-foreground">
            Signed in as {session.email || "user"}
          </Text>
          <Text className="ios:text-foreground text-muted-foreground font-mono text-sm">
            PowerSync (Expo Go / sql-js): {syncReady ? "connected" : "connecting…"}
          </Text>
        </View>
        <View className="flex-row gap-2">
          <Button onPress={() => void signOut()}>
            <Text>Sign out</Text>
          </Button>
          <Link href="https://reactnativereusables.com" asChild>
            <Button variant="ghost">
              <Text>Browse the Docs</Text>
            </Button>
          </Link>
          <Link href="https://github.com/founded-labs/react-native-reusables" asChild>
            <Button variant="ghost">
              <Text>Star the Repo</Text>
              <Icon as={StarIcon} />
            </Button>
          </Link>
        </View>
      </View>
    </>
  )
}

const THEME_ICONS = {
  light: SunIcon,
  dark: MoonStarIcon,
}

function ThemeToggle() {
  const { theme } = useUniwind()

  function toggleTheme() {
    const newTheme = theme === "dark" ? "light" : "dark"
    Uniwind.setTheme(newTheme)
  }

  return (
    <Button
      onPressIn={toggleTheme}
      size="icon"
      variant="ghost"
      className="ios:size-9 web:mx-4 rounded-full"
    >
      <Icon as={THEME_ICONS[theme ?? "light"]} className="size-5" />
    </Button>
  )
}
