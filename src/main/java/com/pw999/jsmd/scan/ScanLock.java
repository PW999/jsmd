package com.pw999.jsmd.scan;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/**
 * Cluster-wide mutual exclusion for scans using a PostgreSQL advisory lock. The lock is transaction-scoped on a
 * dedicated connection, so it is released by the database even if this process dies or the connection is recycled.
 */
@Component
public class ScanLock {

    private static final long KEY = 0x6A736D6453434E31L;

    private final DataSource dataSource;

    public ScanLock(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Runs the task if no other scan holds the lock.
     *
     * @return false if another scan is running and the task was skipped
     */
    public boolean runExclusively(Runnable task) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (!tryLock(connection)) {
                    return false;
                }
                task.run();
                return true;
            } finally {
                connection.rollback();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot take the scan lock", e);
        }
    }

    private static boolean tryLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_try_advisory_xact_lock(?)")) {
            statement.setLong(1, KEY);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }
}
