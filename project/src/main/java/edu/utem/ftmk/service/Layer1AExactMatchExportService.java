package edu.utem.ftmk.service;

import edu.utem.ftmk.util.CsvUtil;

import java.io.BufferedWriter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Layer1AExactMatchExportService {

    public int export(
            Connection conn,
            File outputFile) throws Exception {

        List<ExperimentInfo> experiments =
                loadCompletedExperiments(conn);

        int exportedRows = 0;

        try (BufferedWriter writer =
                     Files.newBufferedWriter(
                             outputFile.toPath(),
                             StandardCharsets.UTF_8
                     )) {

            writeHeader(writer);

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

                exportedRows +=
                        writeAlignedRows(
                                writer,
                                experiment,
                                groundTruth,
                                predictions
                        );
            }
        }

        return exportedRows;
    }

    private List<ExperimentInfo> loadCompletedExperiments(
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
                "gti.quantity_unit_culinary AS unit_original " +
                "FROM ground_truth_ingredient gti " +
                "JOIN ground_truth_reel gtr " +
                "ON gti.gt_reel_id=gtr.gt_reel_id " +
                "WHERE gtr.transcript_id=? " +
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

                                    rs.getString(
                                            "unit_original"
                                    ),

                                    null
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

        /*
         * Only the latest nutrition result for the experiment
         * is used. This prevents historical duplicate rows from
         * multiplying the export.
         */
        String sql =
                "SELECT ir.ingredient_id, " +
                "ir.name_original, " +
                "ir.name_en, " +
                "ir.unit_original, " +
                "ir.unit_en " +
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

                                    rs.getString(
                                            "unit_original"
                                    ),

                                    rs.getString(
                                            "unit_en"
                                    )
                            )
                    );
                }
            }
        }

        return ingredients;
    }

    private int writeAlignedRows(
            BufferedWriter writer,
            ExperimentInfo experiment,
            List<IngredientInfo> groundTruth,
            List<IngredientInfo> predictions)
            throws Exception {

        int count = 0;

        boolean[] predictionUsed =
                new boolean[predictions.size()];

        /*
         * Preserve ground-truth ordering and assign each
         * prediction at most once.
         */
        for (IngredientInfo gt : groundTruth) {

            int matchedIndex =
                    findExactMatch(
                            gt,
                            predictions,
                            predictionUsed
                    );

            if (matchedIndex >= 0) {

                IngredientInfo prediction =
                        predictions.get(
                                matchedIndex
                        );

                predictionUsed[matchedIndex] =
                        true;

                writeRow(
                        writer,
                        experiment,
                        "MATCHED",
                        gt,
                        prediction
                );

            } else {

                writeRow(
                        writer,
                        experiment,
                        "GT_ONLY",
                        gt,
                        null
                );
            }

            count++;
        }

        /*
         * Remaining predictions do not have an exact GT match.
         */
        for (int index = 0;
             index < predictions.size();
             index++) {

            if (predictionUsed[index]) {
                continue;
            }

            writeRow(
                    writer,
                    experiment,
                    "LLM_ONLY",
                    null,
                    predictions.get(index)
            );

            count++;
        }

        return count;
    }

    private int findExactMatch(
            IngredientInfo groundTruth,
            List<IngredientInfo> predictions,
            boolean[] predictionUsed) {

        for (int index = 0;
             index < predictions.size();
             index++) {

            if (predictionUsed[index]) {
                continue;
            }

            if (namesMatch(
                    groundTruth,
                    predictions.get(index)
            )) {
                return index;
            }
        }

        return -1;
    }

    private boolean namesMatch(
            IngredientInfo groundTruth,
            IngredientInfo prediction) {

        String gtOriginal =
                normalize(
                        groundTruth.nameOriginal
                );

        String gtEnglish =
                normalize(
                        groundTruth.nameEnglish
                );

        String predictionOriginal =
                normalize(
                        prediction.nameOriginal
                );

        String predictionEnglish =
                normalize(
                        prediction.nameEnglish
                );

        return equalNonBlank(
                gtOriginal,
                predictionOriginal
        )
                || equalNonBlank(
                gtOriginal,
                predictionEnglish
        )
                || equalNonBlank(
                gtEnglish,
                predictionOriginal
        )
                || equalNonBlank(
                gtEnglish,
                predictionEnglish
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

    private void writeHeader(
            BufferedWriter writer) throws Exception {

        writeCsvRow(
                writer,
                "experiment_id",
                "transcript_id",
                "video_id",
                "model_name",
                "technique_name",
                "rag_enabled",
                "match_status",
                "gt_ingredient_id",
                "pred_ingredient_id",
                "gt_name_original",
                "gt_name_en",
                "gt_unit_original",
                "gt_unit_en",
                "pred_name_original",
                "pred_name_en",
                "pred_unit_original",
                "pred_unit_en"
        );
    }

    private void writeRow(
            BufferedWriter writer,
            ExperimentInfo experiment,
            String matchStatus,
            IngredientInfo gt,
            IngredientInfo prediction)
            throws Exception {

        writeCsvRow(
                writer,
                experiment.experimentId,
                experiment.transcriptId,
                experiment.videoId,
                experiment.modelName,
                experiment.techniqueName,
                experiment.ragEnabled,
                matchStatus,

                gt == null
                        ? null
                        : gt.ingredientId,

                prediction == null
                        ? null
                        : prediction.ingredientId,

                gt == null
                        ? null
                        : gt.nameOriginal,

                gt == null
                        ? null
                        : gt.nameEnglish,

                gt == null
                        ? null
                        : gt.unitOriginal,

                /*
                 * The supplied GT schema contains no English
                 * unit column. Leave it blank rather than
                 * inventing a translation.
                 */
                null,

                prediction == null
                        ? null
                        : prediction.nameOriginal,

                prediction == null
                        ? null
                        : prediction.nameEnglish,

                prediction == null
                        ? null
                        : prediction.unitOriginal,

                prediction == null
                        ? null
                        : prediction.unitEnglish
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
        private final String unitOriginal;
        private final String unitEnglish;

        private IngredientInfo(
                int ingredientId,
                String nameOriginal,
                String nameEnglish,
                String unitOriginal,
                String unitEnglish) {

            this.ingredientId = ingredientId;
            this.nameOriginal = nameOriginal;
            this.nameEnglish = nameEnglish;
            this.unitOriginal = unitOriginal;
            this.unitEnglish = unitEnglish;
        }
    }
}