package de.evilradio.core.radio;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.labymod.api.client.Minecraft;

/**
 * Gate für Audio-Stream-Debug-Logs (nur erlaubte Laby-UUIDs + Setting).
 * Schreibt in {@code logs/evilradio/audio-debug.log} im Minecraft-Verzeichnis.
 */
public final class AudioStreamDebug {

  public static final String LOG_SUBDIR = "evilradio";
  public static final String FILE_NAME = "audio-debug.log";

  private static final Set<String> ALLOWED_UUIDS = Set.of(
      "308893af-77af-4706-ac8a-1c4830038108",
      "966b5d5e-2577-4ab7-987a-89bfa59da74a"
  );

  private static final DateTimeFormatter TIME =
      DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

  private static final AtomicBoolean ENABLED = new AtomicBoolean(false);
  private static final AtomicLong LAST_WAIT_LOG_MS = new AtomicLong(0L);

  private static final Object LOCK = new Object();
  private static volatile Path logFile;
  private static BufferedWriter writer;

  private AudioStreamDebug() {
  }

  public static boolean isUuidAllowed(UUID uuid) {
    return uuid != null && ALLOWED_UUIDS.contains(uuid.toString());
  }

  public static boolean isEnabled() {
    return ENABLED.get();
  }

  public static Path logFile() {
    return logFile;
  }

  /**
   * Aktiviert/deaktiviert das Datei-Logging. Bei Aktivierung wird an die Logdatei angehängt.
   *
   * @return absoluter Pfad der Logdatei, oder {@code null} wenn deaktiviert / nicht ermittelbar
   */
  public static Path setEnabled(boolean enabled, Minecraft minecraft) {
    synchronized (LOCK) {
      if (!enabled) {
        ENABLED.set(false);
        closeWriterUnlocked();
        return logFile;
      }

      Path target = resolveLogFile(minecraft);
      logFile = target;
      if (target == null) {
        ENABLED.set(false);
        return null;
      }

      try {
        Path parent = target.getParent();
        if (parent != null) {
          Files.createDirectories(parent);
        }
        closeWriterUnlocked();
        writer = Files.newBufferedWriter(
            target,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.APPEND
        );
        ENABLED.set(true);
        writeUnlocked("INFO", "=== Audio-Stream-Debug gestartet ("
            + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            + ") ===");
        writeUnlocked("INFO", "Logdatei: " + target.toAbsolutePath());
        return target;
      } catch (IOException e) {
        closeWriterUnlocked();
        ENABLED.set(false);
        return null;
      }
    }
  }

  public static void info(String message) {
    write("INFO", message);
  }

  public static void warn(String message) {
    write("WARN", message);
  }

  public static void error(String message, Throwable throwable) {
    if (!ENABLED.get()) {
      return;
    }
    StringBuilder sb = new StringBuilder(message == null ? "" : message);
    if (throwable != null) {
      sb.append('\n');
      StringWriter sw = new StringWriter();
      throwable.printStackTrace(new PrintWriter(sw));
      sb.append(sw);
    }
    write("ERROR", sb.toString().trim());
  }

  /** Rate-limited Wait-/Buffer-Logs (hot path), max. 1× pro Sekunde. */
  public static void infoThrottled(String message) {
    if (!ENABLED.get()) {
      return;
    }
    long now = System.currentTimeMillis();
    long last = LAST_WAIT_LOG_MS.get();
    if (now - last < 1000L) {
      return;
    }
    if (LAST_WAIT_LOG_MS.compareAndSet(last, now)) {
      write("INFO", message);
    }
  }

  private static void write(String level, String message) {
    if (!ENABLED.get()) {
      return;
    }
    synchronized (LOCK) {
      if (!ENABLED.get() || writer == null) {
        return;
      }
      writeUnlocked(level, message);
    }
  }

  private static void writeUnlocked(String level, String message) {
    try {
      writer.write(TIME.format(LocalDateTime.now()));
      writer.write(" [");
      writer.write(level);
      writer.write("] ");
      writer.write(message == null ? "" : message);
      writer.newLine();
      writer.flush();
    } catch (IOException ignored) {
      closeWriterUnlocked();
      ENABLED.set(false);
    }
  }

  private static void closeWriterUnlocked() {
    if (writer == null) {
      return;
    }
    try {
      writer.flush();
    } catch (IOException ignored) {
    }
    try {
      writer.close();
    } catch (IOException ignored) {
    }
    writer = null;
  }

  private static Path resolveLogFile(Minecraft minecraft) {
    File gameDir = resolveGameDirectory(minecraft);
    if (gameDir != null) {
      return new File(gameDir, "logs" + File.separator + LOG_SUBDIR + File.separator + FILE_NAME)
          .toPath();
    }
    return Path.of("logs", LOG_SUBDIR, FILE_NAME);
  }

  private static File resolveGameDirectory(Minecraft minecraft) {
    if (minecraft == null) {
      return null;
    }
    try {
      Object gameDirectory = minecraft.getClass().getField("gameDirectory").get(minecraft);
      if (gameDirectory instanceof File directory) {
        return directory;
      }
    } catch (ReflectiveOperationException ignored) {
    }
    return null;
  }
}
