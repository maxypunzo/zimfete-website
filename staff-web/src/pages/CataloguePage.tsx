import { useState, type FormEvent } from 'react'

import { useAuth } from '../auth/AuthContext'
import { actions, useAction, useCatalogue, useQuotes, useSuppliers } from '../api/hooks'
import type { AssetCategory, CatalogueItem, Supplier } from '../api/types'
import { useToast } from '../components/Toast'
import { Badge, Empty, ErrorBox, Field, Loading, Modal, PageHeader } from '../components/ui'
import { date, money, today } from '../lib/format'

const CATEGORIES: AssetCategory[] = ['BOREHOLE', 'TRACTOR', 'IRRIGATION', 'SOLAR', 'VEHICLE', 'IMPLEMENT', 'OTHER']
const label = (c: string) => c.charAt(0) + c.slice(1).toLowerCase()

export function CataloguePage() {
  const { can } = useAuth()
  const [tab, setTab] = useState<'items' | 'suppliers'>('items')
  return (
    <>
      <PageHeader title="Catalogue" subtitle={can('ADMIN') ? undefined : 'Only administrators can change the catalogue.'} />
      <div className="tabs" role="tablist">
        <button role="tab" aria-selected={tab === 'items'} className={tab === 'items' ? 'active' : ''}
          onClick={() => setTab('items')}>Assets</button>
        <button role="tab" aria-selected={tab === 'suppliers'} className={tab === 'suppliers' ? 'active' : ''}
          onClick={() => setTab('suppliers')}>Suppliers</button>
      </div>
      {tab === 'items' ? <Items /> : <Suppliers />}
    </>
  )
}

function Items() {
  const { can } = useAuth()
  const { data, isPending, error } = useCatalogue(true)
  const [editing, setEditing] = useState<CatalogueItem | 'new' | null>(null)
  const [quotesFor, setQuotesFor] = useState<CatalogueItem | null>(null)
  return (
    <>
      {can('ADMIN') && (
        <div className="toolbar">
          <button className="btn btn-primary" onClick={() => setEditing('new')}>Add asset</button>
        </div>
      )}
      <ErrorBox error={error} />
      {isPending ? <Loading /> : !data?.length ? <Empty>No assets in the catalogue yet.</Empty> : (
        <div className="table-wrap">
          <table className="table table-cards">
            <thead>
              <tr>
                <th>Asset</th>
                <th>Category</th>
                <th className="num">Standard price</th>
                <th className="num">Deposit (50%)</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {data.map((i) => (
                <tr key={i.id} className={i.active ? '' : 'inactive'}>
                  <td data-label="Asset">
                    <strong>{i.name}</strong> {!i.active && <Badge tone="danger">Inactive</Badge>}
                    <div className="muted small">{i.code}{i.description ? ` · ${i.description}` : ''}</div>
                  </td>
                  <td data-label="Category">{label(i.category)}</td>
                  <td data-label="Price" className="num">{money(i.standardCost, i.currency)}</td>
                  <td data-label="Deposit" className="num">{money(i.standardCost / 2, i.currency)}</td>
                  <td className="row-actions">
                    <button className="btn btn-sm" onClick={() => setQuotesFor(i)}>Quotes</button>
                    {can('ADMIN') && <button className="btn btn-sm btn-ghost" onClick={() => setEditing(i)}>Edit</button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {editing && <ItemDialog item={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} />}
      {quotesFor && <QuotesDialog item={quotesFor} onClose={() => setQuotesFor(null)} />}
    </>
  )
}

function ItemDialog({ item, onClose }: { item?: CatalogueItem; onClose: () => void }) {
  const toast = useToast()
  const [form, setForm] = useState({
    code: item?.code ?? '',
    name: item?.name ?? '',
    category: item?.category ?? ('BOREHOLE' as AssetCategory),
    description: item?.description ?? '',
    standardCost: item ? String(item.standardCost) : '',
    currency: item?.currency ?? 'USD',
    active: item?.active ?? true,
  })
  const save = useAction(() => actions.saveItem(item?.id, {
    ...form,
    description: form.description || null,
    standardCost: Number(form.standardCost),
  }))
  const set = (k: keyof typeof form) => (e: { target: { value: string } }) => setForm({ ...form, [k]: e.target.value })

  function submit(e: FormEvent) {
    e.preventDefault()
    save.mutate(undefined, { onSuccess: () => { toast('Catalogue saved'); onClose() } })
  }

  return (
    <Modal open title={item ? `Edit ${item.name}` : 'Add asset'} onClose={onClose}>
      <form className="form" onSubmit={submit}>
        <div className="row">
          <Field label="Code"><input required value={form.code} onChange={set('code')} disabled={!!item} /></Field>
          <Field label="Category">
            <select value={form.category} onChange={set('category')}>
              {CATEGORIES.map((c) => <option key={c} value={c}>{label(c)}</option>)}
            </select>
          </Field>
        </div>
        <Field label="Name"><input required value={form.name} onChange={set('name')} /></Field>
        <Field label="Description"><textarea rows={2} value={form.description} onChange={set('description')} /></Field>
        <div className="row">
          <Field label="Standard price" hint="Existing applications keep their locked price.">
            <input required type="number" min="0.01" step="0.01" value={form.standardCost} onChange={set('standardCost')} />
          </Field>
          <Field label="Currency">
            <select value={form.currency} onChange={set('currency')}><option>USD</option></select>
          </Field>
        </div>
        {item && (
          <label className="check">
            <input type="checkbox" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} />
            Available for new applications
          </label>
        )}
        <ErrorBox error={save.error} />
        <div className="form-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>Back</button>
          <button className="btn btn-primary" disabled={save.isPending}>Save</button>
        </div>
      </form>
    </Modal>
  )
}

function QuotesDialog({ item, onClose }: { item: CatalogueItem; onClose: () => void }) {
  const { can } = useAuth()
  const toast = useToast()
  const quotes = useQuotes(item.id)
  const suppliers = useSuppliers()
  const [supplierId, setSupplierId] = useState('')
  const [price, setPrice] = useState('')
  const [validUntil, setValidUntil] = useState('')
  const [reference, setReference] = useState('')
  const add = useAction(() => actions.addQuote(item.id, {
    supplierId: Number(supplierId), price: Number(price), quoteDate: today(), validUntil, reference: reference || undefined,
  }))

  return (
    <Modal open title={`Quotes: ${item.name}`} onClose={onClose}>
      <div className="form">
        {quotes.isPending ? <Loading /> : quotes.data?.length ? (
          <ul className="order-list">
            {quotes.data.map((q) => {
              const valid = q.quoteDate <= today() && q.validUntil >= today()
              return (
                <li key={q.id}>
                  <div className="order-head">
                    <strong>{q.supplierName}: {money(q.price, q.currency)}</strong>
                    <Badge tone={valid ? 'success' : 'neutral'}>{valid ? 'Valid' : 'Expired'}</Badge>
                  </div>
                  <div className="muted small">
                    {q.reference ?? 'No reference'} · {date(q.quoteDate)} to {date(q.validUntil)}
                  </div>
                </li>
              )
            })}
          </ul>
        ) : <p className="muted">No quotes yet.</p>}

        {can('ADMIN') && (
          <form className="form subform" onSubmit={(e) => {
            e.preventDefault()
            add.mutate(undefined, {
              onSuccess: () => {
                toast('Quote added')
                setPrice('')
                setReference('')
              },
            })
          }}>
            <h3>Add a quote</h3>
            <Field label="Supplier">
              <select required value={supplierId} onChange={(e) => setSupplierId(e.target.value)}>
                <option value="">Choose</option>
                {suppliers.data?.filter((s) => s.active).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
              </select>
            </Field>
            <div className="row">
              <Field label={`Price (${item.currency})`}>
                <input required type="number" min="0.01" step="0.01" value={price} onChange={(e) => setPrice(e.target.value)} />
              </Field>
              <Field label="Valid until">
                <input required type="date" min={today()} value={validUntil} onChange={(e) => setValidUntil(e.target.value)} />
              </Field>
            </div>
            <Field label="Supplier's quote number">
              <input value={reference} onChange={(e) => setReference(e.target.value)} />
            </Field>
            <ErrorBox error={add.error} />
            <div className="form-actions">
              <button className="btn btn-primary" disabled={add.isPending}>Add quote</button>
            </div>
          </form>
        )}
      </div>
    </Modal>
  )
}

function Suppliers() {
  const { can } = useAuth()
  const { data, isPending, error } = useSuppliers()
  const [editing, setEditing] = useState<Supplier | 'new' | null>(null)
  return (
    <>
      {can('ADMIN') && (
        <div className="toolbar">
          <button className="btn btn-primary" onClick={() => setEditing('new')}>Add supplier</button>
        </div>
      )}
      <ErrorBox error={error} />
      {isPending ? <Loading /> : !data?.length ? <Empty>No suppliers yet.</Empty> : (
        <div className="table-wrap">
          <table className="table table-cards">
            <thead>
              <tr><th>Supplier</th><th>Phone</th><th>Email</th><th>Address</th><th /></tr>
            </thead>
            <tbody>
              {data.map((s) => (
                <tr key={s.id} className={s.active ? '' : 'inactive'}>
                  <td data-label="Supplier"><strong>{s.name}</strong> {!s.active && <Badge tone="danger">Inactive</Badge>}</td>
                  <td data-label="Phone">{s.phone ?? '—'}</td>
                  <td data-label="Email">{s.email ?? '—'}</td>
                  <td data-label="Address">{s.address ?? '—'}</td>
                  <td className="row-actions">
                    {can('ADMIN') && <button className="btn btn-sm btn-ghost" onClick={() => setEditing(s)}>Edit</button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {editing && <SupplierDialog supplier={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} />}
    </>
  )
}

function SupplierDialog({ supplier, onClose }: { supplier?: Supplier; onClose: () => void }) {
  const toast = useToast()
  const [form, setForm] = useState({
    name: supplier?.name ?? '',
    phone: supplier?.phone ?? '',
    email: supplier?.email ?? '',
    address: supplier?.address ?? '',
    active: supplier?.active ?? true,
  })
  const save = useAction(() => actions.saveSupplier(supplier?.id, {
    name: form.name,
    phone: form.phone || null,
    email: form.email || null,
    address: form.address || null,
    active: form.active,
  }))
  const set = (k: keyof typeof form) => (e: { target: { value: string } }) => setForm({ ...form, [k]: e.target.value })
  return (
    <Modal open title={supplier ? `Edit ${supplier.name}` : 'Add supplier'} onClose={onClose}>
      <form className="form" onSubmit={(e) => {
        e.preventDefault()
        save.mutate(undefined, { onSuccess: () => { toast('Supplier saved'); onClose() } })
      }}>
        <Field label="Name"><input required value={form.name} onChange={set('name')} /></Field>
        <div className="row">
          <Field label="Phone"><input type="tel" value={form.phone} onChange={set('phone')} /></Field>
          <Field label="Email"><input type="email" value={form.email} onChange={set('email')} /></Field>
        </div>
        <Field label="Address"><input value={form.address} onChange={set('address')} /></Field>
        {supplier && (
          <label className="check">
            <input type="checkbox" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} />
            Active
          </label>
        )}
        <ErrorBox error={save.error} />
        <div className="form-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>Back</button>
          <button className="btn btn-primary" disabled={save.isPending}>Save</button>
        </div>
      </form>
    </Modal>
  )
}
