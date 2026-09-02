-- Contests and one handle's submissions.
--
-- Together these answer the M1 question: which problems were failed live in a contest and never
-- solved afterwards. Both tables mirror upstream; neither derives anything.

create table contest (
    id                 integer     not null primary key,
    name               text        not null,
    -- CF, ICPC or IOI. Decides how the ranklist is scored, which decides how a counterfactual
    -- rank can be computed at M2.
    type               text        not null,
    phase              text        not null,
    frozen             boolean,
    duration_seconds   bigint,
    start_time_seconds bigint,
    first_mirrored_at  timestamptz not null default now(),
    last_mirrored_at   timestamptz not null default now()
);

create index contest_start_time_idx on contest (start_time_seconds desc);

-- One row per submission Codeforces reports for a handle.
--
-- No foreign key to contest: user.status also returns gym submissions, and gym contests are not
-- in contest.list. The debt query joins to contest and therefore ignores them, which is the
-- intended behaviour - see AbandonedDebtRepository.
create table submission (
    -- Codeforces submission ids are globally unique, so this is upstream's key, not ours.
    id                    bigint      not null primary key,
    -- Stored lowercase. Codeforces handles are case-insensitive and the same handle typed two
    -- ways must not become two people.
    handle                text        not null,
    contest_id            integer     not null,
    problem_index         text        not null,
    -- Denormalised from the submission payload so a reason string can name the problem even if
    -- the problemset mirror has not caught up. Costs one text column; buys a reason that always
    -- renders.
    problem_name          text,
    creation_time_seconds bigint      not null,
    relative_time_seconds bigint,
    -- CONTESTANT, PRACTICE, VIRTUAL, MANAGER or OUT_OF_COMPETITION. The field the whole
    -- abandoned-debt feature rests on. Kept as text: an unknown value from upstream must not
    -- break a sync.
    participant_type      text        not null,
    -- Null while a submission is still being judged. Observed values include OK, WRONG_ANSWER,
    -- TIME_LIMIT_EXCEEDED, RUNTIME_ERROR, COMPILATION_ERROR, MEMORY_LIMIT_EXCEEDED, CHALLENGED
    -- and SKIPPED.
    verdict               text,
    programming_language  text,
    mirrored_at           timestamptz not null default now()
);

create index submission_handle_problem_idx on submission (handle, contest_id, problem_index);
create index submission_handle_verdict_idx on submission (handle, verdict);
create index submission_handle_participation_idx on submission (handle, participant_type);
