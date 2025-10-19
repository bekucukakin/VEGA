package vega.vcs.shared.util;

/**
 * Color output utility for terminal
 * Provides ANSI color codes for better user experience
 * Follows Single Responsibility Principle
 */
public class ColorOutput {
    
    // ANSI Color codes for terminal output
    public static final String RESET = "\033[0m";
    public static final String RED = "\033[31m";
    public static final String GREEN = "\033[32m";
    public static final String YELLOW = "\033[33m";
    public static final String BLUE = "\033[34m";
    public static final String MAGENTA = "\033[35m";
    public static final String CYAN = "\033[36m";
    public static final String BOLD = "\033[1m";
    
    private ColorOutput() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Colors text with the specified color
     * @param text the text to color
     * @param color the color code
     * @return colored text
     */
    public static String colorize(String text, String color) {
        return color + text + RESET;
    }
    
    /**
     * Makes text bold
     * @param text the text to make bold
     * @return bold text
     */
    public static String bold(String text) {
        return BOLD + text + RESET;
    }
}
