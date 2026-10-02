import { describe, expect, it } from 'vitest'

import { date, fromNow, money } from './format'

describe('money', () => {
  it('formats US dollars with cents', () => {
    expect(money(4000)).toMatch(/4,000\.00/)
  })
  it('shows a dash for missing amounts', () => {
    expect(money(null)).toBe('—')
  })
})

describe('date', () => {
  it('formats ISO dates without shifting the day', () => {
    expect(date('2026-10-01')).toBe('1 Oct 2026')
  })
})

describe('fromNow', () => {
  const now = new Date('2026-10-01T09:00:00')
  it('describes near dates in days', () => {
    expect(fromNow('2026-10-05', now)).toBe('in 4 days')
  })
  it('describes far dates in months', () => {
    expect(fromNow('2027-02-01', now)).toBe('in about 4 months')
  })
  it('treats past dates as now', () => {
    expect(fromNow('2026-09-01', now)).toBe('now')
  })
})
