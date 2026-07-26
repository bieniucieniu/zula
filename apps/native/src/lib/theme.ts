import { DarkTheme, DefaultTheme, type Theme } from "@react-navigation/native"
import { useCSSVariable, useUniwind } from "uniwind"

function asColor(value: string | number | undefined, fallback: string): string {
  return typeof value === "string" ? value : fallback
}

/** Nav theme from Uniwind CSS vars (same tokens as className). */
export function useNavTheme(): Theme {
  const { theme } = useUniwind()
  const [background, foreground, card, border, primary, destructive] = useCSSVariable([
    "--color-background",
    "--color-foreground",
    "--color-card",
    "--color-border",
    "--color-primary",
    "--color-destructive",
  ])

  const dark = theme === "dark"
  const base = dark ? DarkTheme : DefaultTheme

  return {
    ...base,
    dark,
    colors: {
      ...base.colors,
      background: asColor(background, base.colors.background),
      text: asColor(foreground, base.colors.text),
      card: asColor(card, base.colors.card),
      border: asColor(border, base.colors.border),
      primary: asColor(primary, base.colors.primary),
      notification: asColor(destructive, base.colors.notification),
    },
  }
}
