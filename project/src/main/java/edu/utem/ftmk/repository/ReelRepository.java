package edu.utem.ftmk.repository;

import edu.utem.ftmk.database.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ReelRepository {
    public List<Object[]> findDashboardRows() {
        String sql =
                "SELECT r.reel_id, i.instagram_account AS influencer_handle, r.reel_id_instagram, r.reel_url, " +
                "r.identified_by_name, r.identified_date, t.transcript_id, gtr.gt_reel_id, " +
                "GROUP_CONCAT(DISTINCT gti.language_mentioned) AS languages_used " +
                "FROM reel r " +
                "LEFT JOIN influencer i ON r.influencer_id = i.influencer_id " +
                "LEFT JOIN transcript t ON t.reel_id = r.reel_id " +
                "LEFT JOIN ground_truth_reel gtr ON gtr.transcript_id = t.transcript_id " +
                "LEFT JOIN ground_truth_ingredient gti ON gti.gt_reel_id = gtr.gt_reel_id " +
                "GROUP BY r.reel_id, i.instagram_account, r.reel_id_instagram, r.reel_url, r.identified_by_name, " +
                "r.identified_date, t.transcript_id, gtr.gt_reel_id " +
                "ORDER BY r.reel_id";

        List<Object[]> rows = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
            	String influencer =
            	        rs.getString("influencer_handle");
                if (influencer == null || influencer.isBlank()) influencer = "Unknown Influencer";

                rows.add(new Object[]{
                        rs.getInt("reel_id"),
                        influencer,
                        rs.getString("reel_id_instagram"),
                        rs.getString("reel_url"),
                        rs.getString("identified_by_name"),
                        rs.getDate("identified_date") != null ? rs.getDate("identified_date") : "-",
                        languageTag(rs.getString("languages_used")),
                        rs.getObject("transcript_id") != null ? "Available" : "Missing",
                        rs.getObject("gt_reel_id") != null ? "Yes" : "No"
                });
            }
        } catch (Exception e) {
            System.err.println("ReelRepository error: " + e.getMessage());
        }

        return rows;
    }

    private String languageTag(String value) {
        if (value == null || value.isBlank()) return "Not Annotated";
        boolean my = value.contains("MY");
        boolean en = value.contains("EN");
        if (my && en) return "Code-switched";
        if (my) return "Malay";
        if (en) return "English";
        return "Other";
    }
}