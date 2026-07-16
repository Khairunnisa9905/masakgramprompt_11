package edu.utem.ftmk.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class FileTextReader {
    public String read(String path) throws IOException {
        File file = resolve(path);
        if (!file.exists()) throw new IOException("File not found: " + path);
        return Files.readString(file.toPath(), StandardCharsets.UTF_8);
    }

    private File resolve(String path) {
        File direct = new File(path);
        if (direct.exists()) return direct;

        File local = new File(System.getProperty("user.dir"), path);
        if (local.exists()) return local;

        String normalized = path.replace("\\", "/");

        int p = normalized.lastIndexOf("/prompts/");
        if (p >= 0) return new File(System.getProperty("user.dir"), "prompts" + normalized.substring(p + 8));

        int t = normalized.lastIndexOf("/transcriptions/");
        if (t >= 0) return new File(System.getProperty("user.dir"), "transcriptions" + normalized.substring(t + 15));

        return local;
    }
}