-- Everything needed to answer "how much rating did not solving this cost you?"
--
-- The counterfactual holds every other competitor fixed and asks where the author would have
-- ranked had this one problem also fallen. That needs the whole ranklist (to recount the rank)
-- and every rated participant's pre-contest rating (to re-run the rating formula).

-- One row per party per contest. Points and penalty are all the counterfactual needs from
-- other competitors: it never asks which problems they solved, only whether they finished above.
create table standings_row (
    contest_id integer        not null,
    -- Handles joined by ','. Team contests exist in the debt list, so a single handle is not
    -- a safe key.
    party_key  text           not null,
    rank       integer        not null,
    points     numeric(12, 2) not null,
    penalty    integer        not null default 0,

    primary key (contest_id, party_key)
);

create index standings_row_contest_idx on standings_row (contest_id);

-- Maximum value of each problem. Null for ICPC-type contests, where a problem is worth one
-- point and the ranking is by penalty time.
create table contest_problem (
    contest_id    integer not null,
    problem_index text    not null,
    max_points    numeric(10, 2),

    primary key (contest_id, problem_index)
);

-- Pre-contest rating and final rank of every rated participant. This is the input to the
-- rating formula; contest.ratingChanges returns it in one call.
create table rating_change (
    contest_id integer not null,
    handle     text    not null,
    rank       integer not null,
    old_rating integer not null,
    new_rating integer not null,

    primary key (contest_id, handle)
);

create index rating_change_contest_idx on rating_change (contest_id);

-- The computed cost of one abandoned item, kept rather than recomputed per request: each one
-- costs two full runs of the rating formula over the contest's whole rated field.
create table debt_rating_cost (
    handle                     text    not null,
    contest_id                 integer not null,
    problem_index              text    not null,

    -- Null when the contest was unrated, i.e. there was no rating to lose. Never negative.
    rating_cost                integer,
    unrated                    boolean not null default false,

    actual_rank                integer,
    counterfactual_rank        integer,
    -- What Codeforces actually awarded, and what the model says it would have awarded for the
    -- same rank. The gap between them is this item's calibration evidence, kept so the number
    -- can be distrusted intelligently rather than taken on faith.
    actual_delta               integer,
    model_actual_delta         integer,
    model_counterfactual_delta integer,

    -- Seconds from contest start at which the problem is assumed to have been solved.
    assumed_solve_seconds      integer,
    assumed_wrong_attempts     integer,

    computed_at                timestamptz not null default now(),

    primary key (handle, contest_id, problem_index)
);
