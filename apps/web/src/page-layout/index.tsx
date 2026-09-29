import { useRender } from "@base-ui/react"
import { Link, type LinkOptions, type LinkProps } from "@tanstack/react-router"
import { ChevronRightIcon } from "lucide-react"
import { Fragment, useMemo } from "react"
import {
  Breadcrumb,
  BreadcrumbEllipsis,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import {
  Item,
  ItemActions,
  ItemContent,
  ItemDescription,
  ItemMedia,
  ItemTitle,
} from "@/components/ui/item"
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from "@/components/ui/resizable"
import { cn } from "@/lib/utils"
import { Separator } from "../ui/separator"

export function AppPageLayout({
  className,
  style,
  headerHeight = "3rem",
  cropWidth = "72rem",
  ...props
}: React.HTMLProps<HTMLDivElement> & {
  headerHeight?: React.CSSProperties["height"]
  cropWidth?: React.CSSProperties["width"]
}) {
  return (
    <div
      data-slot="app-page-layout"
      className={cn(
        "relative flex flex-col items-center w-full [view-transition-name:app-page-layout] max-h-full h-full",
        className
      )}
      style={
        {
          "--app-header-height": headerHeight,
          "--app-content-width-croped": cropWidth,
          ...style,
        } as React.CSSProperties
      }
      {...props}
    />
  )
}

export function AppPageHeader({ className, ...props }: React.HTMLProps<HTMLDivElement>) {
  return (
    <div
      data-slot="app-page-header"
      className={cn(
        "flex gap-2 p-1 items-center w-full justify-between border-b sticky top-0 z-10 bg-background/80 not-empty:min-h-(--app-header-height)",
        className
      )}
      {...props}
    />
  )
}

export function AppPageSeparator({
  className,
  orientation = "horizontal",
  ...props
}: React.ComponentProps<typeof Separator>) {
  return (
    <Separator
      data-slot="separator-root"
      data-orientation={orientation}
      className={cn(
        "bg-border rounded-full shrink-0",
        orientation === "vertical"
          ? "h-[95%] w-[1.5px] my-auto mx-2"
          : "h-[1.5px] w-[95%] my-2 mx-auto",
        className
      )}
      {...props}
    />
  )
}

export function AppPageTitle({ className, ...props }: React.ComponentProps<"h2">) {
  return (
    <h2
      data-slot="app-page-title"
      className={cn(
        "inline-flex w-fit items-center gap-2 text-xl leading-snug font-medium",
        className
      )}
      {...props}
    />
  )
}

export function AppPageContent({
  className,
  render,
  suppressCroping,
  ...props
}: useRender.ComponentProps<"div"> & {
  suppressCroping?: boolean
}) {
  return useRender({
    defaultTagName: "div",
    props: {
      "data-slot": "app-page-content",
      className: cn("flex flex-col gap-2 w-full h-full min-h-0", className),
      ...props,
    },
    render,
  })
}
export function AppPageContentForSidebar({
  className,
  suppressCroping,
  render,
  ...props
}: useRender.ComponentProps<"div"> & {
  suppressCroping?: boolean
}) {
  return useRender({
    defaultTagName: "div",
    props: {
      "data-slot": "app-page-content",
      className: cn("flex flex-col gap-2 pt-2 mx-2 pb-8 w-full h-full", className),
      ...props,
    },
    render,
  })
}

export function AppPageResizableGroup({
  orientation = "horizontal",
  ...props
}: React.ComponentProps<typeof ResizablePanelGroup>) {
  return <ResizablePanelGroup data-slot="app-page-resizable" orientation={orientation} {...props} />
}
export function AppPageResizableContent({
  orientation = "horizontal",
  ...props
}: React.ComponentProps<typeof ResizablePanelGroup>) {
  return <ResizablePanelGroup data-slot="app-page-resizable" orientation={orientation} {...props} />
}

export function AppPageResizablePanel({
  className,
  ...props
}: React.ComponentProps<typeof ResizablePanel>) {
  return (
    <ResizablePanel
      className={cn("flex", className)}
      data-slot="app-page-resizable-panel"
      {...props}
    />
  )
}

export function AppPageResizableHandle({
  className,
  ...props
}: React.ComponentProps<typeof ResizableHandle>) {
  return <ResizableHandle data-slot="app-page-resizable-handle" className={className} {...props} />
}

export type AppPageButtonItemProps = Pick<
  React.ComponentProps<typeof Item>,
  "variant" | "size" | "className"
> & {
  title: React.ReactNode
  icon?: React.ReactNode
  media?: React.ReactNode
  description?: React.ReactNode
}
export function AppPageButtonItem({
  title,
  icon,
  media,
  description,
  variant = "outline",
  size = "sm",
  ...props
}: AppPageButtonItemProps) {
  return (
    <Item
      data-slot="app-page-button-item"
      variant={variant}
      size={size}
      {...props}
      render={
        <button type="button">
          {media != null && <ItemMedia>{media}</ItemMedia>}
          <ItemContent>
            <ItemTitle>{title}</ItemTitle>
            {description != null && <ItemDescription>{description}</ItemDescription>}
          </ItemContent>
          <ItemActions>{icon ?? <ChevronRightIcon className="size-4" />}</ItemActions>
        </button>
      }
    />
  )
}

export function AppPageLinkItem({
  to,
  title,
  icon,
  media,
  description,
  variant = "outline",
  size = "sm",
  viewTransition = true,
  ...props
}: LinkProps & AppPageButtonItemProps) {
  return (
    <Item
      data-slot="app-page-link-item"
      variant={variant}
      size={size}
      render={
        <Link {...props} viewTransition={viewTransition} to={to}>
          {media != null && <ItemMedia>{media}</ItemMedia>}
          <ItemContent>
            <ItemTitle>{title}</ItemTitle>
            {description != null && <ItemDescription>{description}</ItemDescription>}
          </ItemContent>
          <ItemActions>{icon ?? <ChevronRightIcon className="size-4" />}</ItemActions>
        </Link>
      }
    />
  )
}

export type BreadcrumbRoute = (LinkOptions & { title: React.ReactNode }) | null | undefined

function renderBreadcrumbs(items: BreadcrumbRoute[]) {
  return items.map((it, i) => {
    if (!it) return null
    return (
      it && (
        <Fragment key={it.to}>
          <BreadcrumbItem>
            <BreadcrumbLink
              render={
                <Link
                  activeOptions={{
                    exact: true,
                    includeSearch: false,
                    ...it.activeOptions,
                  }}
                  viewTransition
                  activeProps={{ className: "font-semibold text-foreground" }}
                  {...it}
                  title={undefined}
                >
                  {it.title}
                </Link>
              }
            ></BreadcrumbLink>
          </BreadcrumbItem>
          {i < items.length - 1 && <BreadcrumbSeparator />}
        </Fragment>
      )
    )
  })
}
export function AppPageActions({ className, children, ...props }: React.HTMLProps<HTMLDivElement>) {
  return (
    <div
      data-slot="app-page-actions"
      className={cn("flex gap-1 flex-1 justify-end empty:hidden", className)}
      {...props}
    >
      {children}
    </div>
  )
}

export function AppPageBreadcrumbs({
  routes,
  className,
  max = 7,
  min = 3,
  ...props
}: Omit<React.HTMLProps<HTMLElement>, "children"> & {
  routes: BreadcrumbRoute[]
  max?: number
  min?: number
}) {
  const content = useMemo(() => {
    const r = routes.filter(Boolean)
    if (!r?.length) return null
    const overflow = r.length > max
    if (!overflow) return renderBreadcrumbs(r)

    const start = r.slice(0, min)
    const end = r.slice(-(max - min))
    const middle = r.slice(min, -(max - min))
    return (
      <>
        {renderBreadcrumbs(start)}
        <BreadcrumbItem>
          <DropdownMenu>
            <DropdownMenuTrigger
              render={
                <Button size="icon-sm" variant="ghost">
                  <BreadcrumbEllipsis />
                  <span className="sr-only">Toggle menu</span>
                </Button>
              }
            />

            <DropdownMenuContent align="start">
              <DropdownMenuGroup>
                {middle.map(
                  (it) =>
                    it && (
                      <DropdownMenuItem
                        key={it.to}
                        render={
                          <Link viewTransition to={it.to}>
                            {it.title}
                          </Link>
                        }
                      />
                    )
                )}
              </DropdownMenuGroup>
            </DropdownMenuContent>
          </DropdownMenu>
        </BreadcrumbItem>
        {renderBreadcrumbs(end)}
      </>
    )
  }, [routes, max, min])
  return (
    <Breadcrumb
      data-slot="app-page-breadcrumbs"
      className={cn("px-2 items-center", className)}
      {...props}
    >
      <BreadcrumbList>{content}</BreadcrumbList>
    </Breadcrumb>
  )
}
