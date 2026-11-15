/** Decorative radar for the command-center menu. Pure CSS motion. */
export function RadarSweep() {
  return (
    <div className="spy-radar" aria-hidden="true">
      <div className="spy-radar__ring spy-radar__ring--outer" />
      <div className="spy-radar__ring spy-radar__ring--mid" />
      <div className="spy-radar__ring spy-radar__ring--inner" />
      <div className="spy-radar__cross" />
      <div className="spy-radar__beam" />
      <span className="spy-radar__blip spy-radar__blip--1" />
      <span className="spy-radar__blip spy-radar__blip--2" />
      <span className="spy-radar__blip spy-radar__blip--3" />
    </div>
  )
}
