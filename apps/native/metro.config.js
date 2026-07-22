const path = require("node:path")
const { withStorybook } = require("@storybook/react-native/withStorybook")
const { getDefaultConfig } = require("expo/metro-config")
const { withUniwindConfig } = require("uniwind/metro")

const projectRoot = __dirname
const config = getDefaultConfig(projectRoot)

module.exports = withStorybook(
  withUniwindConfig(config, {
    cssEntryFile: "./src/global.css",
    dtsFile: "./uniwind-types.d.ts",
  })
)
