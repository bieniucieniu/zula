import {
  createFormHook,
  createFormHookContexts,
  type DeepKeys,
  type DeepValue,
  FieldApi,
  type Updater,
} from "@tanstack/react-form"

const ctx = createFormHookContexts()

export const { useAppForm, withForm, withFieldGroup } = createFormHook({
  fieldComponents: {},
  formComponents: {},
  ...ctx,
})

export const { useFieldContext, useFormContext, formContext, fieldContext } = ctx

export type AnyAppForm<T = any> = ReturnType<
  typeof useAppForm<T, any, any, any, any, any, any, any, any, any, any, any>
>

export type AnyAppFieldApi<T = any> = FieldApi<
  any,
  any,
  T,
  any,
  any,
  any,
  any,
  any,
  any,
  any,
  any,
  any,
  any,
  any,
  any,
  any,
  any,
  any,
  any,
  any,
  any,
  any,
  any
>

export type { DeepKeys, DeepValue, Updater }
