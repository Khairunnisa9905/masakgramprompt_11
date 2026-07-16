package edu.utem.ftmk.config;

public final class AppConfig {
    public static final String DB_URL = "jdbc:mysql://localhost:3306/masakgramprompt?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    public static final String DB_USER = "root";
    public static final String DB_PASSWORD = "";

    public static final String OLLAMA_BASE_URL = "http://localhost:11434";
    public static final String SERVER_HOST = "localhost";
    public static final int SERVER_PORT = 1234;
    public static final int CLIENT_TIMEOUT_MS = 2400000;

    private AppConfig() {}
}