import { useEffect, useState } from 'react'
import { Navigate, Outlet, useOutletContext, useParams } from 'react-router-dom'
import { fetchCatalog } from '../api/client'
import type { MissionSummary } from '../types/api'
import { paths } from './paths'

export type MissionOutletContext = {
  mission: MissionSummary
}

export function useMissionOutlet(): MissionOutletContext {
  return useOutletContext<MissionOutletContext>()
}

function MissionLoading() {
  return (
    <div className="auth-panel" style={{ margin: '2rem auto', maxWidth: 420 }}>
      <p className="auth-eyebrow">MISSION LOOKUP</p>
      <h1>Loading dossier…</h1>
      <p className="auth-sub">Resolving mission from the operations catalog.</p>
    </div>
  )
}

/** Loads `:missionId` from the catalog and provides it to nested mission pages. */
export function MissionRoute() {
  const { missionId: rawId } = useParams<{ missionId: string }>()
  const missionId = rawId ? decodeURIComponent(rawId) : ''
  const [mission, setMission] = useState<MissionSummary | null | undefined>(undefined)

  useEffect(() => {
    let cancelled = false
    if (!missionId) {
      setMission(null)
      return
    }
    setMission(undefined)
    void fetchCatalog()
      .then((catalog) => {
        if (cancelled) return
        const found = catalog.missions.find((row) => row.missionId === missionId) ?? null
        setMission(found)
      })
      .catch(() => {
        if (!cancelled) setMission(null)
      })
    return () => {
      cancelled = true
    }
  }, [missionId])

  if (mission === undefined) return <MissionLoading />
  if (!mission) return <Navigate to={paths.operations} replace />

  return <Outlet context={{ mission } satisfies MissionOutletContext} />
}
