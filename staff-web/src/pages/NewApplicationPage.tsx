import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'

import { actions, useAction, useCatalogue, useMemberSearch, useQuotes } from '../api/hooks'
import type { CatalogueItem, Member } from '../api/types'
import { useToast } from '../components/Toast'
import { Empty, ErrorBox, Loading, PageHeader } from '../components/ui'
import { date, money, today } from '../lib/format'

export function NewApplicationPage() {
  const navigate = useNavigate()
  const toast = useToast()
  const [text, setText] = useState('')
  const [debounced, setDebounced] = useState('')
  const [member, setMember] = useState<Member | null>(null)
  const [item, setItem] = useState<CatalogueItem | null>(null)
  const [quoteId, setQuoteId] = useState<number>()

  useEffect(() => {
    const t = setTimeout(() => setDebounced(text), 300)
    return () => clearTimeout(t)
  }, [text])

  const members = useMemberSearch(debounced)
  const catalogue = useCatalogue()
  const quotes = useQuotes(item?.id)
  const validQuotes = (quotes.data ?? []).filter((q) => q.quoteDate <= today() && q.validUntil >= today())
  const quote = validQuotes.find((q) => q.id === quoteId)
  const cost = quote?.price ?? item?.standardCost ?? 0

  const open = useAction(actions.openApplication)

  function submit() {
    if (!member || !item) return
    open.mutate(
      { clientId: member.id, catalogueItemId: item.id, quoteId },
      {
        onSuccess: (app) => {
          toast(`Application ${app.reference} opened and deposit account created`)
          navigate(`/applications/${app.id}`)
        },
      },
    )
  }

  return (
    <>
      <PageHeader title="New application"
        subtitle="Opens an Asset Deposit Account for the member in Mifos. The account opening fee is charged there." />

      <section className="card step">
        <h2>
          <span className="step-no">1</span> Member
        </h2>
        {member ? (
          <div className="chosen">
            <div>
              <strong>{member.displayName}</strong>
              <div className="muted small">
                Account {member.accountNo} · {member.officeName}
              </div>
            </div>
            <button className="btn btn-ghost btn-sm" onClick={() => setMember(null)}>
              Change
            </button>
          </div>
        ) : (
          <>
            <input type="search" placeholder="Search by name or account number" value={text} autoFocus
              onChange={(e) => setText(e.target.value)} aria-label="Search members" />
            {debounced.trim().length >= 2 && (
              members.isPending ? <Loading what="Searching" /> : members.error ? <ErrorBox error={members.error} /> :
                members.data?.length ? (
                  <ul className="pick-list">
                    {members.data.map((m) => (
                      <li key={m.id}>
                        <button disabled={!m.active} onClick={() => setMember(m)}>
                          <strong>{m.displayName}</strong>
                          <span className="muted small">
                            {m.accountNo} · {m.officeName}
                            {!m.active && ' · not active'}
                          </span>
                        </button>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <Empty>No members found. Members must first be registered in Mifos.</Empty>
                )
            )}
          </>
        )}
      </section>

      <section className="card step">
        <h2>
          <span className="step-no">2</span> Asset
        </h2>
        {catalogue.isPending ? (
          <Loading />
        ) : (
          <div className="asset-grid">
            {catalogue.data?.map((i) => (
              <button key={i.id} className={`asset-option${item?.id === i.id ? ' selected' : ''}`}
                aria-pressed={item?.id === i.id}
                onClick={() => {
                  setItem(i)
                  setQuoteId(undefined)
                }}>
                <strong>{i.name}</strong>
                <span className="muted small">{i.description}</span>
                <span className="asset-price">{money(i.standardCost, i.currency)}</span>
              </button>
            ))}
          </div>
        )}
        {item && validQuotes.length > 0 && (
          <label className="field">
            <span className="field-label">Price from</span>
            <select value={quoteId ?? ''} onChange={(e) => setQuoteId(e.target.value ? Number(e.target.value) : undefined)}>
              <option value="">Standard price ({money(item.standardCost, item.currency)})</option>
              {validQuotes.map((q) => (
                <option key={q.id} value={q.id}>
                  {q.supplierName}: {money(q.price, q.currency)} (valid to {date(q.validUntil)})
                </option>
              ))}
            </select>
          </label>
        )}
      </section>

      {member && item && (
        <section className="card step summary">
          <h2>
            <span className="step-no">3</span> Confirm
          </h2>
          <dl className="facts">
            <div>
              <dt>Asset cost (locked)</dt>
              <dd>{money(cost, item.currency)}</dd>
            </div>
            <div>
              <dt>Deposit needed (50%)</dt>
              <dd className="strong">{money(Math.round(cost * 50) / 100, item.currency)}</dd>
            </div>
            <div>
              <dt>Financed after delivery</dt>
              <dd>{money(cost - Math.round(cost * 50) / 100, item.currency)} as a loan</dd>
            </div>
          </dl>
          <ErrorBox error={open.error} />
          <button className="btn btn-primary" disabled={open.isPending} onClick={submit}>
            {open.isPending ? 'Opening…' : `Open application for ${member.displayName}`}
          </button>
        </section>
      )}
    </>
  )
}
