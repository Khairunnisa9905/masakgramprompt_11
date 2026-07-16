package edu.utem.ftmk.repository;

import java.sql.*;

public class PromptRepository {
    public String[] findPromptFiles(Connection conn, int techniqueId) throws Exception {
        String sql = "SELECT system_prompt_file, user_prompt_file FROM prompt_technique WHERE technique_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, techniqueId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new String[]{
                            rs.getString("system_prompt_file"),
                            rs.getString("user_prompt_file")
                    };
                }
            }
        }
        return null;
    }
}