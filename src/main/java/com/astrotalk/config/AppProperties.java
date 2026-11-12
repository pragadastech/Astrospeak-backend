package com.astrotalk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "astro")
public class AppProperties {
    private String adminEmail;
    private String adminPassword;
    private String businessState;
    private String jwtSecret;
    private String mailFrom;
    private String whatsappToken;
    private String whatsappPhoneId;

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
}
