package pt.isec.common.model.user;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Base class for domain users (e.g. Teacher / Student).
 * <p>
 * Provides common fields, basic validation and a small utility API.
 */
public abstract class User implements Serializable {

    /* ======================= SERIALIZATION ======================= */

    /**
     * Serialization version marker for compatibility.
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /* ======================= FIELDS ======================= */

    /** Database identifier (0 means not yet persisted). */
    protected long id;

    /** Human-readable name of the user. */
    protected String name;

    /** E-mail address, expected to be unique per user type. */
    protected String email;

    /** Hash of the user's password (never store raw passwords). */
    protected String passwordHash;

    /** Creation timestamp of this user record. */
    protected LocalDateTime createdAt;

    /* ======================= CONSTRUCTORS ======================= */

    /**
     * Default constructor; initializes {@link #createdAt} with {@link LocalDateTime#now()}.
     */
    protected User() {
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Constructs a user with basic fields.
     *
     * @param name         user name
     * @param email        email address
     * @param passwordHash hashed password
     * @throws IllegalArgumentException if any argument is invalid
     */
    protected User(String name, String email, String passwordHash) {
        validate(name, email, passwordHash);
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Constructs a user with all fields.
     *
     * @param id           user id
     * @param name         user name
     * @param email        email address
     * @param passwordHash hashed password
     * @param createdAt    creation time (or {@code now} if {@code null})
     * @throws IllegalArgumentException if name, email or passwordHash are invalid
     */
    protected User(long id, String name, String email, String passwordHash, LocalDateTime createdAt) {
        validate(name, email, passwordHash);
        this.id = id;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    /* ======================= GETTERS ======================= */

    /**
     * @return user identifier
     */
    public long getId() {
        return id;
    }

    /**
     * @return user name
     */
    public String getName() {
        return name;
    }

    /**
     * @return user e-mail address
     */
    public String getEmail() {
        return email;
    }

    /**
     * Returns the password hash.
     * <p>
     * Currently not used directly in the client code, but kept for completeness
     * and for possible future extensions (e.g. export, admin tools).
     *
     * @return hashed password
     */
    @SuppressWarnings("unused")
    public String getPasswordHash() {
        return passwordHash;
    }

    /**
     * Returns the creation timestamp of this user.
     *
     * @return creation instant
     */
    @SuppressWarnings("unused")
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /* ======================= SETTERS ======================= */

    /**
     * Sets the user identifier.
     *
     * @param id new identifier
     */
    public void setId(long id) {
        this.id = id;
    }

    /**
     * Sets the user name.
     *
     * @param name new user name (must not be {@code null} or blank)
     * @throws IllegalArgumentException if {@code name} is invalid
     */
    public void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name cannot be null or blank");
        }
        this.name = name;
    }

    /**
     * Sets the user e-mail address.
     *
     * @param email new e-mail (must be syntactically valid)
     * @throws IllegalArgumentException if {@code email} is invalid
     */
    public void setEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email cannot be null or blank");
        }
        if (!email.contains("@") || !email.contains(".")) {
            throw new IllegalArgumentException("email must be a valid address");
        }
        this.email = email;
    }

    /**
     * Sets the stored password hash.
     *
     * @param passwordHash new password hash (must not be {@code null} or blank)
     * @throws IllegalArgumentException if {@code passwordHash} is invalid
     */
    @SuppressWarnings("unused")
    public void setPasswordHash(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("passwordHash cannot be null or blank");
        }
        this.passwordHash = passwordHash;
    }

    /**
     * Sets the creation timestamp.
     *
     * @param createdAt new creation instant
     */
    @SuppressWarnings("unused")
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /* ======================= VALIDATION ======================= */

    /**
     * Basic validation used by constructors.
     *
     * @param name         user name
     * @param email        email address
     * @param passwordHash hashed password
     * @throws IllegalArgumentException if any argument is invalid
     */
    protected static void validate(String name, String email, String passwordHash) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name cannot be null or blank");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email cannot be null or blank");
        }
        if (!email.contains("@") || !email.contains(".")) {
            throw new IllegalArgumentException("email must be a valid address");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("passwordHash cannot be null or blank");
        }
    }

    /* ======================= TYPE INFORMATION ======================= */

    /**
     * Returns the user type (for example, {@code "student"} or {@code "teacher"}).
     *
     * @return user type string
     */
    @SuppressWarnings("unused")
    public abstract String getUserType();

    /* ======================= OBJECT CONTRACT ======================= */

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        User user = (User) o;
        return id == user.id &&
                Objects.equals(email, user.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, email);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + '{' +
                "id=" + id +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
