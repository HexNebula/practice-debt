-- The local mirror of the shared Codeforces problemset.
--
-- One row per problem, keyed the way Codeforces keys them: (contestId, index).
-- Everything here is a copy of upstream data; nothing in this table is
-- author-specific. Author-specific state (submissions, debt, snapshots) arrives
-- in later migrations.

create table problem (
    contest_id        integer     not null,
    problem_index     text        not null,
    name              text        not null,
    type              text        not null,
    -- Nullable upstream: ~4% of problems carry no difficulty rating, and only
    -- scored (non-ICPC) contests carry points. Verified against the live API.
    rating            integer,
    points            numeric(10, 2),
    tags              text[]      not null default '{}',
    -- From problemStatistics in the same response.
    solved_count      integer,
    first_mirrored_at timestamptz not null default now(),
    last_mirrored_at  timestamptz not null default now(),

    primary key (contest_id, problem_index)
);

create index problem_tags_idx on problem using gin (tags);
create index problem_rating_idx on problem (rating);

-- Bookkeeping for every mirror fetch. Decay is a function of elapsed time, so
-- knowing when the data last arrived is part of the data, not logging.
create table mirror_run (
    id          bigint generated always as identity primary key,
    source      text        not null,
    status      text        not null,
    started_at  timestamptz not null default now(),
    finished_at timestamptz,
    item_count  integer,
    error       text,

    constraint mirror_run_status_check check (status in ('RUNNING', 'SUCCESS', 'FAILED'))
);

create index mirror_run_source_started_idx on mirror_run (source, started_at desc);
