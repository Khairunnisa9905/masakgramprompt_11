package edu.utem.ftmk.repository;

import edu.utem.ftmk.database.DBConnection;
import edu.utem.ftmk.util.FileTextReader;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

public class TranscriptRepository {
    private final FileTextReader reader = new FileTextReader();

    public List<Integer> findAllTranscriptIds() {
        List<Integer> ids = new ArrayList<>();
        String sql = "SELECT transcript_id FROM transcript ORDER BY transcript_id";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) ids.add(rs.getInt("transcript_id"));
        } catch (Exception e) {
            System.err.println("findAllTranscriptIds error: " + e.getMessage());
        }

        return ids;
    }

    public String readTranscriptText(Connection conn, int transcriptId) throws Exception {
        String sql = "SELECT file_path FROM transcript WHERE transcript_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, transcriptId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return reader.read(rs.getString("file_path"));
            }
        }
        return null;
    }

    public String readTranscriptTextByReelId(int reelId) throws Exception {
        String sql = "SELECT file_path FROM transcript WHERE reel_id = ? LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, reelId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return reader.read(rs.getString("file_path"));
            }
        }
        return null;
    }
    
    public List<String> findMalayTermsByReelId(
            int reelId) throws Exception {

        String sql =
                "SELECT DISTINCT gti.name_original " +
                "FROM ground_truth_ingredient gti " +
                "JOIN ground_truth_reel gtr " +
                "ON gti.gt_reel_id=gtr.gt_reel_id " +
                "JOIN transcript t " +
                "ON gtr.transcript_id=t.transcript_id " +
                "WHERE t.reel_id=? " +
                "AND gti.language_mentioned='MY' " +
                "AND gti.name_original IS NOT NULL " +
                "ORDER BY gti.name_original";

        Set<String> terms =
                new LinkedHashSet<>();

        try (Connection conn =
                     DBConnection.getConnection();

             PreparedStatement ps =
                     conn.prepareStatement(sql)) {

            ps.setInt(1, reelId);

            try (ResultSet rs =
                         ps.executeQuery()) {

                while (rs.next()) {

                    String term =
                            rs.getString(
                                    "name_original"
                            );

                    if (term != null
                            && !term.isBlank()) {

                        terms.add(term.trim());
                    }
                }
            }
        }

        return new ArrayList<>(terms);
    }
}