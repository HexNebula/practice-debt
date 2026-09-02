package dev.practicedebt.cf.dto;

/**
 * The participation modes Codeforces reports on a party.
 *
 * <p>Kept as constants rather than an enum on purpose: an unknown value from upstream must not
 * blow up deserialization of an otherwise usable submission list. Classification code matches on
 * these strings and treats anything else as unknown.
 */
public final class ParticipantType {

    /** Entered the contest live, in the official window. Failures here are abandoned debt. */
    public static final String CONTESTANT = "CONTESTANT";

    /** Solved after the contest, in the problemset. Never abandoned debt on its own. */
    public static final String PRACTICE = "PRACTICE";

    /** Ran the contest later against a frozen clock. Policy decision - see README. */
    public static final String VIRTUAL = "VIRTUAL";

    public static final String MANAGER = "MANAGER";

    /** In the contest window but unrated for this handle. */
    public static final String OUT_OF_COMPETITION = "OUT_OF_COMPETITION";

    private ParticipantType() {
    }
}
