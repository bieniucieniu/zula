/**
 * Stable machine codes for Problem Details field errors (forms).
 * Wire values are snake_case; localize on the client, never send copy from the API.
 */

import type { ProblemDetails, ProblemDetailsErrors } from "./generated/model"

export class ProblemDetailsError extends Error {
  readonly type?: string
  readonly title: string
  readonly status: number
  readonly detail?: string | null
  readonly instance?: string | null
  readonly errors?: ProblemDetailsErrors
  constructor(public readonly p: ProblemDetails) {
    super(p?.detail ?? "")
    this.type = p?.type
    this.title = p?.title ?? "Unknown error"
    this.status = p?.status ?? 500
    this.detail = p?.detail
    this.instance = p?.instance
    this.errors = p?.errors
  }
}
