package aijudge.ui;

import aijudge.api.GeminiClient;
import aijudge.core.*;
import aijudge.model.TestCase;
import aijudge.util.StringUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

import static aijudge.ui.MainFrame.*;


public class JudgePanel extends JPanel {

    private final JTextArea analysisArea = new JTextArea();

    public JudgePanel(MainFrame f) {
        super(new BorderLayout(0, 0));
        setBackground(C_BG);
        setupAnalysisArea();

        add(buildToolbar(f), BorderLayout.NORTH);
        add(buildBody(f),    BorderLayout.CENTER);
    }

    private JPanel buildToolbar(MainFrame f) {
        // ── Nhóm 1: Sinh code mẫu ─────────────────────────────────────
        JButton btnAC  = actionBtn("✔  Sinh Code AC",  new Color(0x1B6B3A));
        JButton btnWA  = actionBtn("✗  Sinh Code WA",  new Color(0x8B1A1A));
        JButton btnTLE = actionBtn("⏱  Sinh COde TLE", new Color(0x8B4500));

        btnAC .addActionListener(e -> generateSample(f, SampleCodeGenerator.CodeType.AC));
        btnWA .addActionListener(e -> generateSample(f, SampleCodeGenerator.CodeType.WA));
        btnTLE.addActionListener(e -> generateSample(f, SampleCodeGenerator.CodeType.TLE));

        JButton btnAnalyze = actionBtn("📊  Phân tích đề",      new Color(0x1A3A8F));
        JButton btnEval    = actionBtn("🗂  Đánh giá testcase",  new Color(0x1A5F6F));

        btnAnalyze.addActionListener(e -> analyzeProblem(f));
        btnEval   .addActionListener(e -> evaluateTestcases(f));

        JButton btnVerify  = actionBtn("✅  Verify testcase", new Color(0x1A6B3A));
        JButton btnFix     = actionBtn("✏  Sửa output sai",  new Color(0x6B5500));
        JButton btnChecker = actionBtn("</>  Sinh checker",   new Color(0x3A2B6B));

        btnVerify .addActionListener(e -> verifyTestcases(f));
        btnFix    .addActionListener(e -> autoFixOutputs(f));
        btnChecker.addActionListener(e -> generateChecker(f));

        JButton btnClear = actionBtn("🗑  Xóa phân tích", new Color(0x3A3A3A));
        btnClear.addActionListener(e -> {
            analysisArea.setText("");
            f.setStatus("Đã xóa phân tích.");
        });

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 7));
        bar.setBackground(C_PANEL2);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, C_BORDER));

        bar.add(btnAC); bar.add(btnWA); bar.add(btnTLE);
        bar.add(vSep());
        bar.add(btnAnalyze); bar.add(btnEval);
        bar.add(vSep());
        bar.add(btnVerify); bar.add(btnFix); bar.add(btnChecker);
        bar.add(vSep());
        bar.add(btnClear);

        return bar;
    }

    private JPanel buildBody(MainFrame f) {
   
        JPanel problemPanel = buildLeftPanel(f);
        JPanel analysisPanel = buildCenterPanel();
        JPanel codePanel = buildRightPanel(f);
        JPanel resultPanel = buildResultPanel(f);

        JSplitPane topSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, problemPanel, analysisPanel);
        topSplit.setResizeWeight(0.50);
        topSplit.setDividerSize(4);
        topSplit.setBorder(null);
        topSplit.setBackground(C_BG);

        JSplitPane bottomSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, codePanel, resultPanel);
        bottomSplit.setResizeWeight(0.50);
        bottomSplit.setDividerSize(4);
        bottomSplit.setBorder(null);
        bottomSplit.setBackground(C_BG);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topSplit, bottomSplit);
        mainSplit.setResizeWeight(0.50);
        mainSplit.setDividerSize(4);
        mainSplit.setBorder(null);
        mainSplit.setBackground(C_BG);

        JPanel body = new JPanel(new BorderLayout());
        body.setBackground(C_BG);
        body.add(mainSplit, BorderLayout.CENTER);
        return body;
    }

    private JPanel buildLeftPanel(MainFrame f) {
        // Tabs nội dung
        JPanel tabContent = new JPanel(new CardLayout());
        tabContent.setBackground(C_PANEL);

        JScrollPane problemScroll   = darkScroll(f.problemArea);
        JScrollPane testcaseScroll  = darkScroll(f.testcaseArea);
        JScrollPane checkerScroll   = darkScroll(f.checkerArea);
        tabContent.add(problemScroll,  "debai");
        tabContent.add(testcaseScroll, "testcase");
        tabContent.add(checkerScroll,  "checker");

        String[] tabNames   = {"  Đề bài  ","  Testcases  ","  Checker  "};
        String[] tabKeys    = {"debai","testcase","checker"};
        JLabel[] tabLabels  = new JLabel[3];

        JPanel tabBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tabBar.setBackground(C_PANEL2);
        tabBar.setBorder(BorderFactory.createMatteBorder(0,0,1,0,C_BORDER));

        for (int i = 0; i < 3; i++) {
            final int idx = i;
            final String key = tabKeys[i];
            JLabel tab = new JLabel(tabNames[i]);
            tab.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            tab.setForeground(i == 0 ? C_TEXT : C_TEXT_DIM);
            tab.setBorder(i == 0
                ? BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0,0,2,0,C_ACCENT),
                    new EmptyBorder(8,4,6,4))
                : new EmptyBorder(8,4,8,4));
            tab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            tab.addMouseListener(new java.awt.event.MouseAdapter() {
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    ((CardLayout)tabContent.getLayout()).show(tabContent, key);
                    for (int j = 0; j < 3; j++) {
                        boolean active = j == idx;
                        tabLabels[j].setForeground(active ? C_TEXT : C_TEXT_DIM);
                        tabLabels[j].setBorder(active
                            ? BorderFactory.createCompoundBorder(
                                BorderFactory.createMatteBorder(0,0,2,0,C_ACCENT),
                                new EmptyBorder(8,4,6,4))
                            : new EmptyBorder(8,4,8,4));
                    }
                }
            });
            tabLabels[i] = tab;
            tabBar.add(tab);
        }

        JPanel headerBar = new JPanel(new BorderLayout());
        headerBar.setBackground(new Color(0x181B26));
        headerBar.setBorder(BorderFactory.createMatteBorder(0,0,1,0,C_BORDER));

        JLabel hdrLbl = new JLabel("  ≡  ĐỀ BÀI");
        hdrLbl.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        hdrLbl.setForeground(C_TEXT_DIM);
        hdrLbl.setBorder(new EmptyBorder(6,4,0,0));
        headerBar.add(hdrLbl, BorderLayout.NORTH);
        headerBar.add(tabBar,  BorderLayout.SOUTH);

        JPanel left = new JPanel(new BorderLayout(0,0));
        left.setBackground(C_PANEL);
        left.setBorder(BorderFactory.createMatteBorder(0,0,0,1,C_BORDER));
        left.add(headerBar,  BorderLayout.NORTH);
        left.add(tabContent, BorderLayout.CENTER);
        return left;
    }

    private JPanel buildCenterPanel() {
        JPanel headerBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        headerBar.setBackground(new Color(0x181B26));
        headerBar.setBorder(BorderFactory.createMatteBorder(0,0,1,0,C_BORDER));
        JLabel lbl = new JLabel("ℹ  PHÂN TÍCH ĐỀ / ĐÁNH GIÁ TESTCASE");
        lbl.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        lbl.setForeground(C_TEXT_DIM);
        headerBar.add(lbl);

        JScrollPane scroll = darkScroll(analysisArea);

        JPanel center = new JPanel(new BorderLayout(0,0));
        center.setBackground(C_PANEL);
        center.setBorder(BorderFactory.createMatteBorder(0,0,0,1,C_BORDER));
        center.add(headerBar, BorderLayout.NORTH);
        center.add(scroll,    BorderLayout.CENTER);
        return center;
    }

    private JPanel buildRightPanel(MainFrame f) {
        // Header: "</>  CODE CỦA BẠN" + language dropdown + icons
        JPanel headerBar = new JPanel(new BorderLayout());
        headerBar.setBackground(new Color(0x181B26));
        headerBar.setBorder(BorderFactory.createMatteBorder(0,0,1,0,C_BORDER));

        JLabel hdrLbl = new JLabel("  </>  CODE CỦA BẠN");
        hdrLbl.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        hdrLbl.setForeground(C_TEXT_DIM);
        hdrLbl.setBorder(new EmptyBorder(6,4,6,0));

        JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        rightControls.setOpaque(false);

        JLabel expandIco = new JLabel("⤢");
        expandIco.setForeground(C_TEXT_DIM);
        expandIco.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        expandIco.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        rightControls.add(expandIco);

        JLabel moreIco = new JLabel("⋮");
        moreIco.setForeground(C_TEXT_DIM);
        moreIco.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        moreIco.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        rightControls.add(moreIco);

        headerBar.add(hdrLbl,         BorderLayout.WEST);
        headerBar.add(rightControls,  BorderLayout.EAST);

        f.codeArea.setBackground(new Color(0x0E1120));
        f.codeArea.setForeground(C_TEXT);
        f.codeArea.setLineWrap(false);
        JScrollPane codeScroll = new JScrollPane(f.codeArea);
        codeScroll.setBackground(new Color(0x0E1120));
        codeScroll.getViewport().setBackground(new Color(0x0E1120));
        codeScroll.setBorder(null);
        codeScroll.setRowHeaderView(new LineNumberPanel(f.codeArea));

        JPanel right = new JPanel(new BorderLayout(0,0));
        right.setBackground(new Color(0x0E1120));
        right.add(headerBar,  BorderLayout.NORTH);
        right.add(codeScroll, BorderLayout.CENTER);
        return right;
    }

    private JPanel buildResultPanel(MainFrame f) {
        JPanel headerBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        headerBar.setBackground(new Color(0x181B26));
        headerBar.setBorder(BorderFactory.createMatteBorder(0,0,1,0,C_BORDER));
        JLabel lbl = new JLabel("🏁  KẾT QUẢ CHẤM");
        lbl.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        lbl.setForeground(C_TEXT_DIM);
        headerBar.add(lbl);
        JScrollPane scroll = darkScroll(f.resultArea);
        JPanel panel = new JPanel(new BorderLayout(0,0));
        panel.setBackground(C_PANEL);
        panel.add(headerBar, BorderLayout.NORTH);
        panel.add(scroll,    BorderLayout.CENTER);
        return panel;
    }

    private void setupAnalysisArea() {
        analysisArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        analysisArea.setLineWrap(true);
        analysisArea.setWrapStyleWord(true);
        analysisArea.setEditable(false);
        analysisArea.setBackground(C_PANEL);
        analysisArea.setForeground(C_TEXT);
        analysisArea.setCaretColor(C_ACCENT);
        analysisArea.setBorder(new EmptyBorder(10, 12, 10, 12));
        analysisArea.setText(
            "Nhấn \"📊 Phân tích đề\" hoặc \"🗂 Đánh giá testcase\"\nđể xem kết quả tại đây.");
    }

    private Component vSep() {
        JSeparator s = new JSeparator(SwingConstants.VERTICAL);
        s.setPreferredSize(new Dimension(1, 22));
        s.setForeground(C_BORDER);
        return s;
    }

    private void showAnalysis(String text) {
        SwingUtilities.invokeLater(() -> { analysisArea.setText(text); analysisArea.setCaretPosition(0); });
    }

    static class LineNumberPanel extends JPanel {
        private final JTextArea editor;
        LineNumberPanel(JTextArea ed) {
            this.editor = ed;
            setBackground(new Color(0x0A0D18));
            setForeground(new Color(0x445570));
            setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
            setBorder(new EmptyBorder(0, 8, 0, 10));
            ed.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) { repaint(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { repaint(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e){ repaint(); }
            });
        }
        @Override public Dimension getPreferredSize() {
            int lines = editor.getLineCount();
            int w = getFontMetrics(getFont()).stringWidth(String.valueOf(Math.max(lines, 99))) + 20;
            return new Dimension(w, editor.getPreferredSize().height);
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.setFont(getFont());
            g.setColor(getForeground());
            FontMetrics fm = g.getFontMetrics();
            int lineH = editor.getFontMetrics(editor.getFont()).getHeight();
            int lines = editor.getLineCount();
            for (int i = 1; i <= lines; i++) {
                String n = String.valueOf(i);
                int x = getWidth() - fm.stringWidth(n) - 4;
                int y = (i - 1) * lineH + fm.getAscent() + 2;
                g.drawString(n, x, y);
            }
        }
    }

    private void generateSample(MainFrame f, SampleCodeGenerator.CodeType type) {
        String problem = f.problemArea.getText().trim();
        if (problem.isBlank()) { f.setResult("❌ Chưa có đề bài."); return; }
        String lang = (String) f.languageBox.getSelectedItem();
        f.setResult("⏳ Đang sinh code " + type + "...");
        f.setStatus("⏳ Đang gọi AI...");
        f.runAsync(() -> {
            String code = StringUtils.sanitizeCodeForCompile(
                    StringUtils.stripFence(f.sampleGen.generate(problem, lang, type)).trim());
            SwingUtilities.invokeLater(() -> {
                f.codeArea.setText(code);
                f.setResult("✅ Đã sinh code " + type + ".\n▶ Bấm \"Nộp bài / Chấm\" để kiểm tra.");
                f.setStatus("✅ Sinh code " + type + " xong");
            });
        });
    }

    private void analyzeProblem(MainFrame f) {
        String problem = f.problemArea.getText().trim();
        if (problem.isBlank()) { showAnalysis("❌ Chưa có đề bài."); return; }
        showAnalysis("⏳ Đang phân tích đề bài...");
        f.setStatus("⏳ Đang phân tích...");
        f.runAsync(() -> {
            String r = f.analyzer.analyze(problem);
            showAnalysis("📊 PHÂN TÍCH ĐỀ BÀI\n══════════════════════════\n\n" + r);
            f.setStatus("✅ Phân tích xong");
        });
    }

    private void evaluateTestcases(MainFrame f) {
        String problem  = f.problemArea.getText().trim();
        String testcase = f.testcaseArea.getText().trim();
        if (problem.isBlank())  { showAnalysis("❌ Chưa có đề bài."); return; }
        if (testcase.isBlank() || testcase.startsWith("⏳")) { showAnalysis("❌ Chưa có testcase."); return; }
        showAnalysis("⏳ Đang đánh giá testcase...");
        f.setStatus("⏳ Đang đánh giá...");
        f.runAsync(() -> {
            String r = f.analyzer.evaluateTestcases(problem, testcase);
            showAnalysis("🗂 ĐÁNH GIÁ TESTCASE\n══════════════════════════\n\n" + r);
            f.setStatus("✅ Đánh giá xong");
        });
    }

    private void verifyTestcases(MainFrame f) {
        String problem  = f.problemArea.getText().trim();
        String testcase = f.testcaseArea.getText().trim();
        String lang     = (String) f.languageBox.getSelectedItem();
        if (problem.isBlank() || testcase.isBlank() || testcase.startsWith("⏳")) {
            f.setResult("❌ Cần có đề bài và testcase."); return; }
        List<TestCase> tests = TestcaseGenerator.parse(testcase);
        if (tests.isEmpty()) { f.setResult("❌ Không parse được testcase."); return; }
        f.setResult("⏳ Đang verify testcase...");
        f.setStatus("⏳ Đang verify...");
        f.runAsync(() -> {
            try {
                TestcaseVerifier.VerifyReport rep = f.tcVerifier.verify(problem, lang, tests,
                    msg -> f.appendResult(msg));
                f.setResult(rep.summary());
                f.setStatus(rep.allCorrect() ? "✅ Testcase hợp lệ" : "⚠ Có testcase sai output");
                if (rep.compileOk() && !rep.acCode().isBlank() && f.codeArea.getText().trim().isBlank())
                    SwingUtilities.invokeLater(() ->
                        f.codeArea.setText(StringUtils.sanitizeCodeForCompile(rep.acCode())));
            } catch (Exception ex) { f.setResult("❌ Verify lỗi: " + ex.getMessage()); }
        });
    }

    private void autoFixOutputs(MainFrame f) {
        String problem  = f.problemArea.getText().trim();
        String testcase = f.testcaseArea.getText().trim();
        String lang     = (String) f.languageBox.getSelectedItem();
        if (problem.isBlank() || testcase.isBlank()) { f.setResult("❌ Cần có đề bài và testcase."); return; }
        List<TestCase> tests = TestcaseGenerator.parse(testcase);
        if (tests.isEmpty()) { f.setResult("❌ Không parse được testcase."); return; }
        f.setResult("⏳ Đang tự sửa output sai...");
        f.setStatus("⏳ Đang sửa...");
        f.runAsync(() -> {
            try {
                TestcaseVerifier.VerifyReport rep = f.tcVerifier.verify(problem, lang, tests, null);
                if (!rep.compileOk()) { f.setResult("❌ Code AC không biên dịch:\n" + rep.compileSummary()); return; }
                List<TestCase> fixed = f.tcVerifier.fixWrongOutputs(tests, rep);
                SwingUtilities.invokeLater(() -> f.testcaseArea.setText(TestcaseGenerator.buildText(fixed)));
                f.setResult("✅ Đã sửa xong!\n\n" + rep.summary());
                f.setStatus("✅ Đã sửa output sai");
            } catch (Exception ex) { f.setResult("❌ Lỗi: " + ex.getMessage()); }
        });
    }

    private void generateChecker(MainFrame f) {
        String problem = f.problemArea.getText().trim();
        String lang    = (String) f.languageBox.getSelectedItem();
        if (problem.isBlank()) { f.setResult("❌ Chưa có đề bài."); return; }
        f.setResult("⏳ Đang sinh checker..."); f.setStatus("⏳ Đang sinh checker...");
        f.runAsync(() -> {
            String checker = f.checkerGen.generate(problem, lang);
            if (CheckerGenerator.NO_CHECKER.equals(checker)) {
                f.setResult("ℹ️ Bài này không cần checker (so khớp exact là đủ).");
                f.setStatus("ℹ️ Không cần checker");
            } else {
                SwingUtilities.invokeLater(() -> f.checkerArea.setText(checker));
                f.setResult("✅ Đã sinh checker. Xem tab 'Checker'.");
                f.setStatus("✅ Sinh checker xong");
            }
        });
    }
}