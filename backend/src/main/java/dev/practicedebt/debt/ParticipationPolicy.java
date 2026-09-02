package dev.practicedebt.debt;

import java.util.Set;

import dev.practicedebt.cf.dto.ParticipantType;

/**
 * What counts as failing a problem <em>live</em>.
 *
 * <p>This is the single named point the spec asks for, not a condition scattered across queries.
 * Every debt query reads {@link #liveParticipationTypes()}; changing the policy is changing this
 * one set.
 *
 * <h2>The decision, and why</h2>
 *
 * <p><b>Only {@code CONTESTANT} counts.</b> Two exclusions are deliberate:
 *
 * <ul>
 *   <li><b>{@code VIRTUAL}</b> - a virtual round is practice the author chose to run under a clock.
 *       It can be re-run at will, it costs no rating, and counting it would let someone manufacture
 *       debt by starting virtuals they never intended to finish. The spec requires picking one
 *       reading and being consistent; this is that reading.
 *   <li><b>{@code OUT_OF_COMPETITION}</b> - inside the contest window, but unrated for this handle.
 *       Since the rating cost of such an item is definitionally zero, it would enter the queue at
 *       the bottom and never leave it.
 * </ul>
 *
 * <p>To count virtual participation as debt, add {@link ParticipantType#VIRTUAL} to the set below
 * and say so in the README. Nothing else needs to change - but note that M2's rating cost has no
 * meaning for a virtual attempt, so the ranking policy would need revisiting.
 */
public final class ParticipationPolicy {

    private static final Set<String> LIVE = Set.of(ParticipantType.CONTESTANT);

    private ParticipationPolicy() {
    }

    /** Participation types whose failures are treated as abandoned debt. */
    public static Set<String> liveParticipationTypes() {
        return LIVE;
    }

    /** Rendered for the UI, so the policy is visible rather than implied. */
    public static String describe() {
        return "Only live contest participation counts as abandoned debt. "
                + "Virtual rounds and out-of-competition entries do not.";
    }
}
