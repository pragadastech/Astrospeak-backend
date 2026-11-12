package com.astrotalk.web;

import com.astrotalk.domain.Models;
import com.astrotalk.service.CatalogService;
import com.astrotalk.service.PlaceService;
import com.astrotalk.service.StudioService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
class SeedRunner implements ApplicationRunner {
    private final StudioService studio;
    SeedRunner(StudioService studio) { this.studio = studio; }
    public void run(ApplicationArguments args) { studio.seedAdmin(); }
}

@RestController
@RequestMapping("/api")
class PublicController {
    private final CatalogService catalog;
    private final PlaceService places;
    private final StudioService studio;
    PublicController(CatalogService catalog, PlaceService places, StudioService studio) {
        this.catalog = catalog;
        this.places = places;
        this.studio = studio;
    }

    @GetMapping("/health")
    Map<String, Object> health() { return Map.of("ok", true, "service", "Astrology Talk"); }

    @GetMapping("/config")
    Map<String, Object> config() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("brand", "Astrology Talk");
        m.put("businessState", catalog.businessState());
        m.put("states", CatalogService.STATES);
        m.put("gst", "Indian invoices add 18% GST: 9% CGST + 9% SGST inside the business state, 18% IGST for other Indian states.");
        return m;
    }

    @GetMapping("/plans")
    Map<String, Object> plans() {
        return Map.of("businessState", catalog.businessState(), "plans", catalog.list());
    }

    @GetMapping("/plans/{code}/quote")
    Map<String, Object> quote(@PathVariable String code, @RequestParam String billingState, @RequestParam(defaultValue = "INR") String currency) {
        return catalog.quote(catalog.require(code), billingState, currency);
    }

    @GetMapping("/questions")
    Object questions() { return studio.activeQuestions(); }

    @GetMapping("/places")
    Object places(@RequestParam(defaultValue = "") String q) { return places.search(q); }

    @GetMapping("/tz")
    Map<String, Object> tz(@RequestParam double lat, @RequestParam double lon, @RequestParam String dob, @RequestParam(defaultValue = "12:00:00") String tob) {
        return places.offset(lat, lon, dob, tob);
    }
}

@RestController
@RequestMapping("/api/auth")
class AuthController {
    private final StudioService studio;
    AuthController(StudioService studio) { this.studio = studio; }

    @PostMapping("/register")
    Map<String, Object> register(@RequestBody JsonNode body, HttpServletResponse res) { return studio.register(body, res); }

    @PostMapping("/start")
    Map<String, Object> start(@RequestBody JsonNode body, HttpServletResponse res) { return studio.start(body, res); }

    @PostMapping("/login")
    Map<String, Object> login(@RequestBody JsonNode body, HttpServletResponse res) {
        return studio.login(body.path("email").asText(), body.path("password").asText(), res);
    }

    @PostMapping("/logout")
    Map<String, Object> logout(HttpServletResponse res) { studio.logout(res); return Map.of("ok", true); }

    @GetMapping("/me")
    Map<String, Object> me(Authentication auth) { return studio.publicUser(studio.current(auth.getName())); }

    @PostMapping("/password")
    Map<String, Object> password(Authentication auth, @RequestBody JsonNode body) {
        Models.User user = studio.current(auth.getName());
        studio.changePassword(user, body.path("current").asText(), body.path("next").asText());
        return Map.of("ok", true);
    }
}

@RestController
@RequestMapping("/api/my")
class ClientController {
    private final StudioService studio;
    ClientController(StudioService studio) { this.studio = studio; }

    @GetMapping("/dashboard")
    Map<String, Object> dashboard(Authentication auth) {
        Models.User u = studio.current(auth.getName());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("user", studio.publicUser(u));
        m.put("profiles", studio.profilesOf(u));
        m.put("consultations", studio.myConsultations(u));
        m.put("reports", studio.myReports(u));
        return m;
    }

    @PostMapping("/profiles")
    Models.Profile addProfile(Authentication auth, @RequestBody JsonNode body) {
        return studio.addProfile(studio.current(auth.getName()), body);
    }

    @GetMapping("/chart")
    com.astrotalk.chart.Chart chart(Authentication auth, @RequestParam(required = false) String profileId) {
        Models.User u = studio.current(auth.getName());
        return studio.chart(studio.profile(u, profileId));
    }

    @GetMapping("/chat")
    Map<String, Object> chat(Authentication auth, @RequestParam(required = false) String profileId) {
        Models.User u = studio.current(auth.getName());
        return studio.openChat(u, studio.profile(u, profileId));
    }

    @PostMapping("/chat")
    Map<String, Object> send(Authentication auth, @RequestBody JsonNode body) {
        Models.User u = studio.current(auth.getName());
        return studio.sendChat(u, studio.profile(u, body.path("profileId").asText(null)), body.path("message").asText());
    }

    @PostMapping("/consultations")
    Models.Consultation book(Authentication auth, @RequestBody JsonNode body) {
        return studio.book(studio.current(auth.getName()), body);
    }

    @PostMapping("/reports")
    Models.Report requestReport(Authentication auth, @RequestBody JsonNode body) {
        return studio.requestReport(studio.current(auth.getName()), body.path("profileId").asText(null), body.path("topic").asText());
    }

    @GetMapping("/reports/{id}/file")
    ResponseEntity<byte[]> file(Authentication auth, @PathVariable String id) {
        Models.User u = studio.current(auth.getName());
        var file = studio.download(u, id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName.replace("\"", "") + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(file.data);
    }
}

@RestController
@RequestMapping("/api/admin")
class AdminController {
    private final StudioService studio;
    private final CatalogService catalog;
    AdminController(StudioService studio, CatalogService catalog) { this.studio = studio; this.catalog = catalog; }

    @GetMapping("/stats")
    Map<String, Object> stats() { return studio.stats(); }

    @GetMapping("/customers")
    Object customers() { return studio.customers(); }

    @GetMapping("/customers/{id}")
    Map<String, Object> customer(@PathVariable String id) {
        Models.User u = studio.current(id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("user", studio.publicUser(u));
        m.put("profiles", studio.profilesOf(u));
        m.put("reports", studio.myReports(u));
        return m;
    }

    @GetMapping("/employees")
    Object employees() { return studio.employees(); }

    @PostMapping("/employees")
    Map<String, Object> addEmployee(@RequestBody JsonNode body) {
        return studio.addEmployee(body.path("fullName").asText(), body.path("email").asText());
    }

    @GetMapping("/consultations")
    Object consultations() { return studio.allConsultations(); }

    @PatchMapping("/consultations/{id}")
    Models.Consultation patch(@PathVariable String id, @RequestBody JsonNode body) {
        return studio.updateConsultation(id, body.path("status").asText(null), body.path("meetingLink").asText(null), body.path("notes").asText(null));
    }

    @GetMapping("/reports")
    Object reports() { return studio.allReports(); }

    @PostMapping(value = "/reports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Models.Report upload(Authentication auth, @RequestParam String userId, @RequestParam(required = false) String profileId, @RequestParam(required = false) String title, @RequestParam(required = false) String reportId, @RequestParam MultipartFile file) {
        return studio.upload(userId, profileId, title, reportId, file, auth.getName());
    }

    @GetMapping("/notifications")
    Object notifications() { return studio.allNotifications(); }

    @GetMapping("/questions")
    Object questions() { return studio.allQuestions(); }

    @PostMapping("/questions")
    Models.IntakeQuestion addQuestion(@RequestBody JsonNode body) { return studio.addQuestion(body.path("prompt").asText()); }

    @DeleteMapping("/questions/{id}")
    Map<String, Object> deleteQuestion(@PathVariable String id) { studio.deleteQuestion(id); return Map.of("ok", true); }

    @GetMapping("/settings")
    Map<String, Object> settings() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("businessState", catalog.businessState());
        m.put("states", CatalogService.STATES);
        m.put("plans", catalog.listAll());
        m.put("smtp", "Microsoft 365 mailbox sales@avus24.com. Waiting for the app password.");
        return m;
    }

    @PutMapping("/settings")
    Map<String, Object> saveSettings(@RequestBody JsonNode body) {
        catalog.setBusinessState(body.path("businessState").asText());
        return Map.of("businessState", catalog.businessState());
    }

    @PostMapping("/plans")
    Map<String, Object> addPlan(@RequestBody JsonNode body) {
        String features = featuresOf(body);
        return catalog.view(catalog.create(
                body.path("name").asText(""),
                body.path("tagline").asText(""),
                body.path("minutes").asInt(30),
                body.path("priceInr").asInt(0),
                body.path("priceUsd").asInt(0),
                features,
                body.path("recommended").asBoolean(false),
                body.path("family").asBoolean(false)));
    }

    @PutMapping("/plans/{code}")
    Map<String, Object> savePlan(@PathVariable String code, @RequestBody JsonNode body) {
        Integer inr = body.hasNonNull("priceInr") ? body.get("priceInr").asInt() : null;
        Integer usd = body.hasNonNull("priceUsd") ? body.get("priceUsd").asInt() : null;
        Integer minutes = body.hasNonNull("minutes") ? body.get("minutes").asInt() : null;
        String features = body.has("features") ? featuresOf(body) : null;
        Boolean recommended = body.has("recommended") ? body.get("recommended").asBoolean() : null;
        Boolean family = body.has("family") ? body.get("family").asBoolean() : null;
        Boolean active = body.has("active") ? body.get("active").asBoolean() : null;
        String name = body.hasNonNull("name") ? body.get("name").asText() : null;
        String tagline = body.has("tagline") ? body.get("tagline").asText("") : null;
        return catalog.view(catalog.update(code, name, tagline, minutes, inr, usd, features, recommended, family, active));
    }

    private static String featuresOf(JsonNode body) {
        if (body.has("features") && body.get("features").isArray()) {
            java.util.ArrayList<String> lines = new java.util.ArrayList<>();
            body.get("features").forEach(n -> { if (!n.asText("").isBlank()) lines.add(n.asText().trim()); });
            return String.join("\n", lines);
        }
        return body.path("features").asText("");
    }
}
