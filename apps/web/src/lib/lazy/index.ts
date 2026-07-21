interface Lazy<T> {
  (): T
  pick(): T | undefined
  reset(): void
}
const $LAZY = Symbol("lazy")
type $LAZY = typeof $LAZY
export function lazyValue<T>(factor: () => T): Lazy<T> {
  let value: T | $LAZY = $LAZY

  const fn = () => (value === $LAZY ? (value = factor()) : value)
  fn.pick = () => (value === $LAZY ? undefined : value)
  fn.reset = () => void (value = $LAZY)

  return fn
}
