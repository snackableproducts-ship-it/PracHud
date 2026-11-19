package com.practicehud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.util.Locale;

/**
 * Competitive Safe Mode.
 *
 * While active, every module registered as "restricted" (anything that modifies input, sends
 * extra packets, changes the camera or is otherwise disallowed on competitive servers) reports
 * itself as disabled. Purely visual HUDs keep working. Safe Mode is on when the user switches it
 * on, or automatically when connected to a server from {@link Config#safeServers}.
 */
public final class Safe {
    private static boolean active;
    private static boolean serverMatch;

    private Safe() {}

    /** True while restricted modules are being held off. Cheap: read from a per-tick cache. */
    public static boolean active() { return active; }

    /** True when the current server matched the auto-detect list. */
    public static boolean fromServer() { return serverMatch; }

    public static void update(MinecraftClient mc) {
        Config c = Config.get();
        boolean match = false;
        if (c.autoSafeMode && mc != null) {
            ServerInfo info = mc.getCurrentServerEntry();
            if (info != null && info.address != null) {
                String address = info.address.toLowerCase(Locale.ROOT);
                for (String s : c.safeServers) {
                    if (s != null && !s.isBlank() && address.contains(s.toLowerCase(Locale.ROOT))) {
                        match = true;
                        break;
                    }
                }
            }
        }
        serverMatch = match;
        active = c.safeMode || match;
    }
}
