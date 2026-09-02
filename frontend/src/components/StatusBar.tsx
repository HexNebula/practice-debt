import { KeyRound, ShieldOff } from 'lucide-react'
import type { MirrorStatus } from '@/api'

/**
 * What the numbers below are made of, and how old it is.
 *
 * <p>Both facts change how much the queue is worth. Data mirrored a week ago describes a week-old
 * you, and an unsigned client cannot see private gym or group practice at all — so a queue that
 * looks thin might be thin, or might be blind.
 */
export function StatusBar({ status }: { status: MirrorStatus }) {
  const refreshed = status.problemsetLastRefreshedAt
    ? relative(new Date(status.problemsetLastRefreshedAt))
    : 'never'

  return (
    <div className="mb-4 flex flex-wrap items-center gap-x-5 gap-y-1 text-xs text-[var(--color-muted)]">
      <span>
        {status.problemCount.toLocaleString()} problems mirrored, refreshed {refreshed}
      </span>

      {status.codeforcesAuthenticated ? (
        <span className="inline-flex items-center gap-1.5 text-[var(--color-body)]">
          <KeyRound className="size-3" />
          signed — private gym and group practice is visible
        </span>
      ) : (
        <span
          className="inline-flex items-center gap-1.5"
          title="Codeforces omits private gym and group submissions from unauthenticated responses, with nothing to indicate anything is missing."
        >
          <ShieldOff className="size-3" />
          anonymous — private gym and group practice is invisible
        </span>
      )}
    </div>
  )
}

function relative(when: Date): string {
  const minutes = Math.round((Date.now() - when.getTime()) / 60000)
  if (minutes < 1) return 'just now'
  if (minutes < 60) return `${minutes}m ago`
  const hours = Math.round(minutes / 60)
  if (hours < 24) return `${hours}h ago`
  return `${Math.round(hours / 24)}d ago`
}
