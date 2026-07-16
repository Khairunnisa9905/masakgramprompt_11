package edu.utem.ftmk.server;

import edu.utem.ftmk.config.AppConfig;

import java.net.ServerSocket;
import java.net.Socket;

public class NutritionalServer {
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("MasakGramPrompt Nutritional LLM Server Starting");
        System.out.println("==================================================");

        try (ServerSocket serverSocket = new ServerSocket(AppConfig.SERVER_PORT)) {
            System.out.println("Server listening on port " + AppConfig.SERVER_PORT);

            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(new ClientHandler(socket)).start();
            }
        } catch (Exception e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }
}