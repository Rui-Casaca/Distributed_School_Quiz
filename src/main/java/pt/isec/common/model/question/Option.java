package pt.isec.common.model.question;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Represents a single answer option of a multiple-choice question.
 */
public final class Option implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /* ===================== FIELDS ===================== */

    private Integer id;
    private OptionLetter letter;
    private String text;

    /* ===================== CONSTRUCTORS ===================== */

    /**
     * Default constructor (for serialization frameworks).
     */
    public Option() {
        // no-op
    }

    /**
     * Constructs an option with id, letter and text.
     *
     * @param id     option id (may be {@code null})
     * @param letter option letter (must be non-null)
     * @param text   option text (must be non-blank)
     */
    public Option(Integer id, OptionLetter letter, String text) {
        validate(id, letter, text);
        this.id = id;
        this.letter = letter;
        this.text = text;
    }

    /**
     * Constructs an option without id.
     *
     * @param letter option letter
     * @param text   option text
     */
    public Option(OptionLetter letter, String text) {
        this(null, letter, text);
    }

    /* ===================== GETTERS ===================== */

    public Integer getId() {
        return id;
    }

    public OptionLetter getLetter() {
        return letter;
    }

    public String getText() {
        return text;
    }

    /* ===================== SETTERS ===================== */

    public void setId(Integer id) {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("id must be positive if provided");
        }
        this.id = id;
    }

    /**
     * Updates the option letter.
     *
     * @param letter new letter (non-null)
     */
    @SuppressWarnings("unused") // part of public model API
    public void setLetter(OptionLetter letter) {
        if (letter == null) {
            throw new IllegalArgumentException("letter cannot be null");
        }
        this.letter = letter;
    }

    /**
     * Updates the option text.
     *
     * @param text new text (non-null and non-blank)
     */
    public void setText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text cannot be null or blank");
        }
        this.text = text;
    }

    /* ===================== OBJECT OVERRIDES ===================== */

    @Override
    public String toString() {
        return "Option{" +
                "id=" + id +
                ", letter=" + letter +
                ", text='" + text + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Option option)) return false;
        return Objects.equals(id, option.id) &&
                letter == option.letter &&
                Objects.equals(text, option.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, letter, text);
    }

    /* ===================== VALIDATION HELPERS ===================== */

    /**
     * Validates option data used by constructors.
     *
     * @param id     option id
     * @param letter option letter
     * @param text   option text
     */
    private static void validate(Integer id, OptionLetter letter, String text) {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("id must be positive if provided");
        }

        if (letter == null) {
            throw new IllegalArgumentException("letter cannot be null");
        }

        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text cannot be null or blank");
        }
    }
}
