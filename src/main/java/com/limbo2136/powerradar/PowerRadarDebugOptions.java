package com.limbo2136.powerradar;

public final class PowerRadarDebugOptions {
    private static final boolean SCAN_OPTIMIZATION_LOGGING = false;
    private static final boolean RADAR_LINK_LEASE_LOGGING = false;
    private static final boolean TARGET_SYSTEM_BUG_REPORT_LOGGING = false;
    private static final boolean INTERCEPTION_SYSTEM_BUG_REPORT_LOGGING = false;
    private static final boolean SHELL_ALARM_BUG_REPORT_LOGGING = false;

    private PowerRadarDebugOptions() {
    }

    public static boolean scanOptimizationLogging() {
        return enabled(
                SCAN_OPTIMIZATION_LOGGING,
                "power_radar.scanOptimizationDebug",
                "POWER_RADAR_SCAN_OPTIMIZATION_DEBUG");
    }

    public static boolean radarLinkLeaseLogging() {
        return enabled(
                RADAR_LINK_LEASE_LOGGING,
                "power_radar.radarLinkLeaseDebug",
                "POWER_RADAR_LINK_LEASE_DEBUG");
    }

    public static boolean targetSystemBugReportLogging() {
        return enabled(
                TARGET_SYSTEM_BUG_REPORT_LOGGING,
                "power_radar.targetSystemBugReportDebug",
                "POWER_RADAR_TARGET_SYSTEM_BUG_REPORT_DEBUG");
    }

    public static boolean interceptionSystemBugReportLogging() {
        return enabled(
                INTERCEPTION_SYSTEM_BUG_REPORT_LOGGING,
                "power_radar.interceptionSystemBugReportDebug",
                "POWER_RADAR_INTERCEPTION_SYSTEM_BUG_REPORT_DEBUG");
    }

    public static boolean shellAlarmBugReportLogging() {
        return enabled(
                SHELL_ALARM_BUG_REPORT_LOGGING,
                "power_radar.shellAlarmBugReportDebug",
                "POWER_RADAR_SHELL_ALARM_BUG_REPORT_DEBUG");
    }

    private static boolean enabled(boolean defaultValue, String propertyName, String environmentName) {
        return defaultValue
                || Boolean.getBoolean(propertyName)
                || "true".equalsIgnoreCase(System.getenv(environmentName));
    }
}
