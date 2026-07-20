/**
 * RFC 9457 Problem Details extension: field → error-code lists for forms.
 * Keys are field paths; values are stable machine codes (not localized copy).
 */
export type ProblemDetailsErrors = { [key: string]: string[] } | null

export type ProblemDetails = {
  type?: string
  title: string
  status: number
  detail?: string | null
  instance?: string | null
  errors?: ProblemDetailsErrors
}
