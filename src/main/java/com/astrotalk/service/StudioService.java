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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final Repos.ReferralRepo referrals;
    private final Repos.PointRepo points;
    private final Repos.CommissionRateRepo commissionRates;
    private final Repos.CommissionRecordRepo commissionRecords;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final CatalogService catalog;
    private final NotifyService notify;
    private final AppProperties props;

    public StudioService(Repos.UserRepo users, Repos.ProfileRepo profiles, Repos.ConsultationRepo consultations, Repos.ReportRepo reports, Repos.ReportFileRepo files, Repos.ChatRepo chats, Repos.MemoryRepo memories, Repos.NotificationRepo notifications, Repos.QuestionRepo questions, Repos.ReferralRepo referrals, Repos.PointRepo points, Repos.CommissionRateRepo commissionRates, Repos.CommissionRecordRepo commissionRecords, PasswordEncoder encoder, JwtService jwt, CatalogService catalog, NotifyService notify, AppProperties props) {
        this.users = users;
        this.profiles = profiles;
        this.consultations = consultations;
        this.reports = reports;
        this.files = files;
        this.chats = chats;
        this.memories = memories;
        this.notifications = notifications;
        this.questions = questions;
        this.referrals = referrals;
        this.points = points;
        this.commissionRates = commissionRates;
        this.commissionRecords = commissionRecords;
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
        seedCommissionRates();
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

    private void seedCommissionRates() {
        if (commissionRates.findBySourceIgnoreCase("YouTube").isEmpty()) {
            Models.CommissionRate r = new Models.CommissionRate();
            r.source = "YouTube";
            r.ratePercent = 10.0;
            commissionRates.save(r);
        }
        if (commissionRates.findBySourceIgnoreCase("Instagram").isEmpty()) {
            Models.CommissionRate r = new Models.CommissionRate();
            r.source = "Instagram";
            r.ratePercent = 20.0;
            commissionRates.save(r);
        }
        if (commissionRates.findBySourceIgnoreCase("Referral").isEmpty()) {
            Models.CommissionRate r = new Models.CommissionRate();
            r.source = "Referral";
            r.ratePercent = 15.0; // default configurable referral commission
            commissionRates.save(r);
        }
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
        attachReferral(user, body);
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

    /** Landing-page start. The visitor never picks a password; we create the account and send the sign-in details. */
    @Transactional
    public Map<String, Object> start(JsonNode body, HttpServletResponse res) {
        String email = text(body, "email").toLowerCase();
        String name = text(body, "fullName");
        String phone = text(body, "phone");
        String source = text(body, "source");
        String referralDetails = text(body, "referralDetails");

        if (!email.contains("@") || email.length() > 120) throw new ApiException(400, "Enter a valid email");
        if (name.isBlank() || name.length() > 80) throw new ApiException(400, "Enter the full name");
        String password = phone.replaceAll("\\D", "");
        if (password.length() < 8) throw new ApiException(400, "Enter a phone number");
        if (users.findByEmailIgnoreCase(email).isPresent()) throw new ApiException(409, "This email already has an account. Sign in to continue.");
        if (source == null || source.isBlank()) throw new ApiException(400, "Please select how you heard about us (Lead source is required)");
        
        // Normalize and validate allowed sources: YouTube, Instagram, Referral
        String normalizedSource;
        if ("YouTube".equalsIgnoreCase(source)) normalizedSource = "YouTube";
        else if ("Instagram".equalsIgnoreCase(source)) normalizedSource = "Instagram";
        else if ("Referral".equalsIgnoreCase(source)) normalizedSource = "Referral";
        else throw new ApiException(400, "Invalid lead source. Allowed sources: YouTube, Instagram, Referral");

        if ("Referral".equals(normalizedSource) && (referralDetails == null || referralDetails.isBlank())) {
            throw new ApiException(400, "Please enter your referral code or referrer details");
        }

        Models.User user = new Models.User();
        user.role = "CLIENT";
        user.accountType = "INDIVIDUAL";
        user.fullName = name;
        user.email = email;
        user.phone = phone.trim();
        user.source = normalizedSource;
        user.referralDetails = referralDetails.trim();
        user.leadStatus = "NEW";
        if (body.hasNonNull("selectedPlan")) {
            user.selectedPlan = body.path("selectedPlan").asText();
            if (body.hasNonNull("selectedPlanName")) user.selectedPlanName = body.path("selectedPlanName").asText();
        }
        user.billingState = catalog.businessState();
        user.passwordHash = encoder.encode(password);
        users.save(user);

        saveProfile(user, "Self", name, "", body.path("birth"));
        attachReferral(user, body);

        Map<String, Object> notifyStatus = notify.accountCreated(user, password);
        Map<String, Object> out = publicUser(user);
        out.put("token", issueTabToken(res, user));
        out.put("notificationStatus", notifyStatus);
        out.put("message", notifyStatus.get("message"));
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
        m.put("source", u.source);
        m.put("referralDetails", u.referralDetails);
        m.put("assignedTo", u.assignedTo);
        m.put("assignedToName", u.assignedToName);
        m.put("leadStatus", u.leadStatus);
        m.put("convertedAt", u.convertedAt);
        m.put("selectedPlan", u.selectedPlan);
        m.put("selectedPlanName", u.selectedPlanName);
        m.put("createdAt", u.createdAt);
        m.put("permissions", sectionsOf(u));
        return m;
    }

    /** Screens an employee can be allowed to open. Employees and Settings stay with the administrator. */
    public static final List<String> STAFF_SECTIONS = List.of("overview", "customers", "consults", "questions", "plans", "reports", "notes");
    private static final String ORIGINAL_STAFF = "overview,customers,consults,reports,notes";

    public List<String> sectionsOf(Models.User u) {
        if ("ADMIN".equals(u.role)) return List.of("overview", "customers", "employees", "consults", "questions", "plans", "reports", "notes", "settings");
        String raw = u.permissions == null || u.permissions.isBlank() ? ORIGINAL_STAFF : u.permissions;
        List<String> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            String key = part.trim();
            if (STAFF_SECTIONS.contains(key) && !out.contains(key)) out.add(key);
        }
        return out;
    }

    public void requireSection(Models.User u, String section) {
        if (!sectionsOf(u).contains(section)) throw new ApiException(403, "This section is not open for your account");
    }

    private String storeSections(JsonNode node) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            return ORIGINAL_STAFF;
        }
        List<String> picked = new ArrayList<>();
        for (JsonNode item : node) {
            String key = item.asText("").trim();
            if (STAFF_SECTIONS.contains(key) && !picked.contains(key)) picked.add(key);
        }
        if (picked.isEmpty()) return ORIGINAL_STAFF;
        return String.join(",", picked);
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
        return ScientificChartEngine.compute(p.dob, p.tob, p.lat, p.lon, p.tz, p.division);
    }

    @Transactional
    public Map<String, Object> openChat(Models.User u, Models.Profile p) {
        List<Models.ChatMessage> existing = chats.findByProfileIdOrderByCreatedAtAsc(p.id);
        if (existing.isEmpty()) {
            Chart c = chart(p);
            String who = "Self".equals(p.relation) ? "you" : p.fullName + " (your " + p.relation.toLowerCase() + ")";
            String text = "Hello, I am looking at the natal chart for " + who + ". "
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
        int use = pointsRequested(body);
        applyPoints(u, c, use);
        c.demo = true;
        c.intake = intake(body.path("answers"));
        consultations.save(c);
        if (use > 0) addPoints(u.id, -use, "spent", null, c.id, use + " points on " + c.planName);
        creditPlanShare(u, c, plan.priceInr);

        // Update customer lead details & trigger conversion
        u.selectedPlan = plan.code;
        u.selectedPlanName = plan.name;
        u.leadStatus = "CONVERTED";
        if (u.convertedAt == null || u.convertedAt.isBlank()) {
            u.convertedAt = java.time.Instant.now().toString();
        }
        recordCommissionForLead(u);
        users.save(u);

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
        if (!u.id.equals(r.userId)) {
            if (!"ADMIN".equals(u.role) && !"EMPLOYEE".equals(u.role)) throw new ApiException(404, "Report not found");
            requireSection(u, "reports");
        }
        return files.findById(id).orElseThrow(() -> new ApiException(404, "The file is not ready yet"));
    }

    @Transactional
    public Map<String, Object> addEmployee(String fullName, String email, JsonNode permissions) {
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
        emp.permissions = storeSections(permissions);
        try {
            notify.employeeAccess(emp, temporary);
        } catch (Exception ignored) {}
        users.save(emp);
        Map<String, Object> out = publicUser(emp);
        out.put("temporaryPassword", temporary);
        return out;
    }

    /** Mails a new password. An unknown email gets the same quiet success so the page does not reveal who has an account. */
    @Transactional
    public void forgotPassword(String email) {
        String mail = email == null ? "" : email.trim();
        var found = users.findByEmailIgnoreCase(mail);
        if (found.isEmpty()) return;
        Models.User user = found.get();
        String next = password();
        if (!notify.passwordReset(user, next)) throw new ApiException(502, "The password email could not be sent. Try again in a moment.");
        user.passwordHash = encoder.encode(next);
        users.save(user);
    }

    @Transactional
    public Map<String, Object> updateEmployee(String id, String fullName, JsonNode permissions) {
        Models.User emp = users.findById(id == null ? "" : id).orElseThrow(() -> new ApiException(404, "Employee not found"));
        if (!"EMPLOYEE".equals(emp.role)) throw new ApiException(404, "Employee not found");
        if (fullName == null || fullName.isBlank() || fullName.trim().length() > 80) throw new ApiException(400, "Enter the employee name");
        emp.fullName = fullName.trim();
        emp.permissions = storeSections(permissions);
        users.save(emp);
        return publicUser(emp);
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

    public Map<String, Object> stats(Models.User actor) {
        Map<String, Object> m = new LinkedHashMap<>();
        if ("ADMIN".equals(actor.role)) {
            m.put("customers", users.countByRole("CLIENT"));
            m.put("employees", users.countByRole("EMPLOYEE"));
            m.put("consultations", consultations.count());
            m.put("reports", reports.count());
            m.put("convertedLeads", users.countByRoleAndLeadStatus("CLIENT", "CONVERTED"));
            m.put("newLeads", users.countByRoleAndLeadStatus("CLIENT", "NEW"));
        } else {
            long myCustomers = users.countByRoleAndAssignedTo("CLIENT", actor.id);
            long myConverted = users.countByRoleAndAssignedToAndLeadStatus("CLIENT", actor.id, "CONVERTED");
            long myNew = users.countByRoleAndAssignedToAndLeadStatus("CLIENT", actor.id, "NEW");
            List<String> myCustIds = users.findByRoleAndAssignedToOrderByCreatedAtDesc("CLIENT", actor.id).stream().map(u -> u.id).toList();
            long myConsults = myCustIds.isEmpty() ? 0 : consultations.findByUserIdInOrderByCreatedAtDesc(myCustIds).size();
            long myReps = myCustIds.isEmpty() ? 0 : reports.findByUserIdInOrderByCreatedAtDesc(myCustIds).size();
            m.put("customers", myCustomers);
            m.put("consultations", myConsults);
            m.put("reports", myReps);
            m.put("convertedLeads", myConverted);
            m.put("newLeads", myNew);
        }
        return m;
    }

    public List<Models.Consultation> myConsultations(Models.User u) {
        return consultations.findByUserIdOrderByCreatedAtDesc(u.id);
    }

    public List<Models.User> customers() {
        return users.findByRoleOrderByCreatedAtDesc("CLIENT");
    }

    public List<Models.User> customersForActor(Models.User actor) {
        if ("ADMIN".equals(actor.role)) {
            return users.findByRoleOrderByCreatedAtDesc("CLIENT");
        }
        return users.findByRoleAndAssignedToOrderByCreatedAtDesc("CLIENT", actor.id);
    }

    public List<Map<String, Object>> consultationsForActor(Models.User actor) {
        List<Models.Consultation> list;
        if ("ADMIN".equals(actor.role)) {
            list = consultations.findAllByOrderByCreatedAtDesc();
        } else {
            List<String> myCustIds = users.findByRoleAndAssignedToOrderByCreatedAtDesc("CLIENT", actor.id).stream().map(u -> u.id).toList();
            list = myCustIds.isEmpty() ? List.of() : consultations.findByUserIdInOrderByCreatedAtDesc(myCustIds);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Models.Consultation c : list) {
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
            row.put("pointsUsed", c.pointsUsed);
            row.put("payableMinor", c.payableMinor);
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

    public List<Models.Report> reportsForActor(Models.User actor) {
        if ("ADMIN".equals(actor.role)) {
            return reports.findAllByOrderByCreatedAtDesc();
        }
        List<String> myCustIds = users.findByRoleAndAssignedToOrderByCreatedAtDesc("CLIENT", actor.id).stream().map(u -> u.id).toList();
        return myCustIds.isEmpty() ? List.of() : reports.findByUserIdInOrderByCreatedAtDesc(myCustIds);
    }

    public List<Models.Notification> notificationsForActor(Models.User actor) {
        if ("ADMIN".equals(actor.role)) {
            return notifications.findTop100ByOrderByCreatedAtDesc();
        }
        List<Models.User> myCusts = users.findByRoleAndAssignedToOrderByCreatedAtDesc("CLIENT", actor.id);
        Set<String> addresses = new HashSet<>();
        for (Models.User u : myCusts) {
            if (u.email != null) addresses.add(u.email.toLowerCase());
            if (u.phone != null) addresses.add(u.phone);
        }
        // Note: actor.email and actor.phone are NOT added here so that staff credentials and system password resets remain private to Admin
        return notifications.findTop100ByOrderByCreatedAtDesc().stream()
                .filter(n -> n.toAddr != null && addresses.contains(n.toAddr.toLowerCase()))
                .filter(n -> {
                    // Filter out any notification containing credentials or temporary passwords
                    String subj = n.subject != null ? n.subject.toLowerCase() : "";
                    String body = n.body != null ? n.body.toLowerCase() : "";
                    if (subj.contains("password") || subj.contains("staff sign-in") || subj.contains("account is created")) return false;
                    if (body.contains("password:") || body.contains("sign-in details")) return false;
                    return true;
                })
                .toList();
    }

    @Transactional
    public Models.User assignCustomer(Models.User actor, String customerId, String employeeId) {
        Models.User customer = users.findById(customerId).orElseThrow(() -> new ApiException(404, "Customer not found"));
        if (!"CLIENT".equals(customer.role)) throw new ApiException(400, "Target user is not a customer");

        // Role permissions: Admin can assign to anyone or self. Employees can only self-assign an UNASSIGNED customer, or unassign themselves.
        if (!"ADMIN".equals(actor.role)) {
            // Employee can only assign to themselves (employeeId == actor.id) or unassign themselves (employeeId == null && customer.assignedTo == actor.id)
            if (employeeId != null && !employeeId.isBlank()) {
                if (!actor.id.equals(employeeId)) {
                    throw new ApiException(403, "You can only assign customers to yourself");
                }
                if (customer.assignedTo != null && !customer.assignedTo.isBlank() && !actor.id.equals(customer.assignedTo)) {
                    throw new ApiException(403, "This customer is already assigned to another employee");
                }
            } else {
                // Employee is unassigning
                if (!actor.id.equals(customer.assignedTo)) {
                    throw new ApiException(403, "You cannot unassign another employee's customer");
                }
            }
        }

        if (employeeId == null || employeeId.isBlank()) {
            customer.assignedTo = null;
            customer.assignedToName = null;
        } else {
            Models.User employee = users.findById(employeeId).orElseThrow(() -> new ApiException(404, "Employee not found"));
            customer.assignedTo = employee.id;
            customer.assignedToName = employee.fullName;
        }
        return users.save(customer);
    }

    @Transactional
    public Models.User updateCustomerLead(Models.User actor, String customerId, String leadStatus, String selectedPlan, String selectedPlanName) {
        Models.User customer = users.findById(customerId).orElseThrow(() -> new ApiException(404, "Customer not found"));
        if (!"ADMIN".equals(actor.role) && !actor.id.equals(customer.assignedTo)) {
            throw new ApiException(403, "Permission denied for this customer");
        }
        if (leadStatus != null && !leadStatus.isBlank()) {
            customer.leadStatus = leadStatus.toUpperCase();
            if ("CONVERTED".equalsIgnoreCase(leadStatus) && customer.convertedAt == null) {
                customer.convertedAt = java.time.Instant.now().toString();
            }
        }
        if (selectedPlan != null) customer.selectedPlan = selectedPlan;
        if (selectedPlanName != null) customer.selectedPlanName = selectedPlanName;

        // Auto-calculate commission if converted
        if ("CONVERTED".equalsIgnoreCase(customer.leadStatus)) {
            recordCommissionForLead(customer);
        }

        return users.save(customer);
    }

    private void recordCommissionForLead(Models.User customer) {
        if (customer.source == null || customer.source.isBlank()) return;
        // Check if a commission record already exists for this customer to prevent duplicates
        if (!commissionRecords.findByCustomerIdOrderByCreatedAtDesc(customer.id).isEmpty()) {
            return;
        }
        String monthKey = java.time.LocalDate.now().toString().substring(0, 7); // e.g. "2026-11"
        // Find rate
        Models.CommissionRate rate = commissionRates.findBySourceIgnoreCase(customer.source).orElse(null);
        double ratePct = rate != null ? rate.ratePercent : ("Instagram".equalsIgnoreCase(customer.source) ? 20.0 : "Referral".equalsIgnoreCase(customer.source) ? 15.0 : 10.0);
        long baseAmount = 100000L; // default ₹1,000 in minor units (paise)
        if (customer.selectedPlan != null && !customer.selectedPlan.isBlank()) {
            try {
                Models.Plan p = catalog.require(customer.selectedPlan);
                if (p.priceInr > 0) {
                    baseAmount = (long) p.priceInr * 100L;
                }
            } catch (Exception ignored) {}
        }
        long comm = Math.round(baseAmount * (ratePct / 100.0));
        Models.CommissionRecord rec = new Models.CommissionRecord();
        rec.customerId = customer.id;
        rec.customerName = customer.fullName;
        rec.source = customer.source;
        rec.planCode = customer.selectedPlan;
        rec.planName = customer.selectedPlanName;
        rec.appliedRatePercent = ratePct;
        rec.baseAmountMinor = baseAmount;
        rec.commissionAmountMinor = comm;
        rec.assignedEmployeeId = customer.assignedTo;
        rec.assignedEmployeeName = customer.assignedToName;
        rec.monthKey = monthKey;
        commissionRecords.save(rec);
    }

    public List<Models.CommissionRate> allCommissionRates() {
        return commissionRates.findAll();
    }

    @Transactional
    public Models.CommissionRate updateCommissionRate(String source, double ratePercent, String adminName) {
        if (ratePercent < 0.0 || ratePercent > 100.0) {
            throw new ApiException(400, "Commission rate must be between 0% and 100%");
        }
        Models.CommissionRate rate = commissionRates.findBySourceIgnoreCase(source).orElseGet(() -> {
            Models.CommissionRate cr = new Models.CommissionRate();
            cr.source = source;
            return cr;
        });
        rate.ratePercent = ratePercent;
        rate.updatedBy = adminName;
        return commissionRates.save(rate);
    }

    public List<Models.CommissionRecord> allCommissionRecords() {
        return commissionRecords.findAllByOrderByCreatedAtDesc();
    }
    public List<Map<String, Object>> employees() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Models.User u : users.findByRoleOrderByCreatedAtDesc("EMPLOYEE")) out.add(publicUser(u));
        return out;
    }
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
            row.put("pointsUsed", c.pointsUsed);
            row.put("payableMinor", c.payableMinor);
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
        if (birth.has("number") && !birth.get("number").isNull() && !birth.get("number").asText("").isBlank()) {
            int number = birth.get("number").asInt(-1);
            if (number < 0 || number > 249) throw new ApiException(400, "Choose a number from 0 to 249");
            p.division = number;
        }
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
        return p.fullName + " has " + c.ascendant.sign + " rising. The ascendant sub lord is " + c.ascendant.subLord
                + " and the sub sub lord is " + c.cusps.get(0).subSubLord
                + ". The Moon is in " + moon.sign + " in " + moon.nakshatra + " (star lord " + moon.starLord + ", sub lord " + moon.subLord + ", sub sub lord " + moon.subSubLord + "). "
                + "The running period is " + c.currentDasha.label + ", through " + c.currentDasha.pdEnd + ". "
                + "Use that window for the question you asked. The houses the ascendant sub-lord signifies are " + c.housesSignified(c.ascendant.subLord) + ". "
                + "If you want this written up, request a detailed report from My Reports, or book a live session and the astrologer will go through the remedies with you.";
    }

    /** 1% of the plan price in rupees. ₹1,500 credits 15 points. */
    public static int planShare(int priceInr) {
        if (priceInr <= 0) return 0;
        return (int) Math.round(priceInr / 100.0);
    }

    public int pointsOf(Models.User u) {
        return points.findByUserIdOrderByCreatedAtDesc(u.id).stream().mapToInt(e -> e.points).sum();
    }

    public Map<String, Object> openReferral(String code) {
        Models.Referral ref = referrals.findByCode(code == null ? "" : code.trim()).orElseThrow(() -> new ApiException(404, "This referral link is not active"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("open", "invited".equals(ref.status));
        if (!"invited".equals(ref.status)) return m;
        m.put("fullName", ref.fullName);
        m.put("phone", ref.phone);
        m.put("email", ref.email);
        users.findById(ref.referrerId).ifPresent(r -> m.put("referrerName", r.fullName));
        return m;
    }

    @Transactional
    public Map<String, Object> refer(Models.User u, JsonNode body) {
        if (!"CLIENT".equals(u.role)) throw new ApiException(403, "A client account can refer someone");
        String name = text(body, "fullName");
        String email = text(body, "email").toLowerCase();
        String phone = text(body, "phone");
        String phoneKey = digitsOf(phone);
        if (name.isBlank() || name.length() > 80) throw new ApiException(400, "Enter their full name");
        if (!email.contains("@") || email.length() > 120) throw new ApiException(400, "Enter a valid email");
        if (phoneKey.length() < 8 || phoneKey.length() > 15) throw new ApiException(400, "Enter a phone number");
        if (email.equalsIgnoreCase(u.email) || phoneKey.equals(digitsOf(u.phone))) throw new ApiException(400, "Enter someone else's details");
        if (users.findByEmailIgnoreCase(email).isPresent() || phoneUsed(phoneKey)) throw new ApiException(409, "This person already has an account");
        if (referrals.findByEmailIgnoreCase(email).isPresent()) throw new ApiException(409, "This person has already been referred");
        if (referrals.findByPhoneKey(phoneKey).isPresent()) throw new ApiException(409, "This WhatsApp number has already been referred");
        String code = referralCode();
        String link = publicUrl() + "/?ref=" + code;
        Models.Referral ref = new Models.Referral();
        ref.referrerId = u.id;
        ref.fullName = name;
        ref.phone = phone.trim();
        ref.phoneKey = phoneKey;
        ref.email = email;
        ref.code = code;
        ref.status = "invited";
        ref.signupLink = link;
        ref.message = referralMessage(u, name, text(body, "message"), link);
        if (!notify.referralEmail(email, u.fullName + " referred you to AstroGreet", ref.message)) {
            throw new ApiException(502, "The email could not be sent. Try again in a moment.");
        }
        ref.emailStatus = "SENT";
        referrals.save(ref);
        return referralView(ref, 0);
    }

    @Transactional
    public Map<String, Object> updateReferral(Models.User u, String id, JsonNode body) {
        Models.Referral ref = referrals.findById(id).orElseThrow(() -> new ApiException(404, "Referral not found"));
        if (!u.id.equals(ref.referrerId)) throw new ApiException(404, "Referral not found");
        if (!"invited".equals(ref.status)) throw new ApiException(400, "They have already signed up");
        ref.message = referralMessage(u, ref.fullName, text(body, "message"), ref.signupLink);
        if (!notify.referralEmail(ref.email, u.fullName + " referred you to AstroGreet", ref.message)) {
            throw new ApiException(502, "The email could not be sent. Try again in a moment.");
        }
        ref.emailStatus = "SENT";
        referrals.save(ref);
        int earned = points.findByUserIdOrderByCreatedAtDesc(u.id).stream()
                .filter(e -> ref.id.equals(e.referralId) && e.points > 0)
                .mapToInt(e -> e.points).sum();
        return referralView(ref, earned);
    }

    public Map<String, Object> myReferrals(Models.User u) {
        List<Models.PointEntry> entries = points.findByUserIdOrderByCreatedAtDesc(u.id);
        Map<String, Integer> earned = new LinkedHashMap<>();
        for (Models.PointEntry e : entries) {
            if (e.referralId == null || e.points <= 0) continue;
            earned.merge(e.referralId, e.points, Integer::sum);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Models.Referral ref : referrals.findByReferrerIdOrderByCreatedAtDesc(u.id)) {
            rows.add(referralView(ref, earned.getOrDefault(ref.id, 0)));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("points", entries.stream().mapToInt(e -> e.points).sum());
        m.put("referrals", rows);
        List<Map<String, Object>> history = new ArrayList<>();
        for (Models.PointEntry e : entries) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("points", e.points);
            row.put("note", e.note);
            row.put("createdAt", e.createdAt);
            history.add(row);
        }
        m.put("history", history);
        return m;
    }

    private void attachReferral(Models.User user, JsonNode body) {
        if (user.referredBy != null && !user.referredBy.isBlank()) return;
        Models.Referral ref = invitedReferral(body, user);
        if (ref == null) return;
        Models.User referrer = users.findById(ref.referrerId).orElse(null);
        if (referrer == null || samePerson(referrer, user)) return;
        ref.status = "joined";
        ref.joinedUserId = user.id;
        referrals.save(ref);
        user.referredBy = referrer.id;
        users.save(user);
        addPoints(referrer.id, 5, "signup", ref.id, null, user.fullName + " created an account");
    }

    private Models.Referral invitedReferral(JsonNode body, Models.User user) {
        String code = text(body, "referralCode");
        if (!code.isBlank()) {
            Models.Referral byCode = referrals.findByCode(code).orElse(null);
            if (byCode != null && "invited".equals(byCode.status)) return byCode;
        }
        Models.Referral byEmail = referrals.findByEmailIgnoreCase(user.email).orElse(null);
        if (byEmail != null && "invited".equals(byEmail.status)) return byEmail;
        String phoneKey = digitsOf(user.phone);
        if (phoneKey.length() < 8) return null;
        Models.Referral byPhone = referrals.findByPhoneKey(phoneKey).orElse(null);
        if (byPhone != null && "invited".equals(byPhone.status)) return byPhone;
        return null;
    }

    private void applyPoints(Models.User u, Models.Consultation c, int use) {
        if (use < 0) throw new ApiException(400, "Enter a points amount of zero or more");
        int balance = pointsOf(u);
        if (use > balance) throw new ApiException(400, "You have " + balance + " referral points");
        if (use > 0 && !"INR".equals(c.currency)) throw new ApiException(400, "Referral points come off India bookings. 1 point is ₹1.");
        long rupees = c.totalMinor / 100;
        if (use > rupees) throw new ApiException(400, "This session can take up to " + rupees + " points");
        c.pointsUsed = use;
        c.payableMinor = c.totalMinor - use * 100L;
    }

    private int pointsRequested(JsonNode body) {
        JsonNode node = body.get("points");
        if (node == null || node.isNull() || node.asText("").isBlank()) return 0;
        if (!node.isIntegralNumber()) throw new ApiException(400, "Enter a whole number of points");
        return node.asInt();
    }

    private void creditPlanShare(Models.User buyer, Models.Consultation c, int priceInr) {
        if (buyer.referredBy == null || buyer.referredBy.isBlank()) return;
        int share = planShare(priceInr);
        if (share <= 0) return;
        String referralId = referrals.findByJoinedUserId(buyer.id).map(r -> r.id).orElse(null);
        addPoints(buyer.referredBy, share, "purchase", referralId, c.id, buyer.fullName + " booked " + c.planName + ". 1% of ₹" + priceInr + " is " + share + " points.");
    }

    private void addPoints(String userId, int amount, String reason, String referralId, String consultationId, String note) {
        Models.PointEntry e = new Models.PointEntry();
        e.userId = userId;
        e.points = amount;
        e.reason = reason;
        e.referralId = referralId;
        e.consultationId = consultationId;
        e.note = note;
        points.save(e);
    }

    private Map<String, Object> referralView(Models.Referral ref, int earned) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", ref.id);
        m.put("fullName", ref.fullName);
        m.put("phone", ref.phone);
        m.put("email", ref.email);
        m.put("status", ref.status);
        m.put("points", earned);
        m.put("signupLink", ref.signupLink);
        m.put("whatsappStatus", ref.whatsappStatus);
        m.put("message", ref.message);
        m.put("emailStatus", ref.emailStatus);
        m.put("createdAt", ref.createdAt);
        return m;
    }

    private boolean phoneUsed(String phoneKey) {
        for (Models.User u : users.findAll()) {
            if (phoneKey.equals(digitsOf(u.phone))) return true;
        }
        return false;
    }

    private boolean samePerson(Models.User a, Models.User b) {
        if (a.email != null && a.email.equalsIgnoreCase(b.email)) return true;
        String left = digitsOf(a.phone);
        return left.length() >= 8 && left.equals(digitsOf(b.phone));
    }

    private String referralMessage(Models.User referrer, String guest, String written, String link) {
        String text = written == null ? "" : written.trim();
        if (text.isBlank()) {
            text = "Hello " + guest + ",\n\n" + referrer.fullName + " referred you to AstroGreet. Open the link in this email and create your account. Your Vedic chart is ready as soon as you sign up.";
        }
        if (text.length() > 2000) throw new ApiException(400, "Keep the message under 2000 characters");
        if (link != null && !text.contains(link)) text = text + "\n\n" + link;
        return text;
    }

    private String publicUrl() {
        String requested = requestOrigin();
        if (requested != null && props.getCorsOrigins() != null && props.getCorsOrigins().contains(requested)) return requested;
        String url = props.getPublicUrl();
        if (url == null || url.isBlank()) url = "http://localhost:5173";
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String requestOrigin() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes web)) return null;
        String origin = web.getRequest().getHeader("Origin");
        if (origin == null || origin.isBlank()) return null;
        return origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin;
    }

    private String referralCode() {
        byte[] b = new byte[6];
        RND.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    private static String digitsOf(String phone) {
        return phone == null ? "" : phone.replaceAll("\\D", "");
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
