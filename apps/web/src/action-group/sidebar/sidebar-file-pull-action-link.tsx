import { resolve } from "@/lib/utils"
import { SidebarMenuSubButton } from "../../ui/sidebar"
import type { ActionContext, FilePullActionLink as FilePullActionLinkType } from "../types"

export function SidebarFilePullActionLink<C>({
  action,
  context,
  className,
  ...props
}: {
  action: FilePullActionLinkType<C>
  context: ActionContext & C
} & Omit<React.ComponentProps<typeof SidebarMenuSubButton>, "children">) {
  const label = resolve(action.label, context)

  if (resolve(action.disabled, context)) return null

  return (
    <SidebarMenuSubButton
      {...props}
      title={resolve(action.tooltip, context)}
      className={className}
      href={action.href}
    >
      {resolve(action.icon, context)}
      {label}
    </SidebarMenuSubButton>
  )
}
