package edu.utem.ftmk.repository;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.nio.charset.StandardCharsets;

public class NutritionResultRepository {

    private static final String[] NUTRIENT_KEYS = {
            "calories",
            "total_fat_g",
            "saturated_fat_g",
            "cholesterol_mg",
            "sodium_mg",
            "total_carbohydrate_g",
            "dietary_fiber_g",
            "total_sugars_g",
            "protein_g",
            "vitamin_d_mcg",
            "calcium_mg",
            "iron_mg",
            "potassium_mg"
    };

    /*
     * Saves a parseable JSON response transactionally.
     *
     * Missing fields are stored as SQL NULL rather than zero.
     * Incorrect values are preserved exactly as generated.
     */
    public boolean save(
            Connection conn,
            int experimentId,
            String jsonText,
            String rawResponse) {

        boolean originalAutoCommit = true;

        try {
            originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            JSONObject json;

            try {
                json = new JSONObject(jsonText);
            } catch (JSONException parseException) {
                rollbackQuietly(conn);

                insertRawInvalid(
                        conn,
                        experimentId,
                        rawResponse
                );

                conn.commit();

                System.err.println(
                        "LLM response is not parseable JSON: "
                                + parseException.getMessage()
                );

                return true;
            }

            int resultId = insertNutritionResult(
                    conn,
                    experimentId,
                    json,
                    rawResponse
            );

            JSONArray ingredients =
                    json.optJSONArray("ingredients");

            if (ingredients != null) {
                insertIngredients(
                        conn,
                        resultId,
                        ingredients
                );
            }

            conn.commit();

            reportQualityWarnings(json);

            return true;

        } catch (Exception e) {
            rollbackQuietly(conn);

            System.err.println(
                    "Could not save nutrition result: "
                            + e.getMessage()
            );

            return false;

        } finally {
            try {
                conn.setAutoCommit(originalAutoCommit);
            } catch (SQLException e) {
                System.err.println(
                        "Could not restore auto-commit: "
                                + e.getMessage()
                );
            }
        }
    }

    /*
     * Stores a response that is not parseable JSON.
     *
     * json_valid is FALSE, but the experiment itself may still
     * be completed because the LLM execution produced a response.
     */
    public void saveRawInvalid(
            Connection conn,
            int experimentId,
            String raw) {

        try {
            insertRawInvalid(
                    conn,
                    experimentId,
                    raw
            );

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Could not save raw invalid response.",
                    e
            );
        }
    }

    private int insertNutritionResult(
            Connection conn,
            int experimentId,
            JSONObject json,
            String raw) throws SQLException {

        String sql =
                "INSERT INTO nutrition_result " +
                "(experiment_id, recipe_name, servings_estimated, " +
                "serving_calories, serving_total_fat_g, " +
                "serving_saturated_fat_g, serving_cholesterol_mg, " +
                "serving_sodium_mg, serving_carbohydrate_g, " +
                "serving_fiber_g, serving_sugars_g, " +
                "serving_protein_g, serving_vitamin_d_mcg, " +
                "serving_calcium_mg, serving_iron_mg, " +
                "serving_potassium_mg, total_calories, " +
                "total_fat_g, total_saturated_fat_g, " +
                "total_cholesterol_mg, total_sodium_mg, " +
                "total_carbohydrate_g, total_fiber_g, " +
                "total_sugars_g, total_protein_g, " +
                "total_vitamin_d_mcg, total_calcium_mg, " +
                "total_iron_mg, total_potassium_mg, " +
                "raw_json_output, json_valid) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?," +
                "?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

        try (PreparedStatement ps =
                     conn.prepareStatement(
                             sql,
                             Statement.RETURN_GENERATED_KEYS
                     )) {

            ps.setInt(1, experimentId);

            setNullableString(
                    ps,
                    2,
                    getStringOrNull(
                            json,
                            "recipe_name"
                    )
            );

            setNullableInteger(
                    ps,
                    3,
                    getIntegerOrNull(
                            json,
                            "servings_estimated"
                    )
            );

            bindNutrition(
                    ps,
                    4,
                    json.optJSONObject(
                            "amount_per_serving"
                    )
            );

            bindNutrition(
                    ps,
                    17,
                    json.optJSONObject(
                            "nutrition_total"
                    )
            );

            ps.setString(
                    30,
                    limit(raw)
            );

            ps.setBoolean(
                    31,
                    true
            );

            ps.executeUpdate();

            try (ResultSet keys =
                         ps.getGeneratedKeys()) {

                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }

        throw new SQLException(
                "No result_id was generated."
        );
    }

    private void insertIngredients(
            Connection conn,
            int resultId,
            JSONArray ingredients) throws SQLException {

        String sql =
                "INSERT INTO ingredient_result " +
                "(result_id, name_original, name_en, " +
                "quantity_value, unit_original, unit_en, " +
                "estimated_weight_g, calories, total_fat_g, " +
                "saturated_fat_g, cholesterol_mg, sodium_mg, " +
                "total_carbohydrate_g, dietary_fiber_g, " +
                "total_sugars_g, protein_g, vitamin_d_mcg, " +
                "calcium_mg, iron_mg, potassium_mg) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

        try (PreparedStatement ps =
                     conn.prepareStatement(sql)) {

            for (int index = 0;
                 index < ingredients.length();
                 index++) {

                JSONObject ingredient =
                        ingredients.optJSONObject(index);

                /*
                 * Non-object array entries cannot be represented
                 * in ingredient_result. They remain preserved in
                 * raw_json_output.
                 */
                if (ingredient == null) {
                    System.err.println(
                            "Ingredient at index "
                                    + index
                                    + " is not an object. "
                                    + "Preserved only in raw JSON."
                    );
                    continue;
                }

                ps.setInt(
                        1,
                        resultId
                );

                /*
                 * name_original is required by the supplied
                 * database structure, so missing values use an
                 * empty string rather than inventing a name.
                 */
                ps.setString(
                        2,
                        getStringOrEmpty(
                                ingredient,
                                "ingredient_name_original"
                        )
                );

                setNullableString(
                        ps,
                        3,
                        getStringOrNull(
                                ingredient,
                                "ingredient_name_en"
                        )
                );

                setNullableDouble(
                        ps,
                        4,
                        getNumberOrNull(
                                ingredient,
                                "quantity_value"
                        )
                );

                setNullableString(
                        ps,
                        5,
                        getStringOrNull(
                                ingredient,
                                "quantity_unit_original"
                        )
                );

                setNullableString(
                        ps,
                        6,
                        getStringOrNull(
                                ingredient,
                                "quantity_unit_en"
                        )
                );

                setNullableDouble(
                        ps,
                        7,
                        getNumberOrNull(
                                ingredient,
                                "estimated_weight_g"
                        )
                );

                bindNutrition(
                        ps,
                        8,
                        ingredient
                );

                ps.addBatch();
            }

            ps.executeBatch();
        }
    }

    private void bindNutrition(
            PreparedStatement ps,
            int start,
            JSONObject nutrition) throws SQLException {

        for (int index = 0;
             index < NUTRIENT_KEYS.length;
             index++) {

            Double value = nutrition == null
                    ? null
                    : getNumberOrNull(
                            nutrition,
                            NUTRIENT_KEYS[index]
                    );

            setNullableDouble(
                    ps,
                    start + index,
                    value
            );
        }
    }

    private void insertRawInvalid(
            Connection conn,
            int experimentId,
            String raw) throws SQLException {

        String sql =
                "INSERT INTO nutrition_result " +
                "(experiment_id, raw_json_output, json_valid) " +
                "VALUES (?, ?, FALSE)";

        try (PreparedStatement ps =
                     conn.prepareStatement(sql)) {

            ps.setInt(
                    1,
                    experimentId
            );

            ps.setString(
                    2,
                    limit(raw)
            );

            ps.executeUpdate();
        }
    }

    /*
     * Reports quality defects without changing or rejecting
     * the model's output.
     */
    private void reportQualityWarnings(
            JSONObject json) {

        JSONArray ingredients =
                json.optJSONArray("ingredients");

        if (ingredients == null) {

            System.err.println(
                    "QUALITY WARNING: ingredients array is missing."
            );

            ingredients = new JSONArray();

        } else if (ingredients.length() == 0) {

            System.err.println(
                    "QUALITY WARNING: ingredients array is empty."
            );
        }

        double[] sums =
                new double[NUTRIENT_KEYS.length];

        boolean[] completeSums =
                new boolean[NUTRIENT_KEYS.length];

        for (int index = 0;
             index < completeSums.length;
             index++) {

            completeSums[index] =
                    ingredients.length() > 0;
        }

        for (int ingredientIndex = 0;
             ingredientIndex < ingredients.length();
             ingredientIndex++) {

            JSONObject ingredient =
                    ingredients.optJSONObject(
                            ingredientIndex
                    );

            if (ingredient == null) {

                System.err.println(
                        "QUALITY WARNING: ingredients["
                                + ingredientIndex
                                + "] is not a JSON object."
                );

                for (int index = 0;
                     index < completeSums.length;
                     index++) {

                    completeSums[index] = false;
                }

                continue;
            }

            String englishName =
                    getStringOrNull(
                            ingredient,
                            "ingredient_name_en"
                    );

            if (englishName == null
                    || englishName.isBlank()) {

                System.err.println(
                        "QUALITY WARNING: ingredients["
                                + ingredientIndex
                                + "].ingredient_name_en "
                                + "is missing or blank."
                );
            }

            for (int nutrientIndex = 0;
                 nutrientIndex < NUTRIENT_KEYS.length;
                 nutrientIndex++) {

                String nutrient =
                        NUTRIENT_KEYS[nutrientIndex];

                Double value =
                        getNumberOrNull(
                                ingredient,
                                nutrient
                        );

                if (value == null) {

                    completeSums[nutrientIndex] =
                            false;

                    System.err.println(
                            "QUALITY WARNING: ingredients["
                                    + ingredientIndex
                                    + "]."
                                    + nutrient
                                    + " is missing or non-numeric."
                    );

                } else {

                    sums[nutrientIndex] += value;
                }
            }
        }

        JSONObject totals =
                json.optJSONObject(
                        "nutrition_total"
                );

        if (totals == null) {

            System.err.println(
                    "QUALITY WARNING: nutrition_total "
                            + "object is missing."
            );
        }

        JSONObject serving =
                json.optJSONObject(
                        "amount_per_serving"
                );

        if (serving == null) {

            System.err.println(
                    "QUALITY WARNING: amount_per_serving "
                            + "object is missing."
            );
        }

        Integer servings =
                getIntegerOrNull(
                        json,
                        "servings_estimated"
                );

        if (servings == null || servings <= 0) {

            System.err.println(
                    "QUALITY WARNING: servings_estimated "
                            + "is missing, non-numeric, or not positive."
            );
        }

        for (int index = 0;
             index < NUTRIENT_KEYS.length;
             index++) {

            String nutrient =
                    NUTRIENT_KEYS[index];

            Double totalValue = totals == null
                    ? null
                    : getNumberOrNull(
                            totals,
                            nutrient
                    );

            if (totals != null
                    && totalValue == null) {

                System.err.println(
                        "QUALITY WARNING: nutrition_total."
                                + nutrient
                                + " is missing or non-numeric."
                );
            }

            if (completeSums[index]
                    && totalValue != null) {

                double expectedTotal =
                        roundOneDecimal(
                                sums[index]
                        );

                if (!closeEnough(
                        expectedTotal,
                        totalValue
                )) {

                    System.err.println(
                            "QUALITY WARNING: nutrition_total."
                                    + nutrient
                                    + " does not equal ingredient sum. "
                                    + "Expected "
                                    + expectedTotal
                                    + " but received "
                                    + totalValue
                                    + "."
                    );
                }
            }

            Double servingValue = serving == null
                    ? null
                    : getNumberOrNull(
                            serving,
                            nutrient
                    );

            if (serving != null
                    && servingValue == null) {

                System.err.println(
                        "QUALITY WARNING: amount_per_serving."
                                + nutrient
                                + " is missing or non-numeric."
                );
            }

            if (servings != null
                    && servings > 0
                    && totalValue != null
                    && servingValue != null) {

                double expectedServing =
                        roundOneDecimal(
                                totalValue / servings
                        );

                if (!closeEnough(
                        expectedServing,
                        servingValue
                )) {

                    System.err.println(
                            "QUALITY WARNING: amount_per_serving."
                                    + nutrient
                                    + " is incorrect. Expected "
                                    + expectedServing
                                    + " but received "
                                    + servingValue
                                    + "."
                    );
                }
            }
        }
    }

    private String getStringOrEmpty(
            JSONObject object,
            String key) {

        String value =
                getStringOrNull(
                        object,
                        key
                );

        return value == null
                ? ""
                : value;
    }

    private String getStringOrNull(
            JSONObject object,
            String key) {

        if (object == null
                || !object.has(key)
                || object.isNull(key)) {

            return null;
        }

        Object value =
                object.opt(key);

        return value instanceof String
                ? (String) value
                : null;
    }

    private Double getNumberOrNull(
            JSONObject object,
            String key) {

        if (object == null
                || !object.has(key)
                || object.isNull(key)) {

            return null;
        }

        Object value =
                object.opt(key);

        if (!(value instanceof Number)) {
            return null;
        }

        double number =
                ((Number) value).doubleValue();

        return Double.isFinite(number)
                ? number
                : null;
    }

    private Integer getIntegerOrNull(
            JSONObject object,
            String key) {

        Double value =
                getNumberOrNull(
                        object,
                        key
                );

        if (value == null
                || value != Math.rint(value)
                || value > Integer.MAX_VALUE
                || value < Integer.MIN_VALUE) {

            return null;
        }

        return value.intValue();
    }

    private void setNullableString(
            PreparedStatement ps,
            int index,
            String value) throws SQLException {

        if (value == null) {
            ps.setNull(
                    index,
                    Types.VARCHAR
            );
        } else {
            ps.setString(
                    index,
                    value
            );
        }
    }

    private void setNullableInteger(
            PreparedStatement ps,
            int index,
            Integer value) throws SQLException {

        if (value == null) {
            ps.setNull(
                    index,
                    Types.INTEGER
            );
        } else {
            ps.setInt(
                    index,
                    value
            );
        }
    }

    private void setNullableDouble(
            PreparedStatement ps,
            int index,
            Double value) throws SQLException {

        if (value == null) {
            ps.setNull(
                    index,
                    Types.FLOAT
            );
        } else {
            ps.setDouble(
                    index,
                    value
            );
        }
    }

    private boolean closeEnough(
            double first,
            double second) {

        return Math.abs(first - second)
                <= 0.11;
    }

    private double roundOneDecimal(
            double value) {

        return Math.round(value * 10.0) / 10.0;
    }

    private void rollbackQuietly(
            Connection conn) {

        try {
            conn.rollback();
        } catch (SQLException e) {
            System.err.println(
                    "Rollback failed: "
                            + e.getMessage()
            );
        }
    }

    private String limit(String raw) {

        if (raw == null) {
            return "";
        }

        /*
         * MySQL TEXT is limited by bytes, not Java characters.
         * Keep the stored UTF-8 output safely below 65,535 bytes.
         */
        final int maxBytes = 60000;

        byte[] bytes =
                raw.getBytes(StandardCharsets.UTF_8);

        if (bytes.length <= maxBytes) {
            return raw;
        }

        int safeLength = maxBytes;

        /*
         * Avoid cutting in the middle of a UTF-8 character.
         */
        while (safeLength > 0
                && (bytes[safeLength] & 0xC0) == 0x80) {

            safeLength--;
        }

        return new String(
                bytes,
                0,
                safeLength,
                StandardCharsets.UTF_8
        );
    }
}