import { useState } from "react"
import { cn } from "@/lib/utils"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Field, FieldDescription, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { InputOTP, InputOTPGroup, InputOTPSlot } from "@/components/ui/input-otp"
import { useAuth } from "@/lib/auth"
import { useAppForm } from "@/lib/form"

type Step = "email" | "code"

type LoginFormValues = {
  email: string
  code: string
}

export function LoginForm({ className, ...props }: React.ComponentProps<"div">) {
  const { requestEmailOtp, verifyEmailOtp, loginWithGoogle } = useAuth()
  const [step, setStep] = useState<Step>("email")
  const [challengeId, setChallengeId] = useState<string | null>(null)
  const [devCode, setDevCode] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const form = useAppForm({
    defaultValues: {
      email: "",
      code: "",
    } satisfies LoginFormValues,
    onSubmit: async ({ value }) => {
      setError(null)

      if (step === "email") {
        const nextEmail = value.email.trim()
        if (!nextEmail) {
          setError("Email required")
          return
        }

        try {
          const challenge = await requestEmailOtp(nextEmail)
          setChallengeId(challenge.challengeId)
          setDevCode(challenge.devCode ?? null)
          if (challenge.devCode) {
            form.setFieldValue("code", challenge.devCode)
          }
          setStep("code")
        } catch (err) {
          setError(err instanceof Error ? err.message : "Could not send code")
        }
        return
      }

      if (!challengeId) return
      const code = value.code.trim()
      if (code.length < 6) {
        setError("Enter the 6-digit code")
        return
      }

      try {
        await verifyEmailOtp({
          email: value.email.trim(),
          challengeId,
          code,
        })
      } catch (err) {
        setError(err instanceof Error ? err.message : "Invalid code")
      }
    },
  })

  return (
    <div className={cn("flex flex-col gap-6", className)} {...props}>
      <Card>
        <CardHeader>
          <CardTitle>Login to your account</CardTitle>
          <form.Subscribe selector={(state) => state.values.email}>
            {(email) => (
              <CardDescription>
                {step === "email"
                  ? "We will email you a one-time code."
                  : `Enter the code sent to ${email}`}
              </CardDescription>
            )}
          </form.Subscribe>
        </CardHeader>
        <CardContent>
          <form
            onSubmit={(e) => {
              e.preventDefault()
              e.stopPropagation()
              void form.handleSubmit()
            }}
          >
            <FieldGroup>
              {step === "email" ? (
                <form.Field
                  name="email"
                  validators={{
                    onSubmit: ({ value }) =>
                      !value.trim() ? "Email required" : undefined,
                  }}
                >
                  {(field) => (
                    <Field>
                      <FieldLabel htmlFor={field.name}>Email</FieldLabel>
                      <Input
                        id={field.name}
                        name={field.name}
                        type="email"
                        placeholder="m@example.com"
                        required
                        autoComplete="email"
                        value={field.state.value}
                        onBlur={field.handleBlur}
                        onChange={(e) => field.handleChange(e.target.value)}
                      />
                      {field.state.meta.errors.length > 0 ? (
                        <p className="text-xs text-destructive" role="alert">
                          {field.state.meta.errors.join(", ")}
                        </p>
                      ) : null}
                    </Field>
                  )}
                </form.Field>
              ) : (
                <form.Field
                  name="code"
                  validators={{
                    onSubmit: ({ value }) =>
                      value.trim().length < 6 ? "Enter the 6-digit code" : undefined,
                  }}
                >
                  {(field) => (
                    <Field>
                      <FieldLabel htmlFor={field.name}>Verification code</FieldLabel>
                      <form.Subscribe selector={(state) => state.isSubmitting}>
                        {(isSubmitting) => (
                          <InputOTP
                            id={field.name}
                            maxLength={6}
                            value={field.state.value}
                            onChange={(value) => field.handleChange(value)}
                            disabled={isSubmitting}
                          >
                            <InputOTPGroup>
                              <InputOTPSlot index={0} />
                              <InputOTPSlot index={1} />
                              <InputOTPSlot index={2} />
                              <InputOTPSlot index={3} />
                              <InputOTPSlot index={4} />
                              <InputOTPSlot index={5} />
                            </InputOTPGroup>
                          </InputOTP>
                        )}
                      </form.Subscribe>
                      {devCode ? (
                        <FieldDescription>Dev code: {devCode}</FieldDescription>
                      ) : null}
                      {field.state.meta.errors.length > 0 ? (
                        <p className="text-xs text-destructive" role="alert">
                          {field.state.meta.errors.join(", ")}
                        </p>
                      ) : null}
                    </Field>
                  )}
                </form.Field>
              )}

              {error ? (
                <p className="text-xs text-destructive" role="alert">
                  {error}
                </p>
              ) : null}

              <Field>
                <form.Subscribe selector={(state) => [state.isSubmitting, state.values.code] as const}>
                  {([isSubmitting, code]) => (
                    <>
                      {step === "email" ? (
                        <>
                          <Button type="submit" disabled={isSubmitting}>
                            {isSubmitting ? "Sending code…" : "Continue with email"}
                          </Button>
                          <Button
                            variant="outline"
                            type="button"
                            disabled={isSubmitting}
                            onClick={() => loginWithGoogle()}
                          >
                            Continue with Google
                          </Button>
                        </>
                      ) : (
                        <>
                          <Button type="submit" disabled={isSubmitting || code.length < 6}>
                            {isSubmitting ? "Verifying…" : "Verify and sign in"}
                          </Button>
                          <Button
                            variant="ghost"
                            type="button"
                            disabled={isSubmitting}
                            onClick={() => {
                              setStep("email")
                              setChallengeId(null)
                              setDevCode(null)
                              setError(null)
                              form.setFieldValue("code", "")
                            }}
                          >
                            Use a different email
                          </Button>
                        </>
                      )}
                    </>
                  )}
                </form.Subscribe>
              </Field>
            </FieldGroup>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
