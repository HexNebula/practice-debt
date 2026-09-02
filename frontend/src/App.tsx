import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Loader2, RefreshCw } from 'lucide-react'
import { api, rememberedHandle, type Source } from '@/api'
import { Assumptions } from '@/components/Assumptions'
import { CalibrationPanel } from '@/components/Calibration'
import { QueueRow } from '@/components/QueueRow'
import { StatusBar } from '@/components/StatusBar'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'

type Filter = 'ALL' | Source

/**
 * The whole product is one list.
 *
 * Tracking and charts are explicitly not the point; they exist only insofar as they make the queue
 * inspectable and trustworthy. So there are no charts — just the debt in order, each item saying
 * why it is there, and the evidence about how much to believe it one click away.
 */
export default function App() {
  const remembered = rememberedHandle.read()
  const [handle, setHandle] = useState(remembered)
  const [submitted, setSubmitted] = useState(remembered)
  const [filter, setFilter] = useState<Filter>('ALL')
  const [step, setStep] = useState<string | null>(null)
  const queryClient = useQueryClient()

  const status = useQuery({ queryKey: ['status'], queryFn: api.mirrorStatus })

  const queue = useQuery({
    queryKey: ['queue', submitted],
    queryFn: () => api.queue(submitted),
    enabled: submitted.length > 0,
  })

  const decay = useQuery({
    queryKey: ['decayed', submitted],
    queryFn: () => api.decayed(submitted),
    enabled: submitted.length > 0,
  })

  const refresh = useMutation({
    mutationFn: async (h: string) => {
      // Order matters: submissions first, then costs (which need those contests mirrored), then
      // the snapshot (which needs the taxonomy mapping to exist). Each step is announced because
      // the whole run takes minutes on a first pass and a silent spinner is indistinguishable
      // from a hang.
      setStep('Syncing submissions from Codeforces…')
      await api.sync(h)
      setStep('Computing rating costs — mirrors a ranklist per contest, this is the slow part…')
      await api.computeCosts(h)
      setStep('Recording a decay snapshot…')
      await api.snapshot(h)
      setStep(null)
    },
    onSettled: () => {
      setStep(null)
      queryClient.invalidateQueries()
    },
  })

  function submit(value: string) {
    const trimmed = value.trim()
    setSubmitted(trimmed)
    rememberedHandle.write(trimmed)
  }

  const data = queue.data
  const items = data?.items.filter((i) => filter === 'ALL' || i.source === filter) ?? []

  return (
    <div className="mx-auto max-w-4xl px-6 py-10">
      <header className="mb-6">
        <h1 className="text-2xl font-semibold tracking-tight text-[var(--color-body)]">
          Practice Debt
        </h1>
        <p className="mt-1 text-sm text-[var(--color-muted)]">
          Not what you should solve next — what you already owe.
        </p>
      </header>

      {status.data && <StatusBar status={status.data} />}

      <form
        className="mb-6 flex gap-2"
        onSubmit={(e) => {
          e.preventDefault()
          submit(handle)
        }}
      >
        <Input
          value={handle}
          onChange={(e) => setHandle(e.target.value)}
          placeholder="Codeforces handle"
          className="flex-1"
          autoComplete="off"
        />
        <Button type="submit" variant="solid" disabled={!handle.trim()}>
          Show debt
        </Button>
        {submitted && (
          <Button
            type="button"
            onClick={() => refresh.mutate(submitted)}
            disabled={refresh.isPending}
            title="Re-sync from Codeforces, recompute rating costs, take a decay snapshot"
          >
            {refresh.isPending ? (
              <Loader2 className="size-3.5 animate-spin" />
            ) : (
              <RefreshCw className="size-3.5" />
            )}
            Refresh
          </Button>
        )}
      </form>

      {step && (
        <Card className="mb-4">
          <CardContent className="flex items-center gap-3 pt-4">
            <Loader2 className="size-4 shrink-0 animate-spin text-[var(--color-accent)]" />
            <p className="text-sm text-[var(--color-muted)]">{step}</p>
          </CardContent>
        </Card>
      )}

      {refresh.isError && (
        <Card className="mb-4">
          <CardContent className="pt-4">
            <p className="text-sm text-[var(--color-abandoned)]">
              {(refresh.error as Error).message}
            </p>
          </CardContent>
        </Card>
      )}

      {queue.isLoading && <p className="text-sm text-[var(--color-muted)]">Loading…</p>}

      {queue.isError && (
        <Card>
          <CardContent className="pt-4">
            <p className="text-sm text-[var(--color-abandoned)]">
              {(queue.error as Error).message}
            </p>
          </CardContent>
        </Card>
      )}

      {data && (
        <>
          <Card className="mb-4">
            <CardContent className="flex flex-wrap gap-x-8 gap-y-2 pt-4 text-sm">
              <Stat label="in the queue" value={data.items.length} />
              <Stat label="abandoned" value={data.abandonedCount} />
              <Stat label="decayed" value={data.decayedCount} />
              {data.unattributable > 0 && (
                <Stat label="withheld — contest not mirrored" value={data.unattributable} muted />
              )}
              {data.withheldDecayed > 0 && (
                <Stat label="decayed below the cut" value={data.withheldDecayed} muted />
              )}
            </CardContent>
          </Card>

          {decay.data && (
            <CalibrationPanel
              rows={decay.data.calibration}
              halfLifeDays={decay.data.halfLifeDays}
            />
          )}

          <div className="mb-4">
            <Assumptions policies={data.policies} />
          </div>

          {data.items.length > 0 && (
            <div className="mb-3 flex gap-1.5">
              {(['ALL', 'ABANDONED', 'DECAYED'] as const).map((option) => (
                <button
                  key={option}
                  onClick={() => setFilter(option)}
                  className={cn(
                    'rounded-md border px-2.5 py-1 text-xs',
                    filter === option
                      ? 'border-[var(--color-accent)]/50 bg-[var(--color-raised)] text-[var(--color-body)]'
                      : 'border-[var(--color-line)] text-[var(--color-muted)] hover:text-[var(--color-body)]',
                  )}
                >
                  {option === 'ALL' ? 'everything' : option.toLowerCase()}
                </button>
              ))}
            </div>
          )}

          {data.items.length === 0 ? (
            <Card>
              <CardContent className="pt-4">
                <p className="text-sm text-[var(--color-muted)]">
                  Nothing owed. Either this handle has no live-contest failures and no quiet
                  techniques, or it has not been synced yet — try Refresh.
                </p>
              </CardContent>
            </Card>
          ) : (
            <ol className="space-y-2.5">
              {items.map((item) => (
                <li key={`${item.source}:${item.id}`}>
                  {/* Position is the item's place in the whole queue, not in the filtered view:
                      filtering is a lens on one ranking, not a different ranking. */}
                  <QueueRow
                    item={item}
                    position={data.items.indexOf(item) + 1}
                    handle={submitted}
                  />
                </li>
              ))}
            </ol>
          )}
        </>
      )}
    </div>
  )
}

function Stat({ label, value, muted }: { label: string; value: number; muted?: boolean }) {
  return (
    <div>
      <div
        className={cn(
          'font-mono text-lg',
          muted ? 'text-[var(--color-muted)]' : 'text-[var(--color-body)]',
        )}
      >
        {value}
      </div>
      <div className="text-xs text-[var(--color-muted)]">{label}</div>
    </div>
  )
}
