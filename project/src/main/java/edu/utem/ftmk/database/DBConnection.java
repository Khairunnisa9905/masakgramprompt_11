package edu.utem.ftmk.database;

import edu.utem.ftmk.config.AppConfig;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DBConnection {
    private DBConnection() {}

    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC Driver missing.", e);
        }
        return DriverManager.getConnection(AppConfig.DB_URL, AppConfig.DB_USER, AppConfig.DB_PASSWORD);
    }
}