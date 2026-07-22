import * as ExpoCrypto from "expo-crypto"

type CryptoLike = {
  getRandomValues: <T extends ArrayBufferView>(array: T) => T
  randomUUID: () => string
}

function getRandomValues<T extends ArrayBufferView>(array: T): T {
  return ExpoCrypto.getRandomValues(array as never) as T
}

function randomUUID(): string {
  return ExpoCrypto.randomUUID()
}

const cryptoPolyfill: CryptoLike = {
  getRandomValues,
  randomUUID,
}

function installCrypto(target: object) {
  const current = (target as { crypto?: CryptoLike }).crypto
  const needsInstall =
    !current ||
    typeof current.getRandomValues !== "function" ||
    typeof current.randomUUID !== "function"

  if (!needsInstall) return

  try {
    Object.defineProperty(target, "crypto", {
      value: cryptoPolyfill,
      writable: true,
      configurable: true,
      enumerable: true,
    })
  } catch {
    try {
      ;(target as { crypto: CryptoLike }).crypto = cryptoPolyfill
    } catch {
      // ignore — caller will still fail loudly if crypto is unusable
    }
  }
}

installCrypto(globalThis)

const maybeGlobal = (globalThis as { global?: object }).global
if (maybeGlobal && maybeGlobal !== globalThis) {
  installCrypto(maybeGlobal)
}
