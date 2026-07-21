import { useId } from "react"
import { cn, resolve } from "@/lib/utils"
import { Button } from "../../ui/button"
import { useMutationAction } from "../hooks"
import type { ActionContext, FilePushAction } from "../types"

type FileArgs = { file: File }

export function FilePushActionButton<C, T = void>({
  action,
  onClick,
  disabled,
  context,
  className,
  ...props
}: {
  action: FilePushAction<C, T, FileArgs>
  context: ActionContext & C
} & Omit<React.ComponentProps<typeof Button>, "children">) {
  const {
    localContext,
    mutation: mut,
    throttle,
  } = useMutationAction({
    action,
    context,
  })
  const onClickHandler = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) throttle.maybeExecute({ file })
  }
  const id = `file-input-${useId()}`
  disabled = disabled || mut.isPending || resolve(action.disabled, localContext)
  const label = resolve(action.label, localContext)
  return (
    <Button
      title={resolve(action.tooltip, localContext)}
      size={label ? undefined : "icon"}
      disabled={disabled}
      className={cn(label && "justify-between", {
        "pointer-events-none opacity-50": disabled,
      })}
      {...props}
      render={
        <label htmlFor={id}>
          {resolve(action.icon, localContext)}
          {label}
          <input
            type="file"
            id={id}
            disabled={disabled}
            className="sr-only"
            accept={action.accept}
            style={{ display: "none" }}
            onChange={onClickHandler}
          />
        </label>
      }
    ></Button>
  )
}
