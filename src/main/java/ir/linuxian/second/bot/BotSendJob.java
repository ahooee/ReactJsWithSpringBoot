package ir.linuxian.second.bot;

/**
 * One admin-panel send operation. Tracked in memory and polled by the UI for
 * the progress bar (queued → sending with upload percent → sent/failed).
 */
public class BotSendJob {

    public enum Status {
        QUEUED, SENDING, SENT, FAILED
    }

    private final String jobId;
    private final String kind;
    private final String target;
    private volatile Status status = Status.QUEUED;
    private volatile String chatTitle = "";
    private volatile int percent;
    private volatile long doneBytes;
    private volatile long totalBytes;
    private volatile String message = "";
    private volatile String error = "";
    private volatile long messageId;
    private final long createdAt = System.currentTimeMillis();

    public BotSendJob(String jobId, String kind, String target) {
        this.jobId = jobId;
        this.kind = kind;
        this.target = target;
    }

    public String getJobId() {
        return jobId;
    }

    public String getKind() {
        return kind;
    }

    public String getTarget() {
        return target;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getChatTitle() {
        return chatTitle;
    }

    public void setChatTitle(String chatTitle) {
        this.chatTitle = chatTitle;
    }

    public int getPercent() {
        return percent;
    }

    public void setPercent(int percent) {
        this.percent = percent;
    }

    public long getDoneBytes() {
        return doneBytes;
    }

    public void setDoneBytes(long doneBytes) {
        this.doneBytes = doneBytes;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public void setTotalBytes(long totalBytes) {
        this.totalBytes = totalBytes;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public long getMessageId() {
        return messageId;
    }

    public void setMessageId(long messageId) {
        this.messageId = messageId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public boolean isDone() {
        return status == Status.SENT || status == Status.FAILED;
    }
}
