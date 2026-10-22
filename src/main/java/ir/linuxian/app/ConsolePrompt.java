package ir.linuxian.app;

import ir.linuxian.telegram.LoginPrompt;

import java.util.Scanner;

public class ConsolePrompt implements LoginPrompt {
    private final Scanner in = new Scanner(System.in);

    @Override
    public String ask(String label) {
        System.out.print(label);
        return in.nextLine().trim();
    }

    /** Like ask(), but keeps the text exactly as typed (no trimming). */
    @Override
    public String askRaw(String label) {
        System.out.print(label);
        return in.nextLine();
    }
}
