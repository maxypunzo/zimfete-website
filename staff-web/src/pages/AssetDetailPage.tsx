import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'

import { useAuth } from '../auth/AuthContext'
import { actions, useAction, useAsset, useInspections, useOfficeName, usePhotos } from '../api/hooks'
import type { AssetCondition, FinancedAsset } from '../api/types'
import { useToast } from '../components/Toast'
import { Badge, Empty, ErrorBox, Facts, Field, Loading, Modal, PageHeader, mapLink, useLocation } from '../components/ui'
import { conditionLabel, date, dateTime, ownershipLabel, today } from '../lib/format'
import { ReasonDialog } from './applicationDialogs'

export function AssetDetailPage() {
  const id = Number(useParams().id)
  const { data, isPending, error } = useAsset(id)
  if (isPending) return <Loading />
  if (error || !data) return <ErrorBox error={error ?? 'Asset not found'} />
  return <AssetDetail asset={data} />
}

function AssetDetail({ asset }: { asset: FinancedAsset }) {
  const { can } = useAuth()
  const toast = useToast()
  const officeName = useOfficeName()
  const photos = usePhotos(asset.id)
  const inspections = useInspections(asset.id)
  const [inspecting, setInspecting] = useState(false)
  const [repossessing, setRepossessing] = useState(false)
  const upload = useAction((file: File) => actions.uploadPhoto(asset.id, file, 'INSPECTION'))
  const repossess = useAction((reason: string) => actions.repossess(asset.id, reason))
  const own = ownershipLabel[asset.ownership]

  return (
    <>
      <PageHeader title={asset.assetName}
        subtitle={<>{asset.memberName} · {officeName(asset.officeId)}</>}
        actions={<Badge tone={own.tone}>{own.label}</Badge>} />
      <div className="grid-2">
        <div className="stack">
          <section className="card">
            <h2>Details</h2>
            <Facts items={[
              ['Application', <Link key="a" to={`/applications/${asset.applicationId}`}>{asset.applicationReference}</Link>],
              ['Serial number', asset.serialNumber ?? '—'],
              ['Delivered', `${date(asset.deliveredOn)} by ${asset.deliveredBy}`],
              ['Location', <a key="m" href={mapLink(asset.latitude, asset.longitude)} target="_blank" rel="noreferrer">
                {asset.latitude}, {asset.longitude} (map)</a>],
              ['Ownership since', dateTime(asset.ownershipChangedAt)],
              ...(asset.ownershipNote ? [['Ownership note', asset.ownershipNote] as [string, string]] : []),
              ...(asset.notes ? [['Delivery notes', asset.notes] as [string, string]] : []),
            ]} />
            {can('MANAGER') && asset.ownership === 'SACCO_OWNED' && (
              <div className="button-row">
                <button className="btn btn-sm btn-danger-ghost" onClick={() => setRepossessing(true)}>
                  Record repossession
                </button>
              </div>
            )}
          </section>

          <section className="card">
            <div className="card-head">
              <h2>Inspections</h2>
              {can('OFFICER') && (
                <button className="btn btn-sm" onClick={() => setInspecting(true)}>Add inspection</button>
              )}
            </div>
            {inspections.isPending ? <Loading /> : inspections.data?.length ? (
              <ul className="order-list">
                {inspections.data.map((i) => (
                  <li key={i.id}>
                    <div className="order-head">
                      <strong>{date(i.inspectedOn)}</strong>
                      <Badge tone={conditionLabel[i.condition].tone}>{conditionLabel[i.condition].label}</Badge>
                    </div>
                    <div className="muted small">by {i.inspectedBy}</div>
                    {i.notes && <p>{i.notes}</p>}
                  </li>
                ))}
              </ul>
            ) : <p className="muted">No inspections yet.</p>}
          </section>
        </div>

        <section className="card">
          <div className="card-head">
            <h2>Photos</h2>
            {can('OFFICER') && (
              <label className="btn btn-sm">
                {upload.isPending ? 'Uploading…' : 'Add photo'}
                <input type="file" accept="image/jpeg,image/png,image/webp" capture="environment" hidden
                  onChange={(e) => {
                    const file = e.target.files?.[0]
                    if (file) upload.mutate(file, { onSuccess: () => toast('Photo added') })
                    e.target.value = ''
                  }} />
              </label>
            )}
          </div>
          <ErrorBox error={upload.error} />
          {photos.isPending ? <Loading /> : photos.data?.length ? (
            <div className="photos">
              {photos.data.map((p) => (
                <a key={p.id} href={`/api/assets/${asset.id}/photos/${p.id}`} target="_blank" rel="noreferrer">
                  <img src={`/api/assets/${asset.id}/photos/${p.id}`} alt={`${p.kind.toLowerCase()} photo`} loading="lazy" />
                  <span className="small muted">{p.kind === 'DELIVERY' ? 'Delivery' : 'Inspection'} · {date(p.uploadedAt)}</span>
                </a>
              ))}
            </div>
          ) : <Empty>No photos yet.</Empty>}
        </section>
      </div>

      <InspectionDialog assetId={asset.id} open={inspecting} onClose={() => setInspecting(false)} />
      <ReasonDialog open={repossessing} title="Record repossession" confirm="Record repossession"
        intro="Records that ZimFete has taken the asset back. Recover or write off the loan balance in Mifos."
        onClose={() => setRepossessing(false)} pending={repossess.isPending} error={repossess.error}
        onConfirm={(reason) => repossess.mutate(reason, {
          onSuccess: () => {
            toast('Repossession recorded')
            setRepossessing(false)
          },
        })} />
    </>
  )
}

function InspectionDialog({ assetId, open, onClose }: { assetId: number; open: boolean; onClose: () => void }) {
  const toast = useToast()
  const location = useLocation()
  const [inspectedOn, setInspectedOn] = useState(today())
  const [condition, setCondition] = useState<AssetCondition>('GOOD')
  const [notes, setNotes] = useState('')
  const save = useAction(() => actions.inspect(assetId, {
    inspectedOn,
    condition,
    latitude: location.position?.latitude,
    longitude: location.position?.longitude,
    notes: notes || undefined,
  }))

  function submit(e: FormEvent) {
    e.preventDefault()
    save.mutate(undefined, {
      onSuccess: () => {
        toast('Inspection saved')
        onClose()
      },
    })
  }

  return (
    <Modal open={open} title="Add inspection" onClose={onClose}>
      <form className="form" onSubmit={submit}>
        <div className="row">
          <Field label="Date">
            <input type="date" required max={today()} value={inspectedOn} onChange={(e) => setInspectedOn(e.target.value)} />
          </Field>
          <Field label="Condition">
            <select value={condition} onChange={(e) => setCondition(e.target.value as AssetCondition)}>
              {(Object.keys(conditionLabel) as AssetCondition[]).map((c) => (
                <option key={c} value={c}>{conditionLabel[c].label}</option>
              ))}
            </select>
          </Field>
        </div>
        <Field label="Location (optional)" hint={location.position
          ? `${location.position.latitude}, ${location.position.longitude} (about ${location.position.accuracy} m)`
          : location.error}>
          <button type="button" className="btn" onClick={location.locate} disabled={location.busy}>
            {location.busy ? 'Locating…' : location.position ? 'Update location' : 'Use my location'}
          </button>
        </Field>
        <Field label="Notes">
          <textarea rows={3} value={notes} onChange={(e) => setNotes(e.target.value)} />
        </Field>
        <ErrorBox error={save.error} />
        <div className="form-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>Back</button>
          <button className="btn btn-primary" disabled={save.isPending}>Save inspection</button>
        </div>
      </form>
    </Modal>
  )
}
