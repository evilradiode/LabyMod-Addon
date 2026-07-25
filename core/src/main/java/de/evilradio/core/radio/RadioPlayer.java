package de.evilradio.core.radio;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.LongSupplier;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.decoder.SampleBuffer;
import net.labymod.api.util.logging.Logging;

public class RadioPlayer {

  private static final Logging LOGGING = Logging.create("EvilRadio-RadioPlayer");

  private SourceDataLine audioLine;
  private OpenAlAudioSession openAlSession;
  private InputStream audioStream;
  private volatile Bitstream bitstream;
  private ExecutorService executorService;
  private Future<?> playbackTask;
  private volatile boolean isPlaying;
  private volatile boolean shouldStop;
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

    stop();

    // Hängenden Playback-Task freimachen, sonst blockiert der Single-Thread-Executor neue Starts
    if (playbackTask != null && !playbackTask.isDone()) {
      executorService.shutdownNow();
      executorService = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "RadioPlayer-Thread");
        t.setDaemon(true);
        return t;
      });
    }

    currentStreamUrl = streamUrl;
    shouldStop = false;
    this.lastWriteDoneMs = 0L;

    playbackTask = executorService.submit(() -> {
      try {
        URI uri = URI.create(streamUrl);
        URL url = uri.toURL();
        audioStream = new BufferedInputStream(url.openStream());

        bitstream = new Bitstream(audioStream);
        Decoder decoder = new Decoder();

        if (shouldStop || bitstream == null) {
          return;
        }

        Header header = bitstream.readFrame();
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
        // Eigenes OpenAL-Gerät + alcMakeContextCurrent (prozessweit) kollidiert mit dem
        // Game-Audio-Thread → wiederkehrendes Kratzen trotz „gesunder“ Queue.
        boolean openAlPlayback = false;
        if (!openSourceDataLine(audioFormat)) {
          if (OpenAlAudioSession.isAvailable()) {
            try {
              openAlSession = OpenAlAudioSession.openDevice(outputDeviceName, volume);
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
                  openAlSession = OpenAlAudioSession.openShared(sharedOpenAlContext, volume);
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

        isPlaying = true;
        AudioStreamDebug.info("Stream gestartet: " + streamUrl);

        // ~46 ms Stereo@44.1 kHz pro Chunk; mit 12 OpenAL-Buffern ≈ 0,5 s Vorhalt
        byte[] buffer = new byte[8192];
        while (!shouldStop && isOutputActive() && bitstream != null) {
          try {
            if (bitstream == null) {
              break;
            }

            SampleBuffer sampleBuffer = (SampleBuffer) decoder.decodeFrame(header, bitstream);
            short[] samples = sampleBuffer.getBuffer();
            // Wichtig: getBuffer() ist ein fester Pool (OBUFFERSIZE) – nur getBufferLength()
            // enthält gültige Samples. Rest = Stale/Müll → Kratzen ohne OpenAL-Underrun.
            int validSamples = sampleBuffer.getBufferLength();
            if (validSamples < 0) {
              validSamples = 0;
            }
            if (validSamples > samples.length) {
              validSamples = samples.length;
            }

            int sampleIndex = 0;
            for (int i = 0; i < validSamples && !shouldStop && isOutputActive(); i++) {
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

            if (bitstream == null) {
              break;
            }
            bitstream.closeFrame();
            header = bitstream.readFrame();

            if (header == null) {
              AudioStreamDebug.warn("MP3-Header null – Stream-Reconnect");
              try {
                Thread.sleep(100);
              } catch (InterruptedException e) {
                break;
              }
              try {
                if (bitstream != null) {
                  bitstream.close();
                }
                if (audioStream != null) {
                  audioStream.close();
                }
              } catch (Exception ignored) {
              }
              if (shouldStop) {
                break;
              }
              audioStream = new BufferedInputStream(url.openStream());
              bitstream = new Bitstream(audioStream);
              if (bitstream == null) {
                break;
              }
              header = bitstream.readFrame();
              if (header == null) {
                AudioStreamDebug.warn("Reconnect fehlgeschlagen: erneut kein Header");
                break;
              }
              AudioStreamDebug.info("Stream-Reconnect nach Header-Loss erfolgreich");
            }
          } catch (JavaLayerException e) {
            if (!shouldStop) {
              AudioStreamDebug.warn("JavaLayerException – Reconnect: " + e.getMessage());
              try {
                Thread.sleep(1000);
              } catch (InterruptedException e2) {
                break;
              }
              try {
                if (bitstream != null) {
                  bitstream.close();
                }
                if (audioStream != null) {
                  audioStream.close();
                }
              } catch (Exception ignored) {
              }
              if (shouldStop) {
                break;
              }
              audioStream = new BufferedInputStream(url.openStream());
              bitstream = new Bitstream(audioStream);
              if (bitstream == null) {
                break;
              }
              header = bitstream.readFrame();
              if (header == null) {
                AudioStreamDebug.warn("Reconnect nach JavaLayerException fehlgeschlagen");
                break;
              }
              AudioStreamDebug.info("Reconnect nach JavaLayerException erfolgreich");
            }
          } catch (RuntimeException e) {
            if (!shouldStop) {
              AudioStreamDebug.warn("RuntimeException im Decode-Loop – Reconnect: " + e.getMessage());
              try {
                Thread.sleep(1000);
              } catch (InterruptedException interrupted) {
                break;
              }
              try {
                if (bitstream != null) {
                  bitstream.close();
                }
                if (audioStream != null) {
                  audioStream.close();
                }
              } catch (Exception ignored) {
              }
              if (shouldStop) {
                break;
              }
              try {
                audioStream = new BufferedInputStream(url.openStream());
                bitstream = new Bitstream(audioStream);
                header = bitstream.readFrame();
                if (header == null) {
                  AudioStreamDebug.warn("Reconnect nach RuntimeException fehlgeschlagen");
                  break;
                }
                AudioStreamDebug.info("Reconnect nach RuntimeException erfolgreich");
              } catch (Exception reconnectException) {
                AudioStreamDebug.error("Reconnect nach RuntimeException abgebrochen", reconnectException);
                break;
              }
            }
          }
        }

      } catch (Exception e) {
        if (!shouldStop) {
          LOGGING.error("Fehler beim Abspielen des Radio-Streams: " + e.getMessage(), e);
          AudioStreamDebug.error("Stream-Abbruch", e);
        }
        isPlaying = false;
      } finally {
        AudioStreamDebug.info("Playback-Loop beendet (shouldStop=" + shouldStop + ")");
        cleanup();
        isPlaying = false;
      }
    });
  }

  public void stop() {
    shouldStop = true;
    isPlaying = false;
    currentStreamUrl = null;
    this.spectrum.reset();

    // Streams schließen, damit blockierende Reads/OpenAL abbrechen
    try {
      if (audioStream != null) {
        audioStream.close();
      }
    } catch (Exception ignored) {
    }
    try {
      if (bitstream != null) {
        bitstream.close();
      }
    } catch (Exception ignored) {
    }

    if (playbackTask != null && !playbackTask.isDone()) {
      playbackTask.cancel(true);
    }
  }

  public void setOutputDeviceName(String outputDeviceName) {
    this.outputDeviceName = outputDeviceName;
  }

  public void setSharedContextSupplier(LongSupplier sharedContextSupplier) {
    this.sharedContextSupplier = sharedContextSupplier;
  }

  public void setVolume(float volume) {
    this.volume = Math.max(0.0f, Math.min(1.0f, volume));

    if (openAlSession != null) {
      try {
        openAlSession.setVolume(this.volume);
      } catch (Exception e) {
        LOGGING.warn("Fehler beim Setzen der OpenAL-Lautstärke: " + e.getMessage());
      }
    }

    applySourceDataLineVolume();
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
      applySourceDataLineVolume();
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

  private void applySourceDataLineVolume() {
    if (audioLine == null || !audioLine.isOpen()) {
      return;
    }
    try {
      if (audioLine.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
        FloatControl gainControl = (FloatControl) audioLine.getControl(FloatControl.Type.MASTER_GAIN);
        // MASTER_GAIN ist in dB, nicht linear – lineares Mapping auf die Range = Stille.
        float linear = Math.max(0.0f, Math.min(1.0f, this.volume));
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
    try {
      if (bitstream != null) {
        bitstream.close();
        bitstream = null;
      }
      if (openAlSession != null) {
        openAlSession.close();
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
      if (audioStream != null) {
        audioStream.close();
        audioStream = null;
      }
    } catch (Exception ignored) {
    }
  }

  public void shutdown() {
    stop();
    cleanup();
    if (executorService != null) {
      executorService.shutdown();
    }
  }
}
