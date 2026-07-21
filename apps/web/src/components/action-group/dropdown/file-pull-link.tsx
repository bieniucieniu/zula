import { resolve } from "@/lib/utils"
import { DropdownMenuItem } from "../../ui/dropdown-menu"
import type { ActionContext, FilePullActionLink as FilePullActionLinkType } from "../types"

export function FilePullActionLink<C>({
  action,
  context,
  disabled,
  ...props
}: {
  action: FilePullActionLinkType<C>
  context: ActionContext & C
} & Omit<React.ComponentProps<typeof DropdownMenuItem>, "children">) {
  const label = resolve(action.label, context)
  return (
    <DropdownMenuItem
      title={resolve(action.tooltip, context)}
      disabled={disabled || resolve(action.disabled, context)}
      {...props}
      render={
        <a href={action.href}>
          {resolve(action.icon, context)}
          {label}
        </a>
      }
    ></DropdownMenuItem>
  )
}
