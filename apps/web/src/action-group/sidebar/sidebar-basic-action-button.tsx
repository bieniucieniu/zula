import { resolve } from "@/lib/utils"
import { SidebarMenuSubButton } from "../../ui/sidebar"
import { useMutationAction } from "../hooks"
import type { ActionContext, BasicAction } from "../types"

export function SidebarBasicActionButton<C, T = void>({
  action,
  onClick,
  disabled,
  context,
  className,
  ...props
}: {
  action: BasicAction<C, T>
  context: ActionContext & C
} & Omit<React.ComponentProps<"button">, "children">) {
  const {
    localContext,
    mutation: mut,
    throttle,
  } = useMutationAction({
    action,
    context,
    run: () => action.action(context),
  })
  const onClickHandler: typeof onClick = (e) => {
    e.preventDefault()
    onClick?.(e)
    throttle.maybeExecute()
  }
  const label = resolve(action.label, localContext)
  return (
    <SidebarMenuSubButton
      className={className}
      render={
        <button
          title={resolve(action.tooltip, localContext)}
          disabled={disabled || mut.isPending || resolve(action.disabled, localContext)}
          onClick={onClickHandler}
          {...props}
        >
          {resolve(action.icon, localContext)}
          {label}
        </button>
      }
    />
  )
}
