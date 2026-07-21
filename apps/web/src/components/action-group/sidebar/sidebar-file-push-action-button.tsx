import { useCallback, useId, useRef } from "react"
import { cn, resolve } from "@/lib/utils"
import { SidebarMenuSubButton } from "../../ui/sidebar"
import { useMutationAction } from "../hooks"
import type { ActionContext, FilePushAction } from "../types"

type FileArgs = { file: File }

export function SidebarFilePushActionButton<C, T = void>({
  action,
  onClick,
  disabled,
  context,
  className,
  ...props
}: {
  action: FilePushAction<C, T, FileArgs>
  context: ActionContext & C
  className?: string
  disabled?: boolean
} & Omit<React.ComponentProps<typeof SidebarMenuSubButton>, "children">) {
  const inputRef = useRef<HTMLInputElement>(null)
  const {
    localContext,
    mutation: mut,
    throttle,
  } = useMutationAction({
    action,
    context,
  })
  const onChangeHandler = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0]
      if (file) throttle.maybeExecute({ file })
    },
    [throttle]
  )
  const id = `file-input-${useId()}`
  const label = resolve(action.label, localContext)
  const isDisabled = disabled || mut.isPending || resolve(action.disabled, localContext)
  return (
    <SidebarMenuSubButton
      {...props}
      title={resolve(action.tooltip, localContext)}
      className={cn(className, {
        "pointer-events-none opacity-50": isDisabled,
      })}
      onClick={(e) => {
        e.preventDefault()
        onClick?.(e)
        inputRef.current?.click()
      }}
    >
      {resolve(action.icon, localContext)}
      {label}
      <input
        ref={inputRef}
        type="file"
        id={id}
        disabled={isDisabled}
        className="sr-only"
        accept={action.accept}
        style={{ display: "none" }}
        onChange={onChangeHandler}
      />
    </SidebarMenuSubButton>
  )
}
