import { useEffect, useRef, useState } from 'react'
import { Html5Qrcode } from 'html5-qrcode'
import { api } from '../api/endpoints'
import { friendlyMessage } from '../lib/errors'
import { formatTime } from '../lib/format'
import type { CheckInResult } from '../api/types'

const SCANNER_ELEMENT_ID = 'seatsure-scanner'

type Outcome =
  | { kind: 'ok'; result: CheckInResult }
  | { kind: 'used'; result: CheckInResult }
  | { kind: 'rejected'; message: string }

/** The gate. Camera scanning, with a result screen big enough to read at arm's length. */
export function GateScannerPage() {
  const [scanning, setScanning] = useState(false)
  const [outcome, setOutcome] = useState<Outcome | null>(null)
  const [cameraError, setCameraError] = useState<string | null>(null)
  const [manualCode, setManualCode] = useState('')
  const scannerRef = useRef<Html5Qrcode | null>(null)
  const busyRef = useRef(false)

  useEffect(() => {
    return () => {
      void stopScanner(scannerRef.current)
      scannerRef.current = null
    }
  }, [])

  async function submitCode(qrContent: string) {
    if (busyRef.current) {
      return
    }
    busyRef.current = true
    try {
      const result = await api.checkIn(qrContent)
      setOutcome(result.result === 'OK' ? { kind: 'ok', result } : { kind: 'used', result })
    } catch (error) {
      setOutcome({ kind: 'rejected', message: friendlyMessage(error) })
    } finally {
      // A short cool-down stops one badge in front of the lens from firing dozens of requests.
      window.setTimeout(() => {
        busyRef.current = false
      }, 1500)
    }
  }

  async function startScanning() {
    setCameraError(null)
    setOutcome(null)
    setScanning(true)
    try {
      const scanner = new Html5Qrcode(SCANNER_ELEMENT_ID)
      scannerRef.current = scanner
      await scanner.start(
        { facingMode: 'environment' },
        { fps: 10, qrbox: { width: 240, height: 240 } },
        (decodedText) => {
          void submitCode(decodedText)
        },
        () => {
          // Called constantly for frames with no QR code in them; nothing to do.
        },
      )
    } catch (error) {
      setScanning(false)
      setCameraError(error instanceof Error ? error.message : 'The camera could not be started')
    }
  }

  async function stopScanning() {
    await stopScanner(scannerRef.current)
    scannerRef.current = null
    setScanning(false)
  }

  return (
    <div className="narrow">
      <div className="page-head">
        <h1>Gate check-in</h1>
        <p className="muted">Point the camera at the attendee&apos;s QR code.</p>
      </div>

      {outcome ? <OutcomeScreen outcome={outcome} onDismiss={() => setOutcome(null)} /> : null}

      <div className="card">
        <div id={SCANNER_ELEMENT_ID} className={scanning ? 'scanner' : 'scanner scanner-idle'} />
        {cameraError ? <div className="banner banner-error">{cameraError}</div> : null}
        <div className="row gap">
          {scanning ? (
            <button type="button" className="btn btn-ghost" onClick={() => void stopScanning()}>
              Stop camera
            </button>
          ) : (
            <button type="button" className="btn btn-primary" onClick={() => void startScanning()}>
              Start camera
            </button>
          )}
        </div>

        <form
          className="form"
          onSubmit={(event) => {
            event.preventDefault()
            if (manualCode.trim().length > 0) {
              void submitCode(manualCode.trim())
              setManualCode('')
            }
          }}
        >
          <label>
            Or type the code (useful when the camera is blocked)
            <input
              value={manualCode}
              onChange={(event) => setManualCode(event.target.value)}
              placeholder="12.0f4b21a7-….signature"
            />
          </label>
          <button type="submit" className="btn btn-ghost btn-sm">
            Check in
          </button>
        </form>
      </div>
    </div>
  )
}

function OutcomeScreen({ outcome, onDismiss }: { outcome: Outcome; onDismiss: () => void }) {
  if (outcome.kind === 'ok') {
    return (
      <div className="result result-ok" role="status">
        <div className="result-mark">✓</div>
        <h2>{outcome.result.attendeeName}</h2>
        <p>
          {outcome.result.sectionName} {outcome.result.seatLabel}
        </p>
        <p className="muted small">{outcome.result.eventTitle}</p>
        <button type="button" className="btn btn-ghost btn-sm" onClick={onDismiss}>
          Next
        </button>
      </div>
    )
  }
  if (outcome.kind === 'used') {
    return (
      <div className="result result-bad" role="alert">
        <div className="result-mark">✗</div>
        <h2>Already used</h2>
        <p>
          {outcome.result.sectionName} {outcome.result.seatLabel} · {outcome.result.attendeeName}
        </p>
        <p className="muted small">
          Scanned at {outcome.result.checkedInAt ? formatTime(outcome.result.checkedInAt) : 'an earlier time'}
        </p>
        <button type="button" className="btn btn-ghost btn-sm" onClick={onDismiss}>
          Next
        </button>
      </div>
    )
  }
  return (
    <div className="result result-bad" role="alert">
      <div className="result-mark">✗</div>
      <h2>Not a valid ticket</h2>
      <p>{outcome.message}</p>
      <button type="button" className="btn btn-ghost btn-sm" onClick={onDismiss}>
        Next
      </button>
    </div>
  )
}

async function stopScanner(scanner: Html5Qrcode | null): Promise<void> {
  if (!scanner) {
    return
  }
  try {
    await scanner.stop()
    scanner.clear()
  } catch {
    // Already stopped.
  }
}
