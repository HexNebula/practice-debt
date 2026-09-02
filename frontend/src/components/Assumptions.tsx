import { useState } from 'react'
import { ChevronDown } from 'lucide-react'
import type { Policies } from '@/api'
import { Card, CardContent } from '@/components/ui/card'
import { cn } from '@/lib/utils'

const LABELS: Array<{ key: keyof Policies; title: string }> = [
  { key: 'ranking', title: 'How the two sources are ranked against each other' },
  { key: 'counterfactual', title: 'How rating cost is estimated' },
  { key: 'participation', title: 'What counts as abandoned debt' },
  { key: 'decay', title: 'How freshness is inferred' },
]

/**
 * Every assumption the queue rests on, one click away.
 *
 * The spec asks for the counterfactual to be surfaced rather than hidden, and the decay half-life
 * to be stated plainly as a guess. This is where that happens: the numbers above are only worth
 * anything if the reader can find out how they were made.
 */
export function Assumptions({ policies }: { policies: Policies }) {
  const [open, setOpen] = useState(false)

  return (
    <Card>
      <button
        onClick={() => setOpen((o) => !o)}
        className="flex w-full items-center justify-between px-5 py-3 text-left"
      >
        <span className="text-sm text-[var(--color-muted)]">
          What these numbers assume — and where they are guesses
        </span>
        <ChevronDown
          className={cn(
            'size-4 text-[var(--color-muted)] transition-transform',
            open && 'rotate-180',
          )}
        />
      </button>

      {open && (
        <CardContent className="space-y-4 border-t border-[var(--color-line)] pt-4">
          {LABELS.map(({ key, title }) => (
            <div key={String(key)}>
              <h4 className="text-xs font-medium tracking-wide text-[var(--color-body)] uppercase">
                {title}
              </h4>
              <p className="mt-1 text-sm leading-relaxed text-[var(--color-muted)]">
                {policies[key]}
              </p>
            </div>
          ))}
        </CardContent>
      )}
    </Card>
  )
}
