package dev.practicedebt.queue;

import java.util.List;

/**
 * One line of the unified queue.
 *
 * <p>Deliberately flat: the two sources describe very different things, and the queue's job is to
 * present them as one list a person can read top to bottom.
 *
 * @param reason  why this is here, naming its source. Load-bearing, not decoration: an item that
 *                cannot explain itself does not ship.
 * @param actions what to actually do about it - the problem to open, or problems to try
 */
public record QueueItem(
        Source source,
        String id,
        String title,
        String reason,
        double score,
        Integer ratingCost,
        Integer daysSinceLast,
        List<Action> actions) {

    public enum Source {
        ABANDONED, DECAYED
    }

    public record Action(String label, String url) {
    }
}
