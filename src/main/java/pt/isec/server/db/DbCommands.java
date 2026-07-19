package pt.isec.server.db;

import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thin SQLite/JDBC helper with simple, explicit API and didactic flow.
 * <p>
 * Main operations:
 * <ul>
 *   <li>{@link #executeUpdate(String, Object...)} – INSERT/UPDATE/DELETE/DDL (returns affected row count)</li>
 *   <li>{@link #selectOne(String, Object...)} – SELECT (0..1 row) as {@code Map&lt;String,Object&gt;}</li>
 *   <li>{@link #runInTransaction(TransactionWork)} – transactional block using {@link Transaction}</li>
 * </ul>
 */
@SuppressWarnings({ "SqlSourceToSinkFlow", "DuplicatedCode" })
public final class DbCommands {

    /* ========================= 0) CONFIG ========================= */

    /**
     * JDBC URL for the database, e.g. {@code "jdbc:sqlite:/abs/path/quiz.db"}.
     */
    private final String url;

    /**
     * Constructs the helper with a JDBC URL.
     *
     * @param url JDBC URL for SQLite
     */
    public DbCommands(String url) {
        this.url = url;
    }

    /**
     * Exposes the JDBC URL when a direct {@link Connection} is needed elsewhere.
     *
     * @return JDBC URL string
     */
    public String getUrl() {
        return url;
    }

    /* ========================= 1) SINGLE OPERATIONS ========================= */

    /**
     * Returns the current {@code db_version} from {@code config} (using a new connection).
     *
     * @return DB version or {@code -1} if missing or {@code NULL}
     */
    public long get_db_version() {
        final String sql = "SELECT db_version FROM config WHERE id = 1";
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (!rs.next()) {
                return -1L;
            }
            long v = rs.getLong("db_version");
            return rs.wasNull() ? -1L : v;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Executes INSERT/UPDATE/DELETE/DDL using its own connection.
     * <p>
     * If it affects at least one row, the DB version is incremented.
     *
     * @param sql  SQL statement with {@code ?} placeholders
     * @param args arguments for the placeholders
     * @return number of affected rows
     */
    public int executeUpdate(String sql, Object... args) {
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            bind(ps, args);
            int x = ps.executeUpdate();
            if (x > 0) {
                update_db_version(c);
            }
            return x;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Executes a SELECT expected to return at most one row.
     *
     * @param sql  SQL statement with {@code ?} placeholders
     * @param args arguments for the placeholders
     * @return row as {@code Map&lt;String,Object&gt;} or {@code null} if no row
     */
    public Map<String, Object> selectOne(String sql, Object... args) {
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            bind(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return mapRow(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Increments {@code db_version} in {@code config}.
     *
     * @param c open connection
     */
    private void update_db_version(Connection c) {
        final String sql = "UPDATE config SET db_version = db_version + 1 WHERE id = 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.executeUpdate();
            // Version is now updated in the database; callers should read it directly when needed.
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /* ========================= 2) TRANSACTIONS ========================= */

    /**
     * Runs {@link TransactionWork} inside a database transaction.
     * <p>
     * On success, commits and increments {@code db_version}. On failure, rolls back.
     *
     * @param work transactional work block
     * @throws Exception if the inner work throws an exception
     */
    public void runInTransaction(TransactionWork work) throws Exception {
        try (Connection c = openConnection()) {
            c.setAutoCommit(false);
            try {
                Transaction tx = new Transaction(c);
                work.run(tx);
                update_db_version(c);
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /**
     * Functional interface for transactional work.
     */
    public interface TransactionWork {
        /**
         * Callback executed within a transaction.
         *
         * @param tx transaction context
         * @throws Exception if anything goes wrong
         */
        void run(Transaction tx) throws Exception;
    }

    /**
     * Transaction context wrapper that reuses the same {@link Connection}.
     */
    public static final class Transaction {
        private final Connection connection;
        private String executedSql;


        private Transaction(Connection connection) {
            this.connection = connection;
        }

        /**
         * Executes INSERT/UPDATE/DELETE/DDL inside this transaction.
         *
         * @param sql  SQL statement with {@code ?} placeholders
         * @param args arguments for placeholders
         */
        @SuppressWarnings("unusedReturnValue")
        public void executeUpdate(String sql, Object... args) {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                bind(ps, args);
                int x = ps.executeUpdate();
                if (x > 0) {
                    // recorded for potential debugging or external use
                    executedSql = sql;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Executes a SELECT expected to return at most one row inside this transaction.
         *
         * @param sql  SQL statement with {@code ?} placeholders
         * @param args arguments for placeholders
         * @return row as {@code Map&lt;String,Object&gt;} or {@code null} if no row
         */
        @SuppressWarnings("unused")
        public Map<String, Object> selectOne(String sql, Object... args) {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                bind(ps, args);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    return mapRow(rs);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Returns the last {@code rowid} generated by an INSERT on this connection (SQLite).
         *
         * @return last inserted row id or {@code -1} on failure
         */
        public long getLastInsertId() {
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery("SELECT last_insert_rowid() AS id")) {
                return rs.next() ? rs.getLong("id") : -1L;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Returns the last executed SQL string inside this transaction, if any.
         *
         * @return SQL string or {@code null}
         */
        @SuppressWarnings("unused")
        String getExecutedSql() {
            return executedSql;
        }
    }

    /* ========================= 3) INTERNAL HELPERS ========================= */

    /**
     * Opens a new {@link Connection} and applies useful SQLite pragma directives.
     *
     * @return open connection
     * @throws SQLException if an error occurs
     */
    private Connection openConnection() throws SQLException {
        Connection c = DriverManager.getConnection(url);
        try (Statement st = c.createStatement()) {
            st.execute("PRAGMA foreign_keys=ON");
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA busy_timeout=5000");
        }
        return c;
    }

    /**
     * Binds Java values to {@code ?} parameters of the given statement, in order.
     * <p>
     * Helps prevent SQL injection and ensures correct typing.
     *
     * @param ps   prepared statement
     * @param args values to bind
     * @throws SQLException if binding fails
     */
    private static void bind(PreparedStatement ps, Object... args) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            ps.setObject(i + 1, args[i]);
        }
    }

    /**
     * Converts the current row of a {@link ResultSet} into a {@code Map&lt;String,Object&gt;}
     * mapping column names to values.
     *
     * @param rs result set positioned at a valid row
     * @return row map
     * @throws SQLException if metadata or value access fails
     */
    private static Map<String, Object> mapRow(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int n = md.getColumnCount();
        Map<String, Object> row = new LinkedHashMap<>(n);
        for (int i = 1; i <= n; i++) {
            String name = md.getColumnLabel(i);
            if (name == null || name.isBlank()) {
                name = md.getColumnName(i);
            }
            row.put(name, rs.getObject(i));
        }
        return row;
    }
}
