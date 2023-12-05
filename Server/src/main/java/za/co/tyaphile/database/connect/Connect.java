package za.co.tyaphile.database.connect;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class Connect {
    private static Connection connection;
    static private PreparedStatement preparedStatement;

    public static Connection getConnection(final String database) {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + database);
        } catch (ClassNotFoundException | SQLException e) {
            System.err.println("Error: " + e);
            for(StackTraceElement ste: e.getStackTrace()) {
                System.err.println("Error: " + ste);
            }
        }
        return connection;
    }
}
