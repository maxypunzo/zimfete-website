import { useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'

import { useAuth } from '../auth/AuthContext'
import { useApplications, useOfficeName } from '../api/hooks'
import type { Application, ApplicationStatus } from '../api/types'
import { OfficeFilter } from '../components/OfficeFilter'
import { Badge, Empty, ErrorBox, Loading, PageHeader, ProgressBar } from '../components/ui'
import { applicationStatus, date, fromNow, money } from '../lib/format'

const TABS: (ApplicationStatus | 'ALL')[] = ['ALL', 'SAVING', 'QUALIFIED', 'PROCUREMENT', 'DELIVERED', 'REPAYING',
  'PAID_OFF', 'CANCELLED']

export function ApplicationsPage() {
  const { can } = useAuth()
  const officeName = useOfficeName()
  const [params, setParams] = useSearchParams()
  const status = (params.get('status') as ApplicationStatus | null) ?? undefined
  const [officeId, setOfficeId] = useState<number>()
  const [text, setText] = useState('')
  const { data, isPending, error } = useApplications({ status, officeId })

  const rows = useMemo(() => {
    const q = text.trim().toLowerCase()
    if (!q) return data ?? []
    return (data ?? []).filter((a) =>
      [a.memberName, a.reference, a.assetName, String(a.clientId)].some((v) => v.toLowerCase().includes(q)))
  }, [data, text])

  return (
    <>
      <PageHeader
        title="Applications"
        actions={can('OFFICER') && (
          <Link className="btn btn-primary" to="/applications/new">
            New application
          </Link>
        )}
      />
      <div className="tabs" role="tablist" aria-label="Status">
        {TABS.map((t) => {
          const active = (t === 'ALL' && !status) || t === status
          return (
            <button key={t} role="tab" aria-selected={active} className={active ? 'active' : ''}
              onClick={() => setParams(t === 'ALL' ? {} : { status: t })}>
              {t === 'ALL' ? 'All' : applicationStatus[t].label}
            </button>
          )
        })}
      </div>
      <div className="toolbar">
        <input type="search" placeholder="Search member, reference or asset" value={text}
          onChange={(e) => setText(e.target.value)} aria-label="Search applications" />
        <OfficeFilter value={officeId} onChange={setOfficeId} />
      </div>
      <ErrorBox error={error} />
      {isPending ? (
        <Loading />
      ) : rows.length === 0 ? (
        <Empty>No applications here yet.</Empty>
      ) : (
        <div className="table-wrap">
          <table className="table table-cards">
            <thead>
              <tr>
                <th>Member</th>
                <th>Asset</th>
                <th>Deposit</th>
                <th>Expected</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((a) => (
                <tr key={a.id}>
                  <td data-label="Member">
                    <Link to={`/applications/${a.id}`} className="strong">
                      {a.memberName}
                    </Link>
                    <div className="muted small">
                      {a.reference} · {officeName(a.officeId)}
                    </div>
                  </td>
                  <td data-label="Asset">
                    {a.assetName}
                    <div className="muted small">{money(a.assetCost, a.currency)}</div>
                  </td>
                  <td data-label="Deposit" className="progress-cell">
                    <DepositCell app={a} />
                  </td>
                  <td data-label="Expected">
                    {a.status === 'SAVING' && a.estimatedTargetDate ? (
                      <>
                        {date(a.estimatedTargetDate)}
                        <div className="muted small">{fromNow(a.estimatedTargetDate)}</div>
                      </>
                    ) : (
                      <span className="muted">—</span>
                    )}
                  </td>
                  <td data-label="Status">
                    <Badge tone={applicationStatus[a.status].tone}>{applicationStatus[a.status].label}</Badge>
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

/** Progress while saving; afterwards, how the price was paid. */
function DepositCell({ app: a }: { app: Application }) {
  if (a.status === 'SAVING' || a.status === 'QUALIFIED') {
    return (
      <>
        <ProgressBar percent={a.percentComplete} />
        <div className="small">
          {money(a.depositedAmount, a.currency)} of {money(a.depositTarget, a.currency)} · {a.percentComplete}%
        </div>
      </>
    )
  }
  if (a.depositApplied !== null) {
    return (
      <div className="small">
        {money(a.depositApplied, a.currency)} deposit
        <div className="muted">{money(a.financedAmount, a.currency)} loan</div>
      </div>
    )
  }
  if (a.status === 'CANCELLED') return <span className="muted">—</span>
  return <div className="small">{money(a.depositedAmount, a.currency)} held</div>
}
