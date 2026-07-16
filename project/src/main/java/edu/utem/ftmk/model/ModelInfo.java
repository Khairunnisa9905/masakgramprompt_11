package edu.utem.ftmk.model;

public enum ModelInfo {
    LLAMA(1, "Llama 3.2 3B Instruct", "llama3.2:3b"),
    PHI(2, "Phi-4-mini 3.8B Instruct", "phi4-mini"),
    QWEN(3, "Qwen 2.5 3B Instruct", "qwen2.5:3b"),
    SEA_LION(4, "Gemma-SEA-LION v4 4B", "aisingapore/Gemma-SEA-LION-v4-4B-VL"),
    MEDGEMMA(5, "MedGemma 4B", "medgemma:4b");

    private final int id;
    private final String displayName;
    private final String modelTag;

    ModelInfo(int id, String displayName, String modelTag) {
        this.id = id;
        this.displayName = displayName;
        this.modelTag = modelTag;
    }

    public int id() { return id; }
    public String displayName() { return displayName; }
    public String modelTag() { return modelTag; }

    public static ModelInfo fromId(int id) {
        for (ModelInfo m : values()) if (m.id == id) return m;
        return LLAMA;
    }

    public static String[] displayNames() {
        ModelInfo[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) names[i] = values[i].displayName;
        return names;
    }
}