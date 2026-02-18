package org.redhat;

import java.util.regex.Pattern;

/**
 * Utility class for sanitizing user inputs to prevent security issues
 * such as injection attacks, XSS, and invalid Kubernetes resource names.
 */
public final class InputSanitizer {

    private InputSanitizer() {
    }

    // Kubernetes resource name pattern: lowercase alphanumeric, hyphens, max 253 chars
    // Must start and end with alphanumeric
    private static final Pattern K8S_NAME_PATTERN = Pattern.compile("^[a-z0-9]([a-z0-9\\-]{0,251}[a-z0-9])?$");
    
    // Pattern for potentially dangerous characters in general text
    private static final Pattern DANGEROUS_CHARS = Pattern.compile("[<>\"'`&;|$\\\\]");
    
    // Maximum lengths
    private static final int MAX_ROBOT_NAME_LENGTH = 63; // K8s label value limit
    private static final int MAX_STATUS_LENGTH = 200;
    private static final int MAX_VERBOSE_LENGTH = 1000;
    private static final int MAX_CLIENT_ID_LENGTH = 100;

    /**
     * Sanitizes a robot name for use as a Kubernetes secret name.
     * - Converts to lowercase
     * - Replaces invalid characters with hyphens
     * - Ensures it starts and ends with alphanumeric
     * - Truncates to max length
     * 
     * @param name the raw robot name
     * @return sanitized name safe for K8s resources, or null if input is null/empty
     */
    public static String sanitizeRobotName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        
        // Convert to lowercase and trim
        String sanitized = name.toLowerCase().trim();
        
        // Replace any character that's not alphanumeric or hyphen with hyphen
        sanitized = sanitized.replaceAll("[^a-z0-9\\-]", "-");
        
        // Collapse multiple hyphens into one
        sanitized = sanitized.replaceAll("-+", "-");
        
        // Remove leading/trailing hyphens
        sanitized = sanitized.replaceAll("^-+|-+$", "");
        
        // Truncate to max length
        if (sanitized.length() > MAX_ROBOT_NAME_LENGTH) {
            sanitized = sanitized.substring(0, MAX_ROBOT_NAME_LENGTH);
            // Ensure it doesn't end with hyphen after truncation
            sanitized = sanitized.replaceAll("-+$", "");
        }
        
        // If empty after sanitization, return null
        if (sanitized.isEmpty()) {
            return null;
        }
        
        return sanitized;
    }

    /**
     * Validates if a robot name is valid for Kubernetes resources.
     * 
     * @param name the robot name to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidRobotName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return K8S_NAME_PATTERN.matcher(name).matches() && name.length() <= MAX_ROBOT_NAME_LENGTH;
    }

    /**
     * Sanitizes a status message for display.
     * - Removes potentially dangerous characters
     * - Truncates to max length
     * 
     * @param status the raw status message
     * @return sanitized status safe for display
     */
    public static String sanitizeStatus(String status) {
        if (status == null) {
            return null;
        }
        
        String sanitized = status.trim();
        
        // Remove dangerous characters
        sanitized = DANGEROUS_CHARS.matcher(sanitized).replaceAll("");
        
        // Truncate to max length
        if (sanitized.length() > MAX_STATUS_LENGTH) {
            sanitized = sanitized.substring(0, MAX_STATUS_LENGTH);
        }
        
        return sanitized;
    }

    /**
     * Sanitizes a verbose status message for display.
     * - Removes potentially dangerous characters
     * - Truncates to max length
     * 
     * @param verbose the raw verbose message
     * @return sanitized message safe for display
     */
    public static String sanitizeVerbose(String verbose) {
        if (verbose == null || verbose.isBlank()) {
            return null;
        }
        
        String sanitized = verbose.trim();
        
        // Remove dangerous characters
        sanitized = DANGEROUS_CHARS.matcher(sanitized).replaceAll("");
        
        // Truncate to max length
        if (sanitized.length() > MAX_VERBOSE_LENGTH) {
            sanitized = sanitized.substring(0, MAX_VERBOSE_LENGTH);
        }
        
        return sanitized;
    }

    /**
     * Sanitizes a WebSocket client ID.
     * - Only allows alphanumeric characters
     * - Truncates to max length
     * 
     * @param clientId the raw client ID
     * @return sanitized client ID
     */
    public static String sanitizeClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return null;
        }
        
        // Only allow alphanumeric characters
        String sanitized = clientId.replaceAll("[^a-zA-Z0-9]", "");
        
        // Truncate to max length
        if (sanitized.length() > MAX_CLIENT_ID_LENGTH) {
            sanitized = sanitized.substring(0, MAX_CLIENT_ID_LENGTH);
        }
        
        return sanitized.isEmpty() ? null : sanitized;
    }

    /**
     * HTML-escapes a string for safe display in HTML context.
     * 
     * @param text the raw text
     * @return HTML-escaped text
     */
    public static String htmlEscape(String text) {
        if (text == null) {
            return null;
        }
        
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}
