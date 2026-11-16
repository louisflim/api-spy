import { useEffect, useState } from 'react'
import { fetchCatalog, fetchModule0Progress } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import type { RankKey } from '../content/moduleMeta'
import type { CatalogResponse, MissionSummary } from '../types/api'
import { MissionHub } from '../ui/MissionHub'

interface OperationsBoardScreenProps {
  onPlay: (mission: MissionSummary) => void
  onPractice: (mission: MissionSummary) => void
  /** Prefer Module 0 in the board dropdown (new agents / dashboard CTA). */
  initialModule?: number
  initialRank?: RankKey
  /** After Module 0 finishes, resume a pending mission briefing if App still holds one. */
  onOrientationCompleted?: () => void
  /** When opened from a mission briefing, return without mutating mission progress. */
  onReturnToBriefing?: () => void
}

/** Loads catalog and renders the existing Operations Board under the shared navbar. */
export function OperationsBoardScreen({
  onPlay,
  onPractice,
  initialModule,
  initialRank,
  onOrientationCompleted,
  onReturnToBriefing,
}: OperationsBoardScreenProps) {
  const { profile } = useAuth()
  const [catalog, setCatalog] = useState<CatalogResponse | null>(null)
  const [orientationCompleted, setOrientationCompleted] = useState<boolean | null>(null)
  const [preferredModule, setPreferredModule] = useState<number | undefined>(initialModule)

  useEffect(() => {
    setPreferredModule(initialModule)
  }, [initialModule])

  useEffect(() => {
    let cancelled = false
    async function load() {
      try {
        const [data, progress] = await Promise.all([fetchCatalog(), fetchModule0Progress()])
        if (!cancelled) {
          setCatalog(data)
          setOrientationCompleted(progress.orientationCompleted)
          if (initialModule === undefined && !progress.orientationCompleted) {
            setPreferredModule(0)
          }
        }
      } catch {
        if (!cancelled) {
          setCatalog(null)
          setOrientationCompleted(null)
        }
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [initialModule])

  return (
    <MissionHub
      catalog={catalog}
      agentName={profile?.username}
      playerId={profile?.userId}
      onPlay={onPlay}
      onPractice={onPractice}
      orientationCompleted={orientationCompleted}
      initialModule={preferredModule}
      initialRank={initialRank}
      onOrientationCompleted={() => {
        setOrientationCompleted(true)
        onOrientationCompleted?.()
      }}
      onReturnToBriefing={onReturnToBriefing}
    />
  )
}
