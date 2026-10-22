package ir.linuxian.second.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Full-database backup and restore via pg_dump / pg_restore
 * (custom format, compressed). Restores use --clean so the database
 * comes back exactly as it was when the backup was taken.
 */
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    private static final Pattern JDBC_URL =
            Pattern.compile("^jdbc:postgresql://([^/:?]+)(?::(\\d+))?/([^?]+).*$");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;
    private final String pgDumpBin;
    private final String pgRestoreBin;

    public BackupService(
            @Value("${spring.datasource.url}") String dbUrl,
            @Value("${spring.datasource.username}") String dbUser,
            @Value("${spring.datasource.password:}") String dbPassword,
            @Value("${app.backup.pg-dump:/usr/bin/pg_dump}") String pgDumpBin,
            @Value("${app.backup.pg-restore:/usr/bin/pg_restore}") String pgRestoreBin) {
        this.dbUrl = dbUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword == null ? "" : dbPassword;
        this.pgDumpBin = pgDumpBin;
        this.pgRestoreBin = pgRestoreBin;
    }

    private record DbTarget(String host, String port, String db) {
    }

    private DbTarget target() {
        Matcher m = JDBC_URL.matcher(dbUrl == null ? "" : dbUrl.trim());
        if (!m.matches()) {
            throw new IllegalStateException("Database backup supports only PostgreSQL JDBC URLs");
        }
        String port = m.group(2) == null ? "5432" : m.group(2);
        return new DbTarget(m.group(1), port, m.group(3));
    }

    private void requireTool(String bin) {
        if (!Files.isExecutable(Path.of(bin))) {
            throw new IllegalStateException("Database tool not found: " + bin);
        }
    }

    /** Dump the whole database to a temp .dump file and return it. */
    public Path backup() throws IOException, InterruptedException {
        requireTool(pgDumpBin);
        DbTarget t = target();
        Path file = Files.createTempDirectory("db-backup")
                .resolve("backup-" + LocalDateTime.now().format(STAMP) + ".dump");
        List<String> cmd = List.of(pgDumpBin,
                "-h", t.host(), "-p", t.port(), "-U", dbUser, "-d", t.db(),
                "-F", "c", "-Z", "6", "-f", file.toString());
        run(cmd, 10, "backup");
        log.info("Database backup written: {} ({} bytes)", file, Files.size(file));
        return file;
    }

    /** Validate then restore a .dump archive over the current database. */
    public void restore(Path file) throws IOException, InterruptedException {
        validate(file);
        DbTarget t = target();
        List<String> cmd = new ArrayList<>(List.of(pgRestoreBin,
                "-h", t.host(), "-p", t.port(), "-U", dbUser, "-d", t.db(),
                "--clean", "--if-exists", "--single-transaction",
                "--no-owner", "--no-acl", file.toString()));
        run(cmd, 30, "restore");
        log.warn("Database restored from {}", file.getFileName());
    }

    /** Only real pg_dump archives list successfully. */
    public void validate(Path file) throws IOException, InterruptedException {
        requireTool(pgRestoreBin);
        if (file == null || !Files.isRegularFile(file) || Files.size(file) == 0) {
            throw new IllegalArgumentException("Upload a valid backup file (.dump)");
        }
        run(List.of(pgRestoreBin, "--list", file.toString()), 2, "backup validation");
    }

    private void run(List<String> cmd, int timeoutMinutes, String what) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().put("PGPASSWORD", dbPassword);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output;
        try (var in = process.getInputStream()) {
            output = new String(in.readAllBytes());
        }
        boolean done = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
        if (!done) {
            process.destroyForcibly();
            throw new IllegalStateException("Database " + what + " timed out");
        }
        if (process.exitValue() != 0) {
            String tail = output.length() > 1500 ? output.substring(output.length() - 1500) : output;
            log.warn("Database {} failed: {}", what, tail);
            throw new IllegalStateException("Database " + what + " failed. " + tail.trim());
        }
    }

    public Map<String, String> connectionInfo() {
        DbTarget t = target();
        return Map.of("host", t.host(), "port", t.port(), "database", t.db());
    }
}
