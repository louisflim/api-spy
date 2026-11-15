import { useEffect, useState } from 'react'
import { fetchHealth } from '../api/client'

export function useUplink(): 'checking' | 'up' | 'down' {
  const [status, setStatus] = useState<'checking' | 'up' | 'down'>('checking')

  useEffect(() => {
    let cancelled = false
    async function check() {
      for (let i = 0; i < 8; i += 1) {
        try {
          await fetchHealth()
          if (!cancelled) setStatus('up')
          return
        } catch {
          await new Promise((r) => setTimeout(r, 500))
        }
      }
      if (!cancelled) setStatus('down')
    }
    void check()
    return () => {
      cancelled = true
    }
  }, [])

  return status
}
