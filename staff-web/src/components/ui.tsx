import { cloneElement, isValidElement, useEffect, useId, useRef, useState, type ReactNode } from 'react'

type Tone = 'neutral' | 'info' | 'progress' | 'success' | 'warning' | 'danger'

export function Badge({ tone = 'neutral', children, title }: { tone?: Tone; children: ReactNode; title?: string }) {
  return (
    <span className={`badge badge-${tone}`} title={title}>
      {children}
    </span>
  )
}

export function ProgressBar({ percent, label }: { percent: number; label?: string }) {
  const clamped = Math.max(0, Math.min(100, percent))
  return (
    <div className="progress" role="progressbar" aria-valuenow={Math.round(clamped)} aria-valuemin={0}
      aria-valuemax={100} aria-label={label ?? 'Progress to deposit target'}>
      <div className={`progress-fill${percent >= 100 ? ' done' : ''}`} style={{ width: `${clamped}%` }} />
    </div>
  )
}

export function Loading({ what = 'Loading' }: { what?: string }) {
  return <p className="muted loading">{what}…</p>
}

export function ErrorBox({ error }: { error: unknown }) {
  if (!error) return null
  const message = error instanceof Error ? error.message : String(error)
  return (
    <div className="alert alert-danger" role="alert">
      {message}
    </div>
  )
}

export function Empty({ children }: { children: ReactNode }) {
  return <div className="empty">{children}</div>
}

export function FullPageMessage({ children }: { children: ReactNode }) {
  return <div className="full-page">{children}</div>
}

export function PageHeader({ title, subtitle, actions }: { title: ReactNode; subtitle?: ReactNode; actions?: ReactNode }) {
  return (
    <header className="page-header">
      <div>
        <h1>{title}</h1>
        {subtitle && <p className="muted">{subtitle}</p>}
      </div>
      {actions && <div className="page-actions">{actions}</div>}
    </header>
  )
}

/**
 * A labelled form control. The label is linked to the control by id (not wrapped around it), so a
 * dropdown's options never become part of the label that screen readers announce.
 */
export function Field({ label, hint, children }: { label: string; hint?: ReactNode; children: ReactNode }) {
  const id = useId()
  const hintId = `${id}-hint`
  const control = isValidElement<{ id?: string; 'aria-describedby'?: string }>(children)
    ? cloneElement(children, { id, 'aria-describedby': hint ? hintId : undefined })
    : children
  return (
    <div className="field">
      <label className="field-label" htmlFor={id}>{label}</label>
      {control}
      {hint && <span className="field-hint" id={hintId}>{hint}</span>}
    </div>
  )
}

export function Stat({ label, value, sub, tone }: { label: string; value: ReactNode; sub?: ReactNode; tone?: Tone }) {
  return (
    <div className={`stat${tone ? ` stat-${tone}` : ''}`}>
      <div className="stat-label">{label}</div>
      <div className="stat-value">{value}</div>
      {sub && <div className="stat-sub">{sub}</div>}
    </div>
  )
}

/** Accessible modal built on <dialog>. Closes on Escape and the close button. */
export function Modal({ open, title, onClose, children }: {
  open: boolean
  title: string
  onClose: () => void
  children: ReactNode
}) {
  const ref = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (open && !dialog.open) dialog.showModal()
    if (!open && dialog.open) dialog.close()
  }, [open])
  return (
    <dialog ref={ref} className="modal" onClose={onClose} aria-labelledby="modal-title">
      {open && (
        <>
          <div className="modal-head">
            <h2 id="modal-title">{title}</h2>
            <button type="button" className="icon-btn" aria-label="Close" onClick={onClose}>
              ×
            </button>
          </div>
          <div className="modal-body">{children}</div>
        </>
      )}
    </dialog>
  )
}

/** Detail list of label/value pairs. */
export function Facts({ items }: { items: [string, ReactNode][] }) {
  return (
    <dl className="facts">
      {items.map(([k, v]) => (
        <div key={k}>
          <dt>{k}</dt>
          <dd>{v}</dd>
        </div>
      ))}
    </dl>
  )
}

export type Position = { latitude: number; longitude: number; accuracy: number }

/** Asks the phone/browser for the current location. */
export function useLocation() {
  const [position, setPosition] = useState<Position | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  function locate() {
    if (!navigator.geolocation) {
      setError('This device cannot share its location. Enter the coordinates by hand.')
      return
    }
    setBusy(true)
    setError(null)
    navigator.geolocation.getCurrentPosition(
      (p) => {
        setPosition({
          latitude: Number(p.coords.latitude.toFixed(6)),
          longitude: Number(p.coords.longitude.toFixed(6)),
          accuracy: Math.round(p.coords.accuracy),
        })
        setBusy(false)
      },
      (e) => {
        setError(e.code === e.PERMISSION_DENIED
          ? 'Location permission was refused. Allow it in the browser, or enter the coordinates by hand.'
          : 'Could not get the location. Move to open sky and try again, or enter it by hand.')
        setBusy(false)
      },
      { enableHighAccuracy: true, timeout: 20_000, maximumAge: 0 },
    )
  }

  return { position, setPosition, error, busy, locate }
}

export function mapLink(latitude: number, longitude: number) {
  return `https://www.google.com/maps?q=${latitude},${longitude}`
}
