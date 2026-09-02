import { useQuery } from '@tanstack/react-query'
import { api } from '@/api'

/**
 * How one technique has moved across snapshots.
 *
 * <p>Worth showing because decay is the one thing here that cannot be recomputed from today's
 * data: without the recorded history, a technique that has been sliding for six months looks
 * identical to one that went quiet last week.
 */
export function TechniqueHistory({ handle, techniqueId }: { handle: string; techniqueId: string }) {
  const history = useQuery({
    queryKey: ['history', handle, techniqueId],
    queryFn: () => api.history(handle, techniqueId),
  })

  if (history.isLoading) {
    return <p className="mt-3 text-xs text-[var(--color-muted)]">Loading history…</p>
  }

  const points = history.data ?? []
  if (points.length === 0) {
    return (
      <p className="mt-3 text-xs text-[var(--color-muted)]">
        No snapshots yet — Refresh records one. Decay is a function of elapsed time, so this only
        becomes interesting after a few.
      </p>
    )
  }

  return (
    <div className="mt-3 border-t border-[var(--color-line)] pt-3">
      <table className="w-full text-xs">
        <thead>
          <tr className="text-left tracking-wide text-[var(--color-muted)] uppercase">
            <th className="pb-1.5 pr-4 font-medium">snapshot</th>
            <th className="pb-1.5 pr-4 font-medium">solved</th>
            <th className="pb-1.5 pr-4 font-medium">days quiet</th>
            <th className="pb-1.5 pr-4 font-medium">retained</th>
            <th className="pb-1.5 font-medium">half-life used</th>
          </tr>
        </thead>
        <tbody className="font-mono text-[var(--color-muted)]">
          {points.map((point) => (
            <tr key={point.takenAt} className="border-t border-[var(--color-line)]">
              <td className="py-1.5 pr-4">{new Date(point.takenAt).toLocaleDateString()}</td>
              <td className="py-1.5 pr-4">{point.solvedCount}</td>
              <td className="py-1.5 pr-4">{point.daysSinceLast ?? '—'}</td>
              <td className="py-1.5 pr-4 text-[var(--color-body)]">
                {Math.round(Number(point.retention) * 100)}%
              </td>
              {/* Recorded per row, so revising the guess cannot rewrite what was observed. */}
              <td className="py-1.5">{point.halfLifeDays}d</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
