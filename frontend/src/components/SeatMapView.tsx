import type { SeatMapSeat, SeatMapSection } from '../api/types'
import { formatMoney } from '../lib/format'

const SEAT_SIZE = 18
const SEAT_GAP = 4
const ROW_LABEL_WIDTH = 22

/**
 * An SVG seat map, one block per section. Colour is the only state: available, held by somebody
 * else, booked, or picked by this user.
 */
export function SeatMapView({
  sections,
  selected,
  onToggle,
  disabled,
}: {
  sections: SeatMapSection[]
  selected: Set<number>
  onToggle: (seat: SeatMapSeat) => void
  disabled: boolean
}) {
  return (
    <div className="seatmap">
      <div className="stage">Stage</div>
      {sections.map((section) => (
        <SectionBlock
          key={section.sectionId}
          section={section}
          selected={selected}
          onToggle={onToggle}
          disabled={disabled}
        />
      ))}
      <Legend />
    </div>
  )
}

function SectionBlock({
  section,
  selected,
  onToggle,
  disabled,
}: {
  section: SeatMapSection
  selected: Set<number>
  onToggle: (seat: SeatMapSeat) => void
  disabled: boolean
}) {
  const rows = groupByRow(section.seats)
  const width = ROW_LABEL_WIDTH + section.seatsPerRow * (SEAT_SIZE + SEAT_GAP)
  const height = rows.length * (SEAT_SIZE + SEAT_GAP)

  return (
    <section className="seatmap-section">
      <header>
        <h3>{section.sectionName}</h3>
        <span className="muted">{formatMoney(section.pricePaise)} per seat</span>
      </header>
      <div className="seatmap-scroll">
        <svg
          viewBox={`0 0 ${width} ${height}`}
          width="100%"
          style={{ maxWidth: width, minWidth: Math.min(width, 280) }}
          role="group"
          aria-label={`${section.sectionName} seats`}
        >
          {rows.map(([rowLabel, seats], rowIndex) => (
            <g key={rowLabel}>
              <text
                x={0}
                y={rowIndex * (SEAT_SIZE + SEAT_GAP) + SEAT_SIZE * 0.75}
                className="row-label"
              >
                {rowLabel}
              </text>
              {seats.map((seat) => {
                const state = selected.has(seat.seatId) ? 'selected' : seat.status.toLowerCase()
                const clickable = seat.status === 'AVAILABLE' && !disabled
                return (
                  <rect
                    key={seat.seatId}
                    x={ROW_LABEL_WIDTH + (seat.seatNumber - 1) * (SEAT_SIZE + SEAT_GAP)}
                    y={rowIndex * (SEAT_SIZE + SEAT_GAP)}
                    width={SEAT_SIZE}
                    height={SEAT_SIZE}
                    rx={4}
                    className={`seat seat-${state}${clickable || selected.has(seat.seatId) ? ' seat-clickable' : ''}`}
                    onClick={() => {
                      if (clickable || selected.has(seat.seatId)) {
                        onToggle(seat)
                      }
                    }}
                  >
                    <title>{`${seat.label} · ${formatMoney(section.pricePaise)} · ${seat.status}`}</title>
                  </rect>
                )
              })}
            </g>
          ))}
        </svg>
      </div>
    </section>
  )
}

function Legend() {
  return (
    <ul className="legend">
      <li>
        <span className="swatch seat-available" /> Available
      </li>
      <li>
        <span className="swatch seat-selected" /> Your pick
      </li>
      <li>
        <span className="swatch seat-held" /> Someone is paying
      </li>
      <li>
        <span className="swatch seat-booked" /> Booked
      </li>
    </ul>
  )
}

function groupByRow(seats: SeatMapSeat[]): [string, SeatMapSeat[]][] {
  const rows = new Map<string, SeatMapSeat[]>()
  for (const seat of seats) {
    const row = rows.get(seat.rowLabel)
    if (row) {
      row.push(seat)
    } else {
      rows.set(seat.rowLabel, [seat])
    }
  }
  return [...rows.entries()]
}
