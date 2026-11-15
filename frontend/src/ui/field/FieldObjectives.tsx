interface FieldObjectivesProps {
  terminalLocated: boolean
  unlockedCount: number
  requiredCount: number
  devicesComplete: boolean
  exitReady: boolean
  missionCompleted: boolean
}

export function FieldObjectives({
  terminalLocated,
  unlockedCount,
  requiredCount,
  devicesComplete,
  exitReady,
  missionCompleted,
}: FieldObjectivesProps) {
  const deviceLabel =
    requiredCount <= 1
      ? 'Activate the mandatory API security device'
      : `Activate all mandatory API security devices (${unlockedCount}/${requiredCount})`

  const items = [
    {
      id: 'locate',
      label: 'Locate the required API terminal',
      done: terminalLocated,
    },
    {
      id: 'devices',
      label: deviceLabel,
      done: devicesComplete,
    },
    {
      id: 'exit',
      label: exitReady
        ? 'Reach and interact with the unlocked exit'
        : 'Unlock the exit by activating required devices',
      done: missionCompleted,
      pending: exitReady && !missionCompleted,
    },
  ] as const

  return (
    <section className="field-panel field-objectives" aria-labelledby="field-objectives-title">
      <h2 id="field-objectives-title" className="field-panel__title">
        Mission objectives
      </h2>
      <ul className="field-objectives__list">
        {items.map((item) => (
          <li
            key={item.id}
            className={`field-objectives__item ${item.done ? 'is-done' : ''} ${
              'pending' in item && item.pending ? 'is-pending' : ''
            }`.trim()}
          >
            <span className="field-objectives__mark" aria-hidden="true">
              {item.done ? '✓' : '○'}
            </span>
            <span>{item.label}</span>
          </li>
        ))}
      </ul>
    </section>
  )
}
