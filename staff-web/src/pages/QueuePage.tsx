import { useState } from 'react'
import { Link } from 'react-router-dom'

import { useForecast, useOfficeName, useQueue } from '../api/hooks'
import { OfficeFilter } from '../components/OfficeFilter'
import { Empty, ErrorBox, Loading, PageHeader, Stat } from '../components/ui'
import { date, money } from '../lib/format'

export function QueuePage() {
  const officeName = useOfficeName()
  const [officeId, setOfficeId] = useState<number>()
  const [days, setDays] = useState(60)
  const queue = useQueue(officeId)
  const forecast = useForecast(days, officeId)
  const usd = forecast.data?.byCurrency.USD

  return (
    <>
      <PageHeader title="Queue & cash needed"
        subtitle="Members who reached 50%, in the order they will be served. One queue for all locations." />
      <div className="toolbar">
        <OfficeFilter value={officeId} onChange={setOfficeId} />
        <label className="inline-field">
          <span>Forecast</span>
          <select value={days} onChange={(e) => setDays(Number(e.target.value))}>
            <option value={30}>Next 30 days</option>
            <option value={60}>Next 60 days</option>
            <option value={90}>Next 90 days</option>
            <option value={180}>Next 6 months</option>
          </select>
        </label>
      </div>
      <ErrorBox error={queue.error || forecast.error} />

      <section className="stats">
        <Stat label="Cash needed now" value={money(usd?.cashNeededNow ?? 0)} sub={`${usd?.inQueue ?? 0} in the queue`}
          tone="info" />
        <Stat label={`Expected within ${days} days`} value={money(usd?.expectedCash ?? 0)}
          sub={`${usd?.expectedToQualify ?? 0} members likely to reach 50%`} />
        <Stat label="Total to plan for" value={money(usd?.totalCashNeeded ?? 0)}
          sub={forecast.data ? `up to ${date(forecast.data.horizon)}` : undefined} />
      </section>
      <p className="muted small">
        Before approving new soft loans, compare this with the cash available. The member's deposit is part of the
        lending pool, so the full asset price must be available when they are served.
      </p>

      <section className="card">
        <h2>Waiting queue</h2>
        {queue.isPending ? <Loading /> : queue.data?.length ? (
          <div className="table-wrap">
            <table className="table table-cards">
              <thead>
                <tr>
                  <th>#</th>
                  <th>Member</th>
                  <th>Asset</th>
                  <th>Reached 50%</th>
                  <th className="num">Waiting</th>
                  <th className="num">Price</th>
                  <th className="num">Running total</th>
                </tr>
              </thead>
              <tbody>
                {queue.data.map((q) => (
                  <tr key={q.applicationId}>
                    <td data-label="Position" className="strong">{q.position}</td>
                    <td data-label="Member">
                      <Link to={`/applications/${q.applicationId}`}>{q.memberName}</Link>
                      <div className="muted small">{q.reference} · {officeName(q.officeId)}</div>
                    </td>
                    <td data-label="Asset">{q.assetName}</td>
                    <td data-label="Reached 50%">{date(q.qualifiedOn)}</td>
                    <td data-label="Waiting" className="num">{q.daysWaiting} days</td>
                    <td data-label="Price" className="num">{money(q.assetCost, q.currency)}</td>
                    <td data-label="Running total" className="num">{money(q.cumulativeCashNeeded, q.currency)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <Empty>Nobody is waiting right now.</Empty>
        )}
        {officeId !== undefined && (
          <p className="muted small">Positions are across all locations; only this location's members are shown.</p>
        )}
      </section>
    </>
  )
}
