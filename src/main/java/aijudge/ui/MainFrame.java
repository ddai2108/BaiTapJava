package aijudge.ui;

import aijudge.api.GeminiClient;
import aijudge.core.*;
import aijudge.model.TestCase;
import aijudge.util.StringUtils;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

public class MainFrame extends JFrame {

    static final Color C_BG        = new Color(0x12141C);
    static final Color C_PANEL     = new Color(0x1A1D28);
    static final Color C_PANEL2    = new Color(0x20233A);
    static final Color C_BORDER    = new Color(0x2E3248);
    static final Color C_TEXT      = new Color(0xDCE0F0);
    static final Color C_TEXT_DIM  = new Color(0x7880A0);
    static final Color C_ACCENT    = new Color(0x4E9BFF);
    static final Color C_GREEN     = new Color(0x32C878);
    static final Color C_ORANGE    = new Color(0xFF9A38);
    static final Color C_RED       = new Color(0xFF5555);

    final GeminiClient        gemini     = new GeminiClient();
    final ProcessRunner       runner     = new ProcessRunner();
    final CodeCompiler        compiler   = new CodeCompiler(runner);
    final JudgeEngine         judge      = new JudgeEngine(compiler, runner);
    final TestcaseGenerator   tcGen      = new TestcaseGenerator(gemini);
    final SampleCodeGenerator sampleGen  = new SampleCodeGenerator(gemini);
    final ProblemAnalyzer     analyzer   = new ProblemAnalyzer(gemini);
    final CheckerGenerator    checkerGen = new CheckerGenerator(gemini);
    final TestcaseVerifier    tcVerifier = new TestcaseVerifier(gemini, compiler, runner);

    final JTextArea problemArea  = darkArea();
    final JTextArea codeArea     = darkArea();
    final JTextArea resultArea   = darkArea();
    final JTextArea testcaseArea = darkArea();
    final JTextArea checkerArea  = darkArea();

    final JComboBox<String> languageBox = new JComboBox<>(
            new String[]{"C++17","C++14","Java","Python 3"});
    final JLabel statusLabel = new JLabel("Sẵn sàng chấm");

    final JLabel imageInfoLabel      = statusLabel;
    final JLabel testcaseStatusLabel = statusLabel;
    JButton uploadBtn;
    JButton uploadImageBtn;
    JButton submitBtn;

    volatile boolean isProcessing  = false;
    volatile boolean isGenTestcase = false;
    String lastGenProblem = "";
    private javax.swing.Timer debounceTimer;

    private final JLabel lblTime   = sbarLabel("⏱  0.00s");
    private final JLabel lblMemory = sbarLabel("💾  0.00 MB");

    final JudgePanel   judgePanel;
    final HistoryPanel historyPanel;

    public MainFrame() {
        setTitle("Hệ thống chấm bài AI");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setMinimumSize(new Dimension(1280, 800));
        setBackground(C_BG);

        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); }
        catch (Exception ignored) {}

        setupTextAreas();
        judgePanel   = new JudgePanel(this);
        historyPanel = new HistoryPanel(this);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(C_BG);
        root.add(buildTopBar(),    BorderLayout.NORTH);
        root.add(buildTabs(),      BorderLayout.CENTER);
        root.add(buildStatusBar(), BorderLayout.SOUTH);
        setContentPane(root);

        attachListeners();
        historyPanel.loadFromDisk();
    }
    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(C_PANEL);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, C_BORDER));
        bar.setPreferredSize(new Dimension(0, 52));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        left.setOpaque(false);
        left.setBorder(new EmptyBorder(0, 10, 0, 0));

        JLabel ico = new JLabel("⚖");
        ico.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
        ico.setForeground(C_ACCENT);

        JLabel title = new JLabel(" Hệ thống chấm bài AI");
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        title.setForeground(C_TEXT);

        JLabel dot = new JLabel("");
        dot.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        dot.setForeground(C_TEXT_DIM);

        left.add(ico); left.add(title); left.add(dot);

        JPanel center = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        center.setOpaque(false);

        uploadBtn     = topBtn("⬆  Tải ảnh đề",       C_PANEL2,              C_TEXT,              true);
        submitBtn     = topBtn("▶  Nộp bài / Chấm",   new Color(0x1A5ED4),   Color.WHITE,         false);
        JButton ready = topBtn("✓  Sẵn sàng",         new Color(0x0E3322),   new Color(0x32C878), true);
        uploadImageBtn = uploadBtn;
        ready.setEnabled(false);

        center.add(uploadBtn);
        center.add(submitBtn);
        center.add(ready);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.setBorder(new EmptyBorder(0, 0, 0, 10));

        JLabel langLbl = new JLabel("🌐 Ngôn ngữ:");
        langLbl.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        langLbl.setForeground(C_TEXT_DIM);

        languageBox.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        languageBox.setBackground(C_PANEL2);
        languageBox.setForeground(C_TEXT);
        languageBox.setBorder(BorderFactory.createLineBorder(C_BORDER));
        languageBox.setPreferredSize(new Dimension(100, 26));

        right.add(langLbl); right.add(languageBox);

        bar.add(wrap(left),   BorderLayout.WEST);
        bar.add(wrap(center), BorderLayout.CENTER);
        bar.add(wrap(right),  BorderLayout.EAST);
        return bar;
    }

    private static JPanel wrap(JComponent c) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        p.add(c);
        return p;
    }

    private JComponent buildTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(C_BG);
        tabs.setForeground(C_TEXT);
        tabs.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        tabs.addTab(" ⚖  Chấm bài ", judgePanel);
        tabs.addTab(" 📋  Lịch sử  ", historyPanel);
        return tabs;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(C_PANEL);
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, C_BORDER));
        bar.setPreferredSize(new Dimension(0, 26));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        left.setOpaque(false);
        JLabel dot = new JLabel("●");
        dot.setForeground(C_GREEN);
        dot.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        statusLabel.setForeground(C_TEXT_DIM);
        left.add(dot); left.add(statusLabel);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 3));
        right.setOpaque(false);
        JLabel ver = sbarLabel("AI Judge v2.0.0");
        ver.setForeground(C_ACCENT);
        right.add(lblTime); right.add(lblMemory); right.add(ver);

        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private void setupTextAreas() {
        Font mono = new Font(Font.MONOSPACED, Font.PLAIN, 13);
        for (JTextArea ta : new JTextArea[]{problemArea,codeArea,resultArea,testcaseArea,checkerArea}) {
            ta.setFont(mono); ta.setLineWrap(true); ta.setWrapStyleWord(true);
            ta.setBackground(C_PANEL); ta.setForeground(C_TEXT);
            ta.setCaretColor(C_ACCENT); ta.setBorder(new EmptyBorder(8,10,8,10));
        }
        resultArea.setEditable(false);
        resultArea.setBackground(new Color(0x0E1120));
        checkerArea.setText(CheckerGenerator.NO_CHECKER);
    }

    private void attachListeners() {
        uploadBtn.addActionListener(e -> new ImageUploadHandler(this).handle());
        submitBtn.addActionListener(e -> { if (!isProcessing) new SubmitHandler(this).handle(); });
        problemArea.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { scheduleAutoGenTestcase(); }
            public void removeUpdate(DocumentEvent e) { scheduleAutoGenTestcase(); }
            public void changedUpdate(DocumentEvent e){}
        });
    }

    void scheduleAutoGenTestcase() {
        if (debounceTimer != null && debounceTimer.isRunning()) debounceTimer.stop();
        debounceTimer = new javax.swing.Timer(1800, e -> {
            String prob = problemArea.getText().trim();
            if (prob.isBlank() || prob.equals(lastGenProblem) || isProcessing || isGenTestcase) return;
            triggerTestcaseGeneration(prob);
        });
        debounceTimer.setRepeats(false);
        debounceTimer.start();
    }

    void triggerTestcaseGeneration(String problem) {
        isGenTestcase = true;
        setStatus("⏳ Đang sinh testcase...");
        testcaseArea.setText("⏳ Đang sinh testcase...");
        runAsync(() -> {
            try {
                String tests = tcGen.generate(problem);
                boolean valid = TestcaseGenerator.isValidFormat(tests) && !GeminiClient.isApiError(tests);
                if (valid) {
                    lastGenProblem = problem;
                    List<TestCase> parsed = TestcaseGenerator.parse(tests);
                    final String ft = tests;
                    SwingUtilities.invokeLater(() -> {
                        testcaseArea.setText(ft);
                        setStatus("✅ " + parsed.size() + " testcase sẵn sàng");
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        testcaseArea.setText("");
                        setStatus("⚠ Chưa sinh được testcase");
                        resultArea.setText("⚠ Sinh testcase thất bại:\n\n" + tests);
                    });
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    setStatus("❌ Lỗi sinh testcase");
                    resultArea.setText("❌ Lỗi sinh testcase:\n" + StringUtils.stackTrace(ex));
                });
            } finally { isGenTestcase = false; }
        });
    }

    // ── Public API ─────────────────────────────────────────────────────
    public void startProcessing(Runnable task) {
        isProcessing = true;
        long t0 = System.currentTimeMillis();
        submitBtn.setEnabled(false); submitBtn.setText("⏳ Đang chấm...");
        runAsync(() -> {
            try { task.run(); }
            finally {
                long ms = System.currentTimeMillis() - t0;
                isProcessing = false;
                SwingUtilities.invokeLater(() -> {
                    submitBtn.setEnabled(true);
                    submitBtn.setText("▶  Nộp bài / Chấm");
                    lblTime.setText(String.format("⏱  %.2fs", ms / 1000.0));
                });
            }
        });
    }

    public void setResult(String text)  { SwingUtilities.invokeLater(() -> resultArea.setText(text)); }
    public void appendResult(String l)  {
        SwingUtilities.invokeLater(() -> {
            resultArea.append(l + "\n");
            resultArea.setCaretPosition(resultArea.getDocument().getLength());
        });
    }
    public void setStatus(String s)     { SwingUtilities.invokeLater(() -> statusLabel.setText(s)); }
    public void runAsync(Runnable r)    { new Thread(r).start(); }

    // ── UI factories ───────────────────────────────────────────────────

    /** Nút bo tròn trên top bar */
    static JButton topBtn(String text, Color bg, Color fg, boolean outlined) {
        JButton b = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fill(new RoundRectangle2D.Float(0,0,getWidth(),getHeight(),8,8));
                if (outlined) {
                    g2.setColor(fg.darker());
                    g2.setStroke(new BasicStroke(1f));
                    g2.draw(new RoundRectangle2D.Float(0.5f,0.5f,getWidth()-1,getHeight()-1,8,8));
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setBackground(bg); b.setForeground(fg);
        b.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        b.setOpaque(false); b.setContentAreaFilled(false);
        b.setBorderPainted(false); b.setFocusPainted(false);
        b.setBorder(new EmptyBorder(6,16,6,16));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    /** Nút hành động trên toolbar — bo tròn màu bg */
    static JButton actionBtn(String text, Color bg) {
        JButton b = new JButton(text) {
            private boolean hov = false;
            { addMouseListener(new java.awt.event.MouseAdapter(){
                public void mouseEntered(java.awt.event.MouseEvent e){hov=true;repaint();}
                public void mouseExited (java.awt.event.MouseEvent e){hov=false;repaint();}
            }); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(hov ? bg.brighter() : bg);
                g2.fill(new RoundRectangle2D.Float(0,0,getWidth(),getHeight(),6,6));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setBackground(bg); b.setForeground(Color.WHITE);
        b.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        b.setOpaque(false); b.setContentAreaFilled(false);
        b.setBorderPainted(false); b.setFocusPainted(false);
        b.setBorder(new EmptyBorder(5,12,5,12));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    static JTextArea darkArea() { return new JTextArea(); }

    static JScrollPane darkScroll(JTextArea ta) {
        JScrollPane sp = new JScrollPane(ta);
        sp.setBackground(ta.getBackground());
        sp.getViewport().setBackground(ta.getBackground());
        sp.setBorder(null);
        return sp;
    }

    /**
     * Panel với header nhỏ kiểu ảnh (icon + title muted + border bottom).
     */
    static JPanel panelWithHeader(String iconAndTitle, Component content) {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        header.setBackground(new Color(0x181B26));
        header.setBorder(BorderFactory.createMatteBorder(0,0,1,0,C_BORDER));
        JLabel lbl = new JLabel(iconAndTitle);
        lbl.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        lbl.setForeground(C_TEXT_DIM);
        header.add(lbl);

        JPanel panel = new JPanel(new BorderLayout(0,0));
        panel.setBackground(C_PANEL);
        panel.setBorder(BorderFactory.createLineBorder(C_BORDER));
        panel.add(header, BorderLayout.NORTH);
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    // Backward compat
    public static JPanel titled(String title, Component comp) {
        return panelWithHeader(title, comp);
    }
    static JPanel titledDark(String title, Component comp) {
        return panelWithHeader(title, comp);
    }

    private static JLabel sbarLabel(String t) {
        JLabel l = new JLabel(t);
        l.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        l.setForeground(C_TEXT_DIM);
        return l;
    }
}