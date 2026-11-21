package dev.freakyfriday.mind;

import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared diagnostics for explicitly requested experimental probes.
 *
 * All lines go to the server's normal latest.log via the NeoForge/SLF4J
 * logger. A stable trace ID correlates construction, ticks, errors, cleanup
 * and in-game summaries. Never log usernames, chat, inventories or private
 * player data. This does not prove absence of arbitrary third-party effects.
 */
public final class ProbeTelemetry {
    private static final Logger LOG = LoggerFactory.getLogger("FreakyFriday/Probes");

    private ProbeTelemetry() {}

    public static String newTrace() {
        return UUID.randomUUID().toString();
    }

    public static void info(String trace, String phase, String pattern, Object... values) {
        LOG.info("[FF-PROBE] trace={} phase={} {}",
            trace, phase, String.format(Locale.ROOT, pattern, values));
    }

    public static void warn(String trace, String phase, String pattern, Object... values) {
        LOG.warn("[FF-PROBE] trace={} phase={} {}",
            trace, phase, String.format(Locale.ROOT, pattern, values));
    }

    public static void error(String trace, String phase, Throwable cause, String detail) {
        LOG.error("[FF-PROBE] trace=" + trace + " phase=" + phase + " " + detail,
            cause);
    }
}
