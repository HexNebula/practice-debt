import { useState } from 'react'
import { ArrowUpRight, History } from 'lucide-react'
import type { QueueItem } from '@/api'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent } from '@/components/ui/card'
import { TechniqueHistory } from '@/components/TechniqueHistory'

/**
 * One line of the queue.
 *
 * The reason is given the same visual weight as the title on purpose: it is what makes the
 * ranking arguable, and an item that cannot explain itself has no business being here.
 */
export function QueueRow({
  item,
  position,
  handle,
}: {
  item: QueueItem
  position: number
  handle: string
}) {
  const abandoned = item.source === 'ABANDONED'
  const [showHistory, setShowHistory] = useState(false)

  return (
    <Card className="transition-colors hover:border-[var(--color-line)]/80">
      <CardContent className="pt-4">
        <div className="flex items-start gap-4">
          <div className="w-8 shrink-0 pt-0.5 text-right font-mono text-sm text-[var(--color-muted)]">
            {position}
          </div>

          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <Badge variant={abandoned ? 'abandoned' : 'decayed'}>
                {abandoned ? 'abandoned' : 'decayed'}
              </Badge>
              <h3 className="text-[15px] font-medium text-[var(--color-body)]">{item.title}</h3>
            </div>

            <p className="mt-1.5 text-sm leading-relaxed text-[var(--color-muted)]">{item.reason}</p>

            {!abandoned && (
              <button
                onClick={() => setShowHistory((open) => !open)}
                className="mt-2 inline-flex items-center gap-1 text-xs text-[var(--color-muted)] hover:text-[var(--color-body)]"
              >
                <History className="size-3" />
                {showHistory ? 'hide history' : 'how this technique has moved'}
              </button>
            )}

            {showHistory && <TechniqueHistory handle={handle} techniqueId={item.id} />}

            {item.actions.length > 0 && (
              <div className="mt-3 flex flex-wrap gap-2">
                {item.actions.map((action) => (
                  <a
                    key={action.url}
                    href={action.url}
                    target="_blank"
                    rel="noreferrer"
                    className="inline-flex items-center gap-1 rounded-md border border-[var(--color-line)] bg-[var(--color-raised)] px-2.5 py-1 text-xs text-[var(--color-body)] hover:border-[var(--color-accent)]/50 hover:text-[var(--color-accent)]"
                  >
                    {action.label}
                    <ArrowUpRight className="size-3" />
                  </a>
                ))}
              </div>
            )}
          </div>

          <div className="shrink-0 text-right">
            {item.ratingCost != null && (
              <div className="font-mono text-lg leading-none text-[var(--color-abandoned)]">
                −{item.ratingCost}
              </div>
            )}
            {item.daysSinceLast != null && (
              <div className="font-mono text-lg leading-none text-[var(--color-decayed)]">
                {item.daysSinceLast}d
              </div>
            )}
            <div className="mt-1 font-mono text-[11px] text-[var(--color-muted)]">
              {item.score.toFixed(3)}
            </div>
          </div>
        </div>
      </CardContent>
    </Card>
  )
}
