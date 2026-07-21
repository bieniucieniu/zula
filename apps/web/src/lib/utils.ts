import { type ClassValue, clsx } from "clsx"
import { twMerge } from "tailwind-merge"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export function resolve<T, Args extends any[]>(fn: ((...args: Args) => T) | T, ...args: Args): T {
  return typeof fn === "function" ? (fn as (...args: Args) => T)(...args) : fn
}
