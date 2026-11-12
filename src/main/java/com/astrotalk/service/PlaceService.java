package com.astrotalk.service;

import com.astrotalk.config.ApiException;
import com.astrotalk.config.AppProperties;
import com.astrotalk.domain.Models;
import com.astrotalk.domain.Repos;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PlaceService {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final AppProperties props;

    public PlaceService(AppProperties props) { this.props = props; }

    public List<Map<String, Object>> search(String q) {
        String query = q == null ? "" : q.trim();
        if (query.length() < 2) return List.of();
        try {
            String url = "https://nominatim.openstreetmap.org/search?format=jsonv2&addressdetails=1&limit=8&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "AstrologyTalk/1.0 (" + props.getMailFrom() + ")")
                    .header("Accept", "application/json")
                    .GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) return List.of();
            List<Map<String, Object>> out = new ArrayList<>();
            for (JsonNode n : json.readTree(res.body())) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("label", n.path("display_name").asText());
                row.put("lat", n.path("lat").asDouble());
                row.put("lon", n.path("lon").asDouble());
                out.add(row);
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    public Map<String, Object> offset(double lat, double lon, String dob, String tob) {
        String tzName = zoneFor(lat, lon);
        LocalDate date = parseDate(dob);
        LocalTime time = parseTime(tob);
        ZoneId zone = ZoneId.of(tzName);
        int seconds = zone.getRules().getOffset(LocalDateTime.of(date, time)).getTotalSeconds();
        double hours = seconds / 3600.0;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tzName", tzName);
        m.put("tz", hours);
        m.put("label", String.format("UTC %s%d:%02d", hours < 0 ? "-" : "+", Math.abs(seconds) / 3600, Math.abs(seconds) % 3600 / 60));
        return m;
    }

    private String zoneFor(double lat, double lon) {
        if (lat >= 6 && lat <= 37 && lon >= 68 && lon <= 98) return "Asia/Kolkata";
        try {
            String url = "https://timeapi.io/api/TimeZone/coordinate?latitude=" + lat + "&longitude=" + lon;
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200) {
                String name = json.readTree(res.body()).path("timeZone").asText("");
                if (!name.isBlank()) {
                    ZoneId.of(name);
                    return name;
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        throw new ApiException(400, "Could not find the time zone for that place. Pick a nearby town, or enter the UTC offset.");
    }

    public static LocalDate parseDate(String dob) {
        try {
            LocalDate d = LocalDate.parse(dob);
            if (d.isAfter(LocalDate.now()) || d.isBefore(LocalDate.of(1900, 1, 1))) throw new ApiException(400, "Date of birth must be between 1900 and today");
            return d;
        } catch (DateTimeParseException e) {
            throw new ApiException(400, "Use a real date of birth");
        }
    }

    public static LocalTime parseTime(String tob) {
        String value = tob == null || tob.isBlank() ? "" : tob.trim();
        try {
            if (value.length() == 5) value = value + ":00";
            LocalTime t = LocalTime.parse(value);
            return t;
        } catch (DateTimeParseException e) {
            throw new ApiException(400, "Use a real time of birth");
        }
    }
}
