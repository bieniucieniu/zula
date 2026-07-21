import type { BaseUIEvent } from "@base-ui/react"
import { useState } from "react"
import { Button } from "@/components/ui/button"
import { cn, resolve } from "@/lib/utils"
import {
  DropdownMenuSub,
  DropdownMenuSubContent,
  DropdownMenuSubTrigger,
} from "../../ui/dropdown-menu"
import { useMutationAction } from "../hooks"
import type { ActionContext, ToggleWithDialogAction } from "../types"

export function ToggleWithDialogActionButton<C, T = void>({
  action,
  onClick,
  disabled,
  context,
  className,
  ...props
}: {
  action: ToggleWithDialogAction<C, T>
  context: ActionContext & C
  onClick?: (e: BaseUIEvent<React.MouseEvent<HTMLButtonElement>>) => void
} & Omit<React.ComponentProps<typeof DropdownMenuSubTrigger>, "children" | "onClick">) {
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
  const onSelectHandler = (e: BaseUIEvent<React.MouseEvent<HTMLButtonElement>>) => {
    e.preventDefault()
    onClick?.(e)
    throttle.maybeExecute()
  }
  return (
    <DropdownMenuSub open={open} onOpenChange={setOpen}>
      <DropdownMenuSubTrigger
        title={resolve(action.tooltip, localContext)}
        className={cn(
          "[&_svg:not([class*='text-'])]:text-muted-foreground [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4 gap-2",
          className
        )}
        disabled={disabled || mut.isPending || resolve(action.disabled, localContext)}
        {...props}
      >
        {resolve(action.icon, localContext)}
        {label}
      </DropdownMenuSubTrigger>
      <DropdownMenuSubContent>
        <div className="flex flex-col gap-2 p-2">
          <h3 className="text-lg leading-none font-semibold ">
            {resolve(action.dialog.title, localContext)}
          </h3>
          <p className="text-muted-foreground text-sm [&>a:hover]:text-primary [&>a]:underline [&>a]:underline-offset-4 min-w-40 max-w-min">
            {resolve(action.dialog.description, localContext)}
          </p>
        </div>
        <Button
          size="sm"
          variant={resolve(action.dialog.confirmVariant, localContext) || "destructive"}
          className="w-full"
          onClick={onSelectHandler}
          disabled={mut.isPending}
        >
          {resolve(action.dialog.confirm, localContext)}
        </Button>
      </DropdownMenuSubContent>
    </DropdownMenuSub>
  )
}
