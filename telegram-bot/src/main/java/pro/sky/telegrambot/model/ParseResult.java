package pro.sky.telegrambot.model;

public class ParseResult {
    private final boolean success;
    private final String errorMessage;
    private final NotificationTask task;

    private ParseResult(boolean success, String errorMessage, NotificationTask task) {
        this.success = success;
        this.errorMessage = errorMessage;
        this.task = task;
    }

    public static ParseResult success(NotificationTask task) {
        return new ParseResult(true, null, task);
    }

    public static ParseResult error(String errorMessage) {
        return new ParseResult(false, errorMessage, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public NotificationTask getTask() {
        return task;
    }
}
