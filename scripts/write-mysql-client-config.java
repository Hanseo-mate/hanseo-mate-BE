import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class WriteMysqlClientConfig {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)(?::([^}]*))?}");

    public static void main(String[] args) {
        if (args.length != 2) {
            fail("Usage: java write-mysql-client-config.java <spring-config> <mysql-option-file>");
        }

        Path config = Path.of(args[0]);
        Path output = Path.of(args[1]);
        try {
            Properties properties = new Properties();
            try (BufferedReader reader = Files.newBufferedReader(config)) {
                properties.load(reader);
            }

            loadServiceEnvironment();

            String jdbcUrl = resolve(required(properties, "spring.datasource.url"));
            Matcher url = Pattern.compile("^jdbc:mysql://([^/:?#]+)(?::([0-9]+))?/([^?;]+).*$").matcher(jdbcUrl);
            if (!url.matches()) fail("Production datasource URL is not a supported MySQL URL.");
            String host = url.group(1);
            int port = url.group(2) == null ? 3306 : Integer.parseInt(url.group(2));
            String database = url.group(3);
            if (!(host.equals("localhost") || host.equals("127.0.0.1")) || port != 3306 || !database.equals("hanseo_mate")) {
                fail("Production datasource must point to localhost:3306/hanseo_mate.");
            }

            String username = resolve(required(properties, "spring.datasource.username"));
            String password = resolve(required(properties, "spring.datasource.password"));
            String content = "[client]\nprotocol=TCP\nhost=127.0.0.1\nport=3306\ndefault-character-set=utf8mb4\n"
                    + "user=\"" + escape(username) + "\"\npassword=\"" + escape(password) + "\"\n";

            try {
                Set<PosixFilePermission> ownerOnly = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
                Files.setPosixFilePermissions(output, ownerOnly);
            } catch (UnsupportedOperationException e) {
                output.toFile().setReadable(false, false);
                output.toFile().setWritable(false, false);
                output.toFile().setReadable(true, true);
                output.toFile().setWritable(true, true);
            }
            Files.writeString(output, content);
        } catch (IOException | RuntimeException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            try { Files.deleteIfExists(output); } catch (IOException ignored) { }
            fail("Could not prepare MySQL client credentials from the production config: " + e.getClass().getSimpleName());
        }
    }

    private static void loadServiceEnvironment() throws InterruptedException {
        try {
            Process systemd = new ProcessBuilder("systemctl", "show", "hanseo-mate", "--property=MainPID", "--value")
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            String mainPid = new String(systemd.getInputStream().readAllBytes()).trim();
            if (systemd.waitFor() == 0 && mainPid.matches("[1-9][0-9]*")) {
                byte[] environment = Files.readAllBytes(Path.of("/proc", mainPid, "environ"));
                String decoded = new String(environment, java.nio.charset.StandardCharsets.UTF_8);
                for (String pair : decoded.split(String.valueOf('\0'), -1)) {
                    int equals = pair.indexOf('=');
                    if (equals > 0) System.setProperty("env." + pair.substring(0, equals), pair.substring(equals + 1));
                }
            }
        } catch (IOException ignored) {
            // External config values remain usable even when systemd environment is unavailable.
        }
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) fail("Missing production setting: " + key);
        return value.trim();
    }

    private static String resolve(String value) {
        String resolved = value;
        for (int pass = 0; pass < 5; pass++) {
            var matcher = PLACEHOLDER.matcher(resolved);
            StringBuffer next = new StringBuffer();
            boolean found = false;
            while (matcher.find()) {
                found = true;
                String replacement = System.getProperty("env." + matcher.group(1));
                if (replacement == null) replacement = System.getenv(matcher.group(1));
                if (replacement == null) replacement = matcher.group(2);
                if (replacement == null) fail("Required environment variable is not set: " + matcher.group(1));
                matcher.appendReplacement(next, java.util.regex.Matcher.quoteReplacement(replacement));
            }
            matcher.appendTail(next);
            resolved = next.toString();
            if (!found) return resolved;
        }
        if (PLACEHOLDER.matcher(resolved).find()) fail("Nested production config placeholders exceed the supported limit.");
        return resolved;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                .replace("\r", "\\r").replace("\t", "\\t").replace("\b", "\\b");
    }

    private static void fail(String message) {
        System.err.println(message);
        System.exit(1);
        throw new IllegalStateException(message);
    }
}
