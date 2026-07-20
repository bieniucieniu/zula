import type { Meta, StoryObj } from "@storybook/react-native"
import { View } from "react-native"
import { fn } from "storybook/test"
import { Button } from "@/components/ui/button"
import { Text } from "@/components/ui/text"

const meta = {
  title: "Example/Button",
  component: Button,
  decorators: [
    (Story) => (
      <View className="flex-1 items-center justify-center">
        <Story />
      </View>
    ),
  ],
  // This component will have an automatically generated Autodocs entry: https://storybook.js.org/docs/writing-docs/autodocs
  tags: ["autodocs"],
  // Use `fn` to spy on the onPress arg, which will appear in the actions panel once invoked: https://storybook.js.org/docs/essentials/actions#story-args
  args: { onPress: fn() },
} satisfies Meta<typeof Button>

export default meta

type Story = StoryObj<typeof meta>

export const Primary: Story = {
  args: {
    variant: "default",
    children: <Text>Button</Text>,
  },
}

export const Secondary: Story = {
  args: {
    variant: "secondary",
    children: <Text>Button</Text>,
  },
}

export const Large: Story = {
  args: {
    size: "lg",
    children: <Text>Button</Text>,
  },
}

export const Small: Story = {
  args: {
    size: "sm",
    children: <Text>Button</Text>,
  },
}
