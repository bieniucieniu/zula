import type { BaseUIEvent } from "@base-ui/react"
import { resolve } from "@/lib/utils"
import { DropdownMenuItem } from "../../ui/dropdown-menu"
import { useMutationAction } from "../hooks"
import type { ActionContext, ToggleAction } from "../types"

export function ToggleActionButton<C, T = void>({
  action,
  onClick,
  disabled,
  context,
  ...props
}: {
  action: ToggleAction<C, T>
  context: ActionContext & C
} & Omit<React.ComponentProps<typeof DropdownMenuItem>, "children">) {
  const {
    localContext,
    mutation: mut,
    throttle,
  } = useMutationAction({
    action,
    context,
  })
  const onSelectHandler: React.ComponentProps<typeof DropdownMenuItem>["onSelect"] = (e) => {
    e.preventDefault()
    onClick?.(e as BaseUIEvent<React.MouseEvent<HTMLDivElement, MouseEvent>>)
    throttle.maybeExecute()
  }
  const label = resolve(action.label, localContext)
  return (
    <DropdownMenuItem
      title={resolve(action.tooltip, localContext)}
      disabled={disabled || mut.isPending || resolve(action.disabled, localContext)}
      onSelect={onSelectHandler}
      {...props}
    >
      {resolve(action.icon, localContext)}
      {label}
    </DropdownMenuItem>
  )
}
