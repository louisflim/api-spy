import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type CSSProperties,
  type PointerEvent as ReactPointerEvent,
  type ReactNode,
} from 'react'

export type SpyStageVariant = 'hq' | 'dossier' | 'terminal' | 'command' | 'ops'
export type SpyScene = 'auth' | 'ops' | 'command'

const SCENE_SRC: Record<SpyScene, string> = {
  // Auth v=3 (cyan-white sign). Ops/command v=5 matches same cool cyan-white lettering.
  auth: '/assets/scenes/hq-auth-night.jpg?v=3',
  ops: '/assets/scenes/hq-ops-night.jpg?v=5',
  command: '/assets/scenes/hq-ops-night.jpg?v=5',
}

interface SpyStageProps {
  variant: SpyStageVariant
  /** Illustrated scenery plate (defaults from variant). */
  scene?: SpyScene
  children: ReactNode
  className?: string
  exiting?: boolean
  /** Wider content shell for operations board. */
  wide?: boolean
}

function sceneForVariant(variant: SpyStageVariant): SpyScene {
  if (variant === 'ops' || variant === 'command') return variant === 'command' ? 'command' : 'ops'
  return 'auth'
}

/**
 * Illustrated HQ stage: scenery plate + atmosphere overlays + HUD children.
 * Parallax applies only to background layers so form typing does not remount HUD.
 */
export function SpyStage({
  variant,
  scene,
  children,
  className = '',
  exiting = false,
  wide = false,
}: SpyStageProps) {
  const rootRef = useRef<HTMLDivElement | null>(null)
  const [offset, setOffset] = useState({ x: 0, y: 0 })
  const [reduceMotion, setReduceMotion] = useState(false)
  const [plateFailed, setPlateFailed] = useState(false)

  const resolvedScene = scene ?? sceneForVariant(variant)
  const plateSrc = SCENE_SRC[resolvedScene]

  useEffect(() => {
    const media = window.matchMedia('(prefers-reduced-motion: reduce)')
    const sync = () => setReduceMotion(media.matches)
    sync()
    media.addEventListener('change', sync)
    return () => media.removeEventListener('change', sync)
  }, [])

  useEffect(() => {
    setPlateFailed(false)
  }, [plateSrc])

  const onPointerMove = useCallback(
    (event: ReactPointerEvent<HTMLDivElement>) => {
      if (reduceMotion) return
      const rect = rootRef.current?.getBoundingClientRect()
      if (!rect) return
      const nx = (event.clientX - rect.left) / rect.width - 0.5
      const ny = (event.clientY - rect.top) / rect.height - 0.5
      setOffset({ x: nx, y: ny })
    },
    [reduceMotion],
  )

  const onPointerLeave = useCallback(() => {
    setOffset({ x: 0, y: 0 })
  }, [])

  const farStyle: CSSProperties = reduceMotion
    ? {}
    : { transform: `translate3d(${offset.x * -8}px, ${offset.y * -5}px, 0) scale(1.06)` }
  const midStyle: CSSProperties = reduceMotion
    ? {}
    : { transform: `translate3d(${offset.x * -16}px, ${offset.y * -10}px, 0)` }
  const nearStyle: CSSProperties = reduceMotion
    ? {}
    : { transform: `translate3d(${offset.x * -26}px, ${offset.y * -14}px, 0)` }

  return (
    <div
      ref={rootRef}
      className={`spy-stage spy-stage--${variant} spy-stage--scene-${resolvedScene} ${wide ? 'spy-stage--wide' : ''} ${exiting ? 'spy-stage--exit' : ''} ${className}`.trim()}
      onPointerMove={onPointerMove}
      onPointerLeave={onPointerLeave}
    >
      <div className="spy-stage__scenery" aria-hidden="true">
        {!plateFailed ? (
          <img
            className="spy-stage__plate"
            src={plateSrc}
            alt=""
            draggable={false}
            style={farStyle}
            onError={() => setPlateFailed(true)}
          />
        ) : (
          <div className="spy-stage__sky spy-stage__sky--fallback" style={farStyle} />
        )}
        <div className="spy-stage__radar-glow" style={midStyle} />
        <div className="spy-stage__fog spy-stage__fog--back" style={midStyle} />
        <div className="spy-stage__fog spy-stage__fog--front" style={nearStyle} />
        <div className="spy-stage__vignette" />
        <div className="spy-stage__scan" />
      </div>
      <div className={`spy-stage__content ${wide ? 'spy-stage__content--wide' : ''}`.trim()}>
        {children}
      </div>
    </div>
  )
}
