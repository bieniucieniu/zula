import type { AnyFunction, ReactThrottlerOptions } from "@tanstack/react-pacer"

export const defaultThrottleOptions: ReactThrottlerOptions<AnyFunction> = {
  wait: 100,
}
