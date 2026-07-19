package pt.isec.common.model.user;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Student entity.
 * <p>
 * Identified by a unique {@code studentNumber}.
 */
@SuppressWarnings("unused") // may be instantiated via reflection / serialization
public final class Student extends User implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Unique student number. */
    private long studentNumber;

    /* ===================== Constructors ===================== */

    /**
     * Default constructor.
     */
    public Student() {
        super();
    }

    /**
     * Constructs a student with basic fields.
     *
     * @param name          student name
     * @param email         email address
     * @param passwordHash  hashed password
     * @param studentNumber unique student number
     */
    public Student(String name, String email, String passwordHash, long studentNumber) {
        super(name, email, passwordHash);
        validateStudentNumber(studentNumber);
        this.studentNumber = studentNumber;
    }

    /**
     * Constructs a student with all fields.
     *
     * @param id            student id
     * @param name          student name
     * @param email         email address
     * @param passwordHash  hashed password
     * @param studentNumber unique student number
     * @param createdAt     creation time
     */
    public Student(long id,
                   String name,
                   String email,
                   String passwordHash,
                   long studentNumber,
                   LocalDateTime createdAt) {
        super(id, name, email, passwordHash, createdAt);
        validateStudentNumber(studentNumber);
        this.studentNumber = studentNumber;
    }

    /* ===================== Getters / Setters ===================== */

    /**
     * Returns the unique student number.
     *
     * @return student number
     */
    public long getStudentNumber() {
        return studentNumber;
    }

    /**
     * Updates the student number.
     *
     * @param studentNumber new student number
     */
    public void setStudentNumber(long studentNumber) {
        validateStudentNumber(studentNumber);
        this.studentNumber = studentNumber;
    }

    /* ===================== Validation helpers ===================== */

    /**
     * Validates the student number.
     *
     * @param studentNumber student number
     */
    private static void validateStudentNumber(long studentNumber) {
        if (studentNumber <= 0) {
            throw new IllegalArgumentException("studentNumber must be a positive integer");
        }
    }

    /* ===================== Type information ===================== */

    @Override
    public String getUserType() {
        return "student";
    }

    /* ===================== Object overrides ===================== */

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        Student student = (Student) o;
        return studentNumber == student.studentNumber;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), studentNumber);
    }

    @Override
    public String toString() {
        return "Student{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", studentNumber=" + studentNumber +
                ", createdAt=" + createdAt +
                '}';
    }
}
