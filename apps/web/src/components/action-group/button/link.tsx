import { Link } from "@tanstack/react-router"
import { cn, resolve } from "@/lib/utils"
import { Button } from "../../ui/button"
import type { ActionContext, LinkAction } from "../types"

export function LinkAction<C>({
  action,
  context,
  disabled,
  ...props
}: {
  action: LinkAction<C>
  context: ActionContext & C
} & Omit<React.ComponentProps<typeof Button>, "children">) {
  const label = resolve(action.label, context)
  return (
    <Button
      size={label ? undefined : "icon"}
      title={resolve(action.tooltip, context)}
      disabled={disabled || resolve(action.disabled, context)}
      className={cn(label && "justify-between")}
      {...props}
      render={
        <Link {...resolve(action.link, context)}>
          {resolve(action.icon, context)}
          {label}
        </Link>
      }
    ></Button>
  )
}
