package ir.linuxian.second.service;

/**
 * One "upload books to Telegram" run. The admin page polls it for the progress
 * bar and can request a graceful stop (after the current file).
 */
public class BookUploadJob {

    public enum Status {
        QUEUED, RUNNING, STOPPING, STOPPED, COMPLETED, FAILED
    }

    private final String jobId;
    private final String downloadsPath;
    private final String chatTarget;
    private final long createdAt = System.currentTimeMillis();

    private volatile Status status = Status.QUEUED;
    private volatile long chatId;
    private volatile String chatTitle = "";
    private volatile int totalBooks;
    private volatile int processedBooks;
    private volatile int uploadedBooks;
    private volatile int failedBooks;
    private volatile int skippedBooks;
    private volatile long totalBytes;
    private volatile long doneBytes;
    private volatile int percent;
    private volatile String currentBookId;
    private volatile String currentBookTitle = "";
    private volatile String currentFile = "";
    private volatile String message = "";
    private volatile String error = "";
    private volatile boolean stopRequested;

    public BookUploadJob(String jobId, String downloadsPath, String chatTarget) {
        this.jobId = jobId;
        this.downloadsPath = downloadsPath;
        this.chatTarget = chatTarget;
    }

    public String getJobId() {
        return jobId;
    }

    public String getDownloadsPath() {
        return downloadsPath;
    }

    public String getChatTarget() {
        return chatTarget;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public long getChatId() {
        return chatId;
    }

    public void setChatId(long chatId) {
        this.chatId = chatId;
    }

    public String getChatTitle() {
        return chatTitle;
    }

    public void setChatTitle(String chatTitle) {
        this.chatTitle = chatTitle == null ? "" : chatTitle;
    }

    public int getTotalBooks() {
        return totalBooks;
    }

    public void setTotalBooks(int totalBooks) {
        this.totalBooks = totalBooks;
    }

    public int getProcessedBooks() {
        return processedBooks;
    }

    public void setProcessedBooks(int processedBooks) {
        this.processedBooks = processedBooks;
    }

    public void incrementProcessedBooks() {
        this.processedBooks++;
    }

    public int getUploadedBooks() {
        return uploadedBooks;
    }

    public void setUploadedBooks(int uploadedBooks) {
        this.uploadedBooks = uploadedBooks;
    }

    public void incrementUploadedBooks() {
        this.uploadedBooks++;
    }

    public int getFailedBooks() {
        return failedBooks;
    }

    public void setFailedBooks(int failedBooks) {
        this.failedBooks = failedBooks;
    }

    public void incrementFailedBooks() {
        this.failedBooks++;
    }

    public int getSkippedBooks() {
        return skippedBooks;
    }

    public void setSkippedBooks(int skippedBooks) {
        this.skippedBooks = skippedBooks;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public void setTotalBytes(long totalBytes) {
        this.totalBytes = totalBytes;
    }

    public long getDoneBytes() {
        return doneBytes;
    }

    public void setDoneBytes(long doneBytes) {
        this.doneBytes = doneBytes;
    }

    public int getPercent() {
        return percent;
    }

    public void setPercent(int percent) {
        this.percent = Math.max(0, Math.min(100, percent));
    }

    public String getCurrentBookId() {
        return currentBookId;
    }

    public void setCurrentBookId(String currentBookId) {
        this.currentBookId = currentBookId;
    }

    public String getCurrentBookTitle() {
        return currentBookTitle;
    }

    public void setCurrentBookTitle(String currentBookTitle) {
        this.currentBookTitle = currentBookTitle == null ? "" : currentBookTitle;
    }

    public String getCurrentFile() {
        return currentFile;
    }

    public void setCurrentFile(String currentFile) {
        this.currentFile = currentFile == null ? "" : currentFile;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message == null ? "" : message;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error == null ? "" : error;
    }

    public boolean isStopRequested() {
        return stopRequested;
    }

    public void requestStop() {
        this.stopRequested = true;
    }

    public boolean isActive() {
        return status == Status.QUEUED || status == Status.RUNNING || status == Status.STOPPING;
    }

    public boolean isDone() {
        return status == Status.STOPPED || status == Status.COMPLETED || status == Status.FAILED;
    }
}
