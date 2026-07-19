package pt.isec.common.model.question;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents a student's answer to a question.
 * <p>
 * This class is designed to be used both on the server and client side
 * as a simple DTO for transporting answer data.
 */
public final class Answer implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /* ======================= FIELDS ======================= */

    private Integer id;
    private Integer studentId;
    private Integer studentNumber;
    private Integer questionId;
    private OptionLetter selectedOption;
    private LocalDateTime answeredAt;
    private boolean isCorrect;
    private String studentName;
    private String studentEmail;
    private String questionStatement;

    /* ======================= CONSTRUCTORS ======================= */

    /**
     * Default constructor (intended for serialization frameworks).
     */
    public Answer() {}

    /**
     * Constructs a full answer object.
     *
     * @param id                answer identifier
     * @param studentId         student database identifier
     * @param studentNumber     student number (external identifier)
     * @param questionId        question identifier
     * @param selectedOption    chosen option letter
     * @param answeredAt        date/time when the answer was given
     * @param isCorrect         {@code true} if the answer is correct
     * @param studentName       student full name
     * @param studentEmail      student email address
     * @param questionStatement question statement text
     */
    public Answer(Integer id,
                  Integer studentId,
                  Integer studentNumber,
                  Integer questionId,
                  OptionLetter selectedOption,
                  LocalDateTime answeredAt,
                  boolean isCorrect,
                  String studentName,
                  String studentEmail,
                  String questionStatement) {

        this.id = id;
        this.studentId = studentId;
        this.studentNumber = studentNumber;
        this.questionId = questionId;
        this.selectedOption = selectedOption;
        this.answeredAt = answeredAt;
        this.isCorrect = isCorrect;
        this.studentName = studentName;
        this.studentEmail = studentEmail;
        this.questionStatement = questionStatement;
    }

    /* ======================= GETTERS ======================= */

    /**
     * @return answer identifier
     */
    public Integer getId() {
        return id;
    }

    /**
     * @return student database identifier
     */
    @SuppressWarnings("unused") // kept for future use / frameworks
    public Integer getStudentId() {
        return studentId;
    }

    /**
     * @return student number (external identifier)
     */
    public Integer getStudentNumber() {
        return studentNumber;
    }

    /**
     * @return question identifier
     */
    public Integer getQuestionId() {
        return questionId;
    }

    /**
     * @return selected option letter
     */
    public OptionLetter getSelectedOption() {
        return selectedOption;
    }

    /**
     * @return date/time when the answer was given
     */
    public LocalDateTime getAnsweredAt() {
        return answeredAt;
    }

    /**
     * @return {@code true} if the answer is correct
     */
    public boolean isCorrect() {
        return isCorrect;
    }

    /**
     * @return student full name
     */
    public String getStudentName() {
        return studentName;
    }

    /**
     * @return student email address
     */
    public String getStudentEmail() {
        return studentEmail;
    }

    /**
     * @return question statement text
     */
    public String getQuestionStatement() {
        return questionStatement;
    }

    /* ======================= SETTERS ======================= */

    /**
     * Sets the answer identifier.
     *
     * @param id new identifier
     */
    public void setId(Integer id) {
        this.id = id;
    }

    /**
     * Sets the student identifier.
     *
     * @param studentId student database identifier
     */
    @SuppressWarnings("unused") // kept for symmetry and possible framework binding
    public void setStudentId(Integer studentId) {
        this.studentId = studentId;
    }

    /**
     * Sets the student number.
     *
     * @param studentNumber student number (external identifier)
     */
    @SuppressWarnings("unused") // may be used by mappers / frameworks
    public void setStudentNumber(Integer studentNumber) {
        this.studentNumber = studentNumber;
    }

    /**
     * Sets the question identifier.
     *
     * @param questionId question identifier
     */
    public void setQuestionId(Integer questionId) {
        this.questionId = questionId;
    }

    /**
     * Sets the selected option.
     *
     * @param selectedOption chosen option letter
     */
    @SuppressWarnings("unused") // currently unused, kept for completeness
    public void setSelectedOption(OptionLetter selectedOption) {
        this.selectedOption = selectedOption;
    }

    /**
     * Sets the answer date/time.
     *
     * @param answeredAt date/time when the answer was given
     */
    @SuppressWarnings("unused") // currently unused, kept for completeness
    public void setAnsweredAt(LocalDateTime answeredAt) {
        this.answeredAt = answeredAt;
    }

    /**
     * Sets whether the answer is correct.
     *
     * @param correct {@code true} if the answer is correct
     */
    public void setCorrect(boolean correct) {
        this.isCorrect = correct;
    }

    /* ======================= OVERRIDDEN METHODS ======================= */

    @Override
    public String toString() {
        return "Answer{" +
                "id=" + id +
                ", studentId=" + studentId +
                ", studentNumber=" + studentNumber +
                ", questionId=" + questionId +
                ", selectedOption=" + selectedOption +
                ", answeredAt=" + answeredAt +
                ", isCorrect=" + isCorrect +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Answer answer)) return false;
        return isCorrect == answer.isCorrect &&
                Objects.equals(id, answer.id) &&
                Objects.equals(studentId, answer.studentId) &&
                Objects.equals(studentNumber, answer.studentNumber) &&
                Objects.equals(questionId, answer.questionId) &&
                selectedOption == answer.selectedOption &&
                Objects.equals(answeredAt, answer.answeredAt) &&
                Objects.equals(studentName, answer.studentName) &&
                Objects.equals(studentEmail, answer.studentEmail) &&
                Objects.equals(questionStatement, answer.questionStatement);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id, studentId, studentNumber, questionId,
                selectedOption, answeredAt, isCorrect,
                studentName, studentEmail, questionStatement
        );
    }
}
