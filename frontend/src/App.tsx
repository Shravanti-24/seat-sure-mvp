import { Suspense, lazy } from 'react'
import { Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout'
import { RequireRole } from './auth/RequireRole'
import { AdminPage } from './pages/AdminPage'
import { CheckoutPage } from './pages/CheckoutPage'
import { EventDetailPage } from './pages/EventDetailPage'
import { EventsPage } from './pages/EventsPage'
import { LoginPage } from './pages/LoginPage'
import { MyTicketsPage } from './pages/MyTicketsPage'
import { WaitingRoomPage } from './pages/WaitingRoomPage'
import { Spinner } from './components/Spinner'

// The camera library is large and only staff ever need it, so it loads on demand.
const GateScannerPage = lazy(() =>
  import('./pages/GateScannerPage').then((module) => ({ default: module.GateScannerPage })),
)

export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<EventsPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/events/:id" element={<EventDetailPage />} />
        <Route
          path="/events/:id/queue"
          element={
            <RequireRole role="ATTENDEE">
              <WaitingRoomPage />
            </RequireRole>
          }
        />
        <Route
          path="/checkout/:bookingId"
          element={
            <RequireRole role="ATTENDEE">
              <CheckoutPage />
            </RequireRole>
          }
        />
        <Route
          path="/me/tickets"
          element={
            <RequireRole role="ATTENDEE">
              <MyTicketsPage />
            </RequireRole>
          }
        />
        <Route
          path="/staff/scan"
          element={
            <RequireRole role="STAFF">
              <Suspense fallback={<Spinner label="Loading the scanner" />}>
                <GateScannerPage />
              </Suspense>
            </RequireRole>
          }
        />
        <Route
          path="/admin"
          element={
            <RequireRole role="ADMIN">
              <AdminPage />
            </RequireRole>
          }
        />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  )
}

function NotFound() {
  return (
    <div className="card empty">
      <h2>Nothing here</h2>
      <p className="muted">That page does not exist.</p>
    </div>
  )
}
