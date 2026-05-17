package aijudge.model;

/**
 * Lưu thông tin một lần nộp bài.
 */
public class SubmissionRecord {
    public final String time;
    public final String language;
    public final String verdict;
    public final double score;
    public final String problem;
    public final String code;
    public final String detail;

    public SubmissionRecord(String time, String language, String verdict,
                            double score, String problem, String code, String detail) {
        this.time     = time;
        this.language = language;
        this.verdict  = verdict;
        this.score    = score;
        this.problem  = problem;
        this.code     = code;
        this.detail   = detail;
    }
}
