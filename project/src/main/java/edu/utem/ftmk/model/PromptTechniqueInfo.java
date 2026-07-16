package edu.utem.ftmk.model;

public enum PromptTechniqueInfo {
    ZERO_SHOT(1, "zero-shot", "Zero-Shot"),
    FEW_SHOT(2, "few-shot", "Few-Shot"),
    CHAIN_OF_THOUGHT(3, "chain-of-thought", "Chain-of-Thought (CoT)"),
    STRUCTURED_OUTPUT(4, "structured-output", "Structured Output");

    private final int id;
    private final String dbName;
    private final String displayName;

    PromptTechniqueInfo(int id, String dbName, String displayName) {
        this.id = id;
        this.dbName = dbName;
        this.displayName = displayName;
    }

    public int id() { return id; }
    public String dbName() { return dbName; }
    public String displayName() { return displayName; }

    public static PromptTechniqueInfo fromId(int id) {
        for (PromptTechniqueInfo t : values()) if (t.id == id) return t;
        return ZERO_SHOT;
    }

    public static String[] dbNames() {
        PromptTechniqueInfo[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) names[i] = values[i].dbName;
        return names;
    }
}