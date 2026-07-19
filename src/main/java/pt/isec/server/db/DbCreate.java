package pt.isec.server.db;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;

/**
 * Utility class that creates the SQLite {@code .db} file if it does not exist
 * (or if it has no tables) and applies the {@code schema.sql} script from the classpath.
 */
public final class DbCreate {
    private DbCreate() {}

    /**
     * Ensures that the database file exists and that the schema is applied.
     * <p>
     * If the file is missing or does not contain the {@code config} table,
     * the given SQL script is executed.
     *
     * @param dbPath                   absolute path to the {@code .db} file
     * @param schemaResourceOnClasspath classpath resource path (e.g. {@code "/db/schema.sql"})
     * @throws Exception if database or I/O errors occur
     */
    public static void createIfMissing(Path dbPath, String schemaResourceOnClasspath) throws Exception {
        Files.createDirectories(dbPath.getParent());

        boolean needSchema = !Files.exists(dbPath);

        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath())) {
            if (!needSchema) {
                // If the file exists, check if table 'config' exists
                try (ResultSet rs = c.getMetaData().getTables(null, null, "config", null)) {
                    needSchema = !rs.next();
                }
            }

            if (needSchema) {
                try (InputStream is = DbCreate.class.getResourceAsStream(schemaResourceOnClasspath)) {
                    if (is == null) {
                        throw new IllegalStateException("Resource not found: " + schemaResourceOnClasspath);
                    }
                    runSqlScript(c, is);
                }
            }
        }
    }

    /**
     * Executes the SQL script, handling {@code CREATE TRIGGER ... BEGIN ... END;} blocks correctly.
     *
     * @param c          open database connection
     * @param sqlStream  input stream with SQL text
     * @throws Exception if reading or executing SQL fails
     */
    private static void runSqlScript(Connection c, InputStream sqlStream) throws Exception {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(sqlStream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            boolean inTrigger = false;

            try (Statement st = c.createStatement()) {
                String line;
                while ((line = br.readLine()) != null) {
                    String trimmed = line.trim();
                    // ignore '-- ...' comments and empty lines
                    if (trimmed.startsWith("--") || trimmed.isEmpty()) {
                        continue;
                    }

                    // detect trigger start (case-insensitive)
                    String upper = trimmed.toUpperCase();
                    if (!inTrigger && upper.startsWith("CREATE TRIGGER")) {
                        inTrigger = true;
                    }

                    sb.append(line).append('\n');

                    if (inTrigger) {
                        // inside trigger: only execute when END; is found
                        if (upper.equals("END;") || upper.endsWith("\nEND;")) {
                            String stmt = sb.toString().trim();
                            if (!stmt.isBlank()) {
                                st.execute(stmt);
                            }
                            sb.setLength(0);
                            inTrigger = false;
                        }
                    } else {
                        // normal statements: execute when ending with ';'
                        if (trimmed.endsWith(";")) {
                            String stmt = sb.toString().trim();
                            if (!stmt.isBlank()) {
                                st.execute(stmt);
                            }
                            sb.setLength(0);
                        }
                    }
                }

                // leftover without final ';'
                String leftover = sb.toString().trim();
                if (!leftover.isBlank()) {
                    st.execute(leftover);
                }
            }
        }
    }
}
