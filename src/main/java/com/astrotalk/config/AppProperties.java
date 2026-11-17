package com.astrotalk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "astro")
public class AppProperties {
    private String adminEmail;
    private String adminPassword;
    private String businessState;
    private String jwtSecret;
    private String mailFrom;
    private String whatsappToken;
    private String whatsappPhoneId;
    private String publicUrl;
    private String apiUrl = "https://api.astrogreet.com";
    private String legacyApiUrl = "https://astrospeak-backend.up.railway.app";
    private List<String> corsOrigins = new ArrayList<>(List.of(
            "http://localhost:5173",
            "http://127.0.0.1:5173",
            "https://astrospeak-frontend.up.railway.app",
            "https://astrogreet.com",
            "https://www.astrogreet.com",
            "https://astrogreet.in",
            "https://www.astrogreet.in",
            "https://api.astrogreet.com"));

    public String getAdminEmail() { return adminEmail; }
    public void setAdminEmail(String adminEmail) { this.adminEmail = adminEmail; }
    public String getAdminPassword() { return adminPassword; }
    public void setAdminPassword(String adminPassword) { this.adminPassword = adminPassword; }
    public String getBusinessState() { return businessState; }
    public void setBusinessState(String businessState) { this.businessState = businessState; }
    public String getJwtSecret() { return jwtSecret; }
    public void setJwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; }
    public String getMailFrom() { return mailFrom; }
    public void setMailFrom(String mailFrom) { this.mailFrom = mailFrom; }
    public String getWhatsappToken() { return whatsappToken; }
    public void setWhatsappToken(String whatsappToken) { this.whatsappToken = whatsappToken; }
    public String getWhatsappPhoneId() { return whatsappPhoneId; }
    public void setWhatsappPhoneId(String whatsappPhoneId) { this.whatsappPhoneId = whatsappPhoneId; }
    public String getPublicUrl() { return publicUrl; }
    public void setPublicUrl(String publicUrl) { this.publicUrl = publicUrl; }
    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
    public String getLegacyApiUrl() { return legacyApiUrl; }
    public void setLegacyApiUrl(String legacyApiUrl) { this.legacyApiUrl = legacyApiUrl; }
    public List<String> getCorsOrigins() { return corsOrigins; }
    public void setCorsOrigins(List<String> corsOrigins) { this.corsOrigins = corsOrigins; }
}
