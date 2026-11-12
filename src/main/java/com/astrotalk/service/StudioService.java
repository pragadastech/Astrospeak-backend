package com.astrotalk.service;

import com.astrotalk.chart.Chart;
import com.astrotalk.chart.ScientificChartEngine;
import com.astrotalk.config.ApiException;
import com.astrotalk.config.AppProperties;
import com.astrotalk.config.JwtService;
import com.astrotalk.domain.Models;
import com.astrotalk.domain.Repos;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StudioService {
    private static final SecureRandom RND = new SecureRandom();
    private final Repos.UserRepo users;
    private final Repos.ProfileRepo profiles;
    private final Repos.ConsultationRepo consultations;
    private final Repos.ReportRepo reports;
    private final Repos.ReportFileRepo files;
    private final Repos.ChatRepo chats;
    private final Repos.MemoryRepo memories;
    private final Repos.NotificationRepo notifications;
    private final Repos.QuestionRepo questions;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final CatalogService catalog;
    private final NotifyService notify;
    private final AppProperties props;

    public StudioService(Repos.UserRepo users, Repos.ProfileRepo profiles, Repos.ConsultationRepo consultations, Repos.ReportRepo reports, Repos.ReportFileRepo files, Repos.ChatRepo chats, Repos.MemoryRepo memories, Repos.NotificationRepo notifications, Repos.QuestionRepo questions, PasswordEncoder encoder, JwtService jwt, CatalogService catalog, NotifyService notify, AppProperties props) {
        this.users = users;
        this.profiles = profiles;
        this.consultations = consultations;
        this.reports = reports;
        this.files = files;
        this.chats = chats;
        this.memories = memories;
        this.notifications = notifications;
        this.questions = questions;
        this.encoder = encoder;
        this.jwt = jwt;
        this.catalog = catalog;
        this.notify = notify;
        this.props = props;
    }

    @Transactional
    public void seedAdmin() {
        catalog.seed();
        seedQuestions();
        if (users.findByEmailIgnoreCase(props.getAdminEmail()).isPresent()) return;
        Models.User admin = new Models.User();
        admin.role = "ADMIN";
        admin.accountType = "INDIVIDUAL";
        admin.fullName = "Administrator";
        admin.email = props.getAdminEmail().toLowerCase();
        admin.passwordHash = encoder.encode(props.getAdminPassword());
        admin.billingState = catalog.businessState();
        users.save(admin);
    }

    @Transactional
    public Map<String, Object> register(JsonNode body, HttpServletResponse res) {
        String type = text(body, "accountType");
        if (!"INDIVIDUAL".equals(type) && !"FAMILY".equals(type)) throw new ApiException(400, "Choose an Individual or Family account");
        String email = text(body, "email").toLowerCase();
        String password = text(body, "password");
        String name = text(body, "fullName");
        if (!email.contains("@") || email.length() > 120) throw new ApiException(400, "Enter a valid email");
        if (password.length() < 8) throw new ApiException(400, "Use a password of at least 8 characters");
        if (name.isBlank() || name.length() > 80) throw new ApiException(400, "Enter the full name");
        if (users.findByEmailIgnoreCase(email).isPresent()) throw new ApiException(409, "An account with this email already exists");
        String state = text(body, "billingState");
        if (!CatalogService.STATES.contains(state)) throw new ApiException(400, "Choose a billing state");
        Models.User user = new Models.User();
        user.role = "CLIENT";
        user.accountType = type;
        user.fullName = name;
        user.email = email;
        user.phone = text(body, "phone");
        user.gender = text(body, "gender");
        user.billingState = state;
        user.passwordHash = encoder.encode(password);
        users.save(user);
        saveProfile(user, "Self", name, user.gender, body.path("birth"));
        if ("FAMILY".equals(type) && body.path("members").isArray()) {
            int extra = 0;
            for (JsonNode member : body.path("members")) {
                if (member.path("fullName").asText("").isBlank()) continue;
                if (++extra > 8) break;
                String relation = member.path("relation").asText("Other");
                saveProfile(user, relation, member.path("fullName").asText(), member.path("gender").asText(""), member);
            }
        }
        Map<String, Object> out = publicUser(user);
        out.put("token", issueTabToken(res, user));
        notify.welcome(user);
        return out;
    }

    /** Landing-page start. The visitor never picks a password; we create the account and email the sign-in details. */
    @Transactional
    public Map<String, Object> start(JsonNode body, HttpServletResponse res) {
        String email = text(body, "email").toLowerCase();
        String name = text(body, "fullName");
        String phone = text(body, "phone");
        if (!email.contains("@") || email.length() > 120) throw new ApiException(400, "Enter a valid email");
        if (name.isBlank() || name.length() > 80) throw new ApiException(400, "Enter the full name");
        if (phone.replaceAll("\\D", "").length() < 8) throw new ApiException(400, "Enter a phone number");
        if (users.findByEmailIgnoreCase(email).isPresent()) throw new ApiException(409, "This email already has an account. Sign in to continue.");
        String password = generatedPassword();
        Models.User user = new Models.User();
        user.role = "CLIENT";
        user.accountType = "INDIVIDUAL";
        user.fullName = name;
        user.email = email;
        user.phone = phone.trim();
        user.billingState = catalog.businessState();
        user.passwordHash = encoder.encode(password);
        users.save(user);
        saveProfile(user, "Self", name, "", body.path("birth"));
        boolean emailed = notify.accountCreated(user, password);
        Map<String, Object> out = publicUser(user);
        out.put("token", issueTabToken(res, user));
        out.put("emailed", emailed);
        if (!emailed) out.put("password", password);
        return out;
    }

    public List<Models.IntakeQuestion> activeQuestions() { return questions.findByActiveTrueOrderBySortOrderAsc(); }

    public List<Models.IntakeQuestion> allQuestions() { return questions.findAllByOrderBySortOrderAsc(); }

    @Transactional
    public Models.IntakeQuestion addQuestion(String prompt) {
        if (prompt == null || prompt.isBlank()) throw new ApiException(400, "Write the question");
        String text = prompt.trim();
        if (text.length() > 500) throw new ApiException(400, "Keep the question under 500 characters");
        Models.IntakeQuestion q = new Models.IntakeQuestion();
        q.prompt = text;
        q.sortOrder = (int) questions.count() + 1;
        q.active = true;
        return questions.save(q);
    }

    @Transactional
    public void deleteQuestion(String id) {
        if (!questions.existsById(id)) throw new ApiException(404, "Question not found");
        questions.deleteById(id);
    }

    public Map<String, Object> login(String email, String password, HttpServletResponse res) {
        Models.User user = users.findByEmailIgnoreCase(email == null ? "" : email.trim()).orElseThrow(() -> new ApiException(401, "Email or password is wrong"));
        if (!encoder.matches(password == null ? "" : password, user.passwordHash)) throw new ApiException(401, "Email or password is wrong");
        Map<String, Object> out = publicUser(user);
        out.put("token", issueTabToken(res, user));
        return out;
    }

    public void logout(HttpServletResponse res) {
        ResponseCookie cookie = ResponseCookie.from(com.astrotalk.config.JwtFilter.COOKIE, "")
                .httpOnly(true).path("/").maxAge(0).sameSite("Lax").build();
        res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    @Transactional
    public void changePassword(Models.User user, String current, String next) {
        if (!encoder.matches(current == null ? "" : current, user.passwordHash)) throw new ApiException(400, "Current password is wrong");
        if (next == null || next.length() < 8) throw new ApiException(400, "Use a password of at least 8 characters");
        user.passwordHash = encoder.encode(next);
        users.save(user);
    }

    public Models.User current(String id) {
        return users.findById(id).orElseThrow(() -> new ApiException(401, "Please sign in"));
    }

    public Map<String, Object> publicUser(Models.User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.id);
        m.put("role", u.role);
        m.put("accountType", u.accountType);
        m.put("fullName", u.fullName);
        m.put("email", u.email);
        m.put("phone", u.phone);
        m.put("billingState", u.billingState);
        return m;
    }

    public List<Models.Profile> profilesOf(Models.User u) {
        return profiles.findByUserIdOrderByCreatedAtAsc(u.id);
    }

    public Models.Profile profile(Models.User u, String id) {
        if (id == null || id.isBlank()) {
            return profilesOf(u).stream().filter(p -> "Self".equals(p.relation)).findFirst().orElseThrow(() -> new ApiException(404, "Profile not found"));
        }
        Models.Profile p = profiles.findById(id).orElseThrow(() -> new ApiException(404, "Profile not found"));
        if (!u.id.equals(p.userId)) throw new ApiException(404, "Profile not found");
        return p;
    }

    @Transactional
    public Models.Profile addProfile(Models.User u, JsonNode body) {
        if ("Self".equalsIgnoreCase(body.path("relation").asText())) throw new ApiException(400, "The Self profile comes from registration");
        if (profilesOf(u).size() >= 12) throw new ApiException(400, "This account already has 12 profiles");
        return saveProfile(u, body.path("relation").asText("Other"), text(body, "fullName"), body.path("gender").asText(""), body);
    }

    public Chart chart(Models.Profile p) {
        if (p.dob == null || p.lat == null || p.lon == null || p.tz == null) throw new ApiException(400, "This profile needs a complete birth place and time");
        return ScientificChartEngine.compute(p.dob, p.tob, p.lat, p.lon, p.tz);
    }

    @Transactional
    public Map<String, Object> openChat(Models.User u, Models.Profile p) {
        List<Models.ChatMessage> existing = chats.findByProfileIdOrderByCreatedAtAsc(p.id);
        if (existing.isEmpty()) {
            Chart c = chart(p);
            String who = "Self".equals(p.relation) ? "you" : p.fullName + " (your " + p.relation.toLowerCase() + ")";
            String text = "Hello, I am looking at the scientific chart for " + who + ". "
                    + p.fullName + " has " + c.ascendant.sign + " rising, Moon in " + c.moonSign + " (" + c.nakshatra + "), and the running period is " + c.currentDasha.label + ". "
                    + "Tell me why you are consulting — career, education, marriage, health, money, or something else.";
            say(u, p, "assistant", text);
        }
        return chatState(p);
    }

    @Transactional
    public Map<String, Object> sendChat(Models.User u, Models.Profile p, String message) {
        String text = message == null ? "" : message.trim();
        if (text.isBlank()) throw new ApiException(400, "Please type a message");
        if (text.length() > 2000) text = text.substring(0, 2000);
        openChat(u, p);
        say(u, p, "user", text);
        remember(u, p, text);
        Chart c = chart(p);
        List<Models.ChatMessage> history = chats.findByProfileIdOrderByCreatedAtAsc(p.id);
        long userTurns = history.stream().filter(m -> "user".equals(m.role)).count();
        say(u, p, "assistant", reply(p, c, text, userTurns));
        return chatState(p);
    }

    @Transactional
    public Models.Consultation book(Models.User u, JsonNode body) {
        Models.Plan plan = catalog.require(text(body, "planCode"));
        String date = text(body, "preferredDate");
        String time = text(body, "preferredTime");
        java.time.LocalDate when;
        try { when = java.time.LocalDate.parse(date); }
        catch (Exception e) { throw new ApiException(400, "Choose a preferred date"); }
        if (when.isBefore(java.time.LocalDate.now())) throw new ApiException(400, "Choose today or a later date");
        PlaceService.parseTime(time);
        String currency = body.path("currency").asText("INR");
        String state = body.path("billingState").asText(u.billingState == null ? "" : u.billingState);
        Map<String, Object> quote = catalog.quote(plan, state, currency);
        List<String> ids = new ArrayList<>();
        if (body.path("profileIds").isArray()) {
            for (JsonNode n : body.path("profileIds")) {
                Models.Profile p = profile(u, n.asText());
                ids.add(p.id);
            }
        }
        if (ids.isEmpty()) ids.add(profile(u, null).id);
        if ((plan.family || "FAMILY".equals(plan.code)) && ids.size() > 4) throw new ApiException(400, "This plan covers 4 members");
        if ((plan.family || "FAMILY".equals(plan.code)) && profilesOf(u).size() < 2) throw new ApiException(400, "Add a spouse or child on this account before booking a family plan");
        Models.Consultation c = new Models.Consultation();
        c.userId = u.id;
        c.planCode = plan.code;
        c.planName = plan.name;
        c.durationMin = plan.minutes;
        c.preferredDate = date;
        c.preferredTime = time.length() == 5 ? time : time.substring(0, 5);
        c.topic = body.path("topic").asText("");
        c.profileIds = String.join(",", ids);
        c.status = "requested";
        c.currency = String.valueOf(quote.get("currency"));
        c.billingState = "USD".equals(c.currency) ? "Outside India" : state;
        c.gstMode = String.valueOf(quote.get("gstMode"));
        c.baseMinor = ((Number) quote.get("baseMinor")).longValue();
        c.taxMinor = ((Number) quote.get("taxMinor")).longValue();
        c.totalMinor = ((Number) quote.get("totalMinor")).longValue();
        c.demo = true;
        c.intake = intake(body.path("answers"));
        consultations.save(c);
        notify.booking(u, c);
        if (c.intake != null && !c.intake.isBlank()) notify.adminConnected(props.getAdminEmail(), u, c);
        return c;
    }

    @Transactional
    public Models.Report requestReport(Models.User u, String profileId, String topic) {
        Models.Profile p = profile(u, profileId);
        if (topic == null || topic.isBlank()) throw new ApiException(400, "Say what the report should cover");
        Models.Report r = new Models.Report();
        r.userId = u.id;
        r.profileId = p.id;
        r.topic = topic.trim();
        r.title = topic.trim() + " — " + p.fullName;
        r.status = "requested";
        return reports.save(r);
    }

    public List<Models.Report> myReports(Models.User u) { return reports.findByUserIdOrderByCreatedAtDesc(u.id); }

    public Models.ReportFile download(Models.User u, String id) {
        Models.Report r = reports.findById(id).orElseThrow(() -> new ApiException(404, "Report not found"));
        if (!u.id.equals(r.userId) && !"ADMIN".equals(u.role) && !"EMPLOYEE".equals(u.role)) throw new ApiException(404, "Report not found");
        return files.findById(id).orElseThrow(() -> new ApiException(404, "The file is not ready yet"));
    }

    @Transactional
    public Map<String, Object> addEmployee(String fullName, String email) {
        String mail = email == null ? "" : email.trim().toLowerCase();
        if (fullName == null || fullName.isBlank()) throw new ApiException(400, "Enter the employee name");
        if (!mail.contains("@")) throw new ApiException(400, "Enter a valid email");
        if (users.findByEmailIgnoreCase(mail).isPresent()) throw new ApiException(409, "That email is already in use");
        String temporary = password();
        Models.User emp = new Models.User();
        emp.role = "EMPLOYEE";
        emp.accountType = "INDIVIDUAL";
        emp.fullName = fullName.trim();
        emp.email = mail;
        emp.passwordHash = encoder.encode(temporary);
        emp.billingState = catalog.businessState();
        users.save(emp);
        Map<String, Object> out = publicUser(emp);
        out.put("temporaryPassword", temporary);
        return out;
    }

    @Transactional
    public Models.Consultation updateConsultation(String id, String status, String meetingLink, String notes) {
        Models.Consultation c = consultations.findById(id).orElseThrow(() -> new ApiException(404, "Consultation not found"));
        if (status != null && !status.isBlank()) {
            if (!List.of("requested", "confirmed", "completed", "cancelled").contains(status)) throw new ApiException(400, "Unknown status");
            c.status = status;
        }
        if (meetingLink != null) c.meetingLink = meetingLink.trim();
        if (notes != null) c.notes = notes.trim();
        consultations.save(c);
        if (c.meetingLink != null && !c.meetingLink.isBlank()) {
            users.findById(c.userId).ifPresent(u -> notify.meeting(u, c));
        }
        return c;
    }

    @Transactional
    public Models.Report upload(String userId, String profileId, String title, String reportId, MultipartFile file, String uploaderId) {
        if (file == null || file.isEmpty()) throw new ApiException(400, "Choose a PDF or document");
        String name = file.getOriginalFilename() == null ? "report" : file.getOriginalFilename();
        if (file.getSize() > 20 * 1024 * 1024) throw new ApiException(400, "File must be under 20 MB");
        Models.User client = users.findById(userId).orElseThrow(() -> new ApiException(404, "Customer not found"));
        if (!"CLIENT".equals(client.role)) throw new ApiException(400, "Reports are uploaded to customer accounts");
        Models.Report r;
        if (reportId != null && !reportId.isBlank()) {
            r = reports.findById(reportId).orElseThrow(() -> new ApiException(404, "Report not found"));
            if (!client.id.equals(r.userId)) throw new ApiException(400, "That report belongs to someone else");
        } else {
            r = new Models.Report();
            r.userId = client.id;
            Models.Profile p = profileId == null || profileId.isBlank() ? null : profiles.findById(profileId).orElse(null);
            r.profileId = p == null ? null : p.id;
            r.topic = title;
        }
        r.title = title == null || title.isBlank() ? name : title.trim();
        r.status = "ready";
        r.fileName = name;
        r.uploadedBy = uploaderId;
        reports.save(r);
        Models.ReportFile stored = files.findById(r.id).orElseGet(Models.ReportFile::new);
        stored.reportId = r.id;
        stored.fileName = name;
        try { stored.data = file.getBytes(); } catch (Exception e) { throw new ApiException(400, "Could not read the file"); }
        files.save(stored);
        return r;
    }

    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("customers", users.countByRole("CLIENT"));
        m.put("employees", users.countByRole("EMPLOYEE"));
        m.put("consultations", consultations.count());
        m.put("reports", reports.count());
        return m;
    }

    public List<Models.Consultation> myConsultations(Models.User u) { return consultations.findByUserIdOrderByCreatedAtDesc(u.id); }
    public List<Models.User> customers() { return users.findByRoleOrderByCreatedAtDesc("CLIENT"); }
    public List<Models.User> employees() { return users.findByRoleOrderByCreatedAtDesc("EMPLOYEE"); }
    public List<Map<String, Object>> allConsultations() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Models.Consultation c : consultations.findAllByOrderByCreatedAtDesc()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", c.id);
            row.put("planName", c.planName);
            row.put("durationMin", c.durationMin);
            row.put("preferredDate", c.preferredDate);
            row.put("preferredTime", c.preferredTime);
            row.put("topic", c.topic);
            row.put("status", c.status);
            row.put("meetingLink", c.meetingLink);
            row.put("currency", c.currency);
            row.put("gstMode", c.gstMode);
            row.put("totalMinor", c.totalMinor);
            row.put("intake", c.intake);
            users.findById(c.userId).ifPresent(u -> {
                row.put("customerName", u.fullName);
                row.put("customerEmail", u.email);
                row.put("customerPhone", u.phone);
            });
            out.add(row);
        }
        return out;
    }
    public List<Models.Report> allReports() { return reports.findAllByOrderByCreatedAtDesc(); }
    public List<Models.Notification> allNotifications() { return notifications.findTop100ByOrderByCreatedAtDesc(); }

    private void seedQuestions() {
        if (questions.count() > 0) return;
        String[] prompts = {
                "What do you want to know about your future?",
                "Which area matters most right now: career, marriage, health, money, or education?",
                "Is there a decision you need to make, and by when?",
                "Anything the astrologer should know before the session?"
        };
        for (int i = 0; i < prompts.length; i++) {
            Models.IntakeQuestion q = new Models.IntakeQuestion();
            q.prompt = prompts[i];
            q.sortOrder = i + 1;
            q.active = true;
            questions.save(q);
        }
    }

    private String intake(JsonNode answers) {
        if (answers == null || !answers.isArray() || answers.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (JsonNode a : answers) {
            String q = a.path("question").asText("").trim();
            String ans = a.path("answer").asText("").trim();
            if (q.isBlank() || ans.isBlank()) continue;
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(q).append("\n").append(ans);
        }
        if (sb.length() == 0) return null;
        return sb.length() > 4000 ? sb.substring(0, 4000) : sb.toString();
    }

    private String generatedPassword() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < 10; i++) s.append(alphabet.charAt(RND.nextInt(alphabet.length())));
        return s.toString();
    }

    private Models.Profile saveProfile(Models.User user, String relation, String name, String gender, JsonNode birth) {
        if (name == null || name.isBlank()) throw new ApiException(400, "Enter the name");
        String dob = birth.path("dob").asText("");
        String tob = birth.path("tob").asText("");
        PlaceService.parseDate(dob);
        PlaceService.parseTime(tob);
        double lat = birth.path("lat").asDouble(Double.NaN);
        double lon = birth.path("lon").asDouble(Double.NaN);
        double tz = birth.path("tz").asDouble(Double.NaN);
        if (Double.isNaN(lat) || Double.isNaN(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180) throw new ApiException(400, "Choose a birth place from the suggestions");
        if (Double.isNaN(tz) || Math.abs(tz) > 14) throw new ApiException(400, "The time zone for that birth place is missing");
        Models.Profile p = new Models.Profile();
        p.userId = user.id;
        p.relation = relation == null || relation.isBlank() ? "Other" : relation;
        p.fullName = name.trim();
        p.gender = gender;
        p.dob = dob;
        p.tob = tob.length() == 5 ? tob + ":00" : tob;
        p.city = birth.path("city").asText("");
        p.lat = lat;
        p.lon = lon;
        p.tz = tz;
        p.tzName = birth.path("tzName").asText("");
        return profiles.save(p);
    }

    /** Hand this tab its own token and drop the shared cookie so other tabs keep their own sign-in. */
    private String issueTabToken(HttpServletResponse res, Models.User user) {
        ResponseCookie cookie = ResponseCookie.from(com.astrotalk.config.JwtFilter.COOKIE, "")
                .httpOnly(true).path("/").maxAge(0).sameSite("Lax").build();
        res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return jwt.create(user.id, user.role);
    }

    private Map<String, Object> chatState(Models.Profile p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("profileId", p.id);
        m.put("messages", chats.findByProfileIdOrderByCreatedAtAsc(p.id));
        m.put("memory", memories.findByProfileIdOrderByCreatedAtAsc(p.id));
        return m;
    }

    private void say(Models.User u, Models.Profile p, String role, String text) {
        Models.ChatMessage msg = new Models.ChatMessage();
        msg.userId = u.id;
        msg.profileId = p.id;
        msg.role = role;
        msg.text = text;
        chats.save(msg);
    }

    private void remember(Models.User u, Models.Profile p, String text) {
        if (text.length() < 12 || text.length() > 300) return;
        if (memories.findByProfileIdOrderByCreatedAtAsc(p.id).size() >= 20) return;
        Models.MemoryFact fact = new Models.MemoryFact();
        fact.userId = u.id;
        fact.profileId = p.id;
        fact.fact = text;
        memories.save(fact);
    }

    private String reply(Models.Profile p, Chart c, String text, long turns) {
        String low = text.toLowerCase();
        if (turns < 3) {
            if (low.contains("career") || low.contains("job")) return "About the work itself: do you lose track of time with people, with numbers and systems, with making things, or with taking decisions? And do you want a stable role or the risk of a business?";
            if (low.contains("study") || low.contains("exam") || low.contains("college") || low.contains("education")) return "Which subjects do you enjoy and score in — maths and physics, or biology and chemistry? Say what you actually like, not what the family expects.";
            if (low.contains("marri") || low.contains("wedding")) return "Is this about the timing of marriage, or about a relationship that is already in your life? What matters most to you in a partner?";
            return "Tell me a little more about what is happening now, and what you want clarity on in the next two years. I will then read it from the chart: the rising sub-lord is " + c.ascendant.subLord + ".";
        }
        Chart.Point moon = c.planet("Moon");
        return p.fullName + " has " + c.ascendant.sign + " rising. The ascendant sub-lord is " + c.ascendant.subLord
                + ", and the Moon is in " + moon.sign + " in " + moon.nakshatra + " (star lord " + moon.starLord + ", sub lord " + moon.subLord + "). "
                + "The running period is " + c.currentDasha.label + ", through " + c.currentDasha.pdEnd + ". "
                + "Use that window for the question you asked. The houses the ascendant sub-lord signifies are " + c.housesSignified(c.ascendant.subLord) + ". "
                + "If you want this written up, request a detailed report from My Reports, or book a live session and the astrologer will go through the remedies with you.";
    }

    private static String text(JsonNode body, String field) {
        return body.path(field).asText("").trim();
    }

    private static String password() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) sb.append(alphabet.charAt(RND.nextInt(alphabet.length())));
        return sb.toString();
    }
}
