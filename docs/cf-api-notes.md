# Codeforces API — verified notes

Everything here was checked against the live API on **2026-09-01**, not copied from
documentation. Re-verify before trusting it; Codeforces changes these without announcement.

## Envelope

Every response is `{"status": "OK"|"FAILED", "result": ..., "comment": ...}`.

**A refusal is HTTP 400, not 2xx**, and the body is still JSON:

```
GET /api/user.rating?handle=zzz_definitely_not_a_handle_zzz
→ 400, content-type: application/json
  {"status":"FAILED","comment":"handles: User with handle zzz_... not found"}
```

Consequence: a client that only parses the envelope on 2xx loses the one field that says *why*.
`CodeforcesClient` parses the envelope on any JSON response and keys its retry decision off the
comment.

Rate-limit rejections are different again: HTTP 403 with a **plain-text** body, no envelope.

## Rate limits

Unofficial, per IP, and stricter during live contests. The documented figure is one request per
two seconds; `codeforces.min-request-interval` defaults to that and is a process-wide floor with
no burst allowance. Three back-to-back calls did succeed during testing — that is not evidence the
limit is loose, only that it is not always enforced.

Responses are not requested with `Accept-Encoding: gzip`: the detected `ClientHttpRequestFactory`
does not decode it, so asking for compression would hand Jackson a compressed stream.

## `problemset.problems`

~2 MB, **11,370 problems** in one response. Two parallel lists joined on `(contestId, index)`:

- `problems[]` — `contestId`, `index`, `name`, `type`, `tags[]`, and optionally `points`, `rating`
- `problemStatistics[]` — `contestId`, `index`, `solvedCount`

Field presence measured on the full mirrored set:

| field | present | note |
|---|---|---|
| `rating` | 11,087 / 11,370 | absent for ~2.5%; unrated and very new problems |
| `points` | 7,456 / 11,370 | only scored (`CF`-type) contests carry points |
| `solvedCount` | 11,370 / 11,370 | |

`contestId` is absent for problems in named problemsets (`problemsetName: "ACMSGURU"`). The default
response contained none, but the mirror drops them anyway: they cannot be failed in a live contest
and have no contest for a reason string to name.

## `user.status`

Newest first. Accepts `handle`, `from` (1-based), `count`.

`author.participantType` is the field the whole abandoned-debt feature rests on. Values:
`CONTESTANT`, `PRACTICE`, `VIRTUAL`, `MANAGER`, `OUT_OF_COMPETITION`. Only `CONTESTANT` was
observed in the sample pulled; the full set should be confirmed against a real history rather than
assumed.

`verdict` is **absent** while a submission is still being judged — nullable, not an enum.

## `contest.standings`

**Every extra parameter is rejected** for non-gym contests as a non-admin caller:

```
GET /api/contest.standings?contestId=1985&from=1&count=2
→ {"status":"FAILED","comment":"contestId: Non-gym contest standings for non-admin
   users are available only via anonymous GET requests with no extra parameters"}
```

So no paging, no `handles=` filter, no `showUnofficial`. It is the whole ranklist or nothing.

Measured: contest 1985 (Div. 4) → **16,536 rows, 14.8 MB, ~3.3 s**. This is why standings must be
mirrored rather than fetched per request, and why they must never be fetched while a contest runs.

`result.contest.type` is `CF`, `ICPC` or `IOI` and decides how the ranklist is scored — which
decides how a counterfactual rank can be computed. `rows[].problemResults[]` carries
`points`, `rejectedAttemptCount` and `bestSubmissionTimeSeconds`, positionally aligned with
`result.problems[]`.

## `user.rating`

Oldest first. One entry per rated contest: `contestId`, `contestName`, `rank`,
`ratingUpdateTimeSeconds`, `oldRating`, `newRating`. `tourist` returned 306 entries.

This is the actual delta that a counterfactual delta gets subtracted from.

## `contest.list?gym=false`

One call, **2,143 contests**, every field present on every row (`freezeDurationSeconds` appears on
14). Phases were 2,140 `FINISHED` and 3 `BEFORE`; types split `CF` 1,555 / `ICPC` 543 / `IOI` 45.

Gym contests are excluded by `gym=false` and therefore never enter the mirror — but `user.status`
does return gym submissions, which is why `submission` has no foreign key to `contest`.

## `user.status` paging

`count=100000` was accepted in a single call: `Petr` returned all 2,318 submissions in 1.3 MB. The
mirror still pages at 2,000 to bound memory rather than to stay within a limit.

Verdicts observed in one real history: `OK`, `WRONG_ANSWER`, `TIME_LIMIT_EXCEEDED`, `RUNTIME_ERROR`,
`COMPILATION_ERROR`, `SKIPPED`, `MEMORY_LIMIT_EXCEEDED`, `CHALLENGED`. `SKIPPED` and `CHALLENGED`
both mean "not solved" and need no special handling; only `OK` clears debt.

Participant types in that same history: `CONTESTANT` 1,962, `PRACTICE` 342, `OUT_OF_COMPETITION` 14.
`VIRTUAL` and `MANAGER` did not appear and remain unconfirmed against real data.

## What `user.status` omits

Submissions to **private gym and group contests are not returned at all** to an anonymous caller —
silently, with no marker distinguishing "no submissions" from "submissions you may not see".

Measured on a real account whose own profile page showed 19 solved problems: the API returned 5
submissions across 2 problems. The missing 14 were in gyms `267312` and `435607`. Asked anonymously,
those contests do not merely refuse — they deny existing:

```
contest.standings?contestId=267312 → "Contest with id 267312 not found"
contest.standings?contestId=435607 → "Contest with id 435607 not found"
contest.standings?contestId=102961 → "You have to be authenticated to use this method"
```

The third is the control: gym `102961` is public-but-auth-gated, and its submissions *did* come
through `user.status`. So the boundary is contest visibility, not gym-ness.

Consequence: for a handle whose practice happens inside a group, an unauthenticated mirror sees a
small and unrepresentative sample. Signing requests with an API key (`apiKey` + `apiSig`) is the
documented way to see what that user sees, and is not implemented.

## `contest.ratingChanges`

Every rated participant's pre-contest rating, final rank and awarded delta, in one call. Contest
1993 returned 20,489 rows in 3.5 MB. This is the entire input the rating formula needs, so it is
mirrored per contest that produced a debt item.

**An unrated contest answers in two different ways**, and both must be handled:

```
contestId=1885 → HTTP 400  {"status":"FAILED",
                            "comment":"contestId: Rating changes are unavailable for this contest"}
contestId=1573 → HTTP 200  {"status":"OK","result":[]}
```

Contest 1573 is Round 743 (Div. 2), an ordinary-looking round that was unrated for everyone —
confirmed independently by its absence from that handle's `user.rating`. Treating either shape as a
mirroring failure would retry it forever; treating the 400 as a hard error would abort a costing run.

Because an unrated contest legitimately yields zero rows, "have we mirrored this yet?" must be
answered from the fetch history rather than from whether rows landed.

## Ratings can be negative

Real Div. 2 fields contain participants rated below zero (−45, −21 were both present in one contest
here). Any table indexed by rating needs an offset. `oldRating: 0` is different again — it means
first rated contest, not a rating of zero.
