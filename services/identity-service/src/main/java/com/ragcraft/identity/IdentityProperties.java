package com.ragcraft.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "identity")
public class IdentityProperties {
    private boolean seedDemoAccounts = false;
    private String adminEmail = "";
    private String adminPassword = "";
    private String mailMode = "log";
    /** When false, new accounts start UNVERIFIED and must confirm an e-mailed link before signing in. */
    private boolean autoVerifyEmail = true;
    /** Failed sign-ins before an account is locked; the lock grows by one minute per lock up to {@link #maxLockMinutes}. */
    private int maxFailedAttempts = 5;
    private int maxLockMinutes = 15;
    private int deletionGraceDays = 7;
    private int linkMinutes = 30;
    /** Token lifetime when "remember me" is ticked. */
    private int rememberMinutes = 60 * 24 * 30;

    public boolean isSeedDemoAccounts() { return seedDemoAccounts; }
    public void setSeedDemoAccounts(boolean seedDemoAccounts) { this.seedDemoAccounts = seedDemoAccounts; }
    public String getAdminEmail() { return adminEmail; }
    public void setAdminEmail(String adminEmail) { this.adminEmail = adminEmail; }
    public String getAdminPassword() { return adminPassword; }
    public void setAdminPassword(String adminPassword) { this.adminPassword = adminPassword; }
    public String getMailMode() { return mailMode; }
    public void setMailMode(String mailMode) { this.mailMode = mailMode; }
    public boolean isAutoVerifyEmail() { return autoVerifyEmail; }
    public void setAutoVerifyEmail(boolean autoVerifyEmail) { this.autoVerifyEmail = autoVerifyEmail; }
    public int getMaxFailedAttempts() { return maxFailedAttempts; }
    public void setMaxFailedAttempts(int maxFailedAttempts) { this.maxFailedAttempts = maxFailedAttempts; }
    public int getMaxLockMinutes() { return maxLockMinutes; }
    public void setMaxLockMinutes(int maxLockMinutes) { this.maxLockMinutes = maxLockMinutes; }
    public int getDeletionGraceDays() { return deletionGraceDays; }
    public void setDeletionGraceDays(int deletionGraceDays) { this.deletionGraceDays = deletionGraceDays; }
    public int getLinkMinutes() { return linkMinutes; }
    public void setLinkMinutes(int linkMinutes) { this.linkMinutes = linkMinutes; }
    public int getRememberMinutes() { return rememberMinutes; }
    public void setRememberMinutes(int rememberMinutes) { this.rememberMinutes = rememberMinutes; }
}
