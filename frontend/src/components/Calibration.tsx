import { useState } from 'react'
import { ChevronDown } from 'lucide-react'
import type { Calibration as CalibrationRow } from '@/api'
import { Card, CardContent } from '@/components/ui/card'
import { cn } from '@/lib/utils'

/**
 * The evidence that will one day replace the decay guess — and currently contradicts it.
 *
 * <p>Nothing reads this to make a decision, and nothing should until there is more of it. It is
 * shown because a tool that states a number as a guess owes the reader whatever it knows about
 * whether the guess is any good.
 *
 * <p>Average problem rating sits beside the clean-solve rate deliberately. The obvious way to
 * explain a rate that does not fall with the gap is "they came back to easier problems"; the
 * reader has to be able to check that rather than take either reading on trust.
 */
export function CalibrationPanel({
  rows,
  halfLifeDays,
}: {
  rows: CalibrationRow[]
  halfLifeDays: number
}) {
  const [open, setOpen] = useState(false)
  if (rows.length === 0) return null

  const total = rows.reduce((sum, r) => sum + r.returns, 0)
  const falls = declines(rows)

  return (
    <Card className="mb-4">
      <button
        onClick={() => setOpen((o) => !o)}
        className="flex w-full items-center justify-between px-5 py-3 text-left"
      >
        <span className="text-sm text-[var(--color-muted)]">
          Is the {halfLifeDays}-day half-life right? {total} returns recorded so far
          {!falls && total > 0 && (
            <span className="ml-2 text-[var(--color-decayed)]">
              — the evidence currently says no
            </span>
          )}
        </span>
        <ChevronDown
          className={cn(
            'size-4 shrink-0 text-[var(--color-muted)] transition-transform',
            open && 'rotate-180',
          )}
        />
      </button>

      {open && (
        <CardContent className="border-t border-[var(--color-line)] pt-4">
          <p className="mb-3 text-sm leading-relaxed text-[var(--color-muted)]">
            Every time you return to a technique after a gap of 30 days or more, this records how
            long you were away and whether your first submission passed. If forgetting worked the
            way the model assumes, the clean-solve rate would fall as the gap grows.
          </p>

          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs tracking-wide text-[var(--color-muted)] uppercase">
                  <th className="pb-2 pr-4 font-medium">gap</th>
                  <th className="pb-2 pr-4 font-medium">returns</th>
                  <th className="pb-2 pr-4 font-medium">clean first try</th>
                  <th className="pb-2 pr-4 font-medium">avg attempts</th>
                  <th className="pb-2 font-medium">avg problem rating</th>
                </tr>
              </thead>
              <tbody className="font-mono">
                {rows.map((row) => (
                  <tr key={row.gapBucket} className="border-t border-[var(--color-line)]">
                    <td className="py-2 pr-4 text-[var(--color-body)]">{row.gapBucket}</td>
                    <td className="py-2 pr-4">{row.returns}</td>
                    <td className="py-2 pr-4 text-[var(--color-body)]">
                      {Math.round((row.solvedFirstTry / row.returns) * 100)}%
                    </td>
                    <td className="py-2 pr-4">{Number(row.averageAttempts).toFixed(2)}</td>
                    <td className="py-2">{row.averageProblemRating ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <p className="mt-3 text-sm leading-relaxed text-[var(--color-muted)]">
            The last column is the obvious objection: a rate that holds up might just mean easier
            problems on return. It is shown so you can check rather than assume. Nothing here feeds
            the queue — it accumulates until there is enough to fit a real half-life, which is the
            point at which the guess above can be thrown away.
          </p>
        </CardContent>
      )}
    </Card>
  )
}

/** Whether the clean-solve rate actually falls as the gap grows, as the model assumes it should. */
function declines(rows: CalibrationRow[]): boolean {
  const rates = rows.map((r) => r.solvedFirstTry / r.returns)
  return rates.every((rate, i) => i === 0 || rate <= rates[i - 1] + 0.01)
}
