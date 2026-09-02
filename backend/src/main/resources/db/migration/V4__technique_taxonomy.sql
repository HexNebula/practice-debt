-- The curated technique taxonomy, and the mapping of problems onto it.
--
-- The taxonomy itself lives in a versioned YAML file in the repository, not here and not in code:
-- it is data to be argued with, and its history matters. These tables are the loaded form of that
-- file, rebuilt whenever it changes.

create table technique (
    id               text    not null primary key,
    name             text    not null,
    family           text    not null,
    summary          text    not null,
    -- Which taxonomy version defined this technique. Staleness measured under one version is not
    -- comparable with staleness under another, so the version travels with the data.
    taxonomy_version integer not null,
    display_order    integer not null
);

create table problem_technique (
    contest_id       integer     not null,
    problem_index    text        not null,
    technique_id     text        not null references technique (id) on delete cascade,
    -- PINNED for a hand-curated assignment, RULE for one derived from tags. Kept because the two
    -- deserve different trust: a pin is somebody's judgement, a rule is a heuristic.
    source           text        not null,
    taxonomy_version integer     not null,
    assigned_at      timestamptz not null default now(),

    primary key (contest_id, problem_index, technique_id),
    constraint problem_technique_source_check check (source in ('PINNED', 'RULE'))
);

create index problem_technique_technique_idx on problem_technique (technique_id);
create index problem_technique_problem_idx on problem_technique (contest_id, problem_index);
