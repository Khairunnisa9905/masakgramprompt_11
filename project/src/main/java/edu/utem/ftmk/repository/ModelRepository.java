package edu.utem.ftmk.repository;

import edu.utem.ftmk.model.ModelInfo;
import java.sql.*;

public class ModelRepository {
    public String findModelTag(Connection conn, int modelId) throws Exception {
        String sql = "SELECT model_tag FROM llm_model WHERE model_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, modelId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("model_tag");
            }
        }
        return ModelInfo.fromId(modelId).modelTag();
    }
}