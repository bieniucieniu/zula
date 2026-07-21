import { resolve } from "@/lib/utils"
import { SidebarMenuSubButton } from "../../ui/sidebar"
import { useMutationAction } from "../hooks"
import type { ActionContext, ToggleAction } from "../types"

export function SidebarToggleActionButton<C, T = void>({
  action,
  onClick,
  disabled,
  context,
  className,
  ...props
}: {
  action: ToggleAction<C, T>
  context: ActionContext & C
  disabled?: boolean
} & Omit<React.ComponentProps<"button">, "children">) {
  const {
    localContext,
    mutation: mut,
    throttle,
  } = useMutationAction({
    action,
    context,
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
          {...props}
          title={resolve(action.tooltip, localContext)}
          disabled={disabled || mut.isPending || resolve(action.disabled, localContext)}
          onClick={onClickHandler}
        >
          {resolve(action.icon, localContext)}
          {label}
        </button>
      }
    ></SidebarMenuSubButton>
  )
}
