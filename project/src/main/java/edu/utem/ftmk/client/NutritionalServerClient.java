package edu.utem.ftmk.client;

import edu.utem.ftmk.config.AppConfig;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.function.Consumer;

public class NutritionalServerClient {

    public String sendAnalyzeRequest(
            int modelId,
            int techniqueId) {

        return sendAnalyzeRequest(
                modelId,
                techniqueId,
                message -> {
                    // No progress listener supplied.
                }
        );
    }

    public String sendAnalyzeRequest(
            int modelId,
            int techniqueId,
            Consumer<String> progressListener) {

        String command =
                "ANALYZE|"
                        + modelId
                        + "|"
                        + techniqueId;

        return sendCommand(
                command,
                progressListener
        );
    }

    private String sendCommand(
            String command,
            Consumer<String> progressListener) {

        try {
            Socket rawSocket =
                    new Socket(
                            AppConfig.SERVER_HOST,
                            AppConfig.SERVER_PORT
                    );

            /*
             * This timeout applies between server messages,
             * not to the entire 50-transcript batch.
             */
            rawSocket.setSoTimeout(
                    AppConfig.CLIENT_TIMEOUT_MS
            );

            try (
                    Socket socket = rawSocket;

                    BufferedReader in =
                            new BufferedReader(
                                    new InputStreamReader(
                                            socket.getInputStream()
                                    )
                            );

                    PrintWriter out =
                            new PrintWriter(
                                    socket.getOutputStream(),
                                    true
                            )
            ) {

                String greeting =
                        in.readLine();

                if (!"READY".equals(greeting)) {
                    return "FAILED|Server did not send READY";
                }

                out.println(command);

                String line;

                while ((line = in.readLine()) != null) {

                    if (line.startsWith("STARTED|")
                            || line.startsWith("PROGRESS|")) {

                        progressListener.accept(line);
                        continue;
                    }

                    if (line.startsWith("FINISHED|")) {

                        String result =
                                line.substring(
                                        "FINISHED|".length()
                                );

                        out.println("QUIT");
                        in.readLine();

                        return result;
                    }
                }

                return "FAILED|Server closed connection unexpectedly";
            }

        } catch (SocketTimeoutException e) {
            return "FAILED|No server progress within timeout";

        } catch (Exception e) {
            return "FAILED|Connection error: "
                    + e.getMessage();
        }
    }
}