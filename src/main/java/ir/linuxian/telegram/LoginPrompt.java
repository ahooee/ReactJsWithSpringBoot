package ir.linuxian.telegram;

public interface LoginPrompt {
    String ask(String label);

    String askRaw(String label);
}
