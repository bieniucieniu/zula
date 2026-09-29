import { resolve } from "@/lib/utils"
import { DropdownMenuItem } from "../../ui/dropdown-menu"
import type { ActionContext, ExternalLinkAction } from "../types"

export function ExternalLinkAction<C>({
  action,
  context,
  disabled,
  ...props
}: {
  action: ExternalLinkAction<C>
  context: ActionContext & C
} & Omit<React.ComponentProps<typeof DropdownMenuItem>, "children">) {
  const label = resolve(action.label, context)
  return (
    <DropdownMenuItem
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
    ></DropdownMenuItem>
  )
}
