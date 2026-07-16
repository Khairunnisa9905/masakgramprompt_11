package edu.utem.ftmk.service;

import edu.utem.ftmk.util.CsvUtil;

import java.io.BufferedWriter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AlignedNumericExportService {

    public int exportQuantity(
            Connection conn,
            File outputFile) throws Exception {

        return export(
                conn,
                outputFile,
                ExportType.QUANTITY
        );
    }

    public int exportNutrition(
            Connection conn,
            File outputFile) throws Exception {

        return export(
                conn,
                outputFile,
                ExportType.NUTRITION
        );
    }

    private int export(
            Connection conn,
            File outputFile,
            ExportType type) throws Exception {

        List<ExperimentInfo> experiments =
                loadExperiments(conn);

        int exportedRows = 0;

        try (BufferedWriter writer =
                     Files.newBufferedWriter(
                             outputFile.toPath(),
                             StandardCharsets.UTF_8
                     )) {

            if (type == ExportType.QUANTITY) {
                writeQuantityHeader(writer);
            } else {
                writeNutritionHeader(writer);
            }

            for (ExperimentInfo experiment
                    : experiments) {

                List<IngredientInfo> groundTruth =
                        loadGroundTruth(
                                conn,
                                experiment.transcriptId
                        );

                List<IngredientInfo> predictions =
                        loadPredictions(
                                conn,
                                experiment.experimentId
                        );

                boolean[] predictionUsed =
                        new boolean[predictions.size()];

                for (IngredientInfo gt
                        : groundTruth) {

                    int matchedIndex =
                            findExactMatch(
                                    gt,
                                    predictions,
                                    predictionUsed
                            );

                    /*
                     * Numeric error is meaningful only when
                     * GT and prediction refer to the same
                     * exact-matched ingredient.
                     */
                    if (matchedIndex < 0) {
                        continue;
                    }

                    IngredientInfo prediction =
                            predictions.get(
                                    matchedIndex
                            );

                    predictionUsed[matchedIndex] =
                            true;

                    if (type == ExportType.QUANTITY) {
                        writeQuantityRow(
                                writer,
                                experiment,
                                gt,
                                prediction
                        );
                    } else {
                        writeNutritionRow(
                                writer,
                                experiment,
                                gt,
                                prediction
                        );
                    }

                    exportedRows++;
                }
            }
        }

        return exportedRows;
    }

    private List<ExperimentInfo> loadExperiments(
            Connection conn) throws Exception {

        String sql =
                "SELECT e.experiment_id, " +
                "e.transcript_id, " +
                "r.reel_id_instagram AS video_id, " +
                "m.model_name, " +
                "pt.technique_name, " +
                "e.rag_enabled " +
                "FROM experiment e " +
                "JOIN transcript t " +
                "ON e.transcript_id=t.transcript_id " +
                "JOIN reel r " +
                "ON t.reel_id=r.reel_id " +
                "JOIN llm_model m " +
                "ON e.model_id=m.model_id " +
                "JOIN prompt_technique pt " +
                "ON e.technique_id=pt.technique_id " +
                "WHERE e.status='completed' " +
                "ORDER BY e.experiment_id";

        List<ExperimentInfo> experiments =
                new ArrayList<>();

        try (
                PreparedStatement ps =
                        conn.prepareStatement(sql);

                ResultSet rs =
                        ps.executeQuery()
        ) {

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
                                )
                        )
                );
            }
        }

        return experiments;
    }

    private List<IngredientInfo> loadGroundTruth(
            Connection conn,
            int transcriptId) throws Exception {

        String sql =
                "SELECT gti.gt_ingredient_id AS ingredient_id, " +
                "gti.name_original, " +
                "gti.name_en, " +
                "gti.quantity_value_culinary AS quantity_value, " +
                "gti.estimated_weight_g, " +
                "gti.calories, " +
                "gti.protein_g, " +
                "gti.total_fat_g, " +
                "gti.total_carbohydrate_g " +
                "FROM ground_truth_ingredient gti " +
                "JOIN ground_truth_reel gtr " +
                "ON gti.gt_reel_id=gtr.gt_reel_id " +
                "WHERE gtr.transcript_id=? " +
                "AND gti.annotation_layer='layer2' " +
                "ORDER BY gti.gt_ingredient_id";

        List<IngredientInfo> ingredients =
                new ArrayList<>();

        try (PreparedStatement ps =
                     conn.prepareStatement(sql)) {

            ps.setInt(
                    1,
                    transcriptId
            );

            try (ResultSet rs =
                         ps.executeQuery()) {

                while (rs.next()) {

                    ingredients.add(
                            new IngredientInfo(
                                    rs.getInt(
                                            "ingredient_id"
                                    ),

                                    rs.getString(
                                            "name_original"
                                    ),

                                    rs.getString(
                                            "name_en"
                                    ),

                                    nullableDouble(
                                            rs,
                                            "quantity_value"
                                    ),

                                    nullableDouble(
                                            rs,
                                            "estimated_weight_g"
                                    ),

                                    nullableDouble(
                                            rs,
                                            "calories"
                                    ),

                                    nullableDouble(
                                            rs,
                                            "protein_g"
                                    ),

                                    nullableDouble(
                                            rs,
                                            "total_fat_g"
                                    ),

                                    nullableDouble(
                                            rs,
                                            "total_carbohydrate_g"
                                    )
                            )
                    );
                }
            }
        }

        return ingredients;
    }

    private List<IngredientInfo> loadPredictions(
            Connection conn,
            int experimentId) throws Exception {

        String sql =
                "SELECT ir.ingredient_id, " +
                "ir.name_original, " +
                "ir.name_en, " +
                "ir.quantity_value, " +
                "ir.estimated_weight_g, " +
                "ir.calories, " +
                "ir.protein_g, " +
                "ir.total_fat_g, " +
                "ir.total_carbohydrate_g " +
                "FROM ingredient_result ir " +
                "JOIN nutrition_result nr " +
                "ON ir.result_id=nr.result_id " +
                "WHERE nr.result_id=(" +
                "SELECT MAX(nr2.result_id) " +
                "FROM nutrition_result nr2 " +
                "WHERE nr2.experiment_id=?" +
                ") " +
                "ORDER BY ir.ingredient_id";

        List<IngredientInfo> ingredients =
                new ArrayList<>();

        try (PreparedStatement ps =
                     conn.prepareStatement(sql)) {

            ps.setInt(
                    1,
                    experimentId
            );

            try (ResultSet rs =
                         ps.executeQuery()) {

                while (rs.next()) {

                    ingredients.add(
                            new IngredientInfo(
                                    rs.getInt(
                                            "ingredient_id"
                                    ),

                                    rs.getString(
                                            "name_original"
                                    ),

                                    rs.getString(
                                            "name_en"
                                    ),

                                    nullableDouble(
                                            rs,
                                            "quantity_value"
                                    ),

                                    nullableDouble(
                                            rs,
                                            "estimated_weight_g"
                                    ),

                                    nullableDouble(
                                            rs,
                                            "calories"
                                    ),

                                    nullableDouble(
                                            rs,
                                            "protein_g"
                                    ),

                                    nullableDouble(
                                            rs,
                                            "total_fat_g"
                                    ),

                                    nullableDouble(
                                            rs,
                                            "total_carbohydrate_g"
                                    )
                            )
                    );
                }
            }
        }

        return ingredients;
    }

    private int findExactMatch(
            IngredientInfo gt,
            List<IngredientInfo> predictions,
            boolean[] predictionUsed) {

        for (int index = 0;
             index < predictions.size();
             index++) {

            if (predictionUsed[index]) {
                continue;
            }

            if (namesMatch(
                    gt,
                    predictions.get(index)
            )) {
                return index;
            }
        }

        return -1;
    }

    private boolean namesMatch(
            IngredientInfo gt,
            IngredientInfo prediction) {

        String gtOriginal =
                normalize(gt.nameOriginal);

        String gtEnglish =
                normalize(gt.nameEnglish);

        String predOriginal =
                normalize(prediction.nameOriginal);

        String predEnglish =
                normalize(prediction.nameEnglish);

        return equalNonBlank(
                gtOriginal,
                predOriginal
        )
                || equalNonBlank(
                gtOriginal,
                predEnglish
        )
                || equalNonBlank(
                gtEnglish,
                predOriginal
        )
                || equalNonBlank(
                gtEnglish,
                predEnglish
        );
    }

    private boolean equalNonBlank(
            String first,
            String second) {

        return !first.isBlank()
                && !second.isBlank()
                && first.equals(second);
    }

    private String normalize(
            String value) {

        if (value == null) {
            return "";
        }

        return value
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
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

    private void writeQuantityHeader(
            BufferedWriter writer) throws Exception {

        writeCsvRow(
                writer,
                "experiment_id",
                "video_id",
                "model_name",
                "technique_name",
                "rag_enabled",
                "gt_quantity_value",
                "gt_weight_g",
                "pred_quantity_value",
                "pred_weight_g"
        );
    }

    private void writeQuantityRow(
            BufferedWriter writer,
            ExperimentInfo experiment,
            IngredientInfo gt,
            IngredientInfo prediction)
            throws Exception {

        writeCsvRow(
                writer,
                experiment.experimentId,
                experiment.videoId,
                experiment.modelName,
                experiment.techniqueName,
                experiment.ragEnabled,
                gt.quantityValue,
                gt.estimatedWeight,
                prediction.quantityValue,
                prediction.estimatedWeight
        );
    }

    private void writeNutritionHeader(
            BufferedWriter writer) throws Exception {

        writeCsvRow(
                writer,
                "experiment_id",
                "video_id",
                "model_name",
                "technique_name",
                "rag_enabled",
                "gt_calories",
                "gt_protein_g",
                "gt_fat_g",
                "gt_carbohydrate_g",
                "pred_calories",
                "pred_protein_g",
                "pred_fat_g",
                "pred_carbohydrate_g"
        );
    }

    private void writeNutritionRow(
            BufferedWriter writer,
            ExperimentInfo experiment,
            IngredientInfo gt,
            IngredientInfo prediction)
            throws Exception {

        writeCsvRow(
                writer,
                experiment.experimentId,
                experiment.videoId,
                experiment.modelName,
                experiment.techniqueName,
                experiment.ragEnabled,
                gt.calories,
                gt.protein,
                gt.fat,
                gt.carbohydrate,
                prediction.calories,
                prediction.protein,
                prediction.fat,
                prediction.carbohydrate
        );
    }

    private void writeCsvRow(
            BufferedWriter writer,
            Object... values) throws Exception {

        for (int index = 0;
             index < values.length;
             index++) {

            if (index > 0) {
                writer.write(",");
            }

            writer.write(
                    CsvUtil.escape(
                            values[index]
                    )
            );
        }

        writer.newLine();
    }

    private enum ExportType {
        QUANTITY,
        NUTRITION
    }

    private static class ExperimentInfo {

        private final int experimentId;
        private final int transcriptId;
        private final String videoId;
        private final String modelName;
        private final String techniqueName;
        private final boolean ragEnabled;

        private ExperimentInfo(
                int experimentId,
                int transcriptId,
                String videoId,
                String modelName,
                String techniqueName,
                boolean ragEnabled) {

            this.experimentId = experimentId;
            this.transcriptId = transcriptId;
            this.videoId = videoId;
            this.modelName = modelName;
            this.techniqueName = techniqueName;
            this.ragEnabled = ragEnabled;
        }
    }

    private static class IngredientInfo {

        private final int ingredientId;
        private final String nameOriginal;
        private final String nameEnglish;
        private final Double quantityValue;
        private final Double estimatedWeight;
        private final Double calories;
        private final Double protein;
        private final Double fat;
        private final Double carbohydrate;

        private IngredientInfo(
                int ingredientId,
                String nameOriginal,
                String nameEnglish,
                Double quantityValue,
                Double estimatedWeight,
                Double calories,
                Double protein,
                Double fat,
                Double carbohydrate) {

            this.ingredientId = ingredientId;
            this.nameOriginal = nameOriginal;
            this.nameEnglish = nameEnglish;
            this.quantityValue = quantityValue;
            this.estimatedWeight = estimatedWeight;
            this.calories = calories;
            this.protein = protein;
            this.fat = fat;
            this.carbohydrate = carbohydrate;
        }
    }
}