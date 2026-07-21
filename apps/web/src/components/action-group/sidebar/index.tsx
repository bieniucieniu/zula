import { ChevronDownIcon, ChevronRightIcon } from "lucide-react"
import { Fragment } from "react/jsx-runtime"
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import {
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarMenuSub,
  SidebarMenuSubItem,
  useSidebar,
} from "@/components/ui/sidebar"
import { DropdownActionButton } from "../dropdown"
import { useCreateActionContext } from "../hooks"
import type { SimpleAction, SimpleActionsProps } from "../types"
import { SidebarActionButton } from "./sidebar-actions"

export { SidebarActionButton } from "./sidebar-actions"
export { SidebarBasicActionButton } from "./sidebar-basic-action-button"
export { SidebarExternalLinkAction } from "./sidebar-external-link-action"
export { SidebarFilePullActionLink } from "./sidebar-file-pull-action-link"
export { SidebarFilePushActionButton } from "./sidebar-file-push-action-button"
export { SidebarLinkAction } from "./sidebar-link-action"
export { SidebarToggleActionButton } from "./sidebar-toggle-action-button"
export { SidebarToggleWithDialogActionButton } from "./sidebar-toggle-with-dialog-action-button"

export function SidebarActionsSub<C extends {} = {}>({
  actions,
  context: ctx,
  className,
}: {
  actions: SimpleAction<C>[]
  className?: string
} & SimpleActionsProps<C>) {
  const context = useCreateActionContext(ctx as C)

  return (
    <SidebarMenuSub className={className}>
      {actions.map((action, i) =>
        Array.isArray(action) ? (
          (() => {
            throw Error("Action sub items are not yet implemented")
          })()
        ) : (
          <Fragment key={i}>
            <SidebarMenuSubItem className="*:data-[slot=sidebar-menu-sub-button]:w-full">
              <SidebarActionButton action={action} context={context} />
            </SidebarMenuSubItem>
          </Fragment>
        )
      )}
    </SidebarMenuSub>
  )
}

export function SidebarActions<C extends {} = {}>({
  title,
  icon,
  className,
  actions,
  context: ctx,
  dropdown,
}: {
  title: string
  icon?: React.ReactNode
  className?: string
  dropdown?: true
} & SimpleActionsProps<C>) {
  const { state, isMobile } = useSidebar()
  const collapsed = state === "collapsed" && !isMobile
  const context = useCreateActionContext(ctx as C)

  if (collapsed || dropdown) {
    return (
      <DropdownMenu>
        <SidebarMenuItem className={className}>
          <DropdownMenuTrigger
            render={
              <SidebarMenuButton title={title}>
                {icon}
                <span>{title}</span>
                {dropdown && <ChevronRightIcon className="ml-auto text-muted-foreground" />}
              </SidebarMenuButton>
            }
          />

          <DropdownMenuContent side="right" align="start">
            {actions.map((action, i) => (
              <Fragment key={i}>
                <DropdownActionButton action={action} context={context} />
              </Fragment>
            ))}
          </DropdownMenuContent>
        </SidebarMenuItem>
      </DropdownMenu>
    )
  }

  return (
    <Collapsible
      render={
        <SidebarMenuItem className={className}>
          <CollapsibleTrigger
            render={
              <SidebarMenuButton title={title}>
                {icon}
                <span>{title}</span>
                <ChevronDownIcon
                  className={`ml-auto transition-transform duration-200 group-data-[state=open]/menu-item:-rotate-180`}
                  size={18}
                />
              </SidebarMenuButton>
            }
          />

          <CollapsibleContent
            render={
              <SidebarMenuSub>
                {actions.map((action, i) => (
                  <Fragment key={i}>
                    <SidebarMenuSubItem className="*:data-[slot=sidebar-menu-sub-button]:w-full">
                      <SidebarActionButton action={action} context={context} />
                    </SidebarMenuSubItem>
                  </Fragment>
                ))}
              </SidebarMenuSub>
            }
          />
        </SidebarMenuItem>
      }
    />
  )
}
