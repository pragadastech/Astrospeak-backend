package com.astrotalk.service;

import com.astrotalk.config.ApiException;
import com.astrotalk.config.AppProperties;
import com.astrotalk.domain.Models;
import com.astrotalk.domain.Repos;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CatalogService {
    public static final List<String> STATES = List.of(
            "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chhattisgarh", "Goa", "Gujarat", "Haryana",
            "Himachal Pradesh", "Jharkhand", "Karnataka", "Kerala", "Madhya Pradesh", "Maharashtra", "Manipur",
            "Meghalaya", "Mizoram", "Nagaland", "Odisha", "Punjab", "Rajasthan", "Sikkim", "Tamil Nadu", "Telangana",
            "Tripura", "Uttar Pradesh", "Uttarakhand", "West Bengal", "Delhi", "Jammu and Kashmir", "Ladakh",
            "Chandigarh", "Puducherry", "Outside India");

    private final Repos.PlanRepo plans;
    private final Repos.SettingRepo settings;
    private final AppProperties props;

    public CatalogService(Repos.PlanRepo plans, Repos.SettingRepo settings, AppProperties props) {
        this.plans = plans;
        this.settings = settings;
        this.props = props;
    }

    public void seed() {
        if (plans.count() > 0) return;
        save(plan("PLAN1", "Plan 1 — Chart Reading", "A focused first look at the main cusps.", 30, 500, 9, false, 1,
                List.of("Main cusp analysis", "30-minute live session on Google Meet", "A short written summary after the session"),
                "Working USD price is $9. $25 was also discussed. Change it here when the client confirms."));
        save(plan("PLAN2", "Plan 2 — In-depth Guidance", "A long live session with remedies.", 90, 2499, 45, true, 2,
                List.of("1 hour 30 minute live session on Google Meet", "In-depth analysis", "Remedies and guidance"),
                "Working price ₹2,499. USD is a placeholder until confirmed. This plan is the long remedial session, not the full birth-chart plan."));
        save(plan("PLAN3", "Plan 3 — Full Birth Chart", "The whole chart, relationships, and monthly questions.", 90, 2999, 55, false, 3,
                List.of("Full birth chart analysis", "1 hour 30 minute consultation on Google Meet", "Relationship analysis", "Remedies and guidance", "2 queries every month"),
                "Working price ₹2,999. Different from Plan 2: the full chart, relationship analysis, and two questions a month."));
        save(plan("FAMILY", "Family Pack", "Four people, one in-depth live session.", 90, 4999, 30, false, 4,
                List.of("Up to 4 family members", "Main cusp analysis for each member", "1 hour 30 minute in-depth live session on Google Meet", "Remedies and guidance"),
                "Working prices ₹4,999 and $30, both figures the client mentioned. ₹3,000 was also heard. Confirm before going live."));
        save(plan("CORPORATE", "Corporate Cosmic Analysis", "For a founder or a team. The five points below are drafts.", 90, 5999, 99, false, 5,
                List.of("Draft: timing windows for a launch or a hire", "Draft: live session on Google Meet", "Draft: remedies and practical guidance", "Draft: a written summary after the session", "Draft: one follow-up question within 30 days"),
                "Price ₹5,999 / $99. The five bullet points are drafts until Prasad sends the final list. $100 is the other figure that was mentioned."));
        if (settings.findById("businessState").isEmpty()) {
            Models.Setting s = new Models.Setting();
            s.key = "businessState";
            s.value = props.getBusinessState() == null ? "Telangana" : props.getBusinessState();
            settings.save(s);
        }
    }

    private void save(Models.Plan p) { plans.save(p); }

    private static Models.Plan plan(String code, String name, String tagline, int minutes, int inr, int usd, boolean recommended, int order, List<String> features, String note) {
        Models.Plan p = new Models.Plan();
        p.code = code;
        p.name = name;
        p.tagline = tagline;
        p.minutes = minutes;
        p.priceInr = inr;
        p.priceUsd = usd;
        p.recommended = recommended;
        p.sortOrder = order;
        p.features = String.join("\n", features);
        p.note = note;
        p.active = true;
        p.family = "FAMILY".equals(code);
        return p;
    }

    public String businessState() {
        return settings.findById("businessState").map(s -> s.value).filter(v -> v != null && !v.isBlank()).orElse(props.getBusinessState());
    }

    public void setBusinessState(String state) {
        if (!STATES.contains(state) || "Outside India".equals(state)) throw new ApiException(400, "Choose an Indian state or union territory for the business");
        Models.Setting s = settings.findById("businessState").orElseGet(Models.Setting::new);
        s.key = "businessState";
        s.value = state;
        settings.save(s);
    }

    public List<Map<String, Object>> list() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Models.Plan p : plans.findByActiveTrueOrderBySortOrderAsc()) out.add(view(p));
        return out;
    }

    public Models.Plan require(String code) {
        return plans.findById(code).filter(p -> p.active).orElseThrow(() -> new ApiException(404, "Plan not found"));
    }

    public Map<String, Object> view(Models.Plan p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", p.code);
        m.put("name", p.name);
        m.put("tagline", p.tagline);
        m.put("minutes", p.minutes);
        m.put("priceInr", p.priceInr);
        m.put("priceUsd", p.priceUsd);
        m.put("recommended", p.recommended);
        m.put("features", List.of(p.features.split("\n")));
        m.put("note", p.note);
        m.put("family", p.family || "FAMILY".equals(p.code));
        m.put("active", p.active);
        Map<String, Object> intra = quote(p, businessState(), "INR");
        m.put("inrTotal", intra.get("total"));
        m.put("gstInr", intra.get("tax"));
        return m;
    }

    public Map<String, Object> quote(Models.Plan p, String billingState, String currency) {
        String state = billingState == null ? "" : billingState.trim();
        boolean usd = "USD".equalsIgnoreCase(currency) || "Outside India".equalsIgnoreCase(state);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("planCode", p.code);
        m.put("planName", p.name);
        m.put("minutes", p.minutes);
        if (usd) {
            m.put("currency", "USD");
            m.put("gstMode", "EXPORT");
            m.put("base", p.priceUsd);
            m.put("tax", 0);
            m.put("cgst", 0);
            m.put("sgst", 0);
            m.put("igst", 0);
            m.put("total", p.priceUsd);
            m.put("baseMinor", p.priceUsd * 100L);
            m.put("taxMinor", 0L);
            m.put("totalMinor", p.priceUsd * 100L);
            m.put("note", "USD price as listed. Indian GST is not added for a billing address outside India until the client confirms export treatment.");
            return m;
        }
        if (!STATES.contains(state) || "Outside India".equals(state)) throw new ApiException(400, "Choose the billing state");
        boolean intra = state.equalsIgnoreCase(businessState());
        BigDecimal base = BigDecimal.valueOf(p.priceInr);
        BigDecimal tax = base.multiply(new BigDecimal("0.18")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal half = tax.divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
        BigDecimal otherHalf = tax.subtract(half);
        m.put("currency", "INR");
        m.put("billingState", state);
        m.put("businessState", businessState());
        m.put("gstMode", intra ? "CGST_SGST" : "IGST");
        m.put("base", base);
        m.put("tax", tax);
        m.put("cgst", intra ? half : BigDecimal.ZERO);
        m.put("sgst", intra ? otherHalf : BigDecimal.ZERO);
        m.put("igst", intra ? BigDecimal.ZERO : tax);
        m.put("total", base.add(tax));
        m.put("baseMinor", base.movePointRight(2).longValueExact());
        m.put("taxMinor", tax.movePointRight(2).longValueExact());
        m.put("totalMinor", base.add(tax).movePointRight(2).longValueExact());
        m.put("note", intra
                ? "9% CGST + 9% SGST because the billing state matches the business state (" + businessState() + ")."
                : "18% IGST because the billing state is outside " + businessState() + ".");
        return m;
    }

    public List<Map<String, Object>> listAll() {
        List<Models.Plan> all = new ArrayList<>(plans.findAll());
        all.sort(java.util.Comparator.comparingInt(p -> p.sortOrder));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Models.Plan p : all) out.add(view(p));
        return out;
    }

    public Models.Plan create(String name, String tagline, int minutes, int priceInr, int priceUsd, String features, boolean recommended, boolean family) {
        if (name == null || name.isBlank() || name.length() > 80) throw new ApiException(400, "Enter a plan name");
        if (minutes < 15 || minutes > 480) throw new ApiException(400, "Session length should be between 15 and 480 minutes");
        if (priceInr < 0 || priceUsd < 0) throw new ApiException(400, "Price cannot be negative");
        if (recommended) clearRecommended();
        Models.Plan p = new Models.Plan();
        p.code = freshCode(name);
        p.name = name.trim();
        p.tagline = tagline == null ? "" : tagline.trim();
        p.minutes = minutes;
        p.priceInr = priceInr;
        p.priceUsd = priceUsd;
        p.features = features == null || features.isBlank() ? "Live session on Google Meet" : features.trim();
        p.note = "";
        p.recommended = recommended;
        p.family = family;
        p.active = true;
        p.sortOrder = plans.findAll().stream().mapToInt(x -> x.sortOrder).max().orElse(0) + 1;
        return plans.save(p);
    }

    public Models.Plan update(String code, String name, String tagline, Integer minutes, Integer inr, Integer usd, String features, Boolean recommended, Boolean family, Boolean active) {
        Models.Plan p = plans.findById(code).orElseThrow(() -> new ApiException(404, "Plan not found"));
        if (name != null && !name.isBlank()) p.name = name.trim();
        if (tagline != null) p.tagline = tagline.trim();
        if (minutes != null) {
            if (minutes < 15 || minutes > 480) throw new ApiException(400, "Session length should be between 15 and 480 minutes");
            p.minutes = minutes;
        }
        if (inr != null) {
            if (inr < 0) throw new ApiException(400, "Price cannot be negative");
            p.priceInr = inr;
        }
        if (usd != null) {
            if (usd < 0) throw new ApiException(400, "Price cannot be negative");
            p.priceUsd = usd;
        }
        if (features != null) p.features = features.trim();
        if (Boolean.TRUE.equals(recommended)) clearRecommended();
        if (recommended != null) p.recommended = recommended;
        if (family != null) p.family = family;
        if (active != null) p.active = active;
        return plans.save(p);
    }

    private void clearRecommended() {
        for (Models.Plan existing : plans.findAll()) {
            if (existing.recommended) {
                existing.recommended = false;
                plans.save(existing);
            }
        }
    }

    private String freshCode(String name) {
        String base = name.toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
        if (base.length() > 20) base = base.substring(0, 20);
        if (base.isBlank()) base = "PLAN";
        String code = base;
        int n = 2;
        while (plans.existsById(code)) {
            code = base + "_" + n;
            n++;
        }
        return code;
    }

    public Models.Plan updatePrices(String code, Integer inr, Integer usd, String features, String note) {
        Models.Plan p = plans.findById(code).orElseThrow(() -> new ApiException(404, "Plan not found"));
        if (inr != null) {
            if (inr < 0) throw new ApiException(400, "Price cannot be negative");
            p.priceInr = inr;
        }
        if (usd != null) {
            if (usd < 0) throw new ApiException(400, "Price cannot be negative");
            p.priceUsd = usd;
        }
        if (features != null && !features.isBlank()) p.features = features.trim();
        if (note != null) p.note = note.trim();
        return plans.save(p);
    }
}
