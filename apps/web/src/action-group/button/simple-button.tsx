import type { BaseUIEvent } from "@base-ui/react"
import { cn, resolve } from "@/lib/utils"
import { Button } from "../../ui/button"
import { useMutationAction } from "../hooks"
import type { ActionContext, BasicAction } from "../types"

export function BasicActionButton<C, T = void>({
  action,
  context,
  disabled,
  onClick,
  ...props
}: {
  action: BasicAction<C, T>
  context: ActionContext & C
} & Omit<React.ComponentProps<typeof Button>, "children">) {
  const { localContext, mutation, throttle } = useMutationAction({
    action: action,
    context: context,
  })

  const label = resolve(action.label, localContext)
  const onClickHandler = (e: BaseUIEvent<React.MouseEvent<HTMLButtonElement>>) => {
    e.preventDefault()
    onClick?.(e)
    throttle.maybeExecute()
  }

  return (
    <Button
      size={label ? undefined : "icon"}
      title={resolve(action.tooltip, localContext)}
      disabled={disabled || mutation.isPending || resolve(action.disabled, localContext)}
      onClick={onClickHandler}
      className={cn(label && "justify-between")}
      {...props}
    >
      {resolve(action.icon, localContext)}
      {label}
    </Button>
  )
}
