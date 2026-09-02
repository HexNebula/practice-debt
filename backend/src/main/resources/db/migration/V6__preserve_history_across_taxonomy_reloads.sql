-- Stop a taxonomy reload from destroying the evidence.
--
-- Reloading the taxonomy rebuilt the technique table by deleting it, and both history tables
-- cascaded from it. So editing the taxonomy file — a routine, encouraged action — silently erased
-- every snapshot and every recorded return.
--
-- That is the opposite of what those tables are for. Snapshots exist because decay is a function
-- of elapsed time and cannot be recovered from a stateless fetch. Returns exist to accumulate for
-- years until a real half-life can be fitted to them. Neither can be regenerated: a snapshot of
-- last spring is gone forever, and while returns happen to be re-derivable from submissions today,
-- relying on that makes the archive hostage to a mirror that may itself be incomplete.
--
-- History now keeps its own copy of the technique id, deliberately unconstrained. A snapshot taken
-- under a technique that has since been renamed or merged away is still a true record of what was
-- observed, and deleting it would be falsifying the past rather than tidying it.

alter table technique_snapshot drop constraint technique_snapshot_technique_id_fkey;
alter table technique_return drop constraint technique_return_technique_id_fkey;

-- problem_technique keeps its cascade on purpose: it is derived data, rebuilt from scratch on
-- every apply, and a mapping to a technique that no longer exists is simply wrong.
