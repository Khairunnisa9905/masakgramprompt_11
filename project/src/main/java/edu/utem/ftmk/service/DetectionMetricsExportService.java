package edu.utem.ftmk.service;

import edu.utem.ftmk.util.CsvUtil;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DetectionMetricsExportService {

    public int exportLayer3C(
            Connection conn,
            File file) throws Exception {

        List<ExperimentInfo> experiments =
                loadExperiments(
                        conn,
                        "e.experiment_id"
                );

        int rows = 0;

        try (PrintWriter writer =
                     new PrintWriter(
                             file,
                             StandardCharsets.UTF_8
                     )) {

            writeRow(
                    writer,
                    "experiment_id",
                    "video_id",
                    "model_name",
                    "technique_name",
                    "rag_enabled",
                    "gt_ingredient_count",
                    "pred_ingredient_count",
                    "true_positives",
                    "false_positives"
            );

            for (ExperimentInfo experiment
                    : experiments) {

                List<IngredientName> groundTruth =
                        loadGroundTruth(
                                conn,
                                experiment.transcriptId,
                                false
                        );

                List<IngredientName> predictions =
                        loadPredictions(
                                conn,
                                experiment.resultId
                        );

                int truePositives =
                        maximumOneToOneMatches(
                                groundTruth,
                                predictions
                        );

                int falsePositives =
                        predictions.size()
                                - truePositives;

                writeRow(
                        writer,
                        experiment.experimentId,
                        experiment.videoId,
                        experiment.modelName,
                        experiment.techniqueName,
                        experiment.ragEnabled,
                        groundTruth.size(),
                        predictions.size(),
                        truePositives,
                        falsePositives
                );

                rows++;
            }
        }

        return rows;
    }

    public int exportLayer5(
            Connection conn,
            File file) throws Exception {

        List<ExperimentInfo> experiments =
                loadExperiments(
                        conn,
                        "r.reel_id_instagram, "
                                + "m.model_name, "
                                + "pt.technique_name, "
                                + "e.experiment_id"
                );

        int rows = 0;

        try (PrintWriter writer =
                     new PrintWriter(
                             file,
                             StandardCharsets.UTF_8
                     )) {

            writeRow(
                    writer,
                    "video_id",
                    "model_name",
                    "technique_name",
                    "rag_enabled",
                    "pred_count",
                    "true_positives",
                    "false_positives",
                    "gt_count",
                    "json_valid",
                    "pred_total_kcal",
                    "gt_total_kcal"
            );

            for (ExperimentInfo experiment
                    : experiments) {

                /*
                 * Layer 5 uses Layer 2 ground-truth records.
                 */
                List<IngredientName> groundTruth =
                        loadGroundTruth(
                                conn,
                                experiment.transcriptId,
                                true
                        );

                List<IngredientName> predictions =
                        loadPredictions(
                                conn,
                                experiment.resultId
                        );

                int truePositives =
                        maximumOneToOneMatches(
                                groundTruth,
                                predictions
                        );

                int falsePositives =
                        predictions.size()
                                - truePositives;

                Double groundTruthCalories =
                        loadGroundTruthCalories(
                                conn,
                                experiment.transcriptId
                        );

                writeRow(
                        writer,
                        experiment.videoId,
                        experiment.modelName,
                        experiment.techniqueName,
                        experiment.ragEnabled,
                        predictions.size(),
                        truePositives,
                        falsePositives,
                        groundTruth.size(),
                        experiment.jsonValid,
                        experiment.predictedCalories,
                        groundTruthCalories
                );

                rows++;
            }
        }

        return rows;
    }

    private List<ExperimentInfo> loadExperiments(
            Connection conn,
            String orderBy) throws Exception {

        String sql =
                "SELECT e.experiment_id, "
                        + "e.transcript_id, "
                        + "r.reel_id_instagram AS video_id, "
                        + "m.model_name, "
                        + "pt.technique_name, "
                        + "e.rag_enabled, "
                        + "nr.result_id, "
                        + "nr.json_valid, "
                        + "nr.total_calories "
                        + "FROM experiment e "
                        + "JOIN transcript t "
                        + "ON e.transcript_id=t.transcript_id "
                        + "JOIN reel r "
                        + "ON t.reel_id=r.reel_id "
                        + "JOIN llm_model m "
                        + "ON e.model_id=m.model_id "
                        + "JOIN prompt_technique pt "
                        + "ON e.technique_id=pt.technique_id "
                        + "JOIN nutrition_result nr "
                        + "ON nr.result_id=("
                        + "SELECT MAX(nr_latest.result_id) "
                        + "FROM nutrition_result nr_latest "
                        + "WHERE nr_latest.experiment_id="
                        + "e.experiment_id"
                        + ") "
                        + "WHERE e.status='completed' "
                        + "ORDER BY "
                        + orderBy;

        List<ExperimentInfo> experiments =
                new ArrayList<>();

        try (PreparedStatement ps =
                     conn.prepareStatement(sql);

             ResultSet rs =
                     ps.executeQuery()) {

            while (rs.next()) {

                experiments.add(
                        new ExperimentInfo(
                                rs.getInt(
                                        "experiment_id"
                                ),
                                rs.getInt(
                                        "transcript_id"
                                ),
                                rs.getString(
                                        "video_id"
                                ),
                                rs.getString(
                                        "model_name"
                                ),
                                rs.getString(
                                        "technique_name"
                                ),
                                rs.getBoolean(
                                        "rag_enabled"
                                ),
                                rs.getInt(
                                        "result_id"
                                ),
                                rs.getBoolean(
                                        "json_valid"
                                ),
                                nullableDouble(
                                        rs,
                                        "total_calories"
                                )
                        )
                );
            }
        }

        return experiments;
    }

    private List<IngredientName> loadGroundTruth(
            Connection conn,
            int transcriptId,
            boolean layer2Only) throws Exception {

        String sql =
                "SELECT gti.name_original, "
                        + "gti.name_en "
                        + "FROM ground_truth_reel gtr "
                        + "JOIN ground_truth_ingredient gti "
                        + "ON gti.gt_reel_id=gtr.gt_reel_id "
                        + "WHERE gtr.transcript_id=? "
                        + (layer2Only
                        ? "AND gti.annotation_layer='layer2' "
                        : "")
                        + "ORDER BY gti.gt_ingredient_id";

        List<IngredientName> ingredients =
                new ArrayList<>();

        try (PreparedStatement ps =
                     conn.prepareStatement(sql)) {

            ps.setInt(1, transcriptId);

            try (ResultSet rs =
                         ps.executeQuery()) {

                while (rs.next()) {

                    ingredients.add(
                            new IngredientName(
                                    rs.getString(
                                            "name_original"
                                    ),
                                    rs.getString(
                                            "name_en"
                                    )
                            )
                    );
                }
            }
        }

        return ingredients;
    }

    private List<IngredientName> loadPredictions(
            Connection conn,
            int resultId) throws Exception {

        String sql =
                "SELECT name_original, name_en "
                        + "FROM ingredient_result "
                        + "WHERE result_id=? "
                        + "ORDER BY ingredient_id";

        List<IngredientName> ingredients =
                new ArrayList<>();

        try (PreparedStatement ps =
                     conn.prepareStatement(sql)) {

            ps.setInt(1, resultId);

            try (ResultSet rs =
                         ps.executeQuery()) {

                while (rs.next()) {

                    ingredients.add(
                            new IngredientName(
                                    rs.getString(
                                            "name_original"
                                    ),
                                    rs.getString(
                                            "name_en"
                                    )
                            )
                    );
                }
            }
        }

        return ingredients;
    }

    private Double loadGroundTruthCalories(
            Connection conn,
            int transcriptId) throws Exception {

        String sql =
                "SELECT SUM(gti.calories) AS total_kcal "
                        + "FROM ground_truth_reel gtr "
                        + "JOIN ground_truth_ingredient gti "
                        + "ON gti.gt_reel_id=gtr.gt_reel_id "
                        + "WHERE gtr.transcript_id=? "
                        + "AND gti.annotation_layer='layer2'";

        try (PreparedStatement ps =
                     conn.prepareStatement(sql)) {

            ps.setInt(1, transcriptId);

            try (ResultSet rs =
                         ps.executeQuery()) {

                if (rs.next()) {

                    return nullableDouble(
                            rs,
                            "total_kcal"
                    );
                }
            }
        }

        return null;
    }

    /*
     * Maximum bipartite matching:
     * each prediction can match at most one GT ingredient,
     * and each GT ingredient can be used at most once.
     */
    private int maximumOneToOneMatches(
            List<IngredientName> groundTruth,
            List<IngredientName> predictions) {

        int[] gtMatchedPrediction =
                new int[groundTruth.size()];

        for (int index = 0;
             index < gtMatchedPrediction.length;
             index++) {

            gtMatchedPrediction[index] = -1;
        }

        int matches = 0;

        for (int predictionIndex = 0;
             predictionIndex < predictions.size();
             predictionIndex++) {

            boolean[] visitedGt =
                    new boolean[groundTruth.size()];

            if (findMatch(
                    predictionIndex,
                    predictions,
                    groundTruth,
                    visitedGt,
                    gtMatchedPrediction
            )) {

                matches++;
            }
        }

        return matches;
    }

    private boolean findMatch(
            int predictionIndex,
            List<IngredientName> predictions,
            List<IngredientName> groundTruth,
            boolean[] visitedGt,
            int[] gtMatchedPrediction) {

        for (int gtIndex = 0;
             gtIndex < groundTruth.size();
             gtIndex++) {

            if (visitedGt[gtIndex]) {
                continue;
            }

            if (!ingredientNamesMatch(
                    groundTruth.get(gtIndex),
                    predictions.get(predictionIndex)
            )) {
                continue;
            }

            visitedGt[gtIndex] = true;

            if (gtMatchedPrediction[gtIndex] == -1
                    || findMatch(
                    gtMatchedPrediction[gtIndex],
                    predictions,
                    groundTruth,
                    visitedGt,
                    gtMatchedPrediction
            )) {

                gtMatchedPrediction[gtIndex] =
                        predictionIndex;

                return true;
            }
        }

        return false;
    }

    private boolean ingredientNamesMatch(
            IngredientName groundTruth,
            IngredientName prediction) {

        String gtOriginal =
                normalize(groundTruth.original);

        String gtEnglish =
                normalize(groundTruth.english);

        String predOriginal =
                normalize(prediction.original);

        String predEnglish =
                normalize(prediction.english);

        return exactOrContained(
                gtOriginal,
                predOriginal
        )
                || exact(
                gtOriginal,
                predEnglish
        )
                || exact(
                gtEnglish,
                predOriginal
        )
                || exact(
                gtEnglish,
                predEnglish
        );
    }

    /*
     * Preserves the previous matching rule:
     * original-name matching allows exact or contained text
     * when the contained term has at least five characters.
     */
    private boolean exactOrContained(
            String first,
            String second) {

        if (first.isEmpty()
                || second.isEmpty()) {

            return false;
        }

        if (first.equals(second)) {
            return true;
        }

        return first.length() >= 5
                && second.contains(first)
                || second.length() >= 5
                && first.contains(second);
    }

    private boolean exact(
            String first,
            String second) {

        return !first.isEmpty()
                && first.equals(second);
    }

    private String normalize(String value) {

        return value == null
                ? ""
                : value.trim()
                .toLowerCase(Locale.ROOT);
    }

    private Double nullableDouble(
            ResultSet rs,
            String column) throws SQLException {

        double value =
                rs.getDouble(column);

        return rs.wasNull()
                ? null
                : value;
    }

    private void writeRow(
            PrintWriter writer,
            Object... values) {

        for (int index = 0;
             index < values.length;
             index++) {

            if (index > 0) {
                writer.print(",");
            }

            writer.print(
                    CsvUtil.escape(
                            values[index]
                    )
            );
        }

        writer.println();
    }

    private static class IngredientName {

        private final String original;
        private final String english;

        private IngredientName(
                String original,
                String english) {

            this.original = original;
            this.english = english;
        }
    }

    private static class ExperimentInfo {

        private final int experimentId;
        private final int transcriptId;
        private final String videoId;
        private final String modelName;
        private final String techniqueName;
        private final boolean ragEnabled;
        private final int resultId;
        private final boolean jsonValid;
        private final Double predictedCalories;

        private ExperimentInfo(
                int experimentId,
                int transcriptId,
                String videoId,
                String modelName,
                String techniqueName,
                boolean ragEnabled,
                int resultId,
                boolean jsonValid,
                Double predictedCalories) {

            this.experimentId = experimentId;
            this.transcriptId = transcriptId;
            this.videoId = videoId;
            this.modelName = modelName;
            this.techniqueName = techniqueName;
            this.ragEnabled = ragEnabled;
            this.resultId = resultId;
            this.jsonValid = jsonValid;
            this.predictedCalories = predictedCalories;
        }
    }
}