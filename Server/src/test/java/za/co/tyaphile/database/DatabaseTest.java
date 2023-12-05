package za.co.tyaphile.database;

import org.junit.jupiter.api.Test;
import za.co.tyaphile.database.connect.Connect;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class DatabaseTest {

    private final String user = "CREATE TABLE IF NOT EXISTS users (" +
            "user_id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
            "user_name BIGINT UNIQUE NOT NULL, " +
            "user_email VARCHAR(255) NOT NULL" +
            ");";
    private final String addJohn = "INSERT INTO users (user_name, user_email) VALUES ('John', 'john@example.com');";
    private final String fetchJohn = "SELECT * FROM users;";
    @Test
    void testDatabaseConnection() throws SQLException {
        Connection connection = Connect.getConnection(":memory:");
        assertFalse(connection.isClosed());

        PreparedStatement statement = connection.prepareStatement(user);
        assertEquals(0, statement.executeUpdate());

        statement = connection.prepareStatement(addJohn);
        assertEquals(1, statement.executeUpdate());

        statement = connection.prepareStatement(fetchJohn);
        ResultSet rs = statement.executeQuery();
        while(rs.next()) {
            assertEquals(0, rs.getInt(1));
            assertEquals("John", rs.getString(2));
            assertEquals("john@example.com", rs.getString(3));
        }
    }
}
