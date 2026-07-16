package edu.utem.ftmk.database;

import java.sql.Connection;

public class DatabaseTest {
    public static void main(String[] args) {
        try (Connection conn = DBConnection.getConnection()) {
            System.out.println("SUCCESS: Connected to masakgramprompt database.");
        } catch (Exception e) {
            System.err.println("FAILED: " + e.getMessage());
        }
    }
}