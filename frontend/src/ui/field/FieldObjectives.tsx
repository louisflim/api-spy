interface FieldObjectivesProps {
  terminalLocated: boolean
  unlockedCount: number
  requiredCount: number
  devicesComplete: boolean
  exitReady: boolean
  missionCompleted: boolean
  /** Level 7 boss state from the server (null when the room has no boss). */
  boss?: { shieldDown: boolean; defeated: boolean } | null
  /** Display name of this room's boss (e.g. Nexus Warden). */
  bossName?: string
}

export function FieldObjectives({
  terminalLocated,
  unlockedCount,
  requiredCount,
  devicesComplete,
  exitReady,
  missionCompleted,
  boss = null,
  bossName = 'boss',
}: FieldObjectivesProps) {
  const deviceLabel =
    requiredCount <= 1
      ? 'Activate the mandatory API security device'
      : `Activate all mandatory API security devices (${unlockedCount}/${requiredCount}) — exit stays locked until every device and final lock (if required) are done`

  type Item = { id: string; label: string; done: boolean; pending?: boolean }
  const items: Item[] = [
    {
      id: 'locate',
      label: 'Locate the required API terminal',
      done: terminalLocated,
    },
    {
      id: 'devices',
      label: boss ? `${deviceLabel} to drop the ${bossName}'s shield` : deviceLabel,
      done: devicesComplete,
    },
  ]
  if (boss) {
    items.push({
      id: 'boss',
      label: boss.shieldDown
        ? `Defeat the ${bossName}`
        : `Defeat the ${bossName} (shielded until every API device is operated)`,
      done: boss.defeated || missionCompleted,
      pending: boss.shieldDown && !boss.defeated && !missionCompleted,
    })
  }
  items.push({
    id: 'exit',
    label: exitReady
      ? 'Reach and interact with the unlocked exit'
      : boss
        ? `Unlock the exit by operating every device and defeating the ${bossName}`
        : 'Unlock the exit by activating required devices',
    done: missionCompleted,
    pending: exitReady && !missionCompleted,
  })

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
              item.pending ? 'is-pending' : ''
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
