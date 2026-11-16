package com.astrotalk.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/** Turns a Railway or Neon {@code postgresql://} URL into the JDBC URL Spring expects. */
public final class DataSourceUrl {
    private DataSourceUrl() {}

    public static void apply() {
        String raw = first(System.getenv("DATABASE_URL"), System.getenv("DB_URL"),
                postgresForm(System.getenv("ASTRO_JDBC_URL")), postgresForm(System.getenv("SPRING_DATASOURCE_URL")));
        if (raw == null) return;
        Target target = toJdbc(raw.trim());
        if (target == null) return;
        System.setProperty("spring.datasource.url", target.url());
        if (target.username() != null) System.setProperty("spring.datasource.username", target.username());
        if (target.password() != null) System.setProperty("spring.datasource.password", target.password());
    }

    static Target toJdbc(String raw) {
        if (raw == null || raw.isBlank()) return null;
        raw = raw.trim();
        if (raw.startsWith("jdbc:")) return new Target(raw, null, null);
        if (!raw.startsWith("postgres://") && !raw.startsWith("postgresql://")) return null;
        URI uri = URI.create(raw.replaceFirst("^postgres(ql)?://", "http://"));
        boolean internal = uri.getHost() != null && uri.getHost().endsWith(".internal");
        String query = uri.getRawQuery() == null ? "" : uri.getRawQuery().replace("channel_binding=require", "").replaceAll("&&+", "&").replaceAll("^&|&$", "");
        if (!internal && !query.contains("sslmode")) query = query.isEmpty() ? "sslmode=require" : query + "&sslmode=require";
        String url = "jdbc:postgresql://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "") + uri.getRawPath() + (query.isEmpty() ? "" : "?" + query);
        String username = null;
        String password = null;
        String info = uri.getRawUserInfo();
        if (info != null) {
            String[] parts = info.split(":", 2);
            username = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            if (parts.length > 1) password = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
        }
        return new Target(url, username, password);
    }

    private static String postgresForm(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.startsWith("postgres://") || trimmed.startsWith("postgresql://") ? trimmed : null;
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    record Target(String url, String username, String password) {}
}
