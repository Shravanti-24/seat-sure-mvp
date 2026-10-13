import { useEffect } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/endpoints'
import { Countdown } from '../components/Countdown'
import { ErrorBanner } from '../components/ErrorBanner'
import { Spinner } from '../components/Spinner'
import { formatWait } from '../lib/format'
import { rememberAdmissionToken } from '../lib/admission'

export function WaitingRoomPage() {
  const { id } = useParams<{ id: string }>()
  const eventId = Number(id)
  const navigate = useNavigate()

  const join = useMutation({ mutationFn: () => api.joinQueue(eventId) })

  // Joining is idempotent server side (ZADD NX), so landing here simply takes your place in line.
  useEffect(() => {
    if (Number.isFinite(eventId)) {
      join.mutate()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [eventId])

  const status = useQuery({
    queryKey: ['queue', eventId],
    queryFn: () => api.queueStatus(eventId),
    enabled: Number.isFinite(eventId),
    // Polling is also the heartbeat: stop polling for a minute and the queue drops you.
    refetchInterval: 3000,
  })

  const admissionToken = status.data?.admitted ? status.data.admissionToken : null

  useEffect(() => {
    if (admissionToken) {
      rememberAdmissionToken(eventId, admissionToken)
      navigate(`/events/${eventId}`, { replace: true })
    }
  }, [admissionToken, eventId, navigate])

  if (status.isPending) {
    return <Spinner label="Finding your place in line" />
  }
  if (status.error) {
    return <ErrorBanner error={status.error} onRetry={status.refetch} />
  }
  const queue = status.data
  if (!queue) {
    return <ErrorBanner error={new Error('The waiting room is not available')} />
  }

  if (!queue.saleOpen) {
    return (
      <div className="narrow">
        <div className="card center">
          <h1>The drop has not opened yet</h1>
          <p className="muted">The queue opens the moment the sale does.</p>
          <Countdown target={queue.saleOpensAt} prefix="Opens in" expiredLabel="Opening now" />
        </div>
      </div>
    )
  }

  const ahead = Math.max(0, queue.position - 1)
  const progress =
    queue.queueLength > 0 ? Math.min(100, Math.round(((queue.queueLength - ahead) / queue.queueLength) * 100)) : 100

  return (
    <div className="narrow">
      <div className="card center queue-card">
        <p className="muted">You are in line for</p>
        <h1 className="queue-position">
          {queue.position > 0 ? `#${queue.position}` : 'Almost there'}
        </h1>
        <p className="muted">
          {ahead === 0
            ? 'You are next. Hold on a moment.'
            : `${ahead} ${ahead === 1 ? 'person' : 'people'} ahead of you`}
        </p>

        <div className="progress" role="progressbar" aria-valuenow={progress} aria-valuemin={0} aria-valuemax={100}>
          <div className="progress-fill" style={{ width: `${progress}%` }} />
        </div>

        <dl className="meta center-meta">
          <div>
            <dt>Estimated wait</dt>
            <dd>{formatWait(queue.estimatedWaitSeconds)}</dd>
          </div>
          <div>
            <dt>In the queue</dt>
            <dd>{queue.queueLength}</dd>
          </div>
        </dl>

        <p className="muted small">
          Keep this tab open. Your place is fixed by when you arrived, so refreshing will not move you
          forward — and leaving for a minute gives your place to the people behind you.
        </p>
      </div>
    </div>
  )
}
