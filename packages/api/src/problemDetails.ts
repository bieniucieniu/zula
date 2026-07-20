/**
 * Stable machine codes for Problem Details field errors (forms).
 * Wire values are snake_case; localize on the client, never send copy from the API.
 */
export enum ProblemErrorCode {
  Required = "required",
  TooShort = "too_short",
  TooLong = "too_long",
  Invalid = "invalid",
  Format = "format",
  Mismatch = "mismatch",
  Taken = "taken",
  NotFound = "not_found",
  Expired = "expired",
  Forbidden = "forbidden",
  Unauthorized = "unauthorized",
  Conflict = "conflict",
}

/**
 * RFC 9457 Problem Details extension: field → error-code lists for forms.
 * Keys are field paths; values are [ProblemErrorCode] (not localized copy).
 */
export type ProblemDetailsErrors = { [key: string]: ProblemErrorCode[] } | null

export type ProblemDetails = {
  type?: string
  title: string
  status: number
  detail?: string | null
  instance?: string | null
  errors?: ProblemDetailsErrors
}
