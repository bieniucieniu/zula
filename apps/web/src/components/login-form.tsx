import { useState } from "react"
import { cn } from "@/lib/utils"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Field, FieldDescription, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { InputOTP, InputOTPGroup, InputOTPSlot } from "@/components/ui/input-otp"
import { useAuth } from "@/lib/auth"

type Step = "email" | "code"

export function LoginForm({ className, ...props }: React.ComponentProps<"div">) {
  const { requestEmailOtp, verifyEmailOtp, loginWithGoogle } = useAuth()
  const [step, setStep] = useState<Step>("email")
  const [email, setEmail] = useState("")
  const [challengeId, setChallengeId] = useState<string | null>(null)
  const [code, setCode] = useState("")
  const [devCode, setDevCode] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  async function onRequestCode(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setError(null)
    setPending(true)
    const form = new FormData(e.currentTarget)
    const nextEmail = String(form.get("email") ?? "").trim()
    try {
      const challenge = await requestEmailOtp(nextEmail)
      setEmail(nextEmail)
      setChallengeId(challenge.challengeId)
      setDevCode(challenge.devCode ?? null)
      if (challenge.devCode) setCode(challenge.devCode)
      setStep("code")
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not send code")
    } finally {
      setPending(false)
    }
  }

  async function onVerifyCode(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault()
    if (!challengeId) return
    setError(null)
    setPending(true)
    try {
      await verifyEmailOtp({ email, challengeId, code })
    } catch (err) {
      setError(err instanceof Error ? err.message : "Invalid code")
    } finally {
      setPending(false)
    }
  }

  return (
    <div className={cn("flex flex-col gap-6", className)} {...props}>
      <Card>
        <CardHeader>
          <CardTitle>Login to your account</CardTitle>
          <CardDescription>
            {step === "email"
              ? "We will email you a one-time code."
              : `Enter the code sent to ${email}`}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {step === "email" ? (
            <form onSubmit={onRequestCode}>
              <FieldGroup>
                <Field>
                  <FieldLabel htmlFor="email">Email</FieldLabel>
                  <Input
                    id="email"
                    name="email"
                    type="email"
                    placeholder="m@example.com"
                    required
                    autoComplete="email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                  />
                </Field>
                {error ? (
                  <p className="text-xs text-destructive" role="alert">
                    {error}
                  </p>
                ) : null}
                <Field>
                  <Button type="submit" disabled={pending}>
                    {pending ? "Sending code…" : "Continue with email"}
                  </Button>
                  <Button
                    variant="outline"
                    type="button"
                    disabled={pending}
                    onClick={() => loginWithGoogle()}
                  >
                    Continue with Google
                  </Button>
                </Field>
              </FieldGroup>
            </form>
          ) : (
            <form onSubmit={onVerifyCode}>
              <FieldGroup>
                <Field>
                  <FieldLabel htmlFor="code">Verification code</FieldLabel>
                  <InputOTP
                    id="code"
                    maxLength={6}
                    value={code}
                    onChange={setCode}
                    disabled={pending}
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
                  {devCode ? (
                    <FieldDescription>Dev code: {devCode}</FieldDescription>
                  ) : null}
                </Field>
                {error ? (
                  <p className="text-xs text-destructive" role="alert">
                    {error}
                  </p>
                ) : null}
                <Field>
                  <Button type="submit" disabled={pending || code.length < 6}>
                    {pending ? "Verifying…" : "Verify and sign in"}
                  </Button>
                  <Button
                    variant="ghost"
                    type="button"
                    disabled={pending}
                    onClick={() => {
                      setStep("email")
                      setChallengeId(null)
                      setCode("")
                      setDevCode(null)
                      setError(null)
                    }}
                  >
                    Use a different email
                  </Button>
                </Field>
              </FieldGroup>
            </form>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
