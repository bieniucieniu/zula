import { useThrottler } from "@tanstack/react-pacer"
import {
  type UseMutationOptions,
  type UseMutationResult,
  useMutation,
  useQueryClient,
} from "@tanstack/react-query"
import { resolve } from "@/lib/utils"
import { defaultThrottleOptions } from "./consts"
import type { ActionContext, ActionLocalContext, SimpleActionMutationCommon } from "./types"

export function useCreateActionContext(): ActionContext
export function useCreateActionContext<C extends {}>(extra: C): ActionContext & C
export function useCreateActionContext(extra?: {}): ActionContext & {
  [K in PropertyKey]: any
} {
  const queryClient = useQueryClient()
  return { queryClient, ...extra }
}

export function useMutationAction<C, T = void, A = void>({
  action,
  context,
  mutation: mutOpt = {},
  run = (...args) => action.action(context, ...args),
}: {
  action: SimpleActionMutationCommon<C, T, A>
  context: ActionContext & C
  run?: (args: A) => T | Promise<T>
  mutation?: Omit<UseMutationOptions<T, Error, A>, "mutationFn">
}) {
  const { onSuccess, onError } = mutOpt
  const mutation: UseMutationResult<T, Error, A> = useMutation<T, Error, A>({
    ...mutOpt,
    mutationFn: async (args: A) => {
      return (await run(args)) as T
    },
    onSuccess: action.onSuccess
      ? async (...args) => {
          await action.onSuccess?.(context, ...args)
          await onSuccess?.(...args)
        }
      : onSuccess,
    onError: action.onError
      ? async (...args) => {
          await action.onError?.(context, ...args)
          await onError?.(...args)
        }
      : onError,
  })
  const localContext: ActionLocalContext<T, A> & C = {
    ...context,
    mutation: mutation,
  }
  const throttlerOptions = resolve(action.throttle, localContext)
  const throttle = useThrottler(
    mutation.mutateAsync,
    throttlerOptions || defaultThrottleOptions,
    (s) => ({ isPending: s.isPending })
  )
  throttle.state
  return {
    mutation,
    throttle,
    isPending: mutation.isPending,
    isLocked: throttle.state.isPending,
    localContext,
  }
}
