import type { ReactThrottlerOptions } from "@tanstack/react-pacer"
import type {
  MutationFunctionContext,
  QueryClient,
  UseMutateAsyncFunction,
  UseMutationResult,
} from "@tanstack/react-query"
import type { LinkOptions } from "@tanstack/react-router"
import type { ButtonVariants } from "../ui/button"

export type ActionContext = {
  queryClient: QueryClient
}
export type ActionLocalContext<T = void, A = void> = {
  queryClient: QueryClient
  mutation: UseMutationResult<T, Error, A>
}

export type WithActionContext<T, C> = T | ((ctx: ActionContext & C) => T)
export type ToWithActionContext<O, C> = {
  [K in keyof O]: WithActionContext<O[K], C>
}

export type WithActionLocalContext<D, C, T = void, A = void> =
  | D
  | ((ctx: ActionLocalContext<T, A> & C) => D)

export type ToWithActionLocalContext<O, C, T = void, A = void> = {
  [K in keyof O]: WithActionLocalContext<O[K], C, T, A>
}

export interface SimpleActionCommon<C>
  extends ToWithActionContext<
    {
      icon: React.ReactNode
      label?: React.ReactNode
      tooltip?: string
      disabled?: boolean
    },
    C
  > {
  /**
   * @default true
   */
  // permission?: CanProps['name']
}

export interface SimpleActionMutationCommon<C, T = void, A = void>
  extends ToWithActionLocalContext<
    {
      icon: React.ReactNode
      label?: React.ReactNode
      tooltip?: string
      disabled?: boolean
      throttle?: ReactThrottlerOptions<UseMutateAsyncFunction<T, Error, void, unknown>, {}>
    },
    C,
    T,
    A
  > {
  /**
   * @default true
   */
  // permission?: CanProps["name"]
  onSuccess?: (
    ctx: ActionContext & C,
    data: T,
    variables: A,
    onMutateResult: unknown,
    context: MutationFunctionContext
  ) => void | Promise<void>
  onError?: (
    ctx: ActionContext & C,
    error: Error,
    variables: A,
    onMutateResult: unknown,
    context: MutationFunctionContext
  ) => void | Promise<void>
  action: (ctx: ActionContext & C, ...args: Array<any>) => T | Promise<T>
}

export interface BasicAction<C = void, T = void> extends SimpleActionMutationCommon<C, T> {
  type?: "basic" | "file-pull"
  action: (ctx: ActionContext & C) => T | Promise<T>
}

export interface SubActions<C = void> extends SimpleActionCommon<C> {
  type: "sub"
  actions: SimpleAction<C>[]
}

export interface LinkAction<C = void> extends SimpleActionCommon<C> {
  type: "link"
  link: WithActionContext<LinkOptions, C>
}

export interface ExternalLinkAction<C = void>
  extends SimpleActionCommon<C>,
    ToWithActionContext<{ target?: string; rel?: string; href: string }, C> {
  type: "external-link"
}

export interface FilePullAction<C = void, T = void> extends SimpleActionMutationCommon<C, T> {
  type: "file-pull"
  action: (ctx: ActionContext & C) => T | Promise<T>
}
export interface FilePullActionLink<C = void> extends SimpleActionCommon<C> {
  type: "file-link"
  href: string
}
export interface FilePushAction<C = void, T = void, A = { file: File }>
  extends SimpleActionMutationCommon<C, T, A> {
  type: "file-push"
  accept: string
  action: (ctx: ActionContext & C, args: A) => T | Promise<T>
}
export interface ToggleAction<C = void, T = void> extends SimpleActionMutationCommon<C, T> {
  type: "toggle"
  indicator?: WithActionContext<React.ReactNode, C>
  action: (ctx: ActionContext & C) => T | Promise<T>
}
export interface ToggleWithDialogAction<C = void, T = void>
  extends SimpleActionMutationCommon<C, T> {
  type: "toggle-with-dialog"
  indicator?: WithActionContext<React.ReactNode, C>
  dialog: ToWithActionLocalContext<
    {
      title: React.ReactNode
      description?: React.ReactNode
      confirm?: React.ReactNode
      confirmVariant?: ButtonVariants
      cancel?: React.ReactNode
      cancelVariant?: ButtonVariants
    },
    C,
    T
  >
  action: (ctx: ActionContext & C) => T | Promise<T>
}

export type SimpleActionItem<C = {}, T = unknown> =
  | BasicAction<C, T>
  | ToggleAction<C, T>
  | ToggleWithDialogAction<C, T>
  | FilePullAction<C, T>
  | FilePushAction<C, T>
  | FilePullActionLink<C>
  | LinkAction<C>
  | ExternalLinkAction<C>

export type SimpleAction<C = {}, T = unknown> = SimpleActionItem<C, T> | SubActions<C>

export type SimpleActionsProps<C extends {}> = {
  actions: SimpleAction<C>[]
} & ({} extends C
  ? { context?: {} }
  : {
      context: C
    })
