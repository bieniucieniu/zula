import { Fragment } from "react/jsx-runtime"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { useCreateActionContext } from "../hooks"
import type { SimpleActionsProps } from "../types"
import { DropdownActionButton } from "./dropdown-action-button"

export {
  DropdownActionButton,
  DropdownActionSub,
} from "./dropdown-action-button"
export { ExternalLinkAction } from "./external-link"
export { FilePullActionLink } from "./file-pull-link"
export { FilePushActionButton } from "./file-push-button"
export { LinkAction } from "./link"
export { BasicActionButton } from "./simple-button"
export { ToggleActionButton } from "./toggle-button"
export { ToggleWithDialogActionButton } from "./toggle-with-dialog-button"

export function DropdownActions<C extends {} = {}>({
  actions,
  context: ctx = {} as C,
  open,
  setOpen,
  ...props
}: {
  open?: boolean
  setOpen?: (open: boolean) => void
} & SimpleActionsProps<C> &
  React.ComponentProps<typeof DropdownMenuTrigger>) {
  const context = useCreateActionContext(ctx as C)

  return (
    <DropdownMenu open={open} onOpenChange={setOpen}>
      <DropdownMenuTrigger {...props} />
      <DropdownMenuContent>
        {actions.map((action, i) => (
          <Fragment key={i}>
            <DropdownActionButton action={action} context={context} />
          </Fragment>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
