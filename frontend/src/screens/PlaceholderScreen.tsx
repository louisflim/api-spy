interface PlaceholderScreenProps {
  title: string
  blurb: string
}

/** Placeholder body for Profile / Loadout / Settings under AuthenticatedLayout. */
export function PlaceholderScreen({ title, blurb }: PlaceholderScreenProps) {
  return (
    <div className="auth-placeholder" role="main">
      <p className="dash-kicker">PLACEHOLDER // NOT IMPLEMENTED</p>
      <h1 className="dash-heading">{title}</h1>
      <p className="dash-note">{blurb}</p>
      <p className="dash-note">
        This screen is intentionally incomplete. No fake progress is stored. Use the top navigation to
        return to Dashboard or Operations Board.
      </p>
    </div>
  )
}
