package edu.utem.ftmk.client;

import edu.utem.ftmk.database.DBConnection;
import edu.utem.ftmk.model.ModelInfo;
import edu.utem.ftmk.model.PromptTechniqueInfo;
import edu.utem.ftmk.util.CsvUtil;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.*;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import javax.swing.table.TableModel;
import org.json.JSONArray;
import org.json.JSONObject;

public class NutritionalFactSheetWindow extends JFrame {

    private static final String[] REEL_COLUMNS = {
            "Reel ID",
            "Influencer Handle",
            "Instagram Code",
            "Date",
            "Language",
            "Evaluation Status"
    };

    /*
     * Both tables deliberately use the same columns so the
     * comparison CSV remains correctly aligned.
     */
    private static final String[] INGREDIENT_COLUMNS = {
            "Ingredient",
            "English Name",
            "Qty Expression",
            "Category",
            "Unit",
            "Value",
            "Language",
            "Hallucination",
            "Weight (g)",
            "Calories",
            "Total Fat (g)",
            "Saturated Fat (g)",
            "Cholesterol (mg)",
            "Sodium (mg)",
            "Carbohydrate (g)",
            "Fiber (g)",
            "Sugars (g)",
            "Protein (g)",
            "Vitamin D (mcg)",
            "Calcium (mg)",
            "Iron (mg)",
            "Potassium (mg)"
    };

    private static final String[] NUTRIENT_LABELS = {
            "Calories",
            "Total Fat (g)",
            "Saturated Fat (g)",
            "Cholesterol (mg)",
            "Sodium (mg)",
            "Carbohydrate (g)",
            "Fiber (g)",
            "Sugars (g)",
            "Protein (g)",
            "Vitamin D (mcg)",
            "Calcium (mg)",
            "Iron (mg)",
            "Potassium (mg)"
    };

    private static final String[] RESULT_TOTAL_COLUMNS = {
            "total_calories",
            "total_fat_g",
            "total_saturated_fat_g",
            "total_cholesterol_mg",
            "total_sodium_mg",
            "total_carbohydrate_g",
            "total_fiber_g",
            "total_sugars_g",
            "total_protein_g",
            "total_vitamin_d_mcg",
            "total_calcium_mg",
            "total_iron_mg",
            "total_potassium_mg"
    };

    private JTable reelTable;
    private JTable groundTruthTable;
    private JTable llmResultTable;

    private JTextArea totalsArea;
    private JComboBox<String> modelCombo;
    private JComboBox<String> techniqueCombo;

    private int modelId;
    private int techniqueId;
    private int selectedReelId;

    private NutrientTotals currentHumanTotals =
            new NutrientTotals();

    private NutrientTotals currentAiIngredientTotals =
            new NutrientTotals();

    private Double[] currentAiReportedTotals =
            new Double[NUTRIENT_LABELS.length];

    public NutritionalFactSheetWindow(
            String reelId,
            String titleLabel,
            int modelId,
            int techniqueId) {

        this.selectedReelId =
                parseReelId(reelId);

        this.modelId = modelId;
        this.techniqueId = techniqueId;

        setTitle(
                "Detailed Nutritional Evaluation Sheet - "
                        + titleLabel
        );

        setSize(1500, 900);
        setMinimumSize(
                new Dimension(1150, 720)
        );

        setDefaultCloseOperation(
                JFrame.DISPOSE_ON_CLOSE
        );

        setLocationRelativeTo(null);
        setLayout(
                new BorderLayout(5, 5)
        );

        add(
                buildConfigurationPanel(),
                BorderLayout.NORTH
        );

        add(
                buildMainPanel(),
                BorderLayout.CENTER
        );

        add(
                buildExportPanel(),
                BorderLayout.SOUTH
        );

        loadReels();
        selectInitialReel();
        loadComparison(selectedReelId);
    }

    private JPanel buildConfigurationPanel() {

        JPanel panel =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.LEFT,
                                15,
                                10
                        )
                );

        panel.setBackground(
                new Color(236, 240, 241)
        );

        JLabel title =
                new JLabel(
                        "Experiment Configuration"
                );

        title.setFont(
                new Font(
                        "Arial",
                        Font.BOLD,
                        13
                )
        );

        panel.add(title);
        panel.add(
                new JLabel("Model:")
        );

        modelCombo =
                new JComboBox<>(
                        ModelInfo.displayNames()
                );

        modelCombo.setSelectedIndex(
                validIndex(
                        modelId - 1,
                        modelCombo.getItemCount()
                )
        );

        modelCombo.setPreferredSize(
                new Dimension(250, 30)
        );

        panel.add(modelCombo);
        panel.add(
                new JLabel("Technique:")
        );

        techniqueCombo =
                new JComboBox<>(
                        PromptTechniqueInfo.dbNames()
                );

        techniqueCombo.setSelectedIndex(
                validIndex(
                        techniqueId - 1,
                        techniqueCombo.getItemCount()
                )
        );

        techniqueCombo.setPreferredSize(
                new Dimension(190, 30)
        );

        panel.add(techniqueCombo);

        JButton syncButton =
                new JButton(
                        "Sync Selection"
                );

        syncButton.addActionListener(
                event -> {

                    modelId =
                            modelCombo.getSelectedIndex()
                                    + 1;

                    techniqueId =
                            techniqueCombo.getSelectedIndex()
                                    + 1;

                    String selectedModel =
                            (String) modelCombo.getSelectedItem();

                    String selectedTechnique =
                            (String) techniqueCombo.getSelectedItem();

                    setTitle(
                            "Detailed Nutritional Evaluation Sheet - "
                                    + selectedModel
                                    + " ("
                                    + selectedTechnique
                                    + ")"
                    );

                    loadReels();
                    selectReel(selectedReelId);
                    loadComparison(selectedReelId);
                }
        );

        panel.add(syncButton);

        return panel;
    }

    private JPanel buildMainPanel() {

        JPanel main =
                new JPanel(
                        new BorderLayout(5, 5)
                );

        reelTable =
                createTable(REEL_COLUMNS);

        reelTable.setSelectionMode(
                ListSelectionModel.SINGLE_SELECTION
        );

        reelTable.getSelectionModel()
                .addListSelectionListener(
                        event -> {

                            if (event.getValueIsAdjusting()) {
                                return;
                            }

                            int row =
                                    reelTable.getSelectedRow();

                            if (row < 0) {
                                return;
                            }

                            Object value =
                                    reelTable.getValueAt(
                                            row,
                                            0
                                    );

                            if (value == null) {
                                return;
                            }

                            selectedReelId =
                                    Integer.parseInt(
                                            value.toString()
                                    );

                            loadComparison(
                                    selectedReelId
                            );
                        }
                );

        JScrollPane reelScroll =
                new JScrollPane(reelTable);

        reelScroll.setPreferredSize(
                new Dimension(1000, 190)
        );

        main.add(
                reelScroll,
                BorderLayout.NORTH
        );

        /*
         * Movable divider between the recipe totals and
         * ingredient-comparison tables.
         */
        JSplitPane evaluationSplit =
                new JSplitPane(
                        JSplitPane.VERTICAL_SPLIT,
                        buildTotalsPanel(),
                        buildComparisonTables()
                );

        /*
         * Give both sections approximately equal resizing priority.
         */
        evaluationSplit.setResizeWeight(0.45);

        /*
         * Initial height of the upper recipe-total section.
         */
        evaluationSplit.setDividerLocation(300);

        /*
         * Makes the divider move continuously while dragging.
         */
        evaluationSplit.setContinuousLayout(true);

        /*
         * Adds small collapse/expand arrows to the divider.
         */
        evaluationSplit.setOneTouchExpandable(true);

        /*
         * Makes the draggable divider easier to grab.
         */
        evaluationSplit.setDividerSize(10);

        main.add(
                evaluationSplit,
                BorderLayout.CENTER
        );

        return main;
    }

    private JPanel buildTotalsPanel() {

        JPanel panel =
                new JPanel(
                        new BorderLayout()
                );

        panel.setBorder(
                BorderFactory.createTitledBorder(
                        "Recipe Nutrition Comparison"
                )
        );

        totalsArea =
                new JTextArea(16, 100);

        totalsArea.setEditable(false);

        totalsArea.setFont(
                new Font(
                        Font.MONOSPACED,
                        Font.PLAIN,
                        12
                )
        );

        totalsArea.setBackground(
                new Color(250, 250, 250)
        );

        panel.add(
                new JScrollPane(totalsArea),
                BorderLayout.CENTER
        );

        return panel;
    }

    private JSplitPane buildComparisonTables() {

        groundTruthTable =
                createTable(
                        INGREDIENT_COLUMNS
                );
        /*
         * Hallucination applies only to LLM predictions.
         * The model column remains internally for CSV alignment,
         * but it is removed from the visible GT table.
         */
        groundTruthTable.removeColumn(
                groundTruthTable.getColumn(
                        "Hallucination"
                )
        );

        llmResultTable =
                createTable(
                        INGREDIENT_COLUMNS
                );

        JPanel humanPanel =
                new JPanel(
                        new BorderLayout()
                );

        humanPanel.setBorder(
                BorderFactory.createTitledBorder(
                        "Human Ground Truth"
                )
        );

        humanPanel.add(
                new JScrollPane(
                        groundTruthTable
                ),
                BorderLayout.CENTER
        );

        JPanel aiPanel =
                new JPanel(
                        new BorderLayout()
                );

        aiPanel.setBorder(
                BorderFactory.createTitledBorder(
                        "LLM Extraction"
                )
        );

        aiPanel.add(
                new JScrollPane(
                        llmResultTable
                ),
                BorderLayout.CENTER
        );

        JSplitPane split =
                new JSplitPane(
                        JSplitPane.HORIZONTAL_SPLIT,
                        humanPanel,
                        aiPanel
                );

        split.setResizeWeight(0.5);
        split.setDividerLocation(0.5);

        return split;
    }

    private JPanel buildExportPanel() {

        JPanel panel =
                new JPanel(
                        new BorderLayout()
                );

        JButton exportButton =
                new JButton(
                        "Download Comparison (.CSV)"
                );

        exportButton.setFont(
                new Font(
                        "Arial",
                        Font.BOLD,
                        13
                )
        );

        exportButton.setForeground(
                Color.WHITE
        );

        exportButton.setBackground(
                new Color(155, 89, 182)
        );

        exportButton.setOpaque(true);
        exportButton.setBorderPainted(false);

        exportButton.setPreferredSize(
                new Dimension(1000, 40)
        );

        exportButton.addActionListener(
                event -> exportComparison()
        );

        panel.add(
                exportButton,
                BorderLayout.CENTER
        );

        return panel;
    }

    private JTable createTable(
            String[] columns) {

        DefaultTableModel model =
                new DefaultTableModel(
                        columns,
                        0
                ) {
                    @Override
                    public boolean isCellEditable(
                            int row,
                            int column) {

                        return false;
                    }
                };

        JTable table =
                new JTable(model);

        table.setRowHeight(22);

        /*
         * Horizontal scrolling is required because all
         * 13 nutrition fields must be displayed.
         */
        table.setAutoResizeMode(
                JTable.AUTO_RESIZE_OFF
        );

        table.setGridColor(
                new Color(150, 160, 170)
        );

        table.setSelectionBackground(
                new Color(174, 205, 235)
        );

        table.getTableHeader()
                .setFont(
                        new Font(
                                "Arial",
                                Font.BOLD,
                                11
                        )
                );

        for (int index = 0;
             index < columns.length;
             index++) {

            table.getColumnModel()
                    .getColumn(index)
                    .setPreferredWidth(
                            index < 9
                                    ? 125
                                    : 115
                    );
        }

        return table;
    }

    private void loadReels() {

        DefaultTableModel tableModel =
                (DefaultTableModel)
                        reelTable.getModel();

        tableModel.setRowCount(0);

        String sql =
                "SELECT r.reel_id, " +
                "COALESCE(i.instagram_account, '') " +
                "AS influencer_handle, " +
                "r.reel_id_instagram, " +
                "r.identified_date, " +
                "GROUP_CONCAT(DISTINCT " +
                "gti.language_mentioned) AS languages, " +
                "COALESCE(MAX(e.status), 'pending') " +
                "AS evaluation_status " +
                "FROM reel r " +
                "LEFT JOIN influencer i " +
                "ON r.influencer_id=i.influencer_id " +
                "LEFT JOIN transcript t " +
                "ON t.reel_id=r.reel_id " +
                "LEFT JOIN ground_truth_reel gtr " +
                "ON gtr.transcript_id=t.transcript_id " +
                "LEFT JOIN ground_truth_ingredient gti " +
                "ON gti.gt_reel_id=gtr.gt_reel_id " +
                "LEFT JOIN experiment e " +
                "ON e.transcript_id=t.transcript_id " +
                "AND e.model_id=? " +
                "AND e.technique_id=? " +
                "GROUP BY r.reel_id, " +
                "i.instagram_account, " +
                "r.reel_id_instagram, " +
                "r.identified_date " +
                "ORDER BY r.reel_id";

        try (
                Connection conn =
                        DBConnection.getConnection();

                PreparedStatement ps =
                        conn.prepareStatement(sql)
        ) {

            ps.setInt(1, modelId);
            ps.setInt(2, techniqueId);

            try (ResultSet rs =
                         ps.executeQuery()) {

                while (rs.next()) {

                    tableModel.addRow(
                            new Object[]{
                                    rs.getInt(
                                            "reel_id"
                                    ),

                                    rs.getString(
                                            "influencer_handle"
                                    ),

                                    rs.getString(
                                            "reel_id_instagram"
                                    ),

                                    rs.getDate(
                                            "identified_date"
                                    ),

                                    formatLanguage(
                                            rs.getString(
                                                    "languages"
                                            )
                                    ),

                                    rs.getString(
                                            "evaluation_status"
                                    ).toUpperCase(
                                            Locale.ROOT
                                    )
                            }
                    );
                }
            }

        } catch (Exception e) {
            showDatabaseError(
                    "Could not load reels",
                    e
            );
        }
    }
    private boolean nonBlankEqual(
            String first,
            String second) {

        return first != null
                && second != null
                && !first.isBlank()
                && !second.isBlank()
                && !"n/a".equals(first)
                && !"n/a".equals(second)
                && first.equals(second);
    }

    private String valueAsString(
            Object value) {

        return value == null
                ? ""
                : value.toString();
    }

    private void loadComparison(
            int reelId) {

        clearTable(groundTruthTable);
        clearTable(llmResultTable);
       


        currentHumanTotals =
                loadGroundTruth(reelId);

        LlmData llmData =
                loadLlmResults(reelId);

        currentAiIngredientTotals =
                llmData.ingredientTotals;

        currentAiReportedTotals =
                llmData.reportedTotals;

        displayTotals(
                currentHumanTotals,
                currentAiIngredientTotals,
                currentAiReportedTotals
        );
        
    }

    private NutrientTotals loadGroundTruth(
            int reelId) {

        NutrientTotals totals =
                new NutrientTotals();

        DefaultTableModel model =
                (DefaultTableModel)
                        groundTruthTable.getModel();

        String sql =
                "SELECT gti.* " +
                "FROM ground_truth_ingredient gti " +
                "JOIN ground_truth_reel gtr " +
                "ON gti.gt_reel_id=gtr.gt_reel_id " +
                "JOIN transcript t " +
                "ON gtr.transcript_id=t.transcript_id " +
                "WHERE t.reel_id=? " +
                "ORDER BY gti.gt_ingredient_id";

        try (
                Connection conn =
                        DBConnection.getConnection();

                PreparedStatement ps =
                        conn.prepareStatement(sql)
        ) {

            ps.setInt(1, reelId);

            try (ResultSet rs =
                         ps.executeQuery()) {

                while (rs.next()) {

                    String original =
                            rs.getString(
                                    "name_original"
                            );

                    String english =
                            rs.getString(
                                    "name_en"
                            );


                    Double[] nutrients =
                            readIngredientNutrients(rs);

                    totals.add(nutrients);

                    model.addRow(
                            createIngredientRow(
                                    original,
                                    english,
                                    rs.getString(
                                            "quantity_expression"
                                    ),
                                    rs.getString(
                                            "quantity_category"
                                    ),
                                    rs.getString(
                                            "quantity_unit_culinary"
                                    ),
                                    nullableDouble(
                                            rs,
                                            "quantity_value_culinary"
                                    ),
                                    rs.getString(
                                            "language_mentioned"
                                    ),
                                    "not applicable",
                                    nullableDouble(
                                            rs,
                                            "estimated_weight_g"
                                    ),
                                    nutrients
                            )
                    );
                }
            }

        } catch (Exception e) {
            showDatabaseError(
                    "Could not load ground truth",
                    e
            );
        }

        return totals;
    }

    private LlmData loadLlmResults(int reelId) {

        LlmData data = new LlmData();

        Integer resultId =
                findLatestResultId(
                        reelId,
                        data.reportedTotals
                );

        if (resultId == null) {
            return data;
        }

        DefaultTableModel model =
                (DefaultTableModel) llmResultTable.getModel();

        String sql =
                "SELECT * FROM ingredient_result " +
                "WHERE result_id=? " +
                "ORDER BY ingredient_id";

        int loadedRows = 0;

        try (
                Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setInt(1, resultId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {

                    String original =
                            rs.getString("name_original");

                    String english =
                            rs.getString("name_en");

                    Double[] nutrients =
                            readIngredientNutrients(rs);

                    data.ingredientTotals.add(nutrients);

                    model.addRow(
                            createIngredientRow(
                                    original,
                                    english,
                                    "not provided",
                                    "not provided",
                                    rs.getString("unit_original"),
                                    nullableDouble(
                                            rs,
                                            "quantity_value"
                                    ),
                                    "not provided",
                                    isHallucinated(original, english)
                                            ? "Yes"
                                            : "No",
                                    nullableDouble(
                                            rs,
                                            "estimated_weight_g"
                                    ),
                                    nutrients
                            )
                    );

                    loadedRows++;
                }
            }

            /*
             * Invalid JSON may not have ingredient_result rows.
             * Recover any ingredient objects from raw_json_output
             * and display them directly in the LLM table.
             */
            if (loadedRows == 0) {
                loadIngredientsFromRawOutput(
                        conn,
                        resultId,
                        model,
                        data
                );
            }

        } catch (Exception e) {
            showDatabaseError(
                    "Could not load LLM ingredients",
                    e
            );
        }

        return data;
    }
    
    private void loadIngredientsFromRawOutput(
            Connection conn,
            int resultId,
            DefaultTableModel model,
            LlmData data) throws Exception {

        String sql =
                "SELECT raw_json_output " +
                "FROM nutrition_result " +
                "WHERE result_id=?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, resultId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return;
                }

                String raw =
                        rs.getString("raw_json_output");

                JSONArray ingredients =
                        recoverIngredientArray(raw);

                for (int index = 0;
                     index < ingredients.length();
                     index++) {

                    JSONObject ingredient =
                            ingredients.optJSONObject(index);

                    if (ingredient == null) {
                        continue;
                    }

                    String original =
                            ingredient.optString(
                                    "ingredient_name_original",
                                    ""
                            );

                    String english =
                            ingredient.optString(
                                    "ingredient_name_en",
                                    ""
                            );

                    /*
                     * Ignore non-ingredient JSON objects such as
                     * recipe-level nutrition totals.
                     */
                    if (original.isBlank()
                            && english.isBlank()) {
                        continue;
                    }

                    Double[] nutrients = {
                            jsonNumber(ingredient, "calories"),
                            jsonNumber(ingredient, "total_fat_g"),
                            jsonNumber(ingredient, "saturated_fat_g"),
                            jsonNumber(ingredient, "cholesterol_mg"),
                            jsonNumber(ingredient, "sodium_mg"),
                            jsonNumber(
                                    ingredient,
                                    "total_carbohydrate_g"
                            ),
                            jsonNumber(ingredient, "dietary_fiber_g"),
                            jsonNumber(ingredient, "total_sugars_g"),
                            jsonNumber(ingredient, "protein_g"),
                            jsonNumber(ingredient, "vitamin_d_mcg"),
                            jsonNumber(ingredient, "calcium_mg"),
                            jsonNumber(ingredient, "iron_mg"),
                            jsonNumber(ingredient, "potassium_mg")
                    };

                    data.ingredientTotals.add(nutrients);

                    model.addRow(
                            createIngredientRow(
                                    original,
                                    english,
                                    ingredient.optString(
                                            "quantity_expression",
                                            ""
                                    ),
                                    ingredient.optString(
                                            "quantity_category",
                                            ""
                                    ),
                                    ingredient.optString(
                                            "quantity_unit_original",
                                            ""
                                    ),
                                    jsonNumber(
                                            ingredient,
                                            "quantity_value"
                                    ),
                                    ingredient.optString(
                                            "language",
                                            ""
                                    ),
                                    isHallucinated(original, english)
                                            ? "Yes"
                                            : "No",
                                    jsonNumber(
                                            ingredient,
                                            "estimated_weight_g"
                                    ),
                                    nutrients
                            )
                    );
                }
            }
        }
    }

    private JSONArray recoverIngredientArray(String raw) {

        if (raw == null || raw.isBlank()) {
            return new JSONArray();
        }

        String cleaned =
                raw.replaceAll(
                        "(?s)```(?:json)?",
                        ""
                ).replace("```", "").trim();

        /*
         * First try a proper JSON array.
         */
        try {
            if (cleaned.startsWith("[")) {
                JSONArray array = new JSONArray(cleaned);

                if (containsIngredients(array)) {
                    return array;
                }
            }
        } catch (Exception ignored) {
            // Continue to tolerant object recovery.
        }

        /*
         * A malformed or truncated response can still contain many
         * complete ingredient objects. Parse those objects one by one
         * so a defect later in the response does not hide all earlier
         * ingredients.
         */
        JSONArray recoveredObjects =
                recoverIndividualIngredientObjects(cleaned);

        if (recoveredObjects.length() > 0) {
            return recoveredObjects;
        }

        /*
         * Try a JSON object containing an ingredients array.
         */
        try {
            JSONObject root = new JSONObject(cleaned);

            JSONArray ingredients =
                    root.optJSONArray("ingredients");

            if (ingredients != null) {
                return ingredients;
            }
        } catch (Exception ignored) {
            // Continue to text recovery.
        }

        /*
         * The LLM sometimes writes:
         * 1. Kacang tanah (peanuts) - 500g
         * 2. Bawang merah - 4
         *
         * Recover those entries for the LLM Extraction table.
         */
        return recoverIngredientsFromText(cleaned);
    }

    private JSONArray recoverIndividualIngredientObjects(
            String raw) {

        JSONArray recovered = new JSONArray();

        int ingredientsKey =
                raw.indexOf("\"ingredients\"");

        if (ingredientsKey < 0) {
            return recovered;
        }

        int arrayStart =
                raw.indexOf('[', ingredientsKey);

        if (arrayStart < 0) {
            return recovered;
        }

        int objectStart = -1;
        int objectDepth = 0;
        boolean insideString = false;
        boolean escaped = false;

        for (int index = arrayStart + 1;
             index < raw.length();
             index++) {

            char current = raw.charAt(index);

            if (insideString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    insideString = false;
                }

                continue;
            }

            if (current == '"') {
                insideString = true;
                continue;
            }

            if (current == '{') {
                if (objectDepth == 0) {
                    objectStart = index;
                }

                objectDepth++;
                continue;
            }

            if (current == '}' && objectDepth > 0) {
                objectDepth--;

                if (objectDepth == 0
                        && objectStart >= 0) {

                    addRecoveredIngredient(
                            recovered,
                            raw.substring(
                                    objectStart,
                                    index + 1
                            )
                    );

                    objectStart = -1;
                }

                continue;
            }

            if (current == ']'
                    && objectDepth == 0) {
                break;
            }
        }

        return recovered;
    }

    private void addRecoveredIngredient(
            JSONArray recovered,
            String objectText) {

        try {
            String cleanedObject =
                    objectText.replaceAll(
                            "(?m)//.*$",
                            ""
                    );

            JSONObject object =
                    new JSONObject(cleanedObject);

            if (object.has("ingredient_name_original")
                    || object.has("ingredient_name_en")) {

                recovered.put(object);
            }
        } catch (Exception ignored) {
            /*
             * Skip only this malformed ingredient. Other complete
             * ingredient objects remain available for display.
             */
        }
    }
    
    private boolean containsIngredients(
            JSONArray array) {

        for (int index = 0;
             index < array.length();
             index++) {

            JSONObject object =
                    array.optJSONObject(index);

            if (object != null
                    && (object.has("ingredient_name_original")
                    || object.has("ingredient_name_en"))) {

                return true;
            }
        }

        return false;
    }

    private JSONArray recoverIngredientsFromText(
            String raw) {

        JSONArray ingredients =
                new JSONArray();

        java.util.regex.Pattern linePattern =
                java.util.regex.Pattern.compile(
                        "(?m)^\\s*\\d+\\.\\s+(.+?)\\s+-\\s+(.+?)\\s*$"
                );

        java.util.regex.Matcher matcher =
                linePattern.matcher(raw);

        while (matcher.find()) {

            String namePart =
                    matcher.group(1).trim();

            String quantityPart =
                    matcher.group(2).trim();

            String originalName = namePart;
            String englishName = "";

            java.util.regex.Matcher translationMatcher =
                    java.util.regex.Pattern.compile(
                            "^(.+?)\\s*\\(([^)]+)\\)\\s*$"
                    ).matcher(namePart);

            if (translationMatcher.matches()) {
                originalName =
                        translationMatcher.group(1).trim();

                englishName =
                        translationMatcher.group(2).trim();
            }

            JSONObject ingredient =
                    new JSONObject();

            ingredient.put(
                    "ingredient_name_original",
                    originalName
            );

            if (!englishName.isBlank()) {
                ingredient.put(
                        "ingredient_name_en",
                        englishName
                );
            }

            ingredient.put(
                    "quantity_expression",
                    quantityPart
            );

            java.util.regex.Matcher quantityMatcher =
                    java.util.regex.Pattern.compile(
                            "(-?\\d+(?:\\.\\d+)?)\\s*([a-zA-Z]+)?"
                    ).matcher(quantityPart);

            if (quantityMatcher.find()) {

                ingredient.put(
                        "quantity_value",
                        Double.parseDouble(
                                quantityMatcher.group(1)
                        )
                );

                String unit =
                        quantityMatcher.group(2);

                if (unit != null
                        && !unit.isBlank()) {

                    ingredient.put(
                            "quantity_unit_original",
                            unit
                    );
                }
            }

            ingredients.put(ingredient);
        }

        if (ingredients.length() > 0) {
            return ingredients;
        }

        return recoverIngredientBulletList(raw);
    }

    private JSONArray recoverIngredientBulletList(
            String raw) {

        JSONArray ingredients = new JSONArray();
        Set<String> seen = new HashSet<>();

        String lower = raw.toLowerCase(Locale.ROOT);
        int sectionStart = findIngredientSectionStart(lower);

        if (sectionStart < 0) {
            return ingredients;
        }

        int sectionEnd = raw.length();

        String[] endMarkers = {
                "step 2",
                "estimated nutritional",
                "nutritional values",
                "nutrition facts",
                "json output",
                "calculate the nutrition"
        };

        for (String marker : endMarkers) {
            int markerIndex =
                    lower.indexOf(marker, sectionStart + 1);

            if (markerIndex >= 0
                    && markerIndex < sectionEnd) {
                sectionEnd = markerIndex;
            }
        }

        String section =
                raw.substring(sectionStart, sectionEnd);

        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile(
                        "(?m)^\\s*(?:[-*•]|\\d+\\.)\\s+(.+?)\\s*$"
                ).matcher(section);

        while (matcher.find()) {
            String entry = matcher.group(1)
                    .replace("**", "")
                    .trim();

            if (entry.isBlank()
                    || entry.toLowerCase(Locale.ROOT)
                    .startsWith("step ")) {
                continue;
            }

            String quantityExpression = "";
            int separator = entry.indexOf(" - ");

            if (separator > 0) {
                quantityExpression =
                        entry.substring(separator + 3).trim();
                entry = entry.substring(0, separator).trim();
            }

            String originalName = entry;
            String englishName = "";

            java.util.regex.Matcher translation =
                    java.util.regex.Pattern.compile(
                            "^(.+?)\\s*\\(([^)]+)\\)\\s*$"
                    ).matcher(entry);

            if (translation.matches()) {
                originalName = translation.group(1).trim();
                englishName = translation.group(2).trim();
            } else {
                int colon = entry.indexOf(':');

                if (colon > 0) {
                    originalName = entry.substring(0, colon).trim();
                    englishName = entry.substring(colon + 1).trim();
                }
            }

            originalName = originalName.replaceAll("[:;,]+$", "").trim();

            if (originalName.isBlank()) {
                continue;
            }

            String key = normalizeName(originalName);

            if (key == null || key.isBlank() || !seen.add(key)) {
                continue;
            }

            JSONObject ingredient = new JSONObject();
            ingredient.put("ingredient_name_original", originalName);

            if (!englishName.isBlank()) {
                ingredient.put("ingredient_name_en", englishName);
            }

            if (!quantityExpression.isBlank()) {
                ingredient.put(
                        "quantity_expression",
                        quantityExpression
                );
            }

            ingredients.put(ingredient);
        }

        return ingredients;
    }

    private int findIngredientSectionStart(
            String lower) {

        String[] markers = {
                "identify all ingredients",
                "identify ingredients",
                "identified the following ingredients",
                "identified all ingredients",
                "ingredients mentioned in the transcript",
                "list of ingredients"
        };

        int earliest = -1;

        for (String marker : markers) {
            int index = lower.indexOf(marker);

            if (index >= 0
                    && (earliest < 0 || index < earliest)) {
                earliest = index + marker.length();
            }
        }

        return earliest;
    }
    
    private Double jsonNumber(
            JSONObject object,
            String key) {

        Object value = object.opt(key);

        return value instanceof Number
                ? ((Number) value).doubleValue()
                : null;
    }
    
    private Integer findLatestResultId(
            int reelId,
            Double[] reportedTotals) {

        String sql =
                "SELECT nr.* " +
                "FROM nutrition_result nr " +
                "JOIN experiment e " +
                "ON nr.experiment_id=e.experiment_id " +
                "JOIN transcript t " +
                "ON e.transcript_id=t.transcript_id " +
                "WHERE t.reel_id=? " +
                "AND e.model_id=? " +
                "AND e.technique_id=? " +
                "ORDER BY nr.result_id DESC " +
                "LIMIT 1";

        try (
                Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setInt(1, reelId);
            ps.setInt(2, modelId);
            ps.setInt(3, techniqueId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                for (int index = 0;
                     index < RESULT_TOTAL_COLUMNS.length;
                     index++) {

                    reportedTotals[index] =
                            nullableDouble(
                                    rs,
                                    RESULT_TOTAL_COLUMNS[index]
                            );
                }

                return rs.getInt("result_id");
            }

        } catch (Exception e) {
            showDatabaseError(
                    "Could not load LLM nutrition result",
                    e
            );
            return null;
        }
    }

    private Object[] createIngredientRow(
            String original,
            String english,
            String expression,
            String category,
            String unit,
            Double value,
            String language,
            String hallucination,
            Double weight,
            Double[] nutrients) {

        Object[] row =
                new Object[
                        INGREDIENT_COLUMNS.length
                ];

        row[0] = displayText(original);
        row[1] = displayText(english);
        row[2] = displayText(expression);
        row[3] = displayText(category);
        row[4] = displayText(unit);
        row[5] = displayNumber(value);
        row[6] = displayText(language);
        row[7] = displayText(hallucination);
        row[8] = displayNumber(weight);

        for (int index = 0;
             index < nutrients.length;
             index++) {

            row[9 + index] =
                    displayNumber(
                            nutrients[index]
                    );
        }

        return row;
    }

    private Double[] readIngredientNutrients(
            ResultSet rs) throws SQLException {

        return new Double[]{
                nullableDouble(rs, "calories"),
                nullableDouble(rs, "total_fat_g"),
                nullableDouble(rs, "saturated_fat_g"),
                nullableDouble(rs, "cholesterol_mg"),
                nullableDouble(rs, "sodium_mg"),
                nullableDouble(rs, "total_carbohydrate_g"),
                nullableDouble(rs, "dietary_fiber_g"),
                nullableDouble(rs, "total_sugars_g"),
                nullableDouble(rs, "protein_g"),
                nullableDouble(rs, "vitamin_d_mcg"),
                nullableDouble(rs, "calcium_mg"),
                nullableDouble(rs, "iron_mg"),
                nullableDouble(rs, "potassium_mg")
        };
    }

    private void displayTotals(
            NutrientTotals human,
            NutrientTotals aiIngredients,
            Double[] aiReported) {

        StringBuilder text =
                new StringBuilder();

        text.append(
                String.format(
                        "%-22s | %16s | %18s | %18s | %18s%n",
                        "Nutrient",
                        "Human GT",
                        "AI Ingredient Sum",
                        "AI Reported Total",
                        "Reported - GT"
                )
        );

        text.append(
                "----------------------------------------------------------------------------------------------\n"
        );

        for (int index = 0;
             index < NUTRIENT_LABELS.length;
             index++) {

            Double humanValue =
                    human.value(index);

            Double aiSumValue =
                    aiIngredients.value(index);

            Double reportedValue =
                    aiReported[index];

            Double difference =
                    humanValue == null
                            || reportedValue == null
                            ? null
                            : reportedValue
                            - humanValue;

            text.append(
                    String.format(
                            "%-22s | %16s | %18s | %18s | %18s%n",
                            NUTRIENT_LABELS[index],
                            formatCalculatedTotal(
                                    human,
                                    index
                            ),
                            formatCalculatedTotal(
                                    aiIngredients,
                                    index
                            ),
                            formatNullable(reportedValue),
                            formatNullable(difference)
                    )
            );
        }

        text.append(
        		"\nnot reported = LLM did not provide this recipe total. "
        		        + "* = partial sum; one or more ingredient values were not provided."
        );

        totalsArea.setText(
                text.toString()
        );

        totalsArea.setCaretPosition(0);
    }

    private boolean isHallucinated(
            String original,
            String english) {

        String predictedOriginal =
                normalizeName(original);

        String predictedEnglish =
                normalizeName(english);

        DefaultTableModel gtModel =
                (DefaultTableModel)
                        groundTruthTable.getModel();

        for (int row = 0;
             row < gtModel.getRowCount();
             row++) {

            String gtOriginal =
                    normalizeName(
                            valueAsString(
                                    gtModel.getValueAt(
                                            row,
                                            0
                                    )
                            )
                    );

            String gtEnglish =
                    normalizeName(
                            valueAsString(
                                    gtModel.getValueAt(
                                            row,
                                            1
                                    )
                            )
                    );

            /*
             * Containment is allowed only between faithful
             * original-language ingredient names.
             */
            if (originalNamesMatch(
                    gtOriginal,
                    predictedOriginal
            )) {
                return false;
            }

            /*
             * Translated and cross-language fields must match
             * exactly because an LLM translation may be wrong.
             */
            if (nonBlankEqual(
                    gtOriginal,
                    predictedEnglish
            )
                    || nonBlankEqual(
                    gtEnglish,
                    predictedOriginal
            )
                    || nonBlankEqual(
                    gtEnglish,
                    predictedEnglish
            )) {

                return false;
            }
        }

        return true;
    }
    
    private boolean originalNamesMatch(
            String first,
            String second) {

        if (first.isBlank()
                || second.isBlank()) {

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

    private String normalizeName(
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

    private String displayNumber(
            Double value) {

        return value == null
                ? "not provided"
                : String.format(
                        Locale.US,
                        "%.1f",
                        value
                );
    }

    private String displayText(
            String value) {

        return value == null
                || value.isBlank()
                ? "not provided"
                : value;
    }

    private String formatNullable(
            Double value) {

        return value == null
                ? "not reported"
                : String.format(
                        Locale.US,
                        "%.1f",
                        value
                );
    }
    private String formatCalculatedTotal(
            NutrientTotals totals,
            int index) {

        Double value =
                totals.value(index);

        if (value == null) {
            return "not provided";
        }

        String formatted =
                formatNullable(value);

        /*
         * An asterisk means one or more ingredient values
         * were NULL and were excluded from the sum.
         */
        return totals.isComplete(index)
                ? formatted
                : formatted + "*";
    }
    private void exportComparison() {

        JFileChooser chooser =
                new JFileChooser();

        chooser.setSelectedFile(
                new java.io.File(
                        "Nutrition_Comparison_Reel_"
                                + selectedReelId
                                + ".csv"
                )
        );

        if (chooser.showSaveDialog(this)
                != JFileChooser.APPROVE_OPTION) {

            return;
        }

        try (
                BufferedWriter writer =
                        Files.newBufferedWriter(
                                chooser
                                        .getSelectedFile()
                                        .toPath(),
                                StandardCharsets.UTF_8
                        )
        ) {

            /*
             * Ingredient comparison header.
             */
            writer.write("Layer");

            for (String column
                    : INGREDIENT_COLUMNS) {

                writer.write(",");

                writer.write(
                        CsvUtil.escape(column)
                );
            }

            writer.newLine();

            /*
             * Human and AI ingredient records.
             */
            writeTable(
                    writer,
                    "Human",
                    groundTruthTable
            );

            writeTable(
                    writer,
                    "AI",
                    llmResultTable
            );

            writer.newLine();

            /*
             * Recipe-level summary header.
             */
            writer.write(
                    "Summary,Nutrient,"
                            + "Human GT,Human GT Partial,"
                            + "AI Ingredient Sum,"
                            + "AI Ingredient Sum Partial,"
                            + "AI Reported Total,"
                            + "Reported Minus GT"
            );

            writer.newLine();

            /*
             * One summary row per nutrient.
             */
            for (int index = 0;
                 index < NUTRIENT_LABELS.length;
                 index++) {

                Double human =
                        currentHumanTotals.value(index);

                Double aiSum =
                        currentAiIngredientTotals.value(index);

                Double reported =
                        currentAiReportedTotals[index];

                Double difference =
                        human == null
                                || reported == null
                                ? null
                                : reported - human;

                writer.write("Summary,");

                writer.write(
                        CsvUtil.escape(
                                NUTRIENT_LABELS[index]
                        )
                );

                writer.write(",");

                writer.write(
                        CsvUtil.escape(
                                formatNullable(human)
                        )
                );

                writer.write(",");

                writer.write(
                        currentHumanTotals.isComplete(index)
                                ? "No"
                                : "Yes"
                );

                writer.write(",");

                writer.write(
                        CsvUtil.escape(
                                formatNullable(aiSum)
                        )
                );

                writer.write(",");

                writer.write(
                        currentAiIngredientTotals.isComplete(index)
                                ? "No"
                                : "Yes"
                );

                writer.write(",");

                writer.write(
                        CsvUtil.escape(
                                formatNullable(reported)
                        )
                );

                writer.write(",");

                writer.write(
                        CsvUtil.escape(
                                formatNullable(difference)
                        )
                );

                writer.newLine();
            }

            JOptionPane.showMessageDialog(
                    this,
                    "Comparison exported successfully."
            );

        } catch (Exception e) {

            JOptionPane.showMessageDialog(
                    this,
                    "Export error: "
                            + e.getMessage(),
                    "Export Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void writeTable(
            BufferedWriter writer,
            String layer,
            JTable table) throws Exception {

        TableModel model =
                table.getModel();

        for (int row = 0;
             row < model.getRowCount();
             row++) {

            writer.write(
                    CsvUtil.escape(layer)
            );

            for (int column = 0;
                 column < model.getColumnCount();
                 column++) {

                writer.write(",");

                writer.write(
                        CsvUtil.escape(
                                model.getValueAt(
                                        row,
                                        column
                                )
                        )
                );
            }

            writer.newLine();
        }
    }

    private void clearTable(
            JTable table) {

        ((DefaultTableModel)
                table.getModel())
                .setRowCount(0);
    }

    private void selectInitialReel() {

        if (!selectReel(selectedReelId)
                && reelTable.getRowCount() > 0) {

            reelTable.setRowSelectionInterval(
                    0,
                    0
            );

            selectedReelId =
                    Integer.parseInt(
                            reelTable
                                    .getValueAt(
                                            0,
                                            0
                                    )
                                    .toString()
                    );
        }
    }

    private boolean selectReel(
            int reelId) {

        for (int row = 0;
             row < reelTable.getRowCount();
             row++) {

            int currentId =
                    Integer.parseInt(
                            reelTable
                                    .getValueAt(
                                            row,
                                            0
                                    )
                                    .toString()
                    );

            if (currentId == reelId) {

                reelTable.setRowSelectionInterval(
                        row,
                        row
                );

                reelTable.scrollRectToVisible(
                        reelTable.getCellRect(
                                row,
                                0,
                                true
                        )
                );

                return true;
            }
        }

        return false;
    }

    private int parseReelId(
            String value) {

        try {
            return Integer.parseInt(
                    value.replaceAll(
                            "[^0-9]",
                            ""
                    )
            );

        } catch (Exception e) {
            return 1;
        }
    }

    private int validIndex(
            int index,
            int count) {

        return index < 0
                || index >= count
                ? 0
                : index;
    }

    private String formatLanguage(
            String languages) {

        if (languages == null
                || languages.isBlank()) {

            return "not provided";
        }

        boolean malay =
                languages.contains("MY");

        boolean english =
                languages.contains("EN");

        if (malay && english) {
            return "MY/EN";
        }

        if (malay) {
            return "MY";
        }

        if (english) {
            return "EN";
        }

        return languages;
    }

    private void showDatabaseError(
            String message,
            Exception exception) {

        System.err.println(
                message
                        + ": "
                        + exception.getMessage()
        );

        JOptionPane.showMessageDialog(
                this,
                message
                        + ":\n"
                        + exception.getMessage(),
                "Database Error",
                JOptionPane.ERROR_MESSAGE
        );
    }

    private static class LlmData {

        private final NutrientTotals ingredientTotals =
                new NutrientTotals();

        private final Double[] reportedTotals =
                new Double[NUTRIENT_LABELS.length];
    }

    private static class NutrientTotals {

        private final double[] sums =
                new double[
                        NUTRIENT_LABELS.length
                ];

        private final boolean[] complete =
                new boolean[
                        NUTRIENT_LABELS.length
                ];

        private boolean hasRows;

        private NutrientTotals() {

            for (int index = 0;
                 index < complete.length;
                 index++) {

                complete[index] = true;
            }
        }

        private void add(
                Double[] values) {

            hasRows = true;

            for (int index = 0;
                 index < values.length;
                 index++) {

                if (values[index] == null) {
                    complete[index] = false;
                } else {
                    sums[index] +=
                            values[index];
                }
            }
        }

        private Double value(
                int index) {

            if (!hasRows) {
                return null;
            }

            return sums[index];
        }

        private boolean isComplete(
                int index) {

            return hasRows
                    && complete[index];
        }
    }
}
