import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import {
  fetchCsrf,
  fetchMe,
  fetchPasswordResetStatus,
  loginAccount,
  logoutAccount,
  registerAccount,
  requestPasswordReset,
  type AuthUser,
} from '../api/client'

export interface AuthUserProfile {
  userId: string
  email: string
  username: string
  /** True for research administrators (server-enforced; this only shows the console link). */
  isAdministrator: boolean
}

interface AuthContextValue {
  loading: boolean
  configured: boolean
  profile: AuthUserProfile | null
  signUp: (input: {
    username: string
    email: string
    password: string
    confirmPassword: string
  }) => Promise<{ needsEmailConfirmation: boolean }>
  signIn: (email: string, password: string) => Promise<void>
  signOut: () => Promise<void>
  requestPasswordReset: (email: string) => Promise<void>
  passwordResetAvailable: boolean
  passwordResetMessage: string
}

const AuthContext = createContext<AuthContextValue | null>(null)

function toProfile(user: AuthUser | null): AuthUserProfile | null {
  if (!user) return null
  return {
    userId: user.userId,
    email: user.email,
    username: user.username,
    isAdministrator: user.role === 'ADMINISTRATOR',
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [loading, setLoading] = useState(true)
  const [profile, setProfile] = useState<AuthUserProfile | null>(null)
  const [passwordResetAvailable, setPasswordResetAvailable] = useState(false)
  const [passwordResetMessage, setPasswordResetMessage] = useState(
    'Password recovery is unavailable until an SMTP provider is configured.',
  )

  useEffect(() => {
    let cancelled = false
    async function bootstrap() {
      try {
        await fetchCsrf()
        const me = await fetchMe()
        if (!cancelled) {
          setProfile(toProfile(me))
        }
      } catch {
        if (!cancelled) {
          setProfile(null)
        }
      }

      try {
        const status = await fetchPasswordResetStatus()
        if (!cancelled) {
          setPasswordResetAvailable(status.available)
          setPasswordResetMessage(status.message)
        }
      } catch {
        // Keep default unavailable message.
      }

      if (!cancelled) {
        setLoading(false)
      }
    }
    void bootstrap()
    return () => {
      cancelled = true
    }
  }, [])

  const signUp = useCallback(
    async (input: {
      username: string
      email: string
      password: string
      confirmPassword: string
    }) => {
      const user = await registerAccount(input)
      setProfile(toProfile(user))
      // Email confirmation is not part of the Spring session flow.
      return { needsEmailConfirmation: false }
    },
    [],
  )

  const signIn = useCallback(async (email: string, password: string) => {
    const user = await loginAccount({ email, password })
    setProfile(toProfile(user))
  }, [])

  const signOut = useCallback(async () => {
    await logoutAccount()
    setProfile(null)
  }, [])

  const requestReset = useCallback(async (email: string) => {
    const result = await requestPasswordReset(email)
    // Surface server message (generic / unavailable) to the screen.
    setPasswordResetMessage(result.message)
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      loading,
      configured: true,
      profile,
      signUp,
      signIn,
      signOut,
      requestPasswordReset: requestReset,
      passwordResetAvailable,
      passwordResetMessage,
    }),
    [
      loading,
      profile,
      signUp,
      signIn,
      signOut,
      requestReset,
      passwordResetAvailable,
      passwordResetMessage,
    ],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error('useAuth must be used within AuthProvider')
  }
  return ctx
}
