import { Fragment } from "react/jsx-runtime"
import { Button, type ButtonProps } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { resolve } from "@/lib/utils"
import { DropdownActionButton } from "../dropdown"
import type { ActionContext, SimpleAction, SubActions } from "../types"
import {
  BasicActionButton,
  ExternalLinkAction,
  FilePullActionLink,
  FilePushActionButton,
  LinkAction,
  ToggleActionButton,
  ToggleWithDialogActionButton,
} from "./"

export function SimpleActionButton<C, T = void>(
  props: {
    action: SimpleAction<C, T>
    context: ActionContext & C
  } & Omit<React.ComponentProps<typeof Button>, "children">
) {
  const { action, ...rest } = props

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
      return <ToggleWithDialogActionButton {...rest} action={action} />
    case "sub":
      return <ButtonActionSub {...rest} action={action} />
    case "file-pull":
      return <BasicActionButton {...rest} action={action} />
  }
  return <BasicActionButton {...rest} action={action} />
}

export function ButtonActionSub<C>({
  action,
  context,
  ...props
}: {
  action: SubActions<C>
  context: ActionContext & C
} & ButtonProps) {
  const label = resolve(action.label, context)
  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        render={
          <Button
            size={label ? undefined : "icon"}
            title={resolve(action.tooltip, context)}
            disabled={resolve(action.disabled, context)}
            {...props}
          >
            {resolve(action.icon, context)}
            {label}
          </Button>
        }
      />
      <DropdownMenuContent>
        {action.actions.map((action, i) => (
          <Fragment key={i}>
            <DropdownActionButton action={action} context={context} />
          </Fragment>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
