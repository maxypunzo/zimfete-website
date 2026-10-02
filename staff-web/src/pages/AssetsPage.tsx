import { useState } from 'react'
import { Link } from 'react-router-dom'

import { useAssets, useOfficeName } from '../api/hooks'
import type { Ownership } from '../api/types'
import { OfficeFilter } from '../components/OfficeFilter'
import { Badge, Empty, ErrorBox, Loading, PageHeader } from '../components/ui'
import { date, ownershipLabel } from '../lib/format'

export function AssetsPage() {
  const officeName = useOfficeName()
  const [ownership, setOwnership] = useState<Ownership>()
  const [officeId, setOfficeId] = useState<number>()
  const { data, isPending, error } = useAssets({ ownership, officeId })

  return (
    <>
      <PageHeader title="Asset register" subtitle="Every asset ZimFete has financed, where it is and who owns it." />
      <div className="toolbar">
        <label className="inline-field">
          <span>Ownership</span>
          <select value={ownership ?? ''} onChange={(e) => setOwnership((e.target.value || undefined) as Ownership)}>
            <option value="">All</option>
            {(Object.keys(ownershipLabel) as Ownership[]).map((o) => (
              <option key={o} value={o}>{ownershipLabel[o].label}</option>
            ))}
          </select>
        </label>
        <OfficeFilter value={officeId} onChange={setOfficeId} />
      </div>
      <ErrorBox error={error} />
      {isPending ? <Loading /> : !data?.length ? <Empty>No assets delivered yet.</Empty> : (
        <div className="table-wrap">
          <table className="table table-cards">
            <thead>
              <tr>
                <th>Asset</th>
                <th>Member</th>
                <th>Serial</th>
                <th>Delivered</th>
                <th>Ownership</th>
              </tr>
            </thead>
            <tbody>
              {data.map((a) => (
                <tr key={a.id}>
                  <td data-label="Asset">
                    <Link to={`/assets/${a.id}`} className="strong">{a.assetName}</Link>
                  </td>
                  <td data-label="Member">
                    {a.memberName}
                    <div className="muted small">{officeName(a.officeId)}</div>
                  </td>
                  <td data-label="Serial">{a.serialNumber ?? '—'}</td>
                  <td data-label="Delivered">{date(a.deliveredOn)}</td>
                  <td data-label="Ownership">
                    <Badge tone={ownershipLabel[a.ownership].tone}>{ownershipLabel[a.ownership].label}</Badge>
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
