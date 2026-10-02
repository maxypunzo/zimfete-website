import { useState } from 'react'
import { Link } from 'react-router-dom'

import { useAuth } from '../auth/AuthContext'
import { actions, useAction, useOfficeName, usePurchaseOrders } from '../api/hooks'
import type { PurchaseOrderStatus } from '../api/types'
import { OfficeFilter } from '../components/OfficeFilter'
import { useToast } from '../components/Toast'
import { Badge, Empty, ErrorBox, Loading, PageHeader } from '../components/ui'
import { dateTime, money, orderStatus } from '../lib/format'

const STATUSES: PurchaseOrderStatus[] = ['DRAFT', 'APPROVED', 'COMPLETED', 'CANCELLED']

export function PurchaseOrdersPage() {
  const { me, can } = useAuth()
  const toast = useToast()
  const officeName = useOfficeName()
  const [status, setStatus] = useState<PurchaseOrderStatus | undefined>('DRAFT')
  const [officeId, setOfficeId] = useState<number>()
  const { data, isPending, error } = usePurchaseOrders({ status, officeId })
  const approve = useAction(actions.approveOrder)

  return (
    <>
      <PageHeader title="Purchase orders"
        subtitle="Whoever raises an order cannot approve it. Cancel orders from the application page." />
      <div className="tabs" role="tablist" aria-label="Status">
        {STATUSES.map((s) => (
          <button key={s} role="tab" aria-selected={status === s} className={status === s ? 'active' : ''}
            onClick={() => setStatus(s)}>
            {orderStatus[s].label}
          </button>
        ))}
        <button role="tab" aria-selected={!status} className={!status ? 'active' : ''} onClick={() => setStatus(undefined)}>
          All
        </button>
      </div>
      <div className="toolbar">
        <OfficeFilter value={officeId} onChange={setOfficeId} />
      </div>
      <ErrorBox error={error || approve.error} />
      {isPending ? <Loading /> : !data?.length ? <Empty>No purchase orders here.</Empty> : (
        <div className="table-wrap">
          <table className="table table-cards">
            <thead>
              <tr>
                <th>Order</th>
                <th>Member</th>
                <th>Supplier</th>
                <th className="num">Amount</th>
                <th>Raised</th>
                <th>Status</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {data.map((o) => (
                <tr key={o.id}>
                  <td data-label="Order" className="strong">#{o.id}</td>
                  <td data-label="Member">
                    <Link to={`/applications/${o.applicationId}`}>{o.memberName}</Link>
                    <div className="muted small">{o.applicationReference} · {officeName(o.officeId)}</div>
                    {o.queueOverrideReason && <div className="small">Out of queue order: {o.queueOverrideReason}</div>}
                  </td>
                  <td data-label="Supplier">{o.supplierName}</td>
                  <td data-label="Amount" className="num">{money(o.amount, o.currency)}</td>
                  <td data-label="Raised">
                    {o.createdBy}
                    <div className="muted small">{dateTime(o.createdAt)}</div>
                  </td>
                  <td data-label="Status"><Badge tone={orderStatus[o.status].tone}>{orderStatus[o.status].label}</Badge></td>
                  <td>
                    {o.status === 'DRAFT' && can('MANAGER') && o.createdBy !== me.username && (
                      <button className="btn btn-primary btn-sm" disabled={approve.isPending}
                        onClick={() => approve.mutate(o.id, { onSuccess: () => toast(`Order #${o.id} approved`) })}>
                        Approve
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}
