import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'

import { useAuth } from '../auth/AuthContext'
import {
  actions,
  useAction,
  useApplication,
  useApplicationAsset,
  useApplicationOrders,
  useOfficeName,
  useQueue,
} from '../api/hooks'
import type { Application, PurchaseOrder } from '../api/types'
import { useToast } from '../components/Toast'
import { Badge, ErrorBox, Facts, Loading, PageHeader, ProgressBar } from '../components/ui'
import {
  applicationStatus,
  conversionStepLabel,
  date,
  dateTime,
  fromNow,
  money,
  orderStatus,
  ownershipLabel,
} from '../lib/format'
import {
  ConvertDialog,
  DeliveryDialog,
  DemoDepositDialog,
  RaiseOrderDialog,
  ReasonDialog,
  RepriceDialog,
} from './applicationDialogs'

type Dialog = 'order' | 'delivery' | 'convert' | 'reprice' | 'cancel' | 'deposit' | null

const STAGES = [
  { key: 'SAVING', label: 'Saving' },
  { key: 'QUALIFIED', label: 'In queue' },
  { key: 'PROCUREMENT', label: 'Procurement' },
  { key: 'DELIVERED', label: 'Delivered' },
  { key: 'REPAYING', label: 'Repaying' },
  { key: 'PAID_OFF', label: 'Paid off' },
] as const

export function ApplicationDetailPage() {
  const id = Number(useParams().id)
  const { data: app, isPending, error } = useApplication(id)
  if (isPending) return <Loading />
  if (error || !app) return <ErrorBox error={error ?? 'Application not found'} />
  return <ApplicationDetail app={app} />
}

function ApplicationDetail({ app }: { app: Application }) {
  const { me, can } = useAuth()
  const toast = useToast()
  const officeName = useOfficeName()
  const [dialog, setDialog] = useState<Dialog>(null)
  const close = () => setDialog(null)
  const orders = useApplicationOrders(app.id)
  const queue = useQueue()
  const hasAsset = ['DELIVERED', 'REPAYING', 'PAID_OFF'].includes(app.status)
  const asset = useApplicationAsset(app.id, hasAsset)
  const refresh = useAction(() => actions.refresh(app.id))
  const cancel = useAction((reason: string) => actions.cancelApplication(app.id, reason))
  const repay = useAction(() => actions.demoRepay(app.loanId!))

  const open = app.status === 'SAVING' || app.status === 'QUALIFIED'
  const activeOrder = orders.data?.find((o) => o.status === 'DRAFT' || o.status === 'APPROVED')
  const position = queue.data?.find((q) => q.applicationId === app.id)?.position
  const status = applicationStatus[app.status]

  return (
    <>
      <PageHeader
        title={app.memberName}
        subtitle={
          <>
            {app.reference} · {officeName(app.officeId)} · Mifos client {app.clientId}
          </>
        }
        actions={<Badge tone={status.tone} title={status.help}>{status.label}</Badge>}
      />

      {app.status !== 'CANCELLED' && (
        <ol className="stages" aria-label="Progress through the asset financing steps">
          {STAGES.map((s, i) => {
            const current = STAGES.findIndex((x) => x.key === app.status)
            const state = i < current ? 'done' : i === current ? 'current' : 'todo'
            return (
              <li key={s.key} className={state} aria-current={state === 'current' ? 'step' : undefined}>
                {s.label}
              </li>
            )
          })}
        </ol>
      )}

      <div className="grid-2">
        <div className="stack">
          <section className="card">
            <div className="card-head">
              <h2>{app.assetName}</h2>
              <span className="strong">{money(app.assetCost, app.currency)}</span>
            </div>
            {open && (
              <>
                <ProgressBar percent={app.percentComplete} />
                <p className="progress-text">
                  <strong>{money(app.depositedAmount, app.currency)}</strong> of{' '}
                  {money(app.depositTarget, app.currency)} deposit target ({app.percentComplete}%)
                </p>
                <Facts items={[
                  ['Still needed', money(Math.max(0, app.depositTarget - app.depositedAmount), app.currency)],
                  ['Average monthly deposit', money(app.avgMonthlyDeposit, app.currency)],
                  ['Expected to reach 50%', app.status === 'QUALIFIED' ? 'Reached'
                    : app.estimatedTargetDate ? `${date(app.estimatedTargetDate)} (${fromNow(app.estimatedTargetDate)})`
                      : 'No deposits yet'],
                  ...(position ? [['Queue position', `${position}`] as [string, string]] : []),
                  ['Balance checked', dateTime(app.balanceSyncedAt)],
                ]} />
                <div className="button-row">
                  <button className="btn" disabled={refresh.isPending} onClick={() =>
                    refresh.mutate(undefined, { onSuccess: () => toast('Balance updated from Mifos') })}>
                    {refresh.isPending ? 'Checking…' : 'Check balance now'}
                  </button>
                  {me.demo && can('OFFICER') && (
                    <button className="btn" onClick={() => setDialog('deposit')}>
                      Demo: record deposit
                    </button>
                  )}
                </div>
                <ErrorBox error={refresh.error} />
              </>
            )}
            {(app.status === 'PROCUREMENT' || (app.status === 'DELIVERED' && app.depositApplied === null)) && (
              <Facts items={[
                ['Deposit target (50%)', money(app.depositTarget, app.currency)],
                ['Deposit held', money(app.depositedAmount, app.currency)],
                ['Balance checked', dateTime(app.balanceSyncedAt)],
              ]} />
            )}
            {!open && app.depositApplied !== null && (
              <Facts items={[
                ['Deposit target (50%)', money(app.depositTarget, app.currency)],
                ['Deposit applied', money(app.depositApplied, app.currency)],
                ['Financed by loan', money(app.financedAmount, app.currency)],
                ['Mifos loan', app.loanId ? `#${app.loanId}` : '—'],
              ]} />
            )}
          </section>

          <NextStep app={app} activeOrder={activeOrder} position={position} onOpen={setDialog}
            onRepay={() => repay.mutate(undefined, { onSuccess: () => toast('Loan repaid in full') })} />
          <ErrorBox error={repay.error} />

          {hasAsset && asset.data && (
            <section className="card">
              <div className="card-head">
                <h2>Asset in the register</h2>
                <Badge tone={ownershipLabel[asset.data.ownership].tone}>{ownershipLabel[asset.data.ownership].label}</Badge>
              </div>
              <p>
                Delivered {date(asset.data.deliveredOn)} by {asset.data.deliveredBy}
                {asset.data.serialNumber && <>, serial {asset.data.serialNumber}</>}.{' '}
                <Link to={`/assets/${asset.data.id}`}>Open asset record</Link>
              </p>
            </section>
          )}
        </div>

        <div className="stack">
          <section className="card">
            <h2>Purchase orders</h2>
            {orders.isPending ? <Loading /> : orders.data?.length ? (
              <ul className="order-list">
                {orders.data.map((o) => (
                  <OrderItem key={o.id} order={o} />
                ))}
              </ul>
            ) : (
              <p className="muted">None yet.</p>
            )}
          </section>

          <section className="card">
            <h2>Details</h2>
            <Facts items={[
              ['Opened', `${date(app.openedOn)} by ${app.createdBy}`],
              ['Reached 50%', dateTime(app.qualifiedAt)],
              ['Mifos deposit account', `#${app.savingsAccountId}`],
              ['Deposit rule', `${app.depositPercent}% of the asset cost`],
              ...(app.cancelReason ? [['Cancelled because', app.cancelReason] as [string, string]] : []),
            ]} />
            {can('MANAGER') && open && (
              <div className="button-row">
                <button className="btn btn-sm" onClick={() => setDialog('reprice')}>
                  Change price
                </button>
                <button className="btn btn-sm btn-danger-ghost" onClick={() => setDialog('cancel')}>
                  Cancel application
                </button>
              </div>
            )}
          </section>
        </div>
      </div>

      <RaiseOrderDialog app={app} open={dialog === 'order'} onClose={close} />
      <DeliveryDialog app={app} open={dialog === 'delivery'} onClose={close} />
      <ConvertDialog app={app} open={dialog === 'convert'} onClose={close} />
      <RepriceDialog app={app} open={dialog === 'reprice'} onClose={close} />
      {me.demo && <DemoDepositDialog app={app} open={dialog === 'deposit'} onClose={close} />}
      <ReasonDialog open={dialog === 'cancel'} title="Cancel application" confirm="Cancel application"
        intro="The member's deposit stays in their Mifos account. Refund or transfer it there according to policy."
        onClose={close} pending={cancel.isPending} error={cancel.error}
        onConfirm={(reason) => cancel.mutate(reason, {
          onSuccess: () => {
            toast('Application cancelled')
            close()
          },
        })} />
    </>
  )
}

/** The one thing to do next, depending on the stage and the user's role. */
function NextStep({ app, activeOrder, position, onOpen, onRepay }: {
  app: Application
  activeOrder?: PurchaseOrder
  position?: number
  onOpen: (d: Dialog) => void
  onRepay: () => void
}) {
  const { me, can } = useAuth()
  let body: React.ReactNode = null

  switch (app.status) {
    case 'SAVING':
      body = <p>The member keeps depositing in Mifos. They join the queue automatically at 50%.</p>
      break
    case 'QUALIFIED':
      if (activeOrder) {
        body = <p>Purchase order #{activeOrder.id} is waiting for a manager's approval (see the right-hand side).</p>
      } else {
        body = (
          <>
            <p>
              Target reached{position ? `; number ${position} in the queue` : ''}. When cash is available, raise a
              purchase order.
            </p>
            {can('OFFICER') && (
              <button className="btn btn-primary" onClick={() => onOpen('order')}>
                Raise purchase order
              </button>
            )}
          </>
        )
      }
      break
    case 'PROCUREMENT':
      body = (
        <>
          <p>The order is approved. Once the asset is installed, record the delivery at the site.</p>
          {can('OFFICER') && (
            <button className="btn btn-primary" onClick={() => onOpen('delivery')}>
              Record delivery
            </button>
          )}
        </>
      )
      break
    case 'DELIVERED':
      body = (
        <>
          {app.conversionStep !== 'NOT_STARTED' && (
            <div className="alert alert-warning">
              The last conversion attempt stopped at: {conversionStepLabel[app.conversionStep]}. Run it again to finish.
            </div>
          )}
          <p>Delivered. A manager now converts it: the deposit is applied and the rest becomes a loan in Mifos.</p>
          {can('MANAGER') ? (
            <button className="btn btn-primary" onClick={() => onOpen('convert')}>
              {app.conversionStep === 'NOT_STARTED' ? 'Convert to loan' : 'Resume conversion'}
            </button>
          ) : (
            <p className="muted">Waiting for a manager.</p>
          )}
        </>
      )
      break
    case 'REPAYING':
      body = (
        <>
          <p>
            The member is repaying loan #{app.loanId} in Mifos. Ownership passes to them automatically when it is
            fully repaid.
          </p>
          {me.demo && can('OFFICER') && (
            <button className="btn" onClick={onRepay}>
              Demo: repay loan in full
            </button>
          )}
        </>
      )
      break
    case 'PAID_OFF':
      body = <p>Complete. The asset now belongs to the member.</p>
      break
    case 'CANCELLED':
      body = <p>This application was cancelled.</p>
      break
  }
  return (
    <section className="card next-step">
      <h2>Next step</h2>
      {body}
    </section>
  )
}

function OrderItem({ order }: { order: PurchaseOrder }) {
  const { me, can } = useAuth()
  const toast = useToast()
  const [cancelling, setCancelling] = useState(false)
  const approve = useAction(() => actions.approveOrder(order.id))
  const cancel = useAction((reason: string) => actions.cancelOrder(order.id, reason))
  const s = orderStatus[order.status]
  const ownOrder = order.createdBy === me.username

  return (
    <li>
      <div className="order-head">
        <strong>#{order.id} {order.supplierName}</strong>
        <Badge tone={s.tone}>{s.label}</Badge>
      </div>
      <div className="muted small">
        {money(order.amount, order.currency)} · raised by {order.createdBy} {dateTime(order.createdAt)}
        {order.approvedBy && <> · approved by {order.approvedBy}</>}
        {order.cancelReason && <> · cancelled: {order.cancelReason}</>}
      </div>
      {order.queueOverrideReason && <div className="small">Out of queue order: {order.queueOverrideReason}</div>}
      {can('MANAGER') && (order.status === 'DRAFT' || order.status === 'APPROVED') && (
        <div className="button-row">
          {order.status === 'DRAFT' && (
            ownOrder ? (
              <span className="muted small">You raised this order, so another manager must approve it.</span>
            ) : (
              <button className="btn btn-primary btn-sm" disabled={approve.isPending}
                onClick={() => approve.mutate(undefined, { onSuccess: () => toast('Purchase order approved') })}>
                Approve
              </button>
            )
          )}
          <button className="btn btn-sm btn-danger-ghost" onClick={() => setCancelling(true)}>
            Cancel order
          </button>
        </div>
      )}
      <ErrorBox error={approve.error} />
      <ReasonDialog open={cancelling} title={`Cancel purchase order #${order.id}`} confirm="Cancel order"
        intro="The member goes back into the queue at the same position."
        onClose={() => setCancelling(false)} pending={cancel.isPending} error={cancel.error}
        onConfirm={(reason) => cancel.mutate(reason, {
          onSuccess: () => {
            toast('Purchase order cancelled')
            setCancelling(false)
          },
        })} />
    </li>
  )
}
