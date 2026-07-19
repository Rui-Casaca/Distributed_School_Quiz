package pt.isec.common.model.question;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a multiple-choice question with:
 * <ul>
 *   <li>a statement (text)</li>
 *   <li>a set of answer options</li>
 *   <li>the correct option</li>
 *   <li>an availability period (start/end date-time)</li>
 *   <li>an optional access code</li>
 *   <li>a teacher owner</li>
 * </ul>
 */
@SuppressWarnings("unused") // many methods are part of public API and may be used by other layers/frameworks
public final class Question implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /* ===================== FIELDS ===================== */

    private Integer id;
    private QuestionState state;
    private String statement;
    private String accessCode;
    private OptionLetter correctOption;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Integer teacherId;
    private List<Option> options;

    /* ===================== CONSTRUCTORS ===================== */

    /**
     * Default constructor (for serialization frameworks).
     */
    public Question() {
        // no-op
    }

    /**
     * Constructs a new question without an id.
     *
     * @param statement     question text
     * @param teacherId     teacher owner id
     * @param options       list of options (at least two, unique letters)
     * @param startAt       start date/time
     * @param endAt         end date/time (must be after start)
     * @param correctOption correct option letter
     * @param accessCode    access code (may be {@code null})
     */
    public Question(String statement,
                    Integer teacherId,
                    List<Option> options,
                    LocalDateTime startAt,
                    LocalDateTime endAt,
                    OptionLetter correctOption,
                    String accessCode) {

        validate(statement, teacherId, options, startAt, endAt, correctOption, accessCode);
        this.id = null;
        this.statement = statement;
        this.teacherId = teacherId;
        this.options = List.copyOf(options); // avoid external modification
        this.startAt = startAt;
        this.endAt = endAt;
        this.correctOption = correctOption;
        this.accessCode = accessCode;
        this.state = computeState(startAt, endAt, LocalDateTime.now());
    }

    /**
     * Constructs a question with an id.
     *
     * @param id            question id (must be &gt; 0 if not {@code null})
     * @param statement     question text
     * @param teacherId     teacher owner id
     * @param options       list of options
     * @param startAt       start date/time
     * @param endAt         end date/time
     * @param correctOption correct option letter
     * @param accessCode    access code (may be {@code null})
     */
    public Question(Integer id,
                    String statement,
                    Integer teacherId,
                    List<Option> options,
                    LocalDateTime startAt,
                    LocalDateTime endAt,
                    OptionLetter correctOption,
                    String accessCode) {

        validate(statement, teacherId, options, startAt, endAt, correctOption, accessCode);
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("id must be > 0 if provided");
        }
        this.id = id;
        this.statement = statement;
        this.teacherId = teacherId;
        this.options = List.copyOf(options);
        this.startAt = startAt;
        this.endAt = endAt;
        this.correctOption = correctOption;
        this.accessCode = accessCode;
        this.state = computeState(startAt, endAt, LocalDateTime.now());
    }

    /* ===================== GETTERS ===================== */

    public Integer getId() {
        return id;
    }

    public QuestionState getState() {
        return state;
    }

    public String getStatement() {
        return statement;
    }

    public String getAccessCode() {
        return accessCode;
    }

    public OptionLetter getCorrectOption() {
        return correctOption;
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public LocalDateTime getEndAt() {
        return endAt;
    }

    public Integer getTeacherId() {
        return teacherId;
    }

    public List<Option> getOptions() {
        return options;
    }

    /* ===================== SETTERS ===================== */

    public void setId(Integer id) {
        this.id = id;
    }

    /**
     * Updates the access code.
     *
     * @param accessCode new access code (may be {@code null}, but not blank)
     */
    public void setAccessCode(String accessCode) {
        if (accessCode != null && accessCode.isBlank()) {
            throw new IllegalArgumentException("accessCode cannot be blank if provided");
        }
        this.accessCode = accessCode;
    }

    /**
     * Updates the start date/time.
     *
     * @param startAt start date/time (non-null)
     */
    public void setStartAt(LocalDateTime startAt) {
        if (startAt == null) {
            throw new IllegalArgumentException("startAt cannot be null");
        }
        if (this.endAt != null && !this.endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("endAt must be after startAt");
        }
        this.startAt = startAt;
        refreshState();
    }

    /**
     * Updates the end date/time.
     *
     * @param endAt end date/time (non-null and after {@link #startAt} if it exists)
     */
    public void setEndAt(LocalDateTime endAt) {
        if (endAt == null) {
            throw new IllegalArgumentException("endAt cannot be null");
        }
        if (this.startAt != null && !endAt.isAfter(this.startAt)) {
            throw new IllegalArgumentException("endAt must be after startAt");
        }
        this.endAt = endAt;
        refreshState();
    }

    /**
     * Updates the list of options, ensuring at least two and unique letters.
     *
     * @param options new option list
     */
    public void setOptions(List<Option> options) {
        validateOptionsCore(options);

        if (this.correctOption != null &&
                options.stream().noneMatch(o -> o.getLetter() == this.correctOption)) {
            throw new IllegalArgumentException("current correctOption is not present in new options");
        }

        this.options = List.copyOf(options);
    }

    /**
     * Updates the correct option, ensuring it exists in the options list (if present).
     *
     * @param correctOption correct option letter (non-null)
     */
    public void setCorrectOption(OptionLetter correctOption) {
        if (correctOption == null) {
            throw new IllegalArgumentException("correctOption cannot be null");
        }
        if (this.options != null &&
                this.options.stream().noneMatch(o -> o.getLetter() == correctOption)) {
            throw new IllegalArgumentException("correctOption must exist in the options");
        }
        this.correctOption = correctOption;
    }

    /**
     * Updates the question statement.
     *
     * @param statement new text (non-null and non-blank)
     */
    public void setStatement(String statement) {
        if (statement == null || statement.isBlank()) {
            throw new IllegalArgumentException("statement cannot be null or blank");
        }
        this.statement = statement;
    }

    /**
     * Updates the teacher id.
     *
     * @param teacherId teacher id (positive integer)
     */
    public void setTeacherId(Integer teacherId) {
        if (teacherId == null || teacherId <= 0) {
            throw new IllegalArgumentException("teacherId must be a positive integer");
        }
        this.teacherId = teacherId;
    }

    /* ===================== STATE CHECKS ===================== */

    /**
     * Checks whether the question is currently active.
     *
     * @return {@code true} if active
     */
    public boolean isActive() {
        refreshState();
        return state == QuestionState.ACTIVE;
    }

    /**
     * Checks whether the question has already expired.
     *
     * @return {@code true} if expired
     */
    public boolean isExpired() {
        refreshState();
        return state == QuestionState.EXPIRED;
    }

    /**
     * Checks whether the question is scheduled for the future.
     *
     * @return {@code true} if its availability has not started yet
     */
    public boolean isFuture() {
        refreshState();
        return state == QuestionState.FUTURE;
    }

    /**
     * Checks whether a given answer is correct.
     *
     * @param answer chosen option letter
     * @return {@code true} if it matches {@link #correctOption}
     */
    public boolean isCorrectAnswer(OptionLetter answer) {
        return correctOption != null && correctOption.equals(answer);
    }

    /**
     * Refreshes the {@link QuestionState} based on {@link #startAt}, {@link #endAt}
     * and the current time.
     */
    public void refreshState() {
        if (startAt != null && endAt != null) {
            this.state = computeState(this.startAt, this.endAt, LocalDateTime.now());
        }
    }

    /* ===================== VALIDATION / HELPERS ===================== */

    /**
     * Computes state given a time interval and a reference moment.
     */
    private static QuestionState computeState(LocalDateTime startAt,
                                              LocalDateTime endAt,
                                              LocalDateTime now) {
        if (now.isBefore(startAt)) {
            return QuestionState.FUTURE;
        }
        if (now.isAfter(endAt)) {
            return QuestionState.EXPIRED;
        }
        return QuestionState.ACTIVE;
    }

    /**
     * Common validation used by constructors.
     */
    private static void validate(String statement,
                                 Integer teacherId,
                                 List<Option> options,
                                 LocalDateTime startAt,
                                 LocalDateTime endAt,
                                 OptionLetter correctOption,
                                 String accessCode) {

        if (statement == null || statement.isBlank()) {
            throw new IllegalArgumentException("statement (question text) cannot be null or blank");
        }

        if (teacherId == null || teacherId <= 0) {
            throw new IllegalArgumentException("teacherId must be a positive integer");
        }

        validateOptionsCore(options);

        if (correctOption == null) {
            throw new IllegalArgumentException("correctOption cannot be null");
        }

        boolean containsCorrect = options.stream()
                .anyMatch(o -> o.getLetter() == correctOption);
        if (!containsCorrect) {
            throw new IllegalArgumentException("correctOption must exist in the provided options list");
        }

        if (startAt == null || endAt == null) {
            throw new IllegalArgumentException("startAt and endAt cannot be null");
        }

        if (!endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("endAt must be after startAt");
        }

        if (accessCode != null && accessCode.isBlank()) {
            throw new IllegalArgumentException("accessCode cannot be blank if provided");
        }
    }

    /**
     * Validates that a list of options is non-null, has at least two entries
     * and that each option has a unique, non-null letter.
     *
     * @param options list of options to validate
     */
    private static void validateOptionsCore(List<Option> options) {
        if (options == null || options.size() < 2) {
            throw new IllegalArgumentException("there must be at least two options");
        }

        // unique letters (consistent with UNIQUE(question_id, letter) in DB)
        Set<OptionLetter> seen = new HashSet<>();
        for (Option o : options) {
            if (o == null || o.getLetter() == null) {
                throw new IllegalArgumentException("each option must have a non-null letter");
            }
            if (!seen.add(o.getLetter())) {
                throw new IllegalArgumentException("duplicate option letter: " + o.getLetter());
            }
        }
    }

    /* ===================== OBJECT OVERRIDES ===================== */

    @Override
    public String toString() {
        return "Question{" +
                "id=" + id +
                ", statement='" + statement + '\'' +
                ", accessCode='" + accessCode + '\'' +
                ", correctOption=" + correctOption +
                ", startAt=" + startAt +
                ", endAt=" + endAt +
                ", teacherId=" + teacherId +
                ", options=" + (options == null ? "[]" : options.size() + " items") +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Question q)) return false;
        return Objects.equals(id, q.id) &&
                Objects.equals(statement, q.statement) &&
                Objects.equals(accessCode, q.accessCode) &&
                correctOption == q.correctOption &&
                Objects.equals(startAt, q.startAt) &&
                Objects.equals(endAt, q.endAt) &&
                Objects.equals(teacherId, q.teacherId) &&
                Objects.equals(options, q.options);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, statement, accessCode, correctOption, startAt, endAt, teacherId, options);
    }
}
