// Must run before expo-router loads app modules that need Web Crypto.
require("./src/polyfills/crypto")
require("expo-router/entry")
