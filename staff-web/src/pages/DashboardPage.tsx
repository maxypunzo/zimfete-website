import { Link } from 'react-router-dom'

import { useAuth } from '../auth/AuthContext'
import { useApplications, useForecast, useOfficeName, usePurchaseOrders, useQueue } from '../api/hooks'
import { Empty, ErrorBox, Loading, PageHeader, Stat } from '../components/ui'
import { money } from '../lib/format'

export function DashboardPage() {
  const { me, can } = useAuth()
  const officeName = useOfficeName()
  const apps = useApplications()
  const queue = useQueue()
  const forecast = useForecast(60)
  const drafts = usePurchaseOrders({ status: 'DRAFT' })

  const count = (status: string) => apps.data?.filter((a) => a.status === status).length ?? 0
  const usd = forecast.data?.byCurrency.USD
  const scope = me.allOffices ? 'All locations' : officeName(me.officeId)

  return (
    <>
      <PageHeader
        title="Dashboard"
        subtitle={scope}
        actions={can('OFFICER') && (
          <Link className="btn btn-primary" to="/applications/new">
            New application
          </Link>
        )}
      />
      <ErrorBox error={apps.error || queue.error || forecast.error} />

      <section className="stats">
        <Stat label="In the queue" value={usd?.inQueue ?? 0} sub={`${money(usd?.cashNeededNow ?? 0)} needed now`}
          tone="info" />
        <Stat label="Expected in 60 days" value={usd?.expectedToQualify ?? 0}
          sub={`${money(usd?.expectedCash ?? 0)} more`} />
        <Stat label="Saving toward 50%" value={count('SAVING')} />
        <Stat label="Repaying" value={count('REPAYING')} />
      </section>

      {can('MANAGER') && (
        <section className="stats stats-attention">
          <Link to="/purchase-orders" className="stat-link">
            <Stat label="Orders awaiting approval" value={drafts.data?.length ?? 0}
              tone={drafts.data?.length ? 'warning' : undefined} />
          </Link>
          <Link to="/applications?status=DELIVERED" className="stat-link">
            <Stat label="Delivered, awaiting loan" value={count('DELIVERED')}
              tone={count('DELIVERED') ? 'warning' : undefined} />
          </Link>
          <Link to="/applications?status=PROCUREMENT" className="stat-link">
            <Stat label="Being procured" value={count('PROCUREMENT')} />
          </Link>
        </section>
      )}

      <div className="grid-2">
        <section className="card">
          <div className="card-head">
            <h2>Next in the queue</h2>
            <Link to="/queue">Full queue</Link>
          </div>
          {queue.isPending ? (
            <Loading />
          ) : queue.data?.length ? (
            <div className="card-scroll">
            <table className="table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>Member</th>
                  <th>Asset</th>
                  <th className="num">Waiting</th>
                </tr>
              </thead>
              <tbody>
                {queue.data.slice(0, 6).map((q) => (
                  <tr key={q.applicationId}>
                    <td>{q.position}</td>
                    <td>
                      <Link to={`/applications/${q.applicationId}`}>{q.memberName}</Link>
                      <div className="muted small">{officeName(q.officeId)}</div>
                    </td>
                    <td>{q.assetName}</td>
                    <td className="num">{q.daysWaiting} d</td>
                  </tr>
                ))}
              </tbody>
            </table>
            </div>
          ) : (
            <Empty>Nobody is waiting. Members join the queue when they reach 50%.</Empty>
          )}
        </section>

        <section className="card">
          <div className="card-head">
            <h2>Cash needed by location (60 days)</h2>
          </div>
          {forecast.isPending ? (
            <Loading />
          ) : usd?.byOffice.length ? (
            <div className="card-scroll">
            <table className="table">
              <thead>
                <tr>
                  <th>Location</th>
                  <th className="num">In queue</th>
                  <th className="num">Expected</th>
                  <th className="num">Cash</th>
                </tr>
              </thead>
              <tbody>
                {usd.byOffice.map((o) => (
                  <tr key={o.officeId}>
                    <td>{officeName(o.officeId)}</td>
                    <td className="num">{o.inQueue}</td>
                    <td className="num">{o.expectedToQualify}</td>
                    <td className="num">{money(o.queueCash + o.expectedCash)}</td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <td>Total</td>
                  <td className="num">{usd.inQueue}</td>
                  <td className="num">{usd.expectedToQualify}</td>
                  <td className="num">{money(usd.totalCashNeeded)}</td>
                </tr>
              </tfoot>
            </table>
            </div>
          ) : (
            <Empty>No cash needed in the next 60 days.</Empty>
          )}
          <p className="muted small">
            Full asset cost per member: the supplier is paid in full, and the member's deposit is in the lending pool.
          </p>
        </section>
      </div>
    </>
  )
}
