package edu.utem.ftmk.util;

public final class JsonResponseExtractor {
    private JsonResponseExtractor() {}

    public static String extractObject(String raw) {
        if (raw == null) return null;

        String cleaned = raw.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("(?s)```[a-zA-Z]*\\n?", "").replace("```", "").trim();
        }

        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end < start) return null;

        return cleaned.substring(start, end + 1);
    }
}