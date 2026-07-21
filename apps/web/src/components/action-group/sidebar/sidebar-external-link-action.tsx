import { cn, resolve } from "@/lib/utils"
import { SidebarMenuSubButton } from "../../ui/sidebar"
import type { ActionContext, ExternalLinkAction } from "../types"

export function SidebarExternalLinkAction<C>({
  action,
  context,
  className,
  ...props
}: {
  action: ExternalLinkAction<C>
  context: ActionContext & C
} & Omit<React.ComponentProps<typeof SidebarMenuSubButton>, "children">) {
  const label = resolve(action.label, context)
  if (resolve(action.disabled, context)) return null
  return (
    <SidebarMenuSubButton
      {...props}
      title={resolve(action.tooltip, context)}
      className={cn("group-hover/menu-sub-item:underline", className)}
      href={resolve(action.href, context)}
      target={resolve(action.target, context)}
      rel={resolve(action.rel, context)}
    >
      {resolve(action.icon, context)}
      {label}
    </SidebarMenuSubButton>
  )
}
