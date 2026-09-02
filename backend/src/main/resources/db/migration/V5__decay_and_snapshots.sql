-- Decayed debt: which techniques have gone quiet, and the evidence to calibrate that later.
--
-- Freshness is inferred from activity the author was going to produce anyway. Nothing here
-- schedules a review, because re-solving a competitive programming problem costs 30-60 minutes and
-- a system that demands that will be ignored - and an ignored system produces meaningless data.

-- A point-in-time record of where every technique stood.
--
-- Persistence is the point. Decay is a function of elapsed time and cannot be recovered from a
-- stateless fetch: without these rows, a gap that opened last spring is invisible today.
create table technique_snapshot (
    id               bigint generated always as identity primary key,
    handle           text        not null,
    technique_id     text        not null references technique (id) on delete cascade,
    taken_at         timestamptz not null default now(),

    solved_count     integer     not null,
    last_solved_at   timestamptz,
    days_since_last  integer,
    -- Modelled retention under the guessed half-life. Stored, not derived on read, so that a later
    -- change to the guess does not silently rewrite history.
    retention        numeric(5, 4),
    half_life_days   integer     not null,
    taxonomy_version integer     not null
);

create index technique_snapshot_handle_idx on technique_snapshot (handle, technique_id, taken_at desc);

-- The calibration signal, instrumented from day one exactly as the spec asks.
--
-- When the author returns to a technique after a gap, did they solve cleanly on the first attempt?
-- Nothing reads this yet. It accumulates so that a decay model can one day be fitted to evidence
-- instead of guessed at - and it accumulates whether or not anyone else ever uses this tool.
create table technique_return (
    handle             text        not null,
    technique_id       text        not null references technique (id) on delete cascade,
    contest_id         integer     not null,
    problem_index      text        not null,

    returned_at        timestamptz not null,
    -- Days since the previous solve in this technique.
    gap_days           integer     not null,
    -- Did the first submission to this problem pass? The outcome the model wants to predict.
    solved_first_try   boolean     not null,
    attempts           integer     not null,
    problem_rating     integer,
    taxonomy_version   integer     not null,
    observed_at        timestamptz not null default now(),

    primary key (handle, technique_id, contest_id, problem_index)
);

create index technique_return_gap_idx on technique_return (technique_id, gap_days);
