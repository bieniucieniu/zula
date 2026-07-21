import { useId, useRef } from "react"
import { cn, resolve } from "@/lib/utils"
import { DropdownMenuItem } from "../../ui/dropdown-menu"
import { useMutationAction } from "../hooks"
import type { ActionContext, FilePushAction } from "../types"

type FileArgs = { file: File }

export function FilePushActionButton<C, T = void>({
  action,
  disabled,
  context,
  className,
  onSelect,
  ...props
}: {
  action: FilePushAction<C, T, FileArgs>
  context: ActionContext & C
} & Omit<React.ComponentProps<typeof DropdownMenuItem>, "children">) {
  const inputRef = useRef<HTMLInputElement>(null)
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
    <DropdownMenuItem
      title={resolve(action.tooltip, localContext)}
      disabled={disabled}
      className={cn({
        "pointer-events-none opacity-50": disabled,
      })}
      onSelect={(e) => {
        e.preventDefault()
        onSelect?.(e)
        inputRef.current?.click()
      }}
      {...props}
    >
      {resolve(action.icon, localContext)}
      {label}
      <input
        ref={inputRef}
        type="file"
        id={id}
        disabled={disabled}
        className="sr-only"
        accept={action.accept}
        style={{ display: "none" }}
        onChange={onClickHandler}
      />
    </DropdownMenuItem>
  )
}
