package aijudge.ui;

import aijudge.model.SubmissionRecord;
import aijudge.util.JsonHelper;
import aijudge.util.StringUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class HistoryPanel extends JPanel {

    private static final Path HISTORY_FILE =
        Paths.get(System.getProperty("user.home"), ".aijudge_history.json");

    private final List<SubmissionRecord>    records   = new ArrayList<>();
    private final DefaultListModel<String>  listModel = new DefaultListModel<>();
    private final JList<String>             listView  = new JList<>(listModel);
    private final JTextArea                 detail    = new JTextArea();

    public HistoryPanel(MainFrame frame) {
        super(new BorderLayout(4, 4));

        listView.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        detail  .setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        detail  .setEditable(false);

        listView.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int idx = listView.getSelectedIndex();
            if (idx < 0 || idx >= records.size()) return;
            showDetail(records.get(idx));
        });

        JButton clearBtn = new JButton("🗑 Xóa lịch sử");
        clearBtn.addActionListener(e -> clearHistory());

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setResizeWeight(0.28);
        split.setLeftComponent (MainFrame.titled("Danh sách nộp bài", new JScrollPane(listView)));
        split.setRightComponent(MainFrame.titled("Chi tiết",          new JScrollPane(detail)));

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(clearBtn);

        add(split, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
    }

    public void addRecord(SubmissionRecord rec) {
        records.add(0, rec);
        listModel.add(0, formatLabel(rec));
        saveToDisk();
    }

    public void loadFromDisk() {
        List<SubmissionRecord> loaded = JsonHelper.load(HISTORY_FILE);
        for (SubmissionRecord r : loaded) {
            records.add(r);
            listModel.addElement(formatLabel(r));
        }
    }

    private void showDetail(SubmissionRecord r) {
        detail.setText(
            "Thời gian  : " + r.time     + "\n" +
            "Ngôn ngữ   : " + r.language + "\n" +
            "Kết luận   : " + r.verdict  + "\n" +
            "Điểm       : " + String.format("%.2f", r.score) + " / 10\n\n" +
            "── Đề bài ──\n"  + r.problem + "\n\n" +
            "── Code ──\n"    + r.code    + "\n\n" +
            "── Kết quả ──\n" + r.detail
        );
        detail.setCaretPosition(0);
    }

    private void clearHistory() {
        records.clear();
        listModel.clear();
        detail.setText("");
        saveToDisk();
    }

    private void saveToDisk() {
        try { JsonHelper.save(HISTORY_FILE, records); }
        catch (Exception ignored) {}
    }

    private String formatLabel(SubmissionRecord r) {
        String title = StringUtils.truncate(r.problem, 60);
        return r.time + "  [" + r.language + "]  " + r.verdict + "  " + title;
    }
}
