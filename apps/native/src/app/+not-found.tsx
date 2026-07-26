import { Link, Stack } from "expo-router"
import { View } from "react-native"
import { Text } from "@/components/ui/text"

export default function NotFoundScreen() {
  return (
    <>
      <Stack.Screen options={{ title: "Oops!" }} />
      <View className="bg-background flex-1 items-center justify-center gap-3 p-6">
        <Text>This screen doesn't exist.</Text>

        <Link href="/">
          <Text>Go to home screen!</Text>
        </Link>
      </View>
    </>
  )
}
