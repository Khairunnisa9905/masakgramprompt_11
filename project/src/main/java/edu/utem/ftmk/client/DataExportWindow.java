package edu.utem.ftmk.client;

import edu.utem.ftmk.database.DBConnection;
import edu.utem.ftmk.util.CsvUtil;
import edu.utem.ftmk.service.Layer1AExactMatchExportService;
import edu.utem.ftmk.service.AlignedNumericExportService;
import edu.utem.ftmk.service.DetectionMetricsExportService;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class DataExportWindow extends JFrame {
	private final Layer1AExactMatchExportService layer1AExporter =
	        new Layer1AExactMatchExportService();

	private final AlignedNumericExportService alignedNumericExporter =
	        new AlignedNumericExportService();
	private final DetectionMetricsExportService detectionMetricsExporter =
	        new DetectionMetricsExportService();
    private final Map<String, JCheckBox> checkboxes = new LinkedHashMap<>();
    private final Map<String, String> fileNames = new LinkedHashMap<>();
    private final Map<String, String> queries = new LinkedHashMap<>();
    private JLabel statusLabel;

    public DataExportWindow() {
        setTitle("MasakGramPrompt - Data Export (Requirement 5.5)");
        setSize(820, 620);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        registerQueries();
        buildUI();
    }

    private void registerQueries() {
        addQuery("LAYER 1A", "layer1a_exact_match.csv",
                "SELECT e.experiment_id, e.transcript_id, r.reel_id_instagram AS video_id, " +
                "m.model_name, pt.technique_name, e.rag_enabled, " +
                "gti.name_original AS gt_name_original, gti.name_en AS gt_name_en, " +
                "gti.quantity_unit_culinary AS gt_unit_original, gti.quantity_unit_culinary AS gt_unit_en, " +
                "ir.name_original AS pred_name_original, ir.name_en AS pred_name_en, " +
                "ir.unit_original AS pred_unit_original, ir.unit_en AS pred_unit_en " +
                "FROM experiment e " +
                "JOIN transcript t ON e.transcript_id=t.transcript_id " +
                "JOIN reel r ON t.reel_id=r.reel_id " +
                "JOIN llm_model m ON e.model_id=m.model_id " +
                "JOIN prompt_technique pt ON e.technique_id=pt.technique_id " +
                "JOIN nutrition_result nr ON e.experiment_id=nr.experiment_id " +
                "JOIN ingredient_result ir ON nr.result_id=ir.result_id " +
                "JOIN ground_truth_reel gtr ON t.transcript_id=gtr.transcript_id " +
                "JOIN ground_truth_ingredient gti ON gtr.gt_reel_id=gti.gt_reel_id " +
                "WHERE e.status='completed' " +
                "ORDER BY e.experiment_id, gti.gt_ingredient_id");

        addQuery("LAYER 1B", "layer1b_text_similarity.csv",
                "SELECT e.experiment_id, r.reel_id_instagram AS video_id, " +
                "m.model_name, pt.technique_name, e.rag_enabled, " +
                "gti.name_original AS gt_name_original, gti.name_en AS gt_name_en, " +
                "ir.name_original AS pred_name_original, ir.name_en AS pred_name_en " +
                "FROM experiment e " +
                "JOIN transcript t ON e.transcript_id=t.transcript_id " +
                "JOIN reel r ON t.reel_id=r.reel_id " +
                "JOIN llm_model m ON e.model_id=m.model_id " +
                "JOIN prompt_technique pt ON e.technique_id=pt.technique_id " +
                "JOIN nutrition_result nr ON e.experiment_id=nr.experiment_id " +
                "JOIN ingredient_result ir ON nr.result_id=ir.result_id " +
                "JOIN ground_truth_reel gtr ON t.transcript_id=gtr.transcript_id " +
                "JOIN ground_truth_ingredient gti ON gtr.gt_reel_id=gti.gt_reel_id " +
                "WHERE e.status='completed' " +
                "ORDER BY e.experiment_id, gti.gt_ingredient_id");

        addQuery("LAYER 2A", "layer2a_numeric_quantity.csv",
                "SELECT e.experiment_id, r.reel_id_instagram AS video_id, " +
                "m.model_name, pt.technique_name, e.rag_enabled, " +
                "gti.quantity_value_culinary AS gt_quantity_value, gti.estimated_weight_g AS gt_weight_g, " +
                "ir.quantity_value AS pred_quantity_value, ir.estimated_weight_g AS pred_weight_g " +
                "FROM experiment e " +
                "JOIN transcript t ON e.transcript_id=t.transcript_id " +
                "JOIN reel r ON t.reel_id=r.reel_id " +
                "JOIN llm_model m ON e.model_id=m.model_id " +
                "JOIN prompt_technique pt ON e.technique_id=pt.technique_id " +
                "JOIN nutrition_result nr ON e.experiment_id=nr.experiment_id " +
                "JOIN ingredient_result ir ON nr.result_id=ir.result_id " +
                "JOIN ground_truth_reel gtr ON t.transcript_id=gtr.transcript_id " +
                "JOIN ground_truth_ingredient gti ON gtr.gt_reel_id=gti.gt_reel_id " +
                "WHERE e.status='completed' " +
                "ORDER BY e.experiment_id, gti.gt_ingredient_id");

        addQuery("LAYER 2B", "layer2b_numeric_nutrition.csv",
                "SELECT e.experiment_id, r.reel_id_instagram AS video_id, " +
                "m.model_name, pt.technique_name, e.rag_enabled, " +
                "gti.calories AS gt_calories, gti.protein_g AS gt_protein_g, " +
                "gti.total_fat_g AS gt_fat_g, gti.total_carbohydrate_g AS gt_carbohydrate_g, " +
                "ir.calories AS pred_calories, ir.protein_g AS pred_protein_g, " +
                "ir.total_fat_g AS pred_fat_g, ir.total_carbohydrate_g AS pred_carbohydrate_g " +
                "FROM experiment e " +
                "JOIN transcript t ON e.transcript_id=t.transcript_id " +
                "JOIN reel r ON t.reel_id=r.reel_id " +
                "JOIN llm_model m ON e.model_id=m.model_id " +
                "JOIN prompt_technique pt ON e.technique_id=pt.technique_id " +
                "JOIN nutrition_result nr ON e.experiment_id=nr.experiment_id " +
                "JOIN ingredient_result ir ON nr.result_id=ir.result_id " +
                "JOIN ground_truth_reel gtr ON t.transcript_id=gtr.transcript_id " +
                "JOIN ground_truth_ingredient gti ON gtr.gt_reel_id=gti.gt_reel_id " +
                "WHERE e.status='completed' " +
                "ORDER BY e.experiment_id, gti.gt_ingredient_id");

        addQuery("LAYER 2C", "layer2c_nutrition_totals.csv",
                "SELECT e.experiment_id, r.reel_id_instagram AS video_id, " +
                "m.model_name, pt.technique_name, e.rag_enabled, " +
                "SUM(gti.calories) AS gt_total_calories, " +
                "SUM(gti.protein_g) AS gt_total_protein_g, " +
                "SUM(gti.total_fat_g) AS gt_total_fat_g, " +
                "SUM(gti.total_carbohydrate_g) AS gt_total_carbohydrate_g, " +
                "nr.total_calories AS pred_total_calories, " +
                "nr.total_protein_g AS pred_total_protein_g, " +
                "nr.total_fat_g AS pred_total_fat_g, " +
                "nr.total_carbohydrate_g AS pred_total_carbohydrate_g " +
                "FROM experiment e " +
                "JOIN transcript t ON e.transcript_id=t.transcript_id " +
                "JOIN reel r ON t.reel_id=r.reel_id " +
                "JOIN llm_model m ON e.model_id=m.model_id " +
                "JOIN prompt_technique pt ON e.technique_id=pt.technique_id " +
                "JOIN nutrition_result nr ON e.experiment_id=nr.experiment_id " +
                "JOIN ground_truth_reel gtr ON t.transcript_id=gtr.transcript_id " +
                "JOIN ground_truth_ingredient gti ON gtr.gt_reel_id=gti.gt_reel_id " +
                "WHERE e.status='completed' " +
                "AND gti.annotation_layer='layer2' " +
                "GROUP BY e.experiment_id, r.reel_id_instagram, m.model_name, pt.technique_name, " +
                "e.rag_enabled, nr.total_calories, nr.total_protein_g, nr.total_fat_g, nr.total_carbohydrate_g " +
                "ORDER BY e.experiment_id");

        addQuery("LAYER 3A", "layer3a_json_validity.csv",
                "SELECT m.model_name, pt.technique_name, e.rag_enabled, COUNT(*) AS total_runs, " +
                "SUM(CASE WHEN nr.json_valid=TRUE THEN 1 ELSE 0 END) AS valid_count, " +
                "SUM(CASE WHEN nr.json_valid=FALSE THEN 1 ELSE 0 END) AS invalid_count, " +
                "ROUND(SUM(CASE WHEN nr.json_valid=TRUE THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 2) AS validity_rate_pct " +
                "FROM experiment e " +
                "JOIN llm_model m ON e.model_id=m.model_id " +
                "JOIN prompt_technique pt ON e.technique_id=pt.technique_id " +
                "JOIN nutrition_result nr ON e.experiment_id=nr.experiment_id " +
                "WHERE e.status='completed' " +
                "GROUP BY m.model_name, pt.technique_name, e.rag_enabled " +
                "ORDER BY m.model_name, pt.technique_name");

        addQuery("LAYER 3B", "layer3b_hallucination.csv",
                "SELECT e.experiment_id, r.reel_id_instagram AS video_id, " +
                "m.model_name, pt.technique_name, e.rag_enabled, " +
                "ir.name_original AS pred_name_original, ir.name_en AS pred_name_en, " +
                hallucinationCaseSql() + " AS is_hallucinated " +
                "FROM experiment e " +
                "JOIN transcript t ON e.transcript_id=t.transcript_id " +
                "JOIN reel r ON t.reel_id=r.reel_id " +
                "JOIN llm_model m ON e.model_id=m.model_id " +
                "JOIN prompt_technique pt ON e.technique_id=pt.technique_id " +
                "JOIN nutrition_result nr ON e.experiment_id=nr.experiment_id " +
                "JOIN ingredient_result ir ON nr.result_id=ir.result_id " +
                "WHERE e.status='completed' " +
                "ORDER BY e.experiment_id, ir.ingredient_id");

        
        
        addQuery(
                "LAYER 3C",
                "layer3c_ingredient_detection.csv",
                "SELECT 1 WHERE 1=0"
        );
        
        addQuery("LAYER 4", "layer4_human_evaluation.csv",
        	    "SELECT " +
        	    "NULL AS evaluation_id, " +
        	    "NULL AS result_id, " +
        	    "NULL AS experiment_id, " +
        	    "NULL AS video_id, " +
        	    "NULL AS model_name, " +
        	    "NULL AS technique_name, " +
        	    "NULL AS annotator_id, " +
        	    "NULL AS fluency_score, " +
        	    "NULL AS completeness_score, " +
        	    "NULL AS plausibility_score, " +
        	    "NULL AS evaluated_at " +
        	    "WHERE 1=0");

        addQuery("LAYER 5", "layer5_condition_scores.csv",
                "SELECT r.reel_id_instagram AS video_id, m.model_name, pt.technique_name, e.rag_enabled, " +
                "COUNT(DISTINCT ir.ingredient_id) AS pred_count, " +
                "SUM(CASE WHEN " + hallucinationCaseSql() + "=FALSE THEN 1 ELSE 0 END) AS true_positives, " +
                "SUM(CASE WHEN " + hallucinationCaseSql() + "=TRUE THEN 1 ELSE 0 END) AS false_positives, " +
                "COUNT(DISTINCT gti.gt_ingredient_id) AS gt_count, " +
                "nr.json_valid, nr.total_calories AS pred_total_kcal, SUM(gti.calories) AS gt_total_kcal " +
                "FROM experiment e " +
                "JOIN transcript t ON e.transcript_id=t.transcript_id " +
                "JOIN reel r ON t.reel_id=r.reel_id " +
                "JOIN llm_model m ON e.model_id=m.model_id " +
                "JOIN prompt_technique pt ON e.technique_id=pt.technique_id " +
                "JOIN nutrition_result nr ON e.experiment_id=nr.experiment_id " +
                "JOIN ingredient_result ir ON nr.result_id=ir.result_id " +
                "JOIN ground_truth_reel gtr ON t.transcript_id=gtr.transcript_id " +
                "JOIN ground_truth_ingredient gti ON gtr.gt_reel_id=gti.gt_reel_id " +
                "WHERE e.status='completed' " +
                "GROUP BY r.reel_id_instagram, m.model_name, pt.technique_name, e.rag_enabled, " +
                "nr.json_valid, nr.total_calories " +
                "ORDER BY r.reel_id_instagram, m.model_name, pt.technique_name");
    }

    private String hallucinationCaseSql() {

        String gtOriginal =
                "NULLIF(LOWER(TRIM(gti2.name_original)), '')";

        String gtEnglish =
                "NULLIF(LOWER(TRIM(gti2.name_en)), '')";

        String predOriginal =
                "NULLIF(LOWER(TRIM(ir.name_original)), '')";

        String predEnglish =
                "NULLIF(LOWER(TRIM(ir.name_en)), '')";

        return "CASE WHEN EXISTS (" +

                "SELECT 1 " +
                "FROM ground_truth_reel gtr2 " +

                "JOIN ground_truth_ingredient gti2 " +
                "ON gtr2.gt_reel_id=gti2.gt_reel_id " +

                "WHERE gtr2.transcript_id=e.transcript_id " +

                "AND (" +

                // Containment is allowed only for faithful
                // original-language ingredient names.
                comparableNames(
                        gtOriginal,
                        predOriginal
                ) +

                " OR " +

                exactNames(
                        gtOriginal,
                        predEnglish
                ) +

                " OR " +

                exactNames(
                        gtEnglish,
                        predOriginal
                ) +

                " OR " +

                exactNames(
                        gtEnglish,
                        predEnglish
                ) +

                ")" +

                ") THEN FALSE ELSE TRUE END";
    }

    private String comparableNames(
            String first,
            String second) {

        return "(" +

                first + "=" + second +

                " OR (" +
                "CHAR_LENGTH(" + first + ")>=5 " +
                "AND " + second +
                " LIKE CONCAT('%'," + first + ",'%')" +
                ")" +

                " OR (" +
                "CHAR_LENGTH(" + second + ")>=5 " +
                "AND " + first +
                " LIKE CONCAT('%'," + second + ",'%')" +
                ")" +

                ")";
    }
    
    private String exactNames(
            String first,
            String second) {

        return "(" +
                first +
                "=" +
                second +
                ")";
    }

    private void addQuery(String key, String fileName, String sql) {
        fileNames.put(key, fileName);
        queries.put(key, sql);
    }

    private void buildUI() {
        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBorder(BorderFactory.createTitledBorder("Select which evaluation CSV files to export"));

        for (String key : fileNames.keySet()) {
            JCheckBox cb = new JCheckBox(key + " - " + fileNames.get(key));
            cb.setSelected(true);
            checkboxes.put(key, cb);
            listPanel.add(cb);
        }

        JButton selectAll = new JButton("Select All");
        selectAll.addActionListener(e -> checkboxes.values().forEach(cb -> cb.setSelected(true)));

        JButton deselectAll = new JButton("Deselect All");
        deselectAll.addActionListener(e -> checkboxes.values().forEach(cb -> cb.setSelected(false)));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(selectAll);
        top.add(deselectAll);

        JButton export = new JButton("Export Selected to CSV");
        export.addActionListener(e -> exportSelected());

        statusLabel = new JLabel(" ");

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(statusLabel, BorderLayout.CENTER);
        bottom.add(export, BorderLayout.EAST);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(listPanel), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    private void exportSelected() {

        JFileChooser chooser =
                new JFileChooser();

        chooser.setFileSelectionMode(
                JFileChooser.DIRECTORIES_ONLY
        );

        if (chooser.showSaveDialog(this)
                != JFileChooser.APPROVE_OPTION) {

            return;
        }

        File directory =
                chooser.getSelectedFile();

        StringBuilder summary =
                new StringBuilder();

        try (Connection conn =
                     DBConnection.getConnection()) {

            for (String key
                    : checkboxes.keySet()) {

                if (!checkboxes
                        .get(key)
                        .isSelected()) {

                    continue;
                }

                File file =
                        new File(
                                directory,
                                fileNames.get(key)
                        );

                try {
                    int rows;

                    if ("LAYER 1A".equals(key)) {

                        rows =
                                layer1AExporter.export(
                                        conn,
                                        file
                                );

                    } else if ("LAYER 2A".equals(key)) {

                        rows =
                                alignedNumericExporter
                                        .exportQuantity(
                                                conn,
                                                file
                                        );

                    } else if ("LAYER 2B".equals(key)) {

                        rows =
                                alignedNumericExporter
                                        .exportNutrition(
                                                conn,
                                                file
                                        );
                    } else if ("LAYER 3C".equals(key)) {

                        rows = detectionMetricsExporter.exportLayer3C(
                                conn,
                                file
                        );

                    } else if ("LAYER 5".equals(key)) {

                        rows = detectionMetricsExporter.exportLayer5(
                                conn,
                                file
                        );
                        
                    } else {

                        rows =
                                exportQuery(
                                        conn,
                                        queries.get(key),
                                        file
                                );
                    }

                    summary.append(
                            file.getName()
                    );

                    summary.append(" - ");
                    summary.append(rows);
                    summary.append(" rows\n");

                } catch (Exception e) {

                    summary.append(
                            file.getName()
                    );

                    summary.append(
                            " - skipped: "
                    );

                    summary.append(
                            e.getMessage()
                    );

                    summary.append("\n");
                }
            }

        } catch (Exception e) {

            JOptionPane.showMessageDialog(
                    this,
                    "Database error: "
                            + e.getMessage()
            );

            return;
        }

        statusLabel.setText(
                "Export finished: "
                        + directory.getAbsolutePath()
        );

        JTextArea summaryArea =
                new JTextArea(
                        summary.toString()
                );

        summaryArea.setEditable(false);

        JOptionPane.showMessageDialog(
                this,
                new JScrollPane(summaryArea),
                "Export Summary",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    private int exportQuery(Connection conn, String sql, File file) throws Exception {
        int count = 0;

        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery();
             PrintWriter writer = new PrintWriter(file, StandardCharsets.UTF_8)) {

            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();

            for (int i = 1; i <= cols; i++) {
                writer.print(CsvUtil.escape(meta.getColumnLabel(i)));
                if (i < cols) writer.print(",");
            }
            writer.println();

            while (rs.next()) {
                for (int i = 1; i <= cols; i++) {
                    writer.print(CsvUtil.escape(rs.getObject(i)));
                    if (i < cols) writer.print(",");
                }
                writer.println();
                count++;
            }
        }

        return count;
    }
}