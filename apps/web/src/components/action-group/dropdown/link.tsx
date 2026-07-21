import { Link } from "@tanstack/react-router"
import { resolve } from "@/lib/utils"
import { DropdownMenuItem } from "../../ui/dropdown-menu"
import type { ActionContext, LinkAction } from "../types"

export function LinkAction<C>({
  action,
  context,
  disabled,
  ...props
}: {
  action: LinkAction<C>
  context: ActionContext & C
} & Omit<React.ComponentProps<typeof DropdownMenuItem>, "children">) {
  const label = resolve(action.label, context)
  return (
    <DropdownMenuItem
      title={resolve(action.tooltip, context)}
      disabled={disabled || resolve(action.disabled, context)}
      {...props}
      render={
        <Link {...resolve(action.link, context)}>
          {resolve(action.icon, context)}
          {label}
        </Link>
      }
    />
  )
}
