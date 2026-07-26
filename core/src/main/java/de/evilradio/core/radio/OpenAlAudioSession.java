package de.evilradio.core.radio;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * OpenAL-Ausgabe über den Minecraft-/LWJGL-Audio-Kontext.
 *
 * <p><b>Guideline exception (Reflection):</b> LWJGL-OpenAL ({@code org.lwjgl.openal.AL10}/
 * {@code ALC10}) ist in LabyMod-Addons nicht als stabile Compile-Dependency über alle
 * Minecraft-Versionen verfügbar. Direkte Imports würden den Multi-Version-Build brechen.
 * Deshalb werden die OpenAL-APIs zur Laufzeit per Reflection gebunden, sofern die Klassen
 * im Client-Classpath vorhanden sind. Mixin/AccessWidener sind hier nicht geeignet, weil
 * keine Minecraft-/LabyMod-Klassen erweitert werden, sondern optionale LWJGL-APIs.
 *
 * <p>Ohne diese Ausnahme wäre eine versionsspezifische Hard-Dependency auf LWJGL nötig,
 * die das Addon für ältere bzw. abweichende Game-Runner-Varianten unbrauchbar machen würde.
 */
public final class OpenAlAudioSession {

  private static final boolean AVAILABLE = probeAvailability();
  /** Mehr Buffer = mehr Toleranz gegen Netzwerk-/GC-Hiccups (vorher nur 3 ≈ ~70 ms). */
  private static final int BUFFER_COUNT = 12;
  /** Erst starten, wenn genug PCM vorgehalten ist – verhindert sofortigen Underrun. */
  private static final int PREBUFFER_COUNT = 4;

  private final long sharedContext;
  private final boolean ownsContext;
  private final long device;
  private final int source;
  private final int[] buffers;
  private final int alFormatMono16;
  private final int alFormatStereo16;
  private final int alBuffersProcessed;
  private final int alBuffersQueued;
  private final int alSourceState;
  private final int alPlaying;
  private final int alGain;
  private final Method alGetSourcei;
  private final Method alSourceUnqueueBuffers;
  private final Method alBufferData;
  private final Method alSourceQueueBuffers;
  private final Method alSourcePlay;
  private final Method alSourceStop;
  private final Method alSourcef;
  private final Method alDeleteSources;
  private final Method alDeleteBuffers;
  private final Method alcDestroyContext;
  private final Method alcCloseDevice;
  private final Method alcMakeContextCurrent;

  private int channels = 2;
  private int sampleRate = 44100;
  private float volume = 0.25f;
  private boolean started;
  private final Queue<Integer> freeBuffers = new ArrayDeque<>();
  private long lastHeartbeatMs;

  private OpenAlAudioSession(
      long sharedContext,
      boolean ownsContext,
      long device,
      int source,
      int[] buffers,
      int alFormatMono16,
      int alFormatStereo16,
      int alBuffersProcessed,
      int alBuffersQueued,
      int alSourceState,
      int alPlaying,
      int alGain,
      Method alGetSourcei,
      Method alSourceUnqueueBuffers,
      Method alBufferData,
      Method alSourceQueueBuffers,
      Method alSourcePlay,
      Method alSourceStop,
      Method alSourcef,
      Method alDeleteSources,
      Method alDeleteBuffers,
      Method alcDestroyContext,
      Method alcCloseDevice,
      Method alcMakeContextCurrent
  ) {
    this.sharedContext = sharedContext;
    this.ownsContext = ownsContext;
    this.device = device;
    this.source = source;
    this.buffers = buffers;
    this.alFormatMono16 = alFormatMono16;
    this.alFormatStereo16 = alFormatStereo16;
    this.alBuffersProcessed = alBuffersProcessed;
    this.alBuffersQueued = alBuffersQueued;
    this.alSourceState = alSourceState;
    this.alPlaying = alPlaying;
    this.alGain = alGain;
    this.alGetSourcei = alGetSourcei;
    this.alSourceUnqueueBuffers = alSourceUnqueueBuffers;
    this.alBufferData = alBufferData;
    this.alSourceQueueBuffers = alSourceQueueBuffers;
    this.alSourcePlay = alSourcePlay;
    this.alSourceStop = alSourceStop;
    this.alSourcef = alSourcef;
    this.alDeleteSources = alDeleteSources;
    this.alDeleteBuffers = alDeleteBuffers;
    this.alcDestroyContext = alcDestroyContext;
    this.alcCloseDevice = alcCloseDevice;
    this.alcMakeContextCurrent = alcMakeContextCurrent;
  }

  public static boolean isAvailable() {
    return AVAILABLE;
  }

  public static long getCurrentContext() throws Exception {
    Class<?> alc10 = Class.forName("org.lwjgl.openal.ALC10");
    Method alcGetCurrentContext = alc10.getMethod("alcGetCurrentContext");
    return (long) alcGetCurrentContext.invoke(null);
  }

  public static OpenAlAudioSession openShared(long minecraftContext, float volume) throws Exception {
    if (!AVAILABLE) {
      throw new IllegalStateException("LWJGL OpenAL ist nicht verfügbar");
    }
    if (minecraftContext == 0L) {
      throw new Exception("Minecraft-OpenAL-Kontext nicht verfügbar");
    }

    OpenAlBindings bindings = OpenAlBindings.load();
    long previous = safeCurrentContext();
    if (!(boolean) bindings.alcMakeContextCurrent.invoke(null, minecraftContext)) {
      throw new Exception("Minecraft-OpenAL-Kontext konnte nicht aktiviert werden");
    }

    try {
      int source = (int) bindings.alGenSources.invoke(null);
      int[] buffers = new int[BUFFER_COUNT];
      for (int i = 0; i < buffers.length; i++) {
        buffers[i] = (int) bindings.alGenBuffers.invoke(null);
      }

      OpenAlAudioSession session = newSession(minecraftContext, false, 0L, source, buffers, bindings);
      enqueueFreeBuffers(session, buffers);
      session.setVolumeUnlocked(volume);
      AudioStreamDebug.info(
          "OpenAL shared context geöffnet (context=" + minecraftContext
              + ", source=" + source + ", buffers=" + BUFFER_COUNT
              + ", volume=" + volume + ")");
      return session;
    } finally {
      restoreContext(bindings.alcMakeContextCurrent, previous, minecraftContext);
    }
  }

  public static OpenAlAudioSession openDevice(String deviceName, float volume) throws Exception {
    if (!AVAILABLE) {
      throw new IllegalStateException("LWJGL OpenAL ist nicht verfügbar");
    }

    OpenAlBindings bindings = OpenAlBindings.load();
    Class<?> alc10 = Class.forName("org.lwjgl.openal.ALC10");
    Method alcOpenDevice = alc10.getMethod("alcOpenDevice", CharSequence.class);
    Method alcCreateContext = alc10.getMethod("alcCreateContext", long.class, int[].class);

    String openDeviceName = normalizeOpenAlDeviceName(deviceName);
    long device = (long) alcOpenDevice.invoke(null, (Object) openDeviceName);
    if (device == 0L) {
      throw new Exception("OpenAL-Gerät konnte nicht geöffnet werden: " + openDeviceName);
    }

    long context = (long) alcCreateContext.invoke(null, device, (Object) null);
    if (context == 0L) {
      bindings.alcCloseDevice.invoke(null, device);
      throw new Exception("OpenAL-Kontext konnte nicht erstellt werden");
    }

    long previous = safeCurrentContext();
    if (!(boolean) bindings.alcMakeContextCurrent.invoke(null, context)) {
      bindings.alcDestroyContext.invoke(null, context);
      bindings.alcCloseDevice.invoke(null, device);
      throw new Exception("OpenAL-Kontext konnte nicht aktiviert werden");
    }

    try {
      int source = (int) bindings.alGenSources.invoke(null);
      int[] buffers = new int[BUFFER_COUNT];
      for (int i = 0; i < buffers.length; i++) {
        buffers[i] = (int) bindings.alGenBuffers.invoke(null);
      }

      OpenAlAudioSession session = newSession(context, true, device, source, buffers, bindings);
      enqueueFreeBuffers(session, buffers);
      session.setVolumeUnlocked(volume);
      AudioStreamDebug.info(
          "OpenAL Gerät geöffnet (deviceName=" + (openDeviceName == null ? "default" : openDeviceName)
              + ", device=" + device + ", context=" + context
              + ", source=" + source + ", buffers=" + BUFFER_COUNT
              + ", volume=" + volume + ")");
      return session;
    } finally {
      restoreContext(bindings.alcMakeContextCurrent, previous, context);
    }
  }

  private static OpenAlAudioSession newSession(
      long context,
      boolean ownsContext,
      long device,
      int source,
      int[] buffers,
      OpenAlBindings bindings
  ) {
    return new OpenAlAudioSession(
        context,
        ownsContext,
        device,
        source,
        buffers,
        bindings.alFormatMono16,
        bindings.alFormatStereo16,
        bindings.alBuffersProcessed,
        bindings.alBuffersQueued,
        bindings.alSourceState,
        bindings.alPlaying,
        bindings.alGain,
        bindings.alGetSourcei,
        bindings.alSourceUnqueueBuffers,
        bindings.alBufferData,
        bindings.alSourceQueueBuffers,
        bindings.alSourcePlay,
        bindings.alSourceStop,
        bindings.alSourcef,
        bindings.alDeleteSources,
        bindings.alDeleteBuffers,
        bindings.alcDestroyContext,
        bindings.alcCloseDevice,
        bindings.alcMakeContextCurrent
    );
  }

  public void configureFormat(int sampleRate, int channels) {
    this.sampleRate = sampleRate;
    this.channels = channels;
  }

  public void queuePcm(byte[] pcmData, int length) throws Exception {
    if (length <= 0) {
      return;
    }

    // Lautstärke nur über AL_GAIN – PCM unverändert lassen (kein Quantisierungs-Kratzen).
    short[] samples = bytesToShorts(pcmData, length);
    int format = channels == 1 ? alFormatMono16 : alFormatStereo16;

    int buffer = waitForFreeBuffer();
    withContext(() -> {
      alBufferData.invoke(null, buffer, format, samples, sampleRate);
      alSourceQueueBuffers.invoke(null, source, buffer);
      maybeHeartbeatUnlocked();
    });
  }

  private void maybeHeartbeatUnlocked() throws Exception {
    if (!AudioStreamDebug.isEnabled()) {
      return;
    }
    long now = System.currentTimeMillis();
    if (lastHeartbeatMs != 0L && now - lastHeartbeatMs < 5000L) {
      return;
    }
    lastHeartbeatMs = now;
    int queued = (int) alGetSourcei.invoke(null, source, alBuffersQueued);
    int state = (int) alGetSourcei.invoke(null, source, alSourceState);
    AudioStreamDebug.info(
        "Heartbeat queued=" + queued + "/" + BUFFER_COUNT
            + ", free=" + freeBuffers.size()
            + ", state=" + stateName(state)
            + ", started=" + started
            + ", volume=" + volume
            + ", gain=" + volume);
  }

  private void makeContextCurrent() throws Exception {
    if (sharedContext != 0L) {
      alcMakeContextCurrent.invoke(null, sharedContext);
    }
  }

  /**
   * Führt OpenAL-Aufrufe im Radio-Kontext aus und stellt danach den vorherigen Kontext wieder her.
   * Ohne Restore stehlen wir Minecraft den Current-Context → MC-Sounds/Musik landen auf unserem
   * Gerät (auch bei Radio-Lautstärke 0).
   */
  private void withContext(AlRunnable action) throws Exception {
    long previous = safeCurrentContext();
    makeContextCurrent();
    try {
      action.run();
    } finally {
      restoreContext(alcMakeContextCurrent, previous, this.sharedContext);
    }
  }

  private <T> T withContext(AlCallable<T> action) throws Exception {
    long previous = safeCurrentContext();
    makeContextCurrent();
    try {
      return action.call();
    } finally {
      restoreContext(alcMakeContextCurrent, previous, this.sharedContext);
    }
  }

  @FunctionalInterface
  private interface AlRunnable {
    void run() throws Exception;
  }

  @FunctionalInterface
  private interface AlCallable<T> {
    T call() throws Exception;
  }

  private static long safeCurrentContext() {
    try {
      return getCurrentContext();
    } catch (Exception ignored) {
      return 0L;
    }
  }

  private static void restoreContext(Method alcMakeContextCurrent, long previous, long radioContext)
      throws Exception {
    if (previous != 0L && previous != radioContext) {
      alcMakeContextCurrent.invoke(null, previous);
    }
  }

  private void reclaimProcessedBuffers() throws Exception {
    int processed = (int) alGetSourcei.invoke(null, source, alBuffersProcessed);
    for (int i = 0; i < processed; i++) {
      freeBuffers.add((int) alSourceUnqueueBuffers.invoke(null, source));
    }
  }

  private int waitForFreeBuffer() throws Exception {
    long waitStartedAt = 0L;
    int[] lastSnap = new int[] {-1, -1}; // queued, state
    while (true) {
      Integer buffer = withContext(() -> {
        reclaimProcessedBuffers();
        if (!freeBuffers.isEmpty()) {
          return freeBuffers.poll();
        }
        int queued = (int) alGetSourcei.invoke(null, source, alBuffersQueued);
        int state = (int) alGetSourcei.invoke(null, source, alSourceState);
        lastSnap[0] = queued;
        lastSnap[1] = state;
        ensurePlayingUnlocked();
        // Nur anomal: Source tot oder Queue fast leer (echter Underrun-Druck)
        if (state != alPlaying || queued <= 2) {
          AudioStreamDebug.infoThrottled(
              "OpenAL Buffer-Mangel queued=" + queued
                  + ", free=0, state=" + stateName(state)
                  + ", started=" + started);
        }
        return null;
      });
      if (buffer != null) {
        if (waitStartedAt != 0L) {
          long waitedMs = System.currentTimeMillis() - waitStartedAt;
          // Normales Pacing bei voller Queue ≈ 20–50 ms – kein Problem.
          if (waitedMs >= 100L || (lastSnap[1] != -1 && lastSnap[1] != alPlaying)) {
            AudioStreamDebug.warn(
                "OpenAL Buffer-Wait " + waitedMs + " ms (queued=" + lastSnap[0]
                    + ", state=" + stateName(lastSnap[1]) + ")");
          }
        }
        return buffer;
      }
      if (waitStartedAt == 0L) {
        waitStartedAt = System.currentTimeMillis();
      }
      // Schlaf außerhalb des Radio-Kontexts, damit Minecraft weiter audion kann.
      // 15 ms statt 5 ms → deutlich weniger Context-Switches (weniger Kratzen).
      Thread.sleep(15L);
    }
  }

  public void setVolume(float volume) throws Exception {
    this.volume = Math.max(0.0f, Math.min(1.0f, volume));
    withContext(() -> setVolumeUnlocked(this.volume));
  }

  private void setVolumeUnlocked(float volume) throws Exception {
    // Erwartet bereits den gemappten Ausgangs-Gain aus RadioPlayer.toOutputGain().
    this.volume = Math.max(0.0f, Math.min(1.0f, volume));
    alSourcef.invoke(null, source, alGain, this.volume);
    if (this.volume > 0.0f) {
      ensurePlayingUnlocked();
    }
  }

  /**
   * Startet die Source nach Prebuffer bzw. nach OpenAL-Underrun erneut.
   * OpenAL setzt die Source bei leerer Queue auf {@code AL_STOPPED}; ohne Restart bleibt es still,
   * obwohl weiter PCM gequeued und das Spektrum gefüttert wird.
   */
  public void ensurePlaying() throws Exception {
    withContext(this::ensurePlayingUnlocked);
  }

  private void ensurePlayingUnlocked() throws Exception {
    int queued = (int) alGetSourcei.invoke(null, source, alBuffersQueued);
    if (queued <= 0) {
      return;
    }

    // Beim ersten Start etwas vorhalten, damit ein kurzer Hiccup nicht sofort stoppt
    if (!started && queued < PREBUFFER_COUNT) {
      return;
    }

    int state = (int) alGetSourcei.invoke(null, source, alSourceState);
    if (state != alPlaying) {
      if (started) {
        AudioStreamDebug.warn(
            "OpenAL UNDERRUN Restart: state=" + stateName(state)
                + " → PLAY, queued=" + queued + ", free=" + freeBuffers.size());
      } else {
        AudioStreamDebug.info(
            "OpenAL Start: state=" + stateName(state)
                + " → PLAY, queued=" + queued + ", free=" + freeBuffers.size());
      }
      alSourcePlay.invoke(null, source);
      started = true;
    }
  }

  public void startIfNeeded() throws Exception {
    ensurePlaying();
  }

  public void close() {
    AudioStreamDebug.info(
        "OpenAL Session schließen (ownsContext=" + ownsContext
            + ", device=" + device + ", source=" + source + ")");
    long previous = safeCurrentContext();

    try {
      makeContextCurrent();
    } catch (Exception ignored) {
    }

    try {
      alSourceStop.invoke(null, source);
    } catch (Exception ignored) {
    }

    try {
      alDeleteSources.invoke(null, source);
    } catch (Exception ignored) {
    }

    for (int buffer : buffers) {
      try {
        alDeleteBuffers.invoke(null, buffer);
      } catch (Exception ignored) {
      }
    }

    if (ownsContext) {
      // Minecraft-Kontext wiederherstellen, falls wir ihn vorher verdrängt haben
      if (previous != 0L && previous != this.sharedContext) {
        try {
          alcMakeContextCurrent.invoke(null, previous);
        } catch (Exception ignored) {
        }
      } else {
        try {
          alcMakeContextCurrent.invoke(null, 0L);
        } catch (Exception ignored) {
        }
      }

      try {
        alcDestroyContext.invoke(null, sharedContext);
      } catch (Exception ignored) {
      }

      if (device != 0L) {
        try {
          alcCloseDevice.invoke(null, device);
        } catch (Exception ignored) {
        }
      }
    } else if (previous != 0L && previous != this.sharedContext) {
      try {
        alcMakeContextCurrent.invoke(null, previous);
      } catch (Exception ignored) {
      }
    }
  }

  private static String normalizeOpenAlDeviceName(String deviceName) {
    if (deviceName == null || deviceName.isBlank()) {
      return null;
    }

    String trimmed = deviceName.trim();
    if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
      trimmed = trimmed.substring(1, trimmed.length() - 1);
    }

    String lower = trimmed.toLowerCase();
    if (lower.equals("default") || lower.equals("system default")) {
      return null;
    }

    return trimmed;
  }

  private String stateName(int state) {
    if (state == alPlaying) {
      return "PLAYING";
    }
    // AL_INITIAL=4113, AL_STOPPED=4116, AL_PAUSED=4115 – Zahlen mitloggen falls unbekannt
    return switch (state) {
      case 4113 -> "INITIAL";
      case 4115 -> "PAUSED";
      case 4116 -> "STOPPED";
      default -> String.valueOf(state);
    };
  }

  private static void enqueueFreeBuffers(OpenAlAudioSession session, int[] buffers) {
    for (int buffer : buffers) {
      session.freeBuffers.add(buffer);
    }
  }

  private static short[] bytesToShorts(byte[] bytes, int length) {
    int sampleCount = length / 2;
    short[] samples = new short[sampleCount];
    ByteBuffer.wrap(bytes, 0, length).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples);
    return samples;
  }

  private static int getIntConstant(Class<?> clazz, String name) throws Exception {
    Field field = clazz.getField(name);
    return field.getInt(null);
  }

  private static boolean probeAvailability() {
    try {
      Class.forName("org.lwjgl.openal.ALC10");
      Class.forName("org.lwjgl.openal.AL10");
      return true;
    } catch (ClassNotFoundException ignored) {
      return false;
    }
  }

  private static final class OpenAlBindings {
    private final int alFormatMono16;
    private final int alFormatStereo16;
    private final int alBuffersProcessed;
    private final int alBuffersQueued;
    private final int alSourceState;
    private final int alPlaying;
    private final int alGain;
    private final Method alGenSources;
    private final Method alGenBuffers;
    private final Method alGetSourcei;
    private final Method alSourceUnqueueBuffers;
    private final Method alBufferData;
    private final Method alSourceQueueBuffers;
    private final Method alSourcePlay;
    private final Method alSourceStop;
    private final Method alSourcef;
    private final Method alDeleteSources;
    private final Method alDeleteBuffers;
    private final Method alcDestroyContext;
    private final Method alcCloseDevice;
    private final Method alcMakeContextCurrent;

    private OpenAlBindings(
        int alFormatMono16,
        int alFormatStereo16,
        int alBuffersProcessed,
        int alBuffersQueued,
        int alSourceState,
        int alPlaying,
        int alGain,
        Method alGenSources,
        Method alGenBuffers,
        Method alGetSourcei,
        Method alSourceUnqueueBuffers,
        Method alBufferData,
        Method alSourceQueueBuffers,
        Method alSourcePlay,
        Method alSourceStop,
        Method alSourcef,
        Method alDeleteSources,
        Method alDeleteBuffers,
        Method alcDestroyContext,
        Method alcCloseDevice,
        Method alcMakeContextCurrent
    ) {
      this.alFormatMono16 = alFormatMono16;
      this.alFormatStereo16 = alFormatStereo16;
      this.alBuffersProcessed = alBuffersProcessed;
      this.alBuffersQueued = alBuffersQueued;
      this.alSourceState = alSourceState;
      this.alPlaying = alPlaying;
      this.alGain = alGain;
      this.alGenSources = alGenSources;
      this.alGenBuffers = alGenBuffers;
      this.alGetSourcei = alGetSourcei;
      this.alSourceUnqueueBuffers = alSourceUnqueueBuffers;
      this.alBufferData = alBufferData;
      this.alSourceQueueBuffers = alSourceQueueBuffers;
      this.alSourcePlay = alSourcePlay;
      this.alSourceStop = alSourceStop;
      this.alSourcef = alSourcef;
      this.alDeleteSources = alDeleteSources;
      this.alDeleteBuffers = alDeleteBuffers;
      this.alcDestroyContext = alcDestroyContext;
      this.alcCloseDevice = alcCloseDevice;
      this.alcMakeContextCurrent = alcMakeContextCurrent;
    }

    private static OpenAlBindings load() throws Exception {
      Class<?> alc10 = Class.forName("org.lwjgl.openal.ALC10");
      Class<?> al10 = Class.forName("org.lwjgl.openal.AL10");

      return new OpenAlBindings(
          getIntConstant(al10, "AL_FORMAT_MONO16"),
          getIntConstant(al10, "AL_FORMAT_STEREO16"),
          getIntConstant(al10, "AL_BUFFERS_PROCESSED"),
          getIntConstant(al10, "AL_BUFFERS_QUEUED"),
          getIntConstant(al10, "AL_SOURCE_STATE"),
          getIntConstant(al10, "AL_PLAYING"),
          getIntConstant(al10, "AL_GAIN"),
          al10.getMethod("alGenSources"),
          al10.getMethod("alGenBuffers"),
          al10.getMethod("alGetSourcei", int.class, int.class),
          al10.getMethod("alSourceUnqueueBuffers", int.class),
          al10.getMethod("alBufferData", int.class, int.class, short[].class, int.class),
          al10.getMethod("alSourceQueueBuffers", int.class, int.class),
          al10.getMethod("alSourcePlay", int.class),
          al10.getMethod("alSourceStop", int.class),
          al10.getMethod("alSourcef", int.class, int.class, float.class),
          al10.getMethod("alDeleteSources", int.class),
          al10.getMethod("alDeleteBuffers", int.class),
          alc10.getMethod("alcDestroyContext", long.class),
          alc10.getMethod("alcCloseDevice", long.class),
          alc10.getMethod("alcMakeContextCurrent", long.class)
      );
    }
  }
}
