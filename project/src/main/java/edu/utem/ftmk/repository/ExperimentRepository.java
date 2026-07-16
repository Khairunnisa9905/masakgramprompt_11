package edu.utem.ftmk.repository;

import edu.utem.ftmk.database.DBConnection;

import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class ExperimentRepository {
    public int upsertRunningExperiment(Connection conn, int transcriptId, int modelId, int techniqueId) throws Exception {
        String find = "SELECT experiment_id FROM experiment WHERE transcript_id=? AND model_id=? AND technique_id=?";
        try (PreparedStatement ps = conn.prepareStatement(find)) {
            ps.setInt(1, transcriptId);
            ps.setInt(2, modelId);
            ps.setInt(3, techniqueId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("experiment_id");
                    updateStatus(conn, id, "running");
                    clearPreviousResults(conn, id);
                    return id;
                }
            }
        }

        String insert = "INSERT INTO experiment (transcript_id, model_id, technique_id, rag_enabled, status, executed_at) " +
                "VALUES (?, ?, ?, FALSE, 'running', CURRENT_TIMESTAMP)";
        try (PreparedStatement ps = conn.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, transcriptId);
            ps.setInt(2, modelId);
            ps.setInt(3, techniqueId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }

        throw new Exception("Failed to create experiment row.");
    }

    public void updateStatus(Connection conn, int experimentId, String status) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE experiment SET status=?, executed_at=CURRENT_TIMESTAMP WHERE experiment_id=?")) {
            ps.setString(1, status);
            ps.setInt(2, experimentId);
            ps.executeUpdate();
        }
    }

    public Map<String, String> findAggregateStatus() throws Exception {
        String sql =
                "SELECT m.model_name, pt.technique_name, " +
                "SUM(CASE WHEN e.status='completed' THEN 1 ELSE 0 END) AS completed_count, " +
                "SUM(CASE WHEN e.status='failed' THEN 1 ELSE 0 END) AS failed_count, " +
                "(SELECT COUNT(*) FROM transcript) AS total_transcripts " +
                "FROM experiment e " +
                "JOIN llm_model m ON e.model_id=m.model_id " +
                "JOIN prompt_technique pt ON e.technique_id=pt.technique_id " +
                "GROUP BY m.model_name, pt.technique_name";

        Map<String, String> map = new LinkedHashMap<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                int completed = rs.getInt("completed_count");
                int failed = rs.getInt("failed_count");
                int totalTranscripts = rs.getInt("total_transcripts");

                String state;

                if (totalTranscripts > 0 && completed >= totalTranscripts && failed == 0) {
                    state = "COMPLETED";
                } else if (failed > 0) {
                    state = completed + "/" + totalTranscripts + " (" + failed + " FAILED)";
                } else if (completed > 0) {
                    state = completed + "/" + totalTranscripts;
                } else {
                    state = "PENDING";
                }

                map.put(rs.getString("model_name") + "_" + rs.getString("technique_name"), state);
            }
        }

        return map;
    }

    private void clearPreviousResults(
            Connection conn,
            int experimentId) throws SQLException {

        String deleteIngredients =
                "DELETE ir " +
                "FROM ingredient_result ir " +
                "JOIN nutrition_result nr " +
                "ON ir.result_id = nr.result_id " +
                "WHERE nr.experiment_id = ?";

        try (PreparedStatement ps =
                     conn.prepareStatement(deleteIngredients)) {

            ps.setInt(1, experimentId);
            ps.executeUpdate();
        }

        String deleteNutrition =
                "DELETE FROM nutrition_result " +
                "WHERE experiment_id = ?";

        try (PreparedStatement ps =
                     conn.prepareStatement(deleteNutrition)) {

            ps.setInt(1, experimentId);
            ps.executeUpdate();
        }
    }
    public Map<String, String> findStatusesForReel(
            int reelId) throws Exception {

        String sql =
                "SELECT m.model_name, " +
                "pt.technique_name, " +
                "e.status " +

                "FROM experiment e " +

                "JOIN transcript t " +
                "ON e.transcript_id=t.transcript_id " +

                "JOIN llm_model m " +
                "ON e.model_id=m.model_id " +

                "JOIN prompt_technique pt " +
                "ON e.technique_id=pt.technique_id " +

                "WHERE t.reel_id=?";

        Map<String, String> statuses =
                new LinkedHashMap<>();

        try (Connection conn =
                     DBConnection.getConnection();

             PreparedStatement ps =
                     conn.prepareStatement(sql)) {

            ps.setInt(1, reelId);

            try (ResultSet rs =
                         ps.executeQuery()) {

                while (rs.next()) {

                    String key =
                            rs.getString("model_name")
                                    + "_"
                                    + rs.getString(
                                    "technique_name"
                            );

                    statuses.put(
                            key,
                            rs.getString("status")
                                    .toUpperCase()
                    );
                }
            }
        }

        return statuses;
    }
}