package dev.mzcy.core.updater;

import org.jetbrains.annotations.NotNull;

/**
 * Semantic version comparator supporting the standard SemVer format:
 * {@code MAJOR.MINOR.PATCH} with optional {@code -SNAPSHOT} / {@code -beta.N} suffixes.
 *
 * <p>Suffix rules:
 * <ul>
 *   <li>No suffix         → stable release (highest precedence)</li>
 *   <li>{@code -SNAPSHOT} → pre-release (lower than stable)</li>
 *   <li>{@code -beta.N}   → pre-release (lower than stable)</li>
 *   <li>{@code -alpha.N}  → pre-release (lower than beta)</li>
 * </ul>
 *
 * <p>Leading {@code v} characters are stripped automatically
 * ({@code v1.2.3} → {@code 1.2.3}).
 */
public final class VersionComparator {

    private VersionComparator() {}

    /**
     * Compares two version strings.
     *
     * @param v1 the first version
     * @param v2 the second version
     * @return negative if v1 < v2, zero if equal, positive if v1 > v2
     */
    public static int compare(@NotNull String v1, @NotNull String v2) {
        final ParsedVersion a = parse(v1);
        final ParsedVersion b = parse(v2);
        return a.compareTo(b);
    }

    /**
     * Returns true if {@code candidate} is strictly newer than {@code current}.
     */
    public static boolean isNewer(@NotNull String candidate, @NotNull String current) {
        return compare(candidate, current) > 0;
    }

    /**
     * Returns true if {@code candidate} is strictly older than {@code current}.
     */
    public static boolean isOlder(@NotNull String candidate, @NotNull String current) {
        return compare(candidate, current) < 0;
    }

    /**
     * Returns true if both versions are semantically equal.
     */
    public static boolean isEqual(@NotNull String v1, @NotNull String v2) {
        return compare(v1, v2) == 0;
    }

    // =========================================================================
    // Internal parsing
    // =========================================================================

    private static ParsedVersion parse(@NotNull String raw) {
        // Strip leading 'v' or 'V'
        String version = raw.trim();
        if (version.startsWith("v") || version.startsWith("V")) {
            version = version.substring(1);
        }

        // Split on '-' to separate core version from pre-release suffix
        final String[] dashParts  = version.split("-", 2);
        final String   corePart   = dashParts[0];
        final String   suffixPart = dashParts.length > 1 ? dashParts[1].toLowerCase() : "";

        // Parse MAJOR.MINOR.PATCH
        final String[] numericParts = corePart.split("\\.");
        final int major = parseSafe(numericParts, 0);
        final int minor = parseSafe(numericParts, 1);
        final int patch = parseSafe(numericParts, 2);

        // Assign pre-release weight (higher = higher precedence)
        final int preReleaseWeight = resolvePreReleaseWeight(suffixPart);

        return new ParsedVersion(major, minor, patch, preReleaseWeight);
    }

    /**
     * Maps a pre-release suffix to a numeric weight.
     * Stable releases (no suffix) get the highest weight.
     */
    private static int resolvePreReleaseWeight(@NotNull String suffix) {
        if (suffix.isBlank())               return 100; // stable
        if (suffix.startsWith("rc"))        return 80;  // release candidate
        if (suffix.startsWith("beta"))      return 60;
        if (suffix.startsWith("alpha"))     return 40;
        if (suffix.equals("snapshot"))      return 20;
        return 10; // unknown pre-release suffix
    }

    private static int parseSafe(String[] parts, int index) {
        if (index >= parts.length) return 0;
        try {
            return Integer.parseInt(parts[index].replaceAll("[^0-9]", ""));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    // =========================================================================
    // Value type
    // =========================================================================

    private record ParsedVersion(
            int major,
            int minor,
            int patch,
            int preReleaseWeight
    ) implements Comparable<ParsedVersion> {

        @Override
        public int compareTo(@NotNull ParsedVersion other) {
            if (this.major != other.major) return Integer.compare(this.major, other.major);
            if (this.minor != other.minor) return Integer.compare(this.minor, other.minor);
            if (this.patch != other.patch) return Integer.compare(this.patch, other.patch);
            return Integer.compare(this.preReleaseWeight, other.preReleaseWeight);
        }
    }
}