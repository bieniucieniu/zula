import { Link } from "@tanstack/react-router"
import { cn, resolve } from "@/lib/utils"
import { SidebarMenuSubButton } from "../../ui/sidebar"
import type { ActionContext, LinkAction } from "../types"

export function SidebarLinkAction<C>({
  action,
  context,
  disabled,
  className,
  ...props
}: {
  action: LinkAction<C>
  context: ActionContext & C
  disabled?: boolean
} & Omit<React.ComponentProps<typeof Link>, "children">) {
  const label = resolve(action.label, context)
  return (
    <SidebarMenuSubButton
      className={cn("group-hover/menu-sub-item:underline", className)}
      render={
        <Link
          title={resolve(action.tooltip, context)}
          disabled={disabled || resolve(action.disabled, context)}
          {...resolve(action.link, context)}
          {...(props as any)}
        >
          {resolve(action.icon, context)}
          {label}
        </Link>
      }
    ></SidebarMenuSubButton>
  )
}
