import { cn } from "@/lib/utils"

export default function AppListLayout({ className, ...props }: React.HTMLProps<HTMLDivElement>) {
  return (
    <div
      data-slot="app-list-layout"
      className={cn("relative flex flex-col justify-stretch gap-1", className)}
      {...props}
    />
  )
}

export function AppListHeader({ className, ...props }: React.HTMLProps<HTMLElement>) {
  return (
    <header
      data-slot="app-list-layout-header"
      className={cn("pl-1 items-center flex justify-between w-full", className)}
      {...props}
    />
  )
}

export function AppListTitle({ className, ...props }: React.HTMLProps<HTMLHeadingElement>) {
  return (
    <h2
      data-slot="app-list-layout-title"
      className={cn("text-lg font-semibold text-primary/80 text-nowrap", className)}
      {...props}
    />
  )
}

export function AppListActions({ className, ...props }: React.HTMLProps<HTMLDivElement>) {
  return (
    <div
      data-slot="app-list-layout-actions"
      className={cn("flex gap-2 text-nowrap items-center", className)}
      {...props}
    />
  )
}

export function AppListContent({ className, ...props }: React.HTMLProps<HTMLDivElement>) {
  return (
    <div
      data-slot="app-list-layout-content"
      className={cn("overflow-auto h-full rounded-md", className)}
      {...props}
    />
  )
}

export function AppListFooter({ className, ...props }: React.HTMLProps<HTMLElement>) {
  return (
    <footer
      data-slot="app-list-layout-footer"
      className={cn("flex pt-2 gap-2", className)}
      {...props}
    />
  )
}
