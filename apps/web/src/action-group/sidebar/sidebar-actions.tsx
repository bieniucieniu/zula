import { ChevronRightIcon } from "lucide-react"
import { Fragment } from "react/jsx-runtime"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { SidebarMenuSubButton } from "@/components/ui/sidebar"
import { resolve } from "@/lib/utils"
import { DropdownActionButton } from "../dropdown"
import type { ActionContext, BasicAction, SimpleAction, SubActions } from "../types"
import { SidebarBasicActionButton } from "./sidebar-basic-action-button"
import { SidebarExternalLinkAction } from "./sidebar-external-link-action"
import { SidebarFilePullActionLink } from "./sidebar-file-pull-action-link"
import { SidebarFilePushActionButton } from "./sidebar-file-push-action-button"
import { SidebarLinkAction } from "./sidebar-link-action"
import { SidebarToggleActionButton } from "./sidebar-toggle-action-button"
import { SidebarToggleWithDialogActionButton } from "./sidebar-toggle-with-dialog-action-button"

export function SidebarActionButton<C, T = void>(
  props: {
    action: SimpleAction<C, T>
    context: ActionContext & C
  } & Omit<React.ComponentProps<any>, "children">
) {
  const { action, ...rest } = props

  if (Array.isArray(action)) throw Error("Action sub items are not yet implemented")

  if (!("type" in action)) return <SidebarBasicActionButton {...rest} action={action} />

  switch (action.type) {
    case "file-push":
      return <SidebarFilePushActionButton {...rest} action={action} />
    case "file-link":
      return <SidebarFilePullActionLink {...rest} action={action} />
    case "link":
      return <SidebarLinkAction {...rest} action={action} />
    case "external-link":
      return <SidebarExternalLinkAction {...rest} action={action} />
    case "toggle":
      return <SidebarToggleActionButton {...rest} action={action} />
    case "toggle-with-dialog":
      return <SidebarToggleWithDialogActionButton {...rest} action={action} />
    case "sub":
      return <SidebarActionSub {...rest} action={action} />
  }
  return <SidebarBasicActionButton {...rest} action={action as BasicAction<C, T>} />
}

export function SidebarActionSub<C>({
  action,
  context,
  ...props
}: {
  action: SubActions<C>
  context: ActionContext & C
}) {
  const label = resolve(action.label, context)
  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        title={resolve(action.tooltip, context)}
        disabled={resolve(action.disabled, context)}
        {...props}
        render={
          <SidebarMenuSubButton>
            {resolve(action.icon, context)}
            {label}
            <ChevronRightIcon className="ml-auto" />
          </SidebarMenuSubButton>
        }
      />

      <DropdownMenuContent align="start" side="right">
        {action.actions.map((action, i) => (
          <Fragment key={i}>
            <DropdownActionButton action={action} context={context} />
          </Fragment>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
