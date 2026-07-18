import { Platform, Pressable, type ViewProps } from "react-native"
import Animated, { type AnimatedProps } from "react-native-reanimated"

const AnimatedPressable = Animated.createAnimatedComponent(Pressable)

type NativeOnlyAnimatedViewProps = AnimatedProps<ViewProps> & {
  as?: "View" | "Pressable"
}

/**
 * This component is used to wrap animated views that should only be animated on native.
 * @param props - The props for the animated view.
 * @returns The animated view if the platform is native, otherwise the children.
 * @example
 * <NativeOnlyAnimatedView entering={FadeIn} exiting={FadeOut}>
 *   <Text>I am only animated on native</Text>
 * </NativeOnlyAnimatedView>
 */
function NativeOnlyAnimatedView({ as = "View", ...props }: NativeOnlyAnimatedViewProps) {
  if (Platform.OS === "web") {
    return <>{props.children as React.ReactNode}</>
  }

  if (as === "Pressable") {
    return <AnimatedPressable {...(props as React.ComponentProps<typeof Pressable>)} />
  }

  return <Animated.View {...props} />
}

export { NativeOnlyAnimatedView }
