package edu.utem.ftmk.server;

import edu.utem.ftmk.service.NutritionalAnalysisService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientHandler implements Runnable {

    private final Socket socket;

    private final NutritionalAnalysisService service =
            new NutritionalAnalysisService();

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {

        try (
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

            out.println("READY");

            String command;

            while ((command = in.readLine()) != null) {

                command = command.trim();

                if ("QUIT".equalsIgnoreCase(command)) {
                    out.println("GOODBYE");
                    break;
                }

                processCommand(
                        command,
                        out
                );
            }

        } catch (Exception e) {
            System.err.println(
                    "Client error: " + e.getMessage()
            );

        } finally {
            try {
                socket.close();
            } catch (Exception ignored) {
            }
        }
    }

    private void processCommand(
            String command,
            PrintWriter out) {

        if (!command.startsWith("ANALYZE|")) {
            out.println(
                    "FINISHED|FAILED|Unknown command"
            );
            return;
        }

        String[] parts =
                command.split("\\|");

        if (parts.length != 3) {
            out.println(
                    "FINISHED|FAILED|Expected "
                            + "ANALYZE|modelId|techniqueId"
            );
            return;
        }

        try {
            int modelId =
                    Integer.parseInt(parts[1]);

            int techniqueId =
                    Integer.parseInt(parts[2]);

            String result =
                    service.analyzeAll(
                            modelId,
                            techniqueId,
                            out::println
                    );

            out.println(
                    "FINISHED|" + result
            );

        } catch (NumberFormatException e) {
            out.println(
                    "FINISHED|FAILED|IDs must be integers"
            );

        } catch (Exception e) {
            out.println(
                    "FINISHED|FAILED|"
                            + safeMessage(e)
            );
        }
    }

    private String safeMessage(Exception exception) {

        String message =
                exception.getMessage();

        if (message == null
                || message.isBlank()) {

            return exception
                    .getClass()
                    .getSimpleName();
        }

        /*
         * The socket protocol uses | as a separator.
         */
        return message.replace(
                "|",
                "/"
        );
    }
}