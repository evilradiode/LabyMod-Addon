package de.evilradio.core.radio;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import net.labymod.api.util.logging.Logging;

public class RadioPlayer {

  private static final Logging LOGGING = Logging.create("EvilRadio-RadioPlayer");
  private static final int NETWORK_BUFFER_BYTES = 64 * 1024;
  private static final int CONNECT_TIMEOUT_MS = 10_000;
  private static final int READ_TIMEOUT_MS = 15_000;
  private static final int MAX_RECONNECT_ATTEMPTS = 40;
  /**
   * Radio-Streams sind oft heiß gemastert – linearer Gain macht nur die unteren
   * paar Prozent des Sliders brauchbar. Max-Gain dämpft die Spitze, Exponent
   * spreizt den leisen Bereich über die Skala (50 % ≈ angenehm, 100 % laut).
   */
  private static final float VOLUME_MAX_GAIN = 0.25f;
  private static final float VOLUME_CURVE_EXPONENT = 2.2f;

  private SourceDataLine audioLine;
  private OpenAlAudioSession openAlSession;
  private InputStream audioStream;
  private volatile Bitstream bitstream;
  private ExecutorService executorService;
  private Future<?> playbackTask;
  private volatile boolean isPlaying;
  private volatile boolean shouldStop;
  /** Wird bei jedem stop()/play() erhöht – alte Threads dürfen nicht weiterlaufen/cleanupen. */
  private final AtomicLong playbackEpoch = new AtomicLong(0L);
  private volatile float volume = 0.5f;
  private volatile String currentStreamUrl;
  private volatile String outputDeviceName;
  private volatile LongSupplier sharedContextSupplier;
  private final AudioSpectrumAnalyzer spectrum = new AudioSpectrumAnalyzer();
  private long lastWriteDoneMs;

  public RadioPlayer() {
    this.executorService = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "RadioPlayer-Thread");
      t.setDaemon(true);
      return t;
    });
    this.isPlaying = false;
    this.shouldStop = false;
  }

  public void play(String streamUrl) {
    if (isPlaying && streamUrl != null && streamUrl.equals(currentStreamUrl)) {
      return;
    }

    // Epoch zuerst invalidieren, dann stoppen – Zombie-Reconnects sterben ab
    long epoch = this.playbackEpoch.incrementAndGet();
    this.shouldStop = true;
    this.isPlaying = false;
    closeNetworkStream();
    if (playbackTask != null && !playbackTask.isDone()) {
      playbackTask.cancel(true);
      executorService.shutdownNow();
      executorService = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "RadioPlayer-Thread");
        t.setDaemon(true);
        return t;
      });
    }
    cleanupAudioOutput();

    currentStreamUrl = streamUrl;
    shouldStop = false;
    this.lastWriteDoneMs = 0L;

    playbackTask = executorService.submit(() -> runPlayback(streamUrl, epoch));
    // Sofort true, damit UI (Play/Pause) nicht auf HTTP-Connect / ersten Frame wartet.
    this.isPlaying = true;
  }

  private void runPlayback(String streamUrl, long epoch) {
    Decoder decoder = null;
    Header header = null;
    try {
      if (!isPlaybackActive(epoch)) {
        return;
      }

      URL url = URI.create(streamUrl).toURL();
      openNetworkStream(url);
      decoder = new Decoder();

      if (!isPlaybackActive(epoch) || bitstream == null) {
        return;
      }

      header = bitstream.readFrame();
      if (header == null) {
        throw new Exception("Kein gültiger MP3-Stream gefunden");
      }

      int sampleRate = header.frequency();
      int channels = header.mode() == Header.SINGLE_CHANNEL ? 1 : 2;
      this.spectrum.configure(sampleRate, channels);
      this.spectrum.reset();

      AudioFormat audioFormat = new AudioFormat(
          AudioFormat.Encoding.PCM_SIGNED,
          sampleRate,
          16,
          channels,
          channels * 2,
          sampleRate,
          false
      );

      // SourceDataLine zuerst: komplett getrennt von Minecraft-OpenAL.
      boolean openAlPlayback = false;
      if (!openSourceDataLine(audioFormat)) {
        if (OpenAlAudioSession.isAvailable()) {
          try {
            openAlSession = OpenAlAudioSession.openDevice(outputDeviceName, toOutputGain(volume));
            openAlSession.configureFormat(sampleRate, channels);
            openAlPlayback = true;
            AudioStreamDebug.info(
                "Playback via OpenAL Gerät (sampleRate=" + sampleRate
                    + ", channels=" + channels + ", device=" + outputDeviceName + ")");
          } catch (Exception openDeviceException) {
            AudioStreamDebug.warn(
                "OpenAL Gerät fehlgeschlagen, versuche Shared: "
                    + openDeviceException.getMessage());
          }

          if (!openAlPlayback) {
            long sharedOpenAlContext =
                sharedContextSupplier != null ? sharedContextSupplier.getAsLong() : 0L;
            if (sharedOpenAlContext != 0L) {
              try {
                openAlSession = OpenAlAudioSession.openShared(
                    sharedOpenAlContext, toOutputGain(volume));
                openAlSession.configureFormat(sampleRate, channels);
                openAlPlayback = true;
                AudioStreamDebug.info(
                    "Playback via OpenAL Shared-Context (sampleRate=" + sampleRate
                        + ", channels=" + channels + ", context=" + sharedOpenAlContext + ")");
              } catch (Exception openSharedException) {
                AudioStreamDebug.warn(
                    "OpenAL Shared fehlgeschlagen: " + openSharedException.getMessage());
              }
            } else {
              AudioStreamDebug.warn("Kein Shared-OpenAL-Kontext verfügbar");
            }
          }
        } else {
          AudioStreamDebug.warn("LWJGL OpenAL nicht verfügbar");
        }
      }

      if (!openAlPlayback && audioLine == null) {
        throw new Exception("Kein Audio-Ausgabepfad verfügbar");
      }

      if (!isPlaybackActive(epoch)) {
        return;
      }

      isPlaying = true;
      AudioStreamDebug.info("Stream gestartet: " + streamUrl + " (epoch=" + epoch + ")");

      byte[] buffer = new byte[8192];
      int consecutiveFailures = 0;

      while (isPlaybackActive(epoch) && isOutputActive()) {
        try {
          if (bitstream == null || header == null || decoder == null) {
            throw new IOException("Stream nicht bereit");
          }

          Object decoded = decoder.decodeFrame(header, bitstream);
          if (!(decoded instanceof SampleBuffer sampleBuffer)) {
            throw new IOException("Decoder lieferte keinen SampleBuffer");
          }
          short[] samples = sampleBuffer.getBuffer();
          if (samples == null) {
            throw new IOException("SampleBuffer ohne Daten");
          }
          // getBuffer() ist Pool – nur getBufferLength() ist gültig
          int validSamples = sampleBuffer.getBufferLength();
          if (validSamples < 0) {
            validSamples = 0;
          }
          if (validSamples > samples.length) {
            validSamples = samples.length;
          }

          int sampleIndex = 0;
          for (int i = 0; i < validSamples && isPlaybackActive(epoch) && isOutputActive(); i++) {
            short sample = samples[i];
            buffer[sampleIndex++] = (byte) (sample & 0xFF);
            buffer[sampleIndex++] = (byte) ((sample >> 8) & 0xFF);

            if (sampleIndex >= buffer.length) {
              writeAudioSafe(buffer, sampleIndex);
              sampleIndex = 0;
            }
          }

          if (sampleIndex > 0 && isOutputActive()) {
            writeAudioSafe(buffer, sampleIndex);
          }

          bitstream.closeFrame();
          header = bitstream.readFrame();
          if (header == null) {
            throw new IOException("Stream-Ende / kein MP3-Header");
          }

          consecutiveFailures = 0;
        } catch (Exception streamError) {
          if (!isPlaybackActive(epoch) || Thread.currentThread().isInterrupted()) {
            Thread.currentThread().interrupt();
            break;
          }

          consecutiveFailures++;
          String detail = streamError.getMessage();
          if (detail == null || detail.isBlank()) {
            detail = String.valueOf(streamError);
          }
          AudioStreamDebug.warn(
              "Stream-Fehler (#" + consecutiveFailures + "): "
                  + streamError.getClass().getSimpleName()
                  + ": " + detail);

          if (consecutiveFailures > MAX_RECONNECT_ATTEMPTS) {
            AudioStreamDebug.error(
                "Zu viele Reconnect-Fehlschläge – Playback beendet", streamError);
            break;
          }

          long backoffMs = Math.min(5_000L, 200L * consecutiveFailures);
          try {
            Thread.sleep(backoffMs);
          } catch (InterruptedException sleepInterrupted) {
            Thread.currentThread().interrupt();
            break;
          }
          if (!isPlaybackActive(epoch)) {
            break;
          }

          try {
            // Ausgabe (SDL/OpenAL) bleibt offen – nur Netz/Decoder neu
            openNetworkStream(url);
            decoder = new Decoder();
            header = bitstream.readFrame();
            if (header == null) {
              throw new IOException("Reconnect ohne gültigen Header");
            }
            AudioStreamDebug.info(
                "Stream-Reconnect erfolgreich (Versuch " + consecutiveFailures + ")");
            consecutiveFailures = 0;
            this.lastWriteDoneMs = 0L;
          } catch (Exception reconnectError) {
            AudioStreamDebug.warn(
                "Reconnect fehlgeschlagen: " + reconnectError.getMessage());
          }
        }
      }

    } catch (Exception e) {
      if (isPlaybackActive(epoch)) {
        LOGGING.error("Fehler beim Abspielen des Radio-Streams: " + e.getMessage(), e);
        AudioStreamDebug.error("Stream-Abbruch", e);
      }
    } finally {
      AudioStreamDebug.info(
          "Playback-Loop beendet (epoch=" + epoch
              + ", active=" + isPlaybackActive(epoch)
              + ", shouldStop=" + shouldStop + ")");
      // Nur die aktuelle Session darf Output schließen – sonst killt ein Zombie den neuen Stream
      if (this.playbackEpoch.get() == epoch) {
        cleanup();
        isPlaying = false;
      } else {
        closeNetworkStream();
      }
    }
  }

  private boolean isPlaybackActive(long epoch) {
    return !shouldStop
        && this.playbackEpoch.get() == epoch
        && !Thread.currentThread().isInterrupted();
  }

  /**
   * Öffnet den HTTP(S)-Stream. {@code Icy-MetaData: 0} verhindert, dass Icecast-Metadaten
   * den MP3-Bitstream zerschneiden (häufige Ursache für Random-Abbrüche).
   */
  private void openNetworkStream(URL url) throws IOException {
    closeNetworkStream();

    URLConnection connection = url.openConnection();
    connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
    connection.setReadTimeout(READ_TIMEOUT_MS);
    connection.setRequestProperty("User-Agent", "EvilRadio-LabyMod-Addon");
    connection.setRequestProperty("Icy-MetaData", "0");
    connection.setRequestProperty("Accept", "*/*");
    connection.setRequestProperty("Connection", "close");

    if (connection instanceof HttpURLConnection http) {
      http.setInstanceFollowRedirects(true);
      http.setRequestMethod("GET");
      int code = http.getResponseCode();
      if (code >= 400) {
        throw new IOException("HTTP " + code + " für Stream " + url);
      }
    }

    audioStream = new BufferedInputStream(connection.getInputStream(), NETWORK_BUFFER_BYTES);
    bitstream = new Bitstream(audioStream);
  }

  private void closeNetworkStream() {
    try {
      if (bitstream != null) {
        bitstream.close();
      }
    } catch (Exception ignored) {
    }
    bitstream = null;

    try {
      if (audioStream != null) {
        audioStream.close();
      }
    } catch (Exception ignored) {
    }
    audioStream = null;
  }

  public void stop() {
    this.playbackEpoch.incrementAndGet();
    shouldStop = true;
    isPlaying = false;
    currentStreamUrl = null;
    this.spectrum.reset();

    // Streams schließen, damit blockierende Reads/OpenAL abbrechen
    closeNetworkStream();

    if (playbackTask != null && !playbackTask.isDone()) {
      playbackTask.cancel(true);
    }
    cleanupAudioOutput();
  }

  public void setOutputDeviceName(String outputDeviceName) {
    this.outputDeviceName = outputDeviceName;
  }

  public void setSharedContextSupplier(LongSupplier sharedContextSupplier) {
    this.sharedContextSupplier = sharedContextSupplier;
  }

  public void setVolume(float volume) {
    this.volume = Math.max(0.0f, Math.min(1.0f, volume));
    float gain = toOutputGain(this.volume);

    if (openAlSession != null) {
      try {
        openAlSession.setVolume(gain);
      } catch (Exception e) {
        LOGGING.warn("Fehler beim Setzen der OpenAL-Lautstärke: " + e.getMessage());
      }
    }

    applySourceDataLineVolume(gain);
  }

  /**
   * UI-Slider 0..1 → linearer Ausgangs-Gain für OpenAL / SourceDataLine.
   */
  static float toOutputGain(float slider) {
    if (slider <= 0.0001f) {
      return 0.0f;
    }
    float clamped = Math.min(1.0f, slider);
    return VOLUME_MAX_GAIN * (float) Math.pow(clamped, VOLUME_CURVE_EXPONENT);
  }

  private boolean openSourceDataLine(AudioFormat audioFormat) {
    try {
      DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
      if (!AudioSystem.isLineSupported(info)) {
        AudioStreamDebug.warn("SourceDataLine unterstützt Format nicht");
        return false;
      }

      audioLine = (SourceDataLine) AudioSystem.getLine(info);
      // ~0,5 s Vorhalt – weniger underrun-/Klick-Risiko als Default-Buffer
      int bufferBytes = Math.max(audioFormat.getFrameSize() * 1024 * 8, 32768);
      audioLine.open(audioFormat, bufferBytes);
      applySourceDataLineVolume(toOutputGain(this.volume));
      audioLine.start();
      AudioStreamDebug.info(
          "Playback via SourceDataLine (sampleRate=" + (int) audioFormat.getSampleRate()
              + ", channels=" + audioFormat.getChannels()
              + ", bufferBytes=" + bufferBytes + ")");
      return true;
    } catch (Exception e) {
      AudioStreamDebug.warn("SourceDataLine fehlgeschlagen: " + e.getMessage());
      if (audioLine != null) {
        try {
          audioLine.close();
        } catch (Exception ignored) {
        }
        audioLine = null;
      }
      return false;
    }
  }

  private void applySourceDataLineVolume(float linearGain) {
    if (audioLine == null || !audioLine.isOpen()) {
      return;
    }
    try {
      if (audioLine.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
        FloatControl gainControl = (FloatControl) audioLine.getControl(FloatControl.Type.MASTER_GAIN);
        // MASTER_GAIN ist in dB, nicht linear – lineares Mapping auf die Range = Stille.
        float linear = Math.max(0.0f, Math.min(1.0f, linearGain));
        float gainDb;
        if (linear <= 0.0001f) {
          gainDb = gainControl.getMinimum();
        } else {
          gainDb = (float) (20.0 * Math.log10(linear));
          gainDb = Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), gainDb));
        }
        gainControl.setValue(gainDb);
      }
    } catch (Exception e) {
      LOGGING.warn("Fehler beim Setzen der Lautstärke: " + e.getMessage());
    }
  }

  public float getVolume() {
    return volume;
  }

  public boolean isPlaying() {
    return isPlaying;
  }

  public AudioSpectrumAnalyzer spectrum() {
    return this.spectrum;
  }

  private boolean isOutputActive() {
    if (openAlSession != null) {
      return true;
    }
    return audioLine != null && audioLine.isOpen();
  }

  private void writeAudioSafe(byte[] buffer, int length) {
    // Spektrum immer aus Roh-PCM (unabhängig von der Lautstärke)
    this.spectrum.feedPcm(buffer, length);

    long now = System.currentTimeMillis();
    if (this.lastWriteDoneMs != 0L) {
      long decodeGapMs = now - this.lastWriteDoneMs;
      // Zeit zwischen Ende des letzten Writes und Start dieses Writes = Decode/Netz
      if (decodeGapMs >= 80L) {
        AudioStreamDebug.warn(
            "Decode-Gap " + decodeGapMs + " ms (erwartet <20 ms) – möglicher Aussetzer");
      }
    }

    try {
      if (openAlSession != null) {
        openAlSession.queuePcm(buffer, length);
        openAlSession.startIfNeeded();
        this.lastWriteDoneMs = System.currentTimeMillis();
        return;
      }

      if (audioLine != null && audioLine.isOpen()) {
        // Immer schreiben – Lautstärke über MASTER_GAIN (auch bei 0 = Stille, Pacing bleibt)
        audioLine.write(buffer, 0, length);
        this.lastWriteDoneMs = System.currentTimeMillis();
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void cleanup() {
    closeNetworkStream();
    cleanupAudioOutput();
  }

  private void cleanupAudioOutput() {
    if (openAlSession != null) {
      try {
        openAlSession.close();
      } catch (Exception ignored) {
      }
      openAlSession = null;
    }

    if (audioLine != null) {
      try {
        audioLine.stop();
      } catch (Exception ignored) {
      }
      try {
        audioLine.close();
      } catch (Exception ignored) {
      }
      audioLine = null;
    }
  }

  public void shutdown() {
    stop();
    if (executorService != null) {
      executorService.shutdownNow();
    }
  }
}
