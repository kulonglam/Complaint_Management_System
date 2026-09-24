package com.cms.backend.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Loads {@code .env} from the process working directory and {@code backend/.env}
 * so {@code npm run dev:backend} (repo root) and {@code ./mvnw} (backend/) both work.
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path cwd = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        Map<String, Object> values = new LinkedHashMap<>();
        for (Path file : List.of(cwd.resolve(".env"), cwd.resolve("backend").resolve(".env"))) {
            values.putAll(readEnvFile(file));
        }
        if (values.isEmpty()) {
            return;
        }
        environment.getPropertySources().addLast(new MapPropertySource("cmsDotenv", values));
        values.forEach((key, value) -> {
            if (System.getProperty(key) == null && System.getenv(key) == null) {
                System.setProperty(key, String.valueOf(value));
            }
        });
    }

    private static Map<String, Object> readEnvFile(Path file) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (!Files.isRegularFile(file)) {
            return values;
        }
        try {
            for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("export ")) {
                    line = line.substring(7).trim();
                }
                int separator = line.indexOf('=');
                if (separator < 1) {
                    continue;
                }
                String key = line.substring(0, separator).trim();
                String value = unquote(line.substring(separator + 1).trim());
                values.put(key, value);
            }
        } catch (IOException ignored) {
            // optional local file
        }
        return values;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
