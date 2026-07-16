package edu.utem.ftmk.client;

import edu.utem.ftmk.model.ModelInfo;
import edu.utem.ftmk.model.PromptTechniqueInfo;
import edu.utem.ftmk.repository.ExperimentRepository;
import edu.utem.ftmk.repository.ReelRepository;
import edu.utem.ftmk.repository.TranscriptRepository;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.text.DefaultHighlighter;

public class DashboardView extends JFrame {
    private final ReelRepository reelRepository = new ReelRepository();
    private final TranscriptRepository transcriptRepository = new TranscriptRepository();
    private final ExperimentRepository experimentRepository = new ExperimentRepository();

    private JTable reelTable;
    private JTextPane transcriptPane;
    private JButton btnViewNutrition;
    private JButton btnRunAnalysis;
    private JButton btnExportData;

    private JRadioButton rdoLlama, rdoPhi, rdoQwen, rdoSeaLion, rdoMedGemma;
    private JCheckBox chkZeroShot, chkFewShot, chkCoT, chkStructured;

    private final Map<String, JLabel> statusMatrixMap = new HashMap<>();

    public DashboardView() {
        setTitle("MasakGramPrompt Dashboard - Nutritional Analytics");
        setSize(1200, 750);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        add(buildTopConfigPanel(), BorderLayout.NORTH);

        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        mainSplitPane.setDividerLocation(180);
        mainSplitPane.setTopComponent(new JScrollPane(buildReelTable()));
        mainSplitPane.setBottomComponent(buildWorkspacePanel());

        add(mainSplitPane, BorderLayout.CENTER);

        loadAggregateStatuses();
    }

    private JPanel buildTopConfigPanel() {
        JPanel topConfigPanel = new JPanel();
        topConfigPanel.setLayout(new BoxLayout(topConfigPanel, BoxLayout.Y_AXIS));
        topConfigPanel.setBorder(BorderFactory.createTitledBorder("Pipeline Configuration Selection"));
        topConfigPanel.setBackground(new Color(248, 249, 250));

        JPanel modelRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
        modelRow.setOpaque(false);
        JLabel lblModelTitle = new JLabel("Select LLM Model (Select 1): ");
        lblModelTitle.setFont(new Font("Arial", Font.BOLD, 12));
        modelRow.add(lblModelTitle);

        rdoLlama = new JRadioButton(ModelInfo.LLAMA.displayName());
        rdoPhi = new JRadioButton(ModelInfo.PHI.displayName());
        rdoQwen = new JRadioButton(ModelInfo.QWEN.displayName());
        rdoSeaLion = new JRadioButton(ModelInfo.SEA_LION.displayName());
        rdoMedGemma = new JRadioButton(ModelInfo.MEDGEMMA.displayName());

        ButtonGroup modelGroup = new ButtonGroup();
        for (JRadioButton btn : new JRadioButton[]{rdoLlama, rdoPhi, rdoQwen, rdoSeaLion, rdoMedGemma}) {
            modelGroup.add(btn);
            modelRow.add(btn);
        }

        rdoLlama.setSelected(true);
        topConfigPanel.add(modelRow);

        JPanel techRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
        techRow.setOpaque(false);
        JLabel lblTechTitle = new JLabel("Prompt Techniques (Select 1+):");
        lblTechTitle.setFont(new Font("Arial", Font.BOLD, 12));
        techRow.add(lblTechTitle);

        chkZeroShot = new JCheckBox(PromptTechniqueInfo.ZERO_SHOT.displayName());
        chkFewShot = new JCheckBox(PromptTechniqueInfo.FEW_SHOT.displayName());
        chkCoT = new JCheckBox(PromptTechniqueInfo.CHAIN_OF_THOUGHT.displayName());
        chkStructured = new JCheckBox(PromptTechniqueInfo.STRUCTURED_OUTPUT.displayName());

        for (JCheckBox cb : new JCheckBox[]{chkZeroShot, chkFewShot, chkCoT, chkStructured}) {
            techRow.add(cb);
        }

        topConfigPanel.add(techRow);

        JPanel noteRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        noteRow.setOpaque(false);
        JLabel lblNote = new JLabel("The client sends only the selected model and prompt technique. Transcript batch processing is handled by the server.");
        lblNote.setFont(new Font("Arial", Font.ITALIC, 11));
        lblNote.setForeground(new Color(127, 140, 141));
        noteRow.add(lblNote);
        topConfigPanel.add(noteRow);

        return topConfigPanel;
    }

    private JTable buildReelTable() {
        String[] columns = {
                "Reel ID", "Influencer Handle", "Instagram Code", "Reel URL",
                "Identified By", "Date Added", "Language Tag", "Transcript Status", "Ground Truth"
        };

        DefaultTableModel model = new DefaultTableModel(columns, 0);
        reelTable = new JTable(model);

        List<Object[]> rows = reelRepository.findDashboardRows();
        if (rows.isEmpty()) {
            model.addRow(new Object[]{"No Data Found", "Check XAMPP tables", "-", "-", "-", "-", "-", "-", "-"});
        } else {
            for (Object[] row : rows) {
                model.addRow(row);
            }
        }

        reelTable.getSelectionModel()
        .addListSelectionListener(
                event -> {

                    if (!event.getValueIsAdjusting()) {

                        loadTranscriptPreview();
                        loadStatusesForSelectedReel();
                    }
                }
        );

        return reelTable;
    }
    
    private void loadStatusesForSelectedReel() {

        for (JLabel label
                : statusMatrixMap.values()) {

            updateStatusLabel(
                    label,
                    "PENDING"
            );
        }

        int row =
                reelTable.getSelectedRow();

        if (row < 0) {
            return;
        }

        Object reelValue =
                reelTable.getValueAt(
                        row,
                        0
                );

        if (reelValue == null
                || "No Data Found".equals(
                reelValue.toString()
        )) {

            return;
        }

        try {
            int reelId =
                    Integer.parseInt(
                            reelValue.toString()
                    );

            Map<String, String> statuses =
                    experimentRepository
                            .findStatusesForReel(
                                    reelId
                            );

            for (Map.Entry<String, String> entry
                    : statuses.entrySet()) {

                JLabel label =
                        statusMatrixMap.get(
                                entry.getKey()
                        );

                if (label != null) {

                    updateStatusLabel(
                            label,
                            entry.getValue()
                    );
                }
            }

        } catch (Exception exception) {

            System.err.println(
                    "Could not load reel statuses: "
                            + exception.getMessage()
            );
        }
    }
    private JPanel buildWorkspacePanel() {
        JPanel workspacePanel = new JPanel(new GridLayout(1, 2, 10, 10));

        JPanel transcriptPanel = new JPanel(new BorderLayout());
        transcriptPanel.setBorder(BorderFactory.createTitledBorder("Transcript Preview (Code-Switched Highlights)"));

        transcriptPane = new JTextPane();
        transcriptPane.setEditable(false);
        transcriptPane.setFont(new Font("Consolas", Font.PLAIN, 12));
        transcriptPane.setText("Select a reel from the table above to preview its transcript. Pipeline processing is handled by the server.");

        transcriptPanel.add(new JScrollPane(transcriptPane), BorderLayout.CENTER);
        workspacePanel.add(transcriptPanel);

        JPanel matrixContainer = new JPanel(new BorderLayout(5, 5));
        matrixContainer.setBorder(BorderFactory.createTitledBorder("LLM & Prompt Technique Evaluation Status Matrix"));

        JPanel gridPanel = new JPanel(new GridLayout(6, 5, 6, 6));
        setupMatrixGrid(gridPanel);

        matrixContainer.add(gridPanel, BorderLayout.CENTER);
        matrixContainer.add(buildActionPanel(), BorderLayout.SOUTH);

        workspacePanel.add(matrixContainer);

        return workspacePanel;
    }

    private JPanel buildActionPanel() {
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 10));

        btnRunAnalysis = new JButton("Run Pipeline");
        btnRunAnalysis.setFont(new Font("Arial", Font.BOLD, 12));
        btnRunAnalysis.setBackground(new Color(46, 204, 113));
        btnRunAnalysis.setForeground(Color.WHITE);
        btnRunAnalysis.addActionListener(e -> runPipelineAnalysis());

        btnViewNutrition = new JButton("View Fact Sheet");
        btnViewNutrition.setFont(new Font("Arial", Font.BOLD, 12));
        btnViewNutrition.addActionListener(e -> openFactSheet());

        btnExportData = new JButton("Export Evaluation Data");
        btnExportData.setFont(new Font("Arial", Font.BOLD, 12));
        btnExportData.setBackground(new Color(155, 89, 182));
        btnExportData.setForeground(Color.WHITE);
        btnExportData.addActionListener(e -> openDataExport());
        btnExportData.setPreferredSize(new Dimension(200, 40));
        btnExportData.setOpaque(true);
        btnExportData.setBorderPainted(true);

        actionPanel.add(btnRunAnalysis);
        actionPanel.add(btnViewNutrition);
        actionPanel.add(btnExportData);

        return actionPanel;
    }

    private void loadTranscriptPreview() {

        int row =
                reelTable.getSelectedRow();

        if (row == -1) {
            return;
        }

        Object value =
                reelTable.getValueAt(
                        row,
                        0
                );

        if (value == null
                || value.toString()
                        .equals("No Data Found")) {

            return;
        }

        try {
            int reelId =
                    Integer.parseInt(
                            value.toString()
                    );

            String text =
                    transcriptRepository
                            .readTranscriptTextByReelId(
                                    reelId
                            );

            if (text == null) {

                transcriptPane.setText(
                        "No transcript found for Reel ID "
                                + reelId
                );

                return;
            }

            transcriptPane.setText(text);

            highlightMalayTerms(
                    reelId,
                    text
            );

        } catch (Exception e) {

            transcriptPane.setText(
                    "Could not read transcript: "
                            + e.getMessage()
            );
        }
    }
    
    private void highlightMalayTerms(
            int reelId,
            String transcript) throws Exception {

        transcriptPane
                .getHighlighter()
                .removeAllHighlights();

        List<String> terms =
                transcriptRepository
                        .findMalayTermsByReelId(
                                reelId
                        );

        String lowerTranscript =
                transcript.toLowerCase(
                        Locale.ROOT
                );

        DefaultHighlighter.DefaultHighlightPainter painter =
                new DefaultHighlighter.DefaultHighlightPainter(
                        new Color(
                                255,
                                235,
                                130
                        )
                );

        for (String term : terms) {

            String lowerTerm =
                    term.toLowerCase(
                            Locale.ROOT
                    );

            int fromIndex = 0;

            while (fromIndex
                    < lowerTranscript.length()) {

                int start =
                        lowerTranscript.indexOf(
                                lowerTerm,
                                fromIndex
                        );

                if (start < 0) {
                    break;
                }

                int end =
                        start
                                + lowerTerm.length();

                if (isTermBoundary(
                        lowerTranscript,
                        start,
                        end
                )) {

                    transcriptPane
                            .getHighlighter()
                            .addHighlight(
                                    start,
                                    end,
                                    painter
                            );
                }

                fromIndex = end;
            }
        }
    }

    private boolean isTermBoundary(
            String text,
            int start,
            int end) {

        boolean leftBoundary =
                start == 0
                        || !Character.isLetterOrDigit(
                        text.charAt(start - 1)
                );

        boolean rightBoundary =
                end >= text.length()
                        || !Character.isLetterOrDigit(
                        text.charAt(end)
                );

        return leftBoundary
                && rightBoundary;
    }

    private void loadAggregateStatuses() {
        for (JLabel lbl : statusMatrixMap.values()) {
            updateStatusLabel(lbl, "PENDING");
        }

        try {
            Map<String, String> statuses = experimentRepository.findAggregateStatus();

            for (Map.Entry<String, String> entry : statuses.entrySet()) {
                JLabel lbl = statusMatrixMap.get(entry.getKey());
                if (lbl != null) {
                    updateStatusLabel(lbl, entry.getValue());
                }
            }
        } catch (Exception e) {
            System.err.println("loadAggregateStatuses error: " + e.getMessage());
        }
    }

    private void runPipelineAnalysis() {
        ModelInfo model = selectedModel();
        List<Integer> techniques = selectedTechniqueIds();

        if (techniques.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please select at least one prompt technique.",
                    "Selection Required",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "This will ask the server to run " + model.displayName() + "\n" +
                        "for " + techniques.size() + " technique(s). This may take a long time. Continue?",
                "Confirm Batch Run",
                JOptionPane.YES_NO_OPTION);

        if (confirm != JOptionPane.YES_OPTION) return;

        btnRunAnalysis.setEnabled(false);
        new Thread(() -> runBatch(model, techniques)).start();
    }

    private void runBatch(
            ModelInfo model,
            List<Integer> techniques) {

        NutritionalServerClient client =
                new NutritionalServerClient();

        String stopType = null;
        String stopDetails = null;

        for (int techniqueId : techniques) {

            String techName =
                    PromptTechniqueInfo
                            .fromId(techniqueId)
                            .dbName();

            SwingUtilities.invokeLater(
                    () -> updateStatus(
                            model.displayName(),
                            techName,
                            "RUNNING"
                    )
            );

            String response =
                    client.sendAnalyzeRequest(
                            model.id(),
                            techniqueId,
                            progressMessage ->
                                    SwingUtilities.invokeLater(
                                            () -> updateStatus(
                                                    model.displayName(),
                                                    techName,
                                                    progressStatusText(
                                                            progressMessage
                                                    )
                                            )
                                    )
                    );

            SwingUtilities.invokeLater(
                    () -> updateStatus(
                            model.displayName(),
                            techName,
                            statusFromResponse(response)
                    )
            );

            if (response == null) {

                stopType = "CONNECTION";
                stopDetails = "No response received from server.";
                break;
            }

            if (response.startsWith("SUCCESS")) {
                continue;
            }

            if (response.startsWith("PARTIAL|")) {

                stopType = "PARTIAL";
                stopDetails = response;
                break;
            }

            if (response.startsWith(
                    "FAILED|No server progress")
                    || response.startsWith(
                    "FAILED|Connection error")
                    || response.startsWith(
                    "FAILED|Server closed connection")) {

                stopType = "CONNECTION";
                stopDetails = response;
                break;
            }

            stopType = "FAILED";
            stopDetails = response;
            break;
        }

        String finalStopType = stopType;
        String finalStopDetails = stopDetails;

        SwingUtilities.invokeLater(() -> {

            btnRunAnalysis.setEnabled(true);
            loadStatusesForSelectedReel();

            if (finalStopType == null) {

                JOptionPane.showMessageDialog(
                        this,
                        "All selected server batches finished.",
                        "Batch Run Complete",
                        JOptionPane.INFORMATION_MESSAGE
                );

            } else if ("PARTIAL".equals(
                    finalStopType)) {

                JOptionPane.showMessageDialog(
                        this,
                        "The server batch finished with "
                                + "one or more failed transcripts.\n\n"
                                + finalStopDetails
                                + "\n\nThe next selected technique "
                                + "was not started.",
                        "Batch Completed with Failures",
                        JOptionPane.WARNING_MESSAGE
                );

            } else if ("CONNECTION".equals(
                    finalStopType)) {

                JOptionPane.showMessageDialog(
                        this,
                        "The dashboard stopped waiting "
                                + "for the server.\n\n"
                                + finalStopDetails
                                + "\n\nThe server may still be processing. "
                                + "Check the server console before "
                                + "starting another run.",
                        "Batch Connection Ended",
                        JOptionPane.ERROR_MESSAGE
                );

            } else {

                JOptionPane.showMessageDialog(
                        this,
                        "The server reported that the batch failed.\n\n"
                                + finalStopDetails
                                + "\n\nThe next selected technique "
                                + "was not started.",
                        "Batch Failed",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });
    }

    private String statusFromResponse(String response) {
        if (response == null) {
            return "FAILED";
        }

        if (response.startsWith("SUCCESS")) {
            return "COMPLETED";
        }

        if (response.startsWith("PARTIAL|")) {
            return response.replace("PARTIAL|", "");
        }

        if (response.startsWith("FAILED|")) {
            return "FAILED";
        }

        return response;
    }

    private ModelInfo selectedModel() {
        if (rdoPhi.isSelected()) return ModelInfo.PHI;
        if (rdoQwen.isSelected()) return ModelInfo.QWEN;
        if (rdoSeaLion.isSelected()) return ModelInfo.SEA_LION;
        if (rdoMedGemma.isSelected()) return ModelInfo.MEDGEMMA;
        return ModelInfo.LLAMA;
    }

    private List<Integer> selectedTechniqueIds() {
        List<Integer> ids = new ArrayList<>();

        if (chkZeroShot.isSelected()) {
            ids.add(PromptTechniqueInfo.ZERO_SHOT.id());
        }

        if (chkFewShot.isSelected()) {
            ids.add(PromptTechniqueInfo.FEW_SHOT.id());
        }

        if (chkCoT.isSelected()) {
            ids.add(PromptTechniqueInfo.CHAIN_OF_THOUGHT.id());
        }

        if (chkStructured.isSelected()) {
            ids.add(PromptTechniqueInfo.STRUCTURED_OUTPUT.id());
        }

        return ids;
    }

    private void openFactSheet() {
        int row = reelTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this,
                    "Please select a reel from the table first.",
                    "Selection Required",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        String reelId = reelTable.getValueAt(row, 0).toString();
        ModelInfo model = selectedModel();
        int techniqueId = selectedTechniqueIds().isEmpty() ? 1 : selectedTechniqueIds().get(0);
        String techName = PromptTechniqueInfo.fromId(techniqueId).dbName();

        new NutritionalFactSheetWindow(
                reelId,
                model.displayName() + " (" + techName + ")",
                model.id(),
                techniqueId
        ).setVisible(true);
    }

    private void openDataExport() {
        new DataExportWindow().setVisible(true);
    }

    private void setupMatrixGrid(JPanel panel) {
        String[] models = ModelInfo.displayNames();
        String[] techniques = PromptTechniqueInfo.dbNames();

        panel.add(new JLabel(""));

        for (String tech : techniques) {
            JLabel lbl = new JLabel(tech, SwingConstants.CENTER);
            lbl.setFont(new Font("Arial", Font.BOLD, 11));
            panel.add(lbl);
        }

        for (String model : models) {
            JLabel modelLabel = new JLabel(model, SwingConstants.CENTER);
            modelLabel.setFont(new Font("Arial", Font.BOLD, 11));
            panel.add(modelLabel);

            for (String tech : techniques) {
                JLabel status = new JLabel("PENDING", SwingConstants.CENTER);
                status.setFont(new Font("Arial", Font.PLAIN, 10));
                status.setOpaque(true);
                status.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));

                statusMatrixMap.put(model + "_" + tech, status);
                updateStatusLabel(status, "PENDING");
                panel.add(status);
            }
        }
    }

    public void updateStatus(String model, String technique, String state) {
        JLabel lbl = statusMatrixMap.get(model + "_" + technique);
        if (lbl != null) {
            updateStatusLabel(lbl, state);
        }
    }

    private void updateStatusLabel(JLabel lbl, String state) {
        lbl.setText(state);

        String upper = state.toUpperCase();

        if (upper.startsWith("RUNNING") ||
                (!upper.equals("PENDING") && !upper.equals("COMPLETED") && !upper.contains("FAILED"))) {
            lbl.setBackground(new Color(241, 196, 15));
            lbl.setForeground(Color.BLACK);
        } else if (upper.equals("COMPLETED")) {
            lbl.setBackground(new Color(46, 204, 113));
            lbl.setForeground(Color.WHITE);
        } else if (upper.contains("FAILED")) {
            lbl.setBackground(new Color(231, 76, 60));
            lbl.setForeground(Color.WHITE);
        } else {
            lbl.setBackground(new Color(236, 240, 241));
            lbl.setForeground(Color.DARK_GRAY);
        }
    }
    private String progressStatusText(
            String message) {

        if (message == null) {
            return "RUNNING";
        }

        String[] parts =
                message.split("\\|", 5);

        if (parts.length >= 2
                && "STARTED".equals(parts[0])) {

            return "RUNNING 0/" + parts[1];
        }

        if (parts.length >= 4
                && "PROGRESS".equals(parts[0])) {

            String current = parts[1];
            String total = parts[2];
            String outcome = parts[3];

            if ("FAILED".equals(outcome)) {
                return "RUNNING "
                        + current
                        + "/"
                        + total
                        + " - LAST FAILED";
            }

            return "RUNNING "
                    + current
                    + "/"
                    + total;
        }

        return "RUNNING";
    }
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new DashboardView().setVisible(true));
    }
}