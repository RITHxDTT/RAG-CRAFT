package com.ragcraft.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "identity")
public class IdentityProperties {
    private boolean seedDemoAccounts = false;
    private String adminEmail = "";
    private String adminPassword = "";
    private String mailMode = "log";

    public boolean isSeedDemoAccounts() { return seedDemoAccounts; }
    public void setSeedDemoAccounts(boolean seedDemoAccounts) { this.seedDemoAccounts = seedDemoAccounts; }
    public String getAdminEmail() { return adminEmail; }
    public void setAdminEmail(String adminEmail) { this.adminEmail = adminEmail; }
    public String getAdminPassword() { return adminPassword; }
    public void setAdminPassword(String adminPassword) { this.adminPassword = adminPassword; }
    public String getMailMode() { return mailMode; }
    public void setMailMode(String mailMode) { this.mailMode = mailMode; }
}
