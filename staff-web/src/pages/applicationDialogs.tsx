import { useState, type FormEvent } from 'react'

import { actions, useAction, useQueue, useQuotes, useSuppliers } from '../api/hooks'
import type { Application } from '../api/types'
import { useToast } from '../components/Toast'
import { ErrorBox, Field, Modal, useLocation } from '../components/ui'
import { date, money, today } from '../lib/format'

type DialogProps = { app: Application; open: boolean; onClose: () => void }

export function RaiseOrderDialog({ app, open, onClose }: DialogProps) {
  const toast = useToast()
  const suppliers = useSuppliers()
  const quotes = useQuotes(app.catalogueItemId)
  const queue = useQueue()
  const [supplierId, setSupplierId] = useState<number>()
  const [quoteId, setQuoteId] = useState<number>()
  const [reason, setReason] = useState('')
  const [notes, setNotes] = useState('')
  const raise = useAction((v: Parameters<typeof actions.raiseOrder>[1]) => actions.raiseOrder(app.id, v))

  const position = queue.data?.find((q) => q.applicationId === app.id)?.position
  const outOfOrder = position !== undefined && position > 1
  const matchingQuotes = (quotes.data ?? []).filter((q) => q.supplierId === supplierId
    && q.price === app.assetCost && q.quoteDate <= today() && q.validUntil >= today())

  function submit(e: FormEvent) {
    e.preventDefault()
    if (!supplierId) return
    raise.mutate(
      { supplierId, quoteId, queueOverrideReason: outOfOrder ? reason : undefined, notes: notes || undefined },
      {
        onSuccess: () => {
          toast('Purchase order raised. A manager must approve it.')
          onClose()
        },
      },
    )
  }

  return (
    <Modal open={open} title="Raise purchase order" onClose={onClose}>
      <form onSubmit={submit} className="form">
        <p>
          Amount: <strong>{money(app.assetCost, app.currency)}</strong> for {app.assetName}. The supplier is paid the
          full price.
        </p>
        {outOfOrder && (
          <div className="alert alert-warning">
            This member is number {position} in the queue. Serving them first needs a reason, which is kept for audit.
          </div>
        )}
        <Field label="Supplier">
          <select required value={supplierId ?? ''} onChange={(e) => {
            setSupplierId(e.target.value ? Number(e.target.value) : undefined)
            setQuoteId(undefined)
          }}>
            <option value="">Choose a supplier</option>
            {suppliers.data?.filter((s) => s.active).map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
        </Field>
        {matchingQuotes.length > 0 && (
          <Field label="Supplier quote (optional)">
            <select value={quoteId ?? ''} onChange={(e) => setQuoteId(e.target.value ? Number(e.target.value) : undefined)}>
              <option value="">No quote</option>
              {matchingQuotes.map((q) => (
                <option key={q.id} value={q.id}>
                  {q.reference ?? `Quote ${q.id}`}, valid to {date(q.validUntil)}
                </option>
              ))}
            </select>
          </Field>
        )}
        {outOfOrder && (
          <Field label="Reason for serving out of queue order">
            <textarea required value={reason} onChange={(e) => setReason(e.target.value)} rows={2} />
          </Field>
        )}
        <Field label="Notes (optional)">
          <textarea value={notes} onChange={(e) => setNotes(e.target.value)} rows={2} />
        </Field>
        <ErrorBox error={raise.error} />
        <div className="form-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            Back
          </button>
          <button className="btn btn-primary" disabled={raise.isPending}>
            Raise order
          </button>
        </div>
      </form>
    </Modal>
  )
}

export function DeliveryDialog({ app, open, onClose }: DialogProps) {
  const toast = useToast()
  const location = useLocation()
  const [deliveredOn, setDeliveredOn] = useState(today())
  const [serial, setSerial] = useState('')
  const [lat, setLat] = useState('')
  const [lng, setLng] = useState('')
  const [ack, setAck] = useState(false)
  const [notes, setNotes] = useState('')
  const [files, setFiles] = useState<File[]>([])
  const [uploadError, setUploadError] = useState<string | null>(null)
  const record = useAction(async () => {
    const asset = await actions.recordDelivery(app.id, {
      serialNumber: serial || undefined,
      latitude: Number(lat),
      longitude: Number(lng),
      deliveredOn,
      memberAcknowledged: ack,
      notes: notes || undefined,
    })
    let failed = 0
    for (const file of files) {
      try {
        await actions.uploadPhoto(asset.id, file, 'DELIVERY')
      } catch {
        failed++
      }
    }
    return { asset, failed }
  })

  if (location.position && lat === '' && lng === '') {
    setLat(String(location.position.latitude))
    setLng(String(location.position.longitude))
  }

  function submit(e: FormEvent) {
    e.preventDefault()
    setUploadError(null)
    record.mutate(undefined, {
      onSuccess: ({ failed }) => {
        if (failed) {
          setUploadError(`Delivery saved, but ${failed} photo(s) did not upload. Add them from the asset page.`)
        } else {
          toast('Delivery recorded')
          onClose()
        }
      },
    })
  }

  return (
    <Modal open={open} title="Record delivery" onClose={onClose}>
      <form onSubmit={submit} className="form">
        <p className="muted">Fill this in at the member's site, once the {app.assetName.toLowerCase()} is installed.</p>
        <fieldset className="field">
          <legend className="field-label">Location of the asset</legend>
          <div className="row">
            <input required inputMode="decimal" placeholder="Latitude" value={lat} onChange={(e) => setLat(e.target.value)}
              aria-label="Latitude" />
            <input required inputMode="decimal" placeholder="Longitude" value={lng}
              onChange={(e) => setLng(e.target.value)} aria-label="Longitude" />
            <button type="button" className="btn" onClick={() => {
              setLat('')
              setLng('')
              location.locate()
            }} disabled={location.busy}>
              {location.busy ? 'Locating…' : 'Use my location'}
            </button>
          </div>
          {(location.position || location.error) && (
            <span className="field-hint">
              {location.position ? `Accurate to about ${location.position.accuracy} m` : location.error}
            </span>
          )}
        </fieldset>
        <div className="row">
          <Field label="Delivered on">
            <input type="date" required max={today()} value={deliveredOn} onChange={(e) => setDeliveredOn(e.target.value)} />
          </Field>
          <Field label="Serial number">
            <input value={serial} onChange={(e) => setSerial(e.target.value)} />
          </Field>
        </div>
        <Field label="Photos" hint="Take photos of the installed asset and its serial plate.">
          <input type="file" accept="image/jpeg,image/png,image/webp" capture="environment" multiple
            onChange={(e) => setFiles(Array.from(e.target.files ?? []))} />
        </Field>
        <Field label="Notes (optional)">
          <textarea rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} />
        </Field>
        <label className="check">
          <input type="checkbox" checked={ack} onChange={(e) => setAck(e.target.checked)} required />
          The member has received the asset and confirmed it works.
        </label>
        <ErrorBox error={record.error ?? uploadError} />
        <div className="form-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            Back
          </button>
          <button className="btn btn-primary" disabled={record.isPending}>
            {record.isPending ? 'Saving…' : 'Save delivery'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

export function ConvertDialog({ app, open, onClose }: DialogProps) {
  const toast = useToast()
  const convert = useAction(() => actions.convert(app.id))
  const resuming = app.conversionStep !== 'NOT_STARTED'
  const deposit = Math.min(app.depositedAmount, app.assetCost)

  return (
    <Modal open={open} title={resuming ? 'Resume conversion' : 'Convert to loan'} onClose={onClose}>
      <div className="form">
        {resuming ? (
          <p>
            A previous attempt stopped part-way. Running it again continues where it stopped. Nothing already posted in
            Mifos is posted twice.
          </p>
        ) : (
          <>
            <p>This posts in Mifos:</p>
            <ol className="steps-list">
              <li>
                Withdraw the member's deposit (about <strong>{money(deposit, app.currency)}</strong>) to pay the supplier.
              </li>
              <li>
                Create, approve and disburse a loan of about{' '}
                <strong>{money(app.assetCost - deposit, app.currency)}</strong> to the supplier.
              </li>
            </ol>
            <p className="muted small">Exact amounts use the live balance at the moment of conversion.</p>
          </>
        )}
        <ErrorBox error={convert.error} />
        <div className="form-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            Back
          </button>
          <button className="btn btn-primary" disabled={convert.isPending} onClick={() =>
            convert.mutate(undefined, {
              onSuccess: (result) => {
                toast(result.status === 'PAID_OFF' ? 'Deposit covered the full cost: no loan needed'
                  : `Loan ${result.loanId} disbursed to the supplier`)
                onClose()
              },
            })}>
            {convert.isPending ? 'Posting in Mifos…' : resuming ? 'Resume' : 'Convert to loan'}
          </button>
        </div>
      </div>
    </Modal>
  )
}

export function RepriceDialog({ app, open, onClose }: DialogProps) {
  const toast = useToast()
  const quotes = useQuotes(app.catalogueItemId)
  const [quoteId, setQuoteId] = useState<number>()
  const [cost, setCost] = useState('')
  const reprice = useAction((v: { quoteId?: number; assetCost?: number }) => actions.reprice(app.id, v))
  const valid = (quotes.data ?? []).filter((q) => q.quoteDate <= today() && q.validUntil >= today())
  const newCost = quoteId ? valid.find((q) => q.id === quoteId)?.price : Number(cost) || undefined

  return (
    <Modal open={open} title="Change asset price" onClose={onClose}>
      <form className="form" onSubmit={(e) => {
        e.preventDefault()
        reprice.mutate(quoteId ? { quoteId } : { assetCost: Number(cost) }, {
          onSuccess: (a) => {
            toast(`New price ${money(a.assetCost, a.currency)}; deposit target ${money(a.depositTarget, a.currency)}`)
            onClose()
          },
        })
      }}>
        <p>
          Current price {money(app.assetCost, app.currency)}, deposit target {money(app.depositTarget, app.currency)}.
        </p>
        {valid.length > 0 && (
          <Field label="Use a supplier quote">
            <select value={quoteId ?? ''} onChange={(e) => setQuoteId(e.target.value ? Number(e.target.value) : undefined)}>
              <option value="">Enter a price instead</option>
              {valid.map((q) => (
                <option key={q.id} value={q.id}>
                  {q.supplierName}: {money(q.price, q.currency)}
                </option>
              ))}
            </select>
          </Field>
        )}
        {!quoteId && (
          <Field label={`New price (${app.currency})`}>
            <input required type="number" min="0.01" step="0.01" value={cost} onChange={(e) => setCost(e.target.value)} />
          </Field>
        )}
        {newCost !== undefined && newCost > app.assetCost && app.depositedAmount < newCost / 2 && (
          <div className="alert alert-warning">
            The new target is {money(newCost / 2, app.currency)}. The member has {money(app.depositedAmount, app.currency)}
            {app.status === 'QUALIFIED' ? ' and will leave the queue until they top up.' : '.'}
          </div>
        )}
        <ErrorBox error={reprice.error} />
        <div className="form-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            Back
          </button>
          <button className="btn btn-primary" disabled={reprice.isPending}>
            Save price
          </button>
        </div>
      </form>
    </Modal>
  )
}

export function ReasonDialog({ open, title, intro, confirm, onClose, onConfirm, pending, error }: {
  open: boolean
  title: string
  intro: string
  confirm: string
  onClose: () => void
  onConfirm: (reason: string) => void
  pending: boolean
  error: unknown
}) {
  const [reason, setReason] = useState('')
  return (
    <Modal open={open} title={title} onClose={onClose}>
      <form className="form" onSubmit={(e) => {
        e.preventDefault()
        onConfirm(reason)
      }}>
        <p>{intro}</p>
        <Field label="Reason">
          <textarea required rows={3} value={reason} onChange={(e) => setReason(e.target.value)} />
        </Field>
        <ErrorBox error={error} />
        <div className="form-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            Back
          </button>
          <button className="btn btn-danger" disabled={pending}>
            {confirm}
          </button>
        </div>
      </form>
    </Modal>
  )
}

export function DemoDepositDialog({ app, open, onClose }: DialogProps) {
  const toast = useToast()
  const [amount, setAmount] = useState(String(Math.max(50, Math.ceil(app.depositTarget - app.depositedAmount))))
  const deposit = useAction(() => actions.demoDeposit(app.savingsAccountId, Number(amount)))
  return (
    <Modal open={open} title="Demo: record a deposit" onClose={onClose}>
      <form className="form" onSubmit={(e) => {
        e.preventDefault()
        deposit.mutate(undefined, {
          onSuccess: () => {
            toast('Deposit recorded')
            onClose()
          },
        })
      }}>
        <p className="muted">In real use, deposits are posted by tellers in Mifos and appear here automatically.</p>
        <Field label={`Amount (${app.currency})`}>
          <input type="number" min="0.01" step="0.01" required value={amount} onChange={(e) => setAmount(e.target.value)} />
        </Field>
        <ErrorBox error={deposit.error} />
        <div className="form-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            Back
          </button>
          <button className="btn btn-primary" disabled={deposit.isPending}>
            Deposit
          </button>
        </div>
      </form>
    </Modal>
  )
}
