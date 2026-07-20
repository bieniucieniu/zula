import AsyncStorage from "@react-native-async-storage/async-storage"
import { registerRootComponent } from "expo"

import { view } from "./storybook.requires"

/**
 * This file is user-editable.
 *
 * Use it as your React Native Storybook entrypoint and wrap `StorybookUIRoot`
 * with application decorators/providers (theme, i18n, state, navigation, etc).
 */
const StorybookUIRoot = view.getStorybookUI({
  shouldPersistSelection: true,
  storage: AsyncStorage,
})

registerRootComponent(StorybookUIRoot)
