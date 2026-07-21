import type { VariantProps } from "class-variance-authority"
import { Fragment } from "react/jsx-runtime"
import type { buttonVariants } from "@/components/ui/button"
import { ButtonGroup } from "@/components/ui/button-group"
import { useCreateActionContext } from "../hooks"
import type { ActionContext, SimpleAction, SimpleActionsProps } from "../types"
import { SimpleActionButton } from "./action-button"

export { ExternalLinkAction } from "./external-link"
export { FilePullActionLink } from "./file-pull-link"
export { FilePushActionButton } from "./file-push-button"
export { LinkAction } from "./link"
export { BasicActionButton } from "./simple-button"
export { ToggleActionButton } from "./toggle-button"
export { ToggleWithDialogActionButton } from "./toggle-with-dialog-button"

export function SimpleActions<C extends {} = {}>({
  actions,
  variant = "outline",
  context: ctx,
  ...props
}: SimpleActionsProps<C> &
  Omit<React.ComponentProps<typeof ButtonGroup>, "children"> &
  VariantProps<typeof buttonVariants>) {
  return (
    <SimpleActionsMinimial
      actions={actions}
      context={useCreateActionContext(ctx as C)}
      variant={variant}
      {...props}
    />
  )
}

export function SimpleActionsMinimial<C extends {} = {}>({
  actions,
  variant = "outline",
  context,
  ...props
}: {
  actions: SimpleAction<C>[]
  context: ActionContext & C
} & Omit<React.ComponentProps<typeof ButtonGroup>, "children"> &
  VariantProps<typeof buttonVariants>) {
  return (
    <ButtonGroup {...props}>
      {actions.map((action, i) => (
        <Fragment key={i}>
          <SimpleActionButton context={context} variant={variant} action={action} />
        </Fragment>
      ))}
    </ButtonGroup>
  )
}
