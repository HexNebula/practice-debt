/** Types mirroring the backend's responses. */

export type Source = 'ABANDONED' | 'DECAYED'

export interface Action {
  label: string
  url: string
}

export interface QueueItem {
  source: Source
  id: string
  title: string
  reason: string
  score: number
  ratingCost: number | null
  daysSinceLast: number | null
  actions: Action[]
}

export interface Policies {
  ranking: string
  participation: string
  counterfactual: string
  decay: string
}

export interface Queue {
  items: QueueItem[]
  abandonedCount: number
  decayedCount: number
  unattributable: number
  withheldDecayed: number
  policies: Policies
}

export interface MirrorRun {
  source: string
  status: 'RUNNING' | 'SUCCESS' | 'FAILED'
  startedAt: string
  finishedAt: string | null
  itemCount: number | null
  error: string | null
}

export interface MirrorStatus {
  problemCount: number
  problemsetLastRefreshedAt: string | null
  codeforcesAuthenticated: boolean
  recentRuns: MirrorRun[]
}

/** One bucket of the returns-after-a-gap evidence. */
export interface Calibration {
  gapBucket: string
  returns: number
  solvedFirstTry: number
  averageAttempts: string
  averageProblemRating: number | null
}

export interface DecayReport {
  items: unknown[]
  withheld: number
  fresh: number
  neverEstablished: number
  untouched: number
  techniques: number
  suggestionAnchorRating: number
  halfLifeDays: number
  policy: string
  calibration: Calibration[]
}

/** One recorded observation of where a technique stood. */
export interface SnapshotPoint {
  takenAt: string
  solvedCount: number
  daysSinceLast: number | null
  retention: string
  halfLifeDays: number
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, init)
  if (!response.ok) {
    // The backend distinguishes "Codeforces refused" from "Codeforces unreachable"; both arrive
    // as RFC 7807 problem details, so surface whatever it said rather than a generic failure.
    const detail = await response.json().catch(() => null)
    throw new Error(detail?.detail ?? detail?.error ?? `Request failed (${response.status})`)
  }
  return response.json() as Promise<T>
}

const forHandle = (handle: string) => `/api/handles/${encodeURIComponent(handle)}`

export const api = {
  queue: (handle: string) => request<Queue>(`${forHandle(handle)}/queue`),
  decayed: (handle: string) => request<DecayReport>(`${forHandle(handle)}/debt/decayed`),
  history: (handle: string, techniqueId: string) =>
    request<SnapshotPoint[]>(`${forHandle(handle)}/decay/history/${techniqueId}`),
  mirrorStatus: () => request<MirrorStatus>('/api/mirror/status'),

  sync: (handle: string) => request<unknown>(`${forHandle(handle)}/sync`, { method: 'POST' }),
  computeCosts: (handle: string) =>
    request<unknown>(`${forHandle(handle)}/debt/abandoned/cost`, { method: 'POST' }),
  snapshot: (handle: string) =>
    request<unknown>(`${forHandle(handle)}/decay/snapshot`, { method: 'POST' }),
}

const HANDLE_KEY = 'practice-debt.handle'

/**
 * The handle is remembered because this is meant to be opened daily for a month, and retyping it
 * every time is exactly the kind of friction that stops that happening.
 */
export const rememberedHandle = {
  read(): string {
    try {
      return localStorage.getItem(HANDLE_KEY) ?? ''
    } catch {
      // Private windows and blocked site data both throw here. Not remembering is survivable.
      return ''
    }
  },
  write(handle: string) {
    try {
      localStorage.setItem(HANDLE_KEY, handle)
    } catch {
      /* ignore */
    }
  },
}
