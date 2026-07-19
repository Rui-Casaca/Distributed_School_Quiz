package pt.isec.common.model.question;

import java.time.LocalDateTime;

/**
 * Question state based on its availability period.
 * <p>
 * The state is determined using the following rules:
 * <ul>
 *     <li>{@link #FUTURE}  – not started yet ({@code now < startAt})</li>
 *     <li>{@link #ACTIVE}  – currently available ({@code startAt <= now <= endAt})</li>
 *     <li>{@link #EXPIRED} – already finished ({@code now > endAt})</li>
 * </ul>
 */
public enum QuestionState {

    /**
     * Question is scheduled for the future and cannot be answered yet.
     */
    FUTURE("Futura", "Ainda não começou"),

    /**
     * Question is currently active and can be answered.
     */
    ACTIVE("Ativa", "Em curso"),

    /**
     * Question has finished and is no longer available for answers.
     */
    EXPIRED("Expirada", "Já terminou");

    private final String displayName;
    private final String description;

    QuestionState(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /* ===================== Accessors ===================== */

    /**
     * Returns a human-friendly display name (in Portuguese) for this state.
     *
     * @return display name
     */
    @SuppressWarnings("unused") // kept for future UI usage
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns a short description (in Portuguese) for this state.
     *
     * @return description string
     */
    @SuppressWarnings("unused") // kept for future UI usage
    public String getDescription() {
        return description;
    }

    /* ===================== Factory methods ===================== */

    /**
     * Computes the question state based on start and end dates using the current time.
     *
     * @param startAt start date/time (inclusive)
     * @param endAt   end date/time (inclusive)
     * @return corresponding {@link QuestionState}
     * @throws IllegalArgumentException if {@code startAt} or {@code endAt} is {@code null}
     */
    @SuppressWarnings("unused") // convenience overload reserved for callers
    public static QuestionState fromDates(LocalDateTime startAt, LocalDateTime endAt) {
        return fromDates(startAt, endAt, LocalDateTime.now());
    }

    /**
     * Computes the question state based on start/end dates and a specific reference timestamp.
     *
     * @param startAt start date/time (inclusive)
     * @param endAt   end date/time (inclusive)
     * @param now     reference date/time
     * @return corresponding {@link QuestionState}
     * @throws IllegalArgumentException if any argument is {@code null}
     */
    public static QuestionState fromDates(LocalDateTime startAt,
                                          LocalDateTime endAt,
                                          LocalDateTime now) {
        if (startAt == null || endAt == null || now == null) {
            throw new IllegalArgumentException("startAt, endAt and now must not be null");
        }

        if (now.isBefore(startAt)) {
            return FUTURE;
        }
        if (now.isAfter(endAt)) {
            return EXPIRED;
        }
        return ACTIVE;
    }

    /* ===================== Business rules ===================== */

    /**
     * Checks whether the question can be answered in this state.
     *
     * @return {@code true} if answering is allowed, {@code false} otherwise
     */
    @SuppressWarnings("unused") // kept for domain/business-logic usage
    public boolean canBeAnswered() {
        return this == ACTIVE;
    }

    /**
     * Checks whether results can be viewed in this state.
     *
     * @return {@code true} if results are available, {@code false} otherwise
     */
    @SuppressWarnings("unused") // kept for domain/business-logic usage
    public boolean canViewResults() {
        return this == EXPIRED;
    }

    /**
     * Checks whether the question can be modified (edited/deleted) in this state.
     *
     * @return {@code true} if modifications are allowed, {@code false} otherwise
     */
    @SuppressWarnings("unused") // kept for domain/business-logic usage
    public boolean canBeModified() {
        return this == FUTURE || this == ACTIVE;
    }
}
