/**
 * Rookie Detective down-facing walk/idle cycle from the normalized 4×4 sheet.
 * CSS sprite animation only — no Phaser instance on the menu.
 */
interface RookieIdlePortraitProps {
  size?: 'default' | 'large'
}

export function RookieIdlePortrait({ size = 'default' }: RookieIdlePortraitProps) {
  return (
    <div
      className={`rookie-portrait ${size === 'large' ? 'rookie-portrait--large' : ''}`.trim()}
      aria-hidden="true"
    >
      <div className="rookie-portrait__glow" />
      <div className="rookie-portrait__sprite" />
      <p className="rookie-portrait__caption">Rookie Detective</p>
    </div>
  )
}
