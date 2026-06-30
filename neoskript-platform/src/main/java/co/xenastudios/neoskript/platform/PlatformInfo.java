package co.xenastudios.neoskript.platform;

import org.bukkit.Bukkit;

/**
 * Describes the server NeoSkript is running on, including whether it is a Folia
 * (regionized-multithreading) server.
 */
public final class PlatformInfo {

    private static final String FOLIA_MARKER = "io.papermc.paper.threadedregions.RegionizedServer";

    private final boolean folia;
    private final String serverName;
    private final String serverVersion;

    private PlatformInfo(boolean folia, String serverName, String serverVersion) {
        this.folia = folia;
        this.serverName = serverName;
        this.serverVersion = serverVersion;
    }

    /**
     * Detects the current platform from the running server.
     *
     * @return a populated {@link PlatformInfo}
     */
    public static PlatformInfo detect() {
        return new PlatformInfo(classPresent(FOLIA_MARKER), Bukkit.getName(), Bukkit.getVersion());
    }

    /**
     * @return {@code true} if running on Folia
     */
    public boolean isFolia() {
        return folia;
    }

    /**
     * @return a short, human-readable platform description for logs
     */
    public String describe() {
        return serverName + " " + serverVersion + (folia ? " [Folia]" : "");
    }

    private static boolean classPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
