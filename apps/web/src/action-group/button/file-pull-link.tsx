import { resolve } from "@/lib/utils"
import { Button } from "../../ui/button"
import type { ActionContext, FilePullActionLink as FilePullActionLinkType } from "../types"

export function FilePullActionLink<C>({
  action,
  context,
  disabled,
  ...props
}: {
  action: FilePullActionLinkType<C>
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
        <a href={action.href}>
          {resolve(action.icon, context)}
          {label}
        </a>
      }
    />
  )
}
