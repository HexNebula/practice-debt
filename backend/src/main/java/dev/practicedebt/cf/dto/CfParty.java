package dev.practicedebt.cf.dto;

import java.util.List;

/**
 * Who made a submission or occupies a standings row.
 *
 * <p>{@code participantType} is the field the whole abandoned-debt feature rests on. Codeforces
 * documents these values: CONTESTANT, PRACTICE, VIRTUAL, MANAGER, OUT_OF_COMPETITION.
 * See {@link ParticipantType}.
 */
public record CfParty(
        Integer contestId,
        Long participantId,
        List<CfMember> members,
        String participantType,
        Boolean ghost,
        Integer room,
        Long startTimeSeconds) {
}
