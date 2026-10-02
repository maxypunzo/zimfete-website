import { useAuth } from '../auth/AuthContext'
import { useOffices } from '../api/hooks'

/** Location picker, only for Head Office users (officers always see their own location). */
export function OfficeFilter({ value, onChange }: { value?: number; onChange: (id?: number) => void }) {
  const { me } = useAuth()
  const { data: offices } = useOffices()
  if (!me.allOffices) return null
  return (
    <label className="inline-field">
      <span>Location</span>
      <select value={value ?? ''} onChange={(e) => onChange(e.target.value ? Number(e.target.value) : undefined)}>
        <option value="">All locations</option>
        {offices?.filter((o) => o.id !== me.officeId).map((o) => (
          <option key={o.id} value={o.id}>
            {o.name}
          </option>
        ))}
      </select>
    </label>
  )
}
