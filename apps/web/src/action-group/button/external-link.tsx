import { resolve } from "@/lib/utils"
import { Button } from "../../ui/button"
import type { ActionContext, ExternalLinkAction } from "../types"

export function ExternalLinkAction<C>({
  action,
  context,
  disabled,
  ...props
}: {
  action: ExternalLinkAction<C>
  context: ActionContext & C
} & Omit<React.ComponentProps<typeof Button>, "children">) {
  const label = resolve(action.label, context)
  return (
    <Button
      size={label ? undefined : "icon"}
      title={resolve(action.tooltip, context)}
      disabled={disabled || resolve(action.disabled, context)}
      {...props}
      render={
        <a
          href={resolve(action.href, context)}
          target={resolve(action.target, context)}
          rel={resolve(action.rel, context)}
        >
          {resolve(action.icon, context)}
          {label}
        </a>
      }
    ></Button>
  )
}
