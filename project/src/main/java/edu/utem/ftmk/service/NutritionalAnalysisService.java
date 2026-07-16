package edu.utem.ftmk.service;

import edu.utem.ftmk.database.DBConnection;
import edu.utem.ftmk.llm.LLMService;
import edu.utem.ftmk.repository.ExperimentRepository;
import edu.utem.ftmk.repository.ModelRepository;
import edu.utem.ftmk.repository.NutritionResultRepository;
import edu.utem.ftmk.repository.PromptRepository;
import edu.utem.ftmk.repository.TranscriptRepository;
import edu.utem.ftmk.util.FileTextReader;
import edu.utem.ftmk.util.JsonResponseExtractor;

import java.sql.Connection;
import java.util.List;
import java.util.function.Consumer;

public class NutritionalAnalysisService {

    private final TranscriptRepository transcriptRepo =
            new TranscriptRepository();

    private final ModelRepository modelRepo =
            new ModelRepository();

    private final PromptRepository promptRepo =
            new PromptRepository();

    private final ExperimentRepository experimentRepo =
            new ExperimentRepository();

    private final NutritionResultRepository resultRepo =
            new NutritionResultRepository();

    private final FileTextReader reader =
            new FileTextReader();

    private final LLMService llm =
            new LLMService();

    /*
     * Processes every transcript registered in the database.
     *
     * This overload is used when no progress listener is needed.
     */
    public String analyzeAll(
            int modelId,
            int techniqueId) {

        return analyzeAll(
                modelId,
                techniqueId,
                message -> {
                    // No progress listener.
                }
        );
    }

    /*
     * Processes every transcript registered in the database
     * and reports progress to the connected client.
     */
    public String analyzeAll(
            int modelId,
            int techniqueId,
            Consumer<String> progressListener) {

        List<Integer> transcriptIds =
                transcriptRepo.findAllTranscriptIds();

        if (transcriptIds.isEmpty()) {
            return "FAILED|No transcript records found";
        }

        int total = transcriptIds.size();
        int completed = 0;
        int failed = 0;

        progressListener.accept(
                "STARTED|" + total
        );

        System.out.println(
                "=================================================="
        );

        System.out.println(
                "SERVER BATCH STARTED"
        );

        System.out.println(
                "Model ID: " + modelId
        );

        System.out.println(
                "Technique ID: " + techniqueId
        );

        System.out.println(
                "Total database transcripts: " + total
        );

        System.out.println(
                "=================================================="
        );

        for (int index = 0;
             index < transcriptIds.size();
             index++) {

            int transcriptId =
                    transcriptIds.get(index);

            int current =
                    index + 1;

            System.out.println(
                    "Processing transcript "
                            + current
                            + "/"
                            + total
                            + " | transcript_id="
                            + transcriptId
            );

            String response =
                    analyze(
                            transcriptId,
                            modelId,
                            techniqueId
                    );

            boolean successful =
                    response != null
                            && response.startsWith(
                                    "SUCCESS"
                            );

            if (successful) {
                completed++;

                progressListener.accept(
                        "PROGRESS|"
                                + current
                                + "|"
                                + total
                                + "|SUCCESS|transcript_id="
                                + transcriptId
                );

                System.out.println(
                        "SUCCESS transcript "
                                + current
                                + "/"
                                + total
                                + " | transcript_id="
                                + transcriptId
                );

            } else {
                failed++;

                progressListener.accept(
                        "PROGRESS|"
                                + current
                                + "|"
                                + total
                                + "|FAILED|transcript_id="
                                + transcriptId
                );

                System.err.println(
                        "FAILED transcript "
                                + current
                                + "/"
                                + total
                                + " | transcript_id="
                                + transcriptId
                                + " | "
                                + response
                );
            }
        }

        System.out.println(
                "=================================================="
        );

        System.out.println(
                "SERVER BATCH FINISHED"
        );

        System.out.println(
                "Completed: "
                        + completed
                        + "/"
                        + total
        );

        System.out.println(
                "Failed: "
                        + failed
                        + "/"
                        + total
        );

        System.out.println(
                "=================================================="
        );

        if (failed == 0) {
            return "SUCCESS|"
                    + completed
                    + "/"
                    + total;
        }

        if (completed > 0) {
            return "PARTIAL|"
                    + completed
                    + "/"
                    + total
                    + "|"
                    + failed
                    + " failed";
        }

        return "FAILED|0/"
                + total
                + "|"
                + failed
                + " failed";
    }

    /*
     * Processes one transcript using one model and one
     * prompt-engineering technique.
     */
    public String analyze(
            int transcriptId,
            int modelId,
            int techniqueId) {

        int experimentId = -1;

        try (Connection conn =
                     DBConnection.getConnection()) {

            String transcript =
                    transcriptRepo.readTranscriptText(
                            conn,
                            transcriptId
                    );

            if (transcript == null) {
                return "FAILED|Transcript not found";
            }

            String modelTag =
                    modelRepo.findModelTag(
                            conn,
                            modelId
                    );

            String[] promptFiles =
                    promptRepo.findPromptFiles(
                            conn,
                            techniqueId
                    );

            if (promptFiles == null) {
                return "FAILED|Prompt technique not found";
            }

            String systemPrompt =
                    reader.read(
                            promptFiles[0]
                    );

            String userPrompt =
                    reader.read(
                            promptFiles[1]
                    ).replace(
                            "{{TRANSCRIPT}}",
                            transcript
                    );

            experimentId =
                    experimentRepo.upsertRunningExperiment(
                            conn,
                            transcriptId,
                            modelId,
                            techniqueId
                    );

            String rawResponse =
                    llm.chat(
                            modelTag,
                            systemPrompt,
                            userPrompt
                    );

            String extractedJson =
                    JsonResponseExtractor.extractObject(
                            rawResponse
                    );

            /*
             * The LLM completed and returned a response,
             * but no parseable JSON object could be extracted.
             *
             * This is a completed experiment with
             * json_valid = false.
             */
            if (extractedJson == null) {

                String reason;

                if (rawResponse == null) {

                    reason =
                            "the LLM returned a null response";

                } else if (rawResponse.isBlank()) {

                    reason =
                            "the LLM returned an empty response";

                } else {

                    reason =
                            "no complete JSON object could be extracted";
                }

                System.err.println(
                        "LLM response is not parseable JSON: "
                                + reason
                                + "."
                );

                resultRepo.saveRawInvalid(
                        conn,
                        experimentId,
                        rawResponse
                );

                experimentRepo.updateStatus(
                        conn,
                        experimentId,
                        "completed"
                );

                return "SUCCESS|"
                        + experimentId
                        + "|INVALID_JSON";
            }

            /*
             * A parseable JSON object was found.
             * The repository preserves missing and incorrect
             * model-generated values for later evaluation.
             */
            boolean saved =
                    resultRepo.save(
                            conn,
                            experimentId,
                            extractedJson,
                            rawResponse
                    );

            experimentRepo.updateStatus(
                    conn,
                    experimentId,
                    saved
                            ? "completed"
                            : "failed"
            );

            if (saved) {
                return "SUCCESS|"
                        + experimentId;
            }

            return "FAILED|Could not save result";

        } catch (Exception e) {

            markFailed(
                    experimentId
            );

            String message =
                    e.getMessage();

            if (message == null
                    || message.isBlank()) {

                message =
                        e.getClass()
                                .getSimpleName();
            }

            return "FAILED|" + message;
        }
    }

    /*
     * Marks genuine execution failures such as:
     * - database failure
     * - Ollama failure
     * - timeout
     * - missing prompt file
     */
    private void markFailed(
            int experimentId) {

        if (experimentId <= 0) {
            return;
        }

        try (Connection conn =
                     DBConnection.getConnection()) {

            experimentRepo.updateStatus(
                    conn,
                    experimentId,
                    "failed"
            );

        } catch (Exception e) {
            System.err.println(
                    "Could not mark experiment "
                            + experimentId
                            + " as failed: "
                            + e.getMessage()
            );
        }
    }
}