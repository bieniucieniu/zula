import { useState } from "react"
import { cn, resolve } from "@/lib/utils"
import { Button } from "../../ui/button"
import { Popover, PopoverContent, PopoverTrigger } from "../../ui/popover"
import { SidebarMenuSubButton } from "../../ui/sidebar"
import { useMutationAction } from "../hooks"
import type { ActionContext, ToggleWithDialogAction } from "../types"

export function SidebarToggleWithDialogActionButton<C, T = void>({
  action,
  onClick,
  disabled,
  context,
  className,
  ...props
}: {
  action: ToggleWithDialogAction<C, T>
  context: ActionContext & C
} & Omit<React.ComponentProps<"button">, "children">) {
  const [open, setOpen] = useState(false)
  const {
    localContext,
    mutation: mut,
    throttle,
  } = useMutationAction({
    action,
    context,
    mutation: {
      onSuccess: () => setOpen(false),
    },
  })
  const label = resolve(action.label, localContext)
  const onClickHandler: typeof onClick = (e) => {
    e.preventDefault()
    onClick?.(e)
    throttle.maybeExecute()
  }
  return (
    <Popover open={open} onOpenChange={setOpen}>
      <SidebarMenuSubButton
        className={cn("justify-normal", className)}
        render={
          <PopoverTrigger
            render={
              <button
                title={resolve(action.tooltip, localContext)}
                disabled={disabled || mut.isPending || resolve(action.disabled, localContext)}
                {...props}
              >
                {resolve(action.icon, localContext)}
                {label}
              </button>
            }
          />
        }
      />

      <PopoverContent className="p-2">
        <div className="flex flex-col gap-2 p-2">
          <h3 className="text-lg leading-none font-semibold">
            {resolve(action.dialog.title, localContext)}
          </h3>
          <p className="text-muted-foreground text-sm [&>a:hover]:text-primary [&>a]:underline [&>a]:underline-offset-4">
            {resolve(action.dialog.description, localContext)}
          </p>
        </div>
        <div className="grid grid-cols-2 gap-2 p-2">
          <Button
            onClick={() => setOpen(false)}
            variant={resolve(action.dialog.cancelVariant, localContext) || "ghost"}
            disabled={mut.isPending}
          >
            {resolve(action.dialog.cancel, localContext)}
          </Button>
          <Button
            variant={resolve(action.dialog.confirmVariant, localContext) || "destructive"}
            onClick={onClickHandler}
            disabled={mut.isPending}
          >
            {resolve(action.dialog.confirm, localContext)}
          </Button>
        </div>
      </PopoverContent>
    </Popover>
  )
}
