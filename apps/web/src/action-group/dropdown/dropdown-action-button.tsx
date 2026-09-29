import { Fragment } from "react/jsx-runtime"
import { cn, resolve } from "@/lib/utils"
import {
  type DropdownMenuItem,
  DropdownMenuSub,
  DropdownMenuSubContent,
  DropdownMenuSubTrigger,
} from "../../ui/dropdown-menu"
import type { ActionContext, BasicAction, SimpleAction, SubActions } from "../types"
import { ExternalLinkAction } from "./external-link"
import { FilePullActionLink } from "./file-pull-link"
import { FilePushActionButton } from "./file-push-button"
import { LinkAction } from "./link"
import { BasicActionButton } from "./simple-button"
import { ToggleActionButton } from "./toggle-button"
import { ToggleWithDialogActionButton } from "./toggle-with-dialog-button"

export function DropdownActionButton<C, T = void>(
  props: {
    action: SimpleAction<C, T>
    context: ActionContext & C
    className?: string
  } & Omit<React.ComponentProps<typeof DropdownMenuItem>, "children" | "className">
) {
  const { action, ...rest } = props

  if (Array.isArray(action)) throw Error("Action sub items are not yet implemented")

  if (!("type" in action)) return <BasicActionButton {...rest} action={action} />

  switch (action.type) {
    case "file-push":
      return <FilePushActionButton {...rest} action={action} />
    case "file-link":
      return <FilePullActionLink {...rest} action={action} />
    case "link":
      return <LinkAction {...rest} action={action} />
    case "external-link":
      return <ExternalLinkAction {...rest} action={action} />
    case "toggle":
      return <ToggleActionButton {...rest} action={action} />
    case "toggle-with-dialog":
      return (
        <ToggleWithDialogActionButton
          action={action}
          {...(rest as Omit<
            React.ComponentProps<typeof ToggleWithDialogActionButton<C, T>>,
            "action"
          >)}
        />
      )
    case "sub":
      return <DropdownActionSub {...rest} action={action} />
  }
  return <BasicActionButton {...rest} action={action as BasicAction<C, T>} />
}

export function DropdownActionSub<C>({
  action,
  context,
  className,
  ...props
}: {
  action: SubActions<C>
  context: ActionContext & C
  className?: string
}) {
  const label = resolve(action.label, context)
  return (
    <DropdownMenuSub>
      <DropdownMenuSubTrigger
        title={resolve(action.tooltip, context)}
        disabled={resolve(action.disabled, context)}
        className={cn(
          "[&_svg:not([class*='text-'])]:text-muted-foreground [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4 gap-2",
          className
        )}
        {...props}
      >
        {resolve(action.icon, context)}
        {label}
      </DropdownMenuSubTrigger>
      <DropdownMenuSubContent>
        {action.actions.map((action, i) => (
          <Fragment key={i}>
            <DropdownActionButton action={action} context={context} />
          </Fragment>
        ))}
      </DropdownMenuSubContent>
    </DropdownMenuSub>
  )
}
