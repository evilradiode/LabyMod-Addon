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

public class RadioPlayer {
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

    if (isPlaying) {
      stop();
    }

    currentStreamUrl = streamUrl;
    shouldStop = false;

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

        AudioFormat audioFormat = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            sampleRate,
            16,
            channels,
            channels * 2,
            sampleRate,
            false
        );

        boolean openAlPlayback = false;
        if (OpenAlAudioSession.isAvailable()) {
          long sharedOpenAlContext = sharedContextSupplier != null ? sharedContextSupplier.getAsLong() : 0L;
          if (sharedOpenAlContext != 0L) {
            try {
              openAlSession = OpenAlAudioSession.openShared(sharedOpenAlContext, volume);
              openAlSession.configureFormat(sampleRate, channels);
              openAlPlayback = true;
            } catch (Exception ignored) {
            }
          }

          if (!openAlPlayback) {
            try {
              openAlSession = OpenAlAudioSession.openDevice(outputDeviceName, volume);
              openAlSession.configureFormat(sampleRate, channels);
              openAlPlayback = true;
            } catch (Exception ignored) {
            }
          }
        }

        if (!openAlPlayback) {
          DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
          if (!AudioSystem.isLineSupported(info)) {
            throw new Exception("Audio-Format wird nicht unterstützt");
          }

          audioLine = (SourceDataLine) AudioSystem.getLine(info);
          audioLine.open(audioFormat);

          if (audioLine.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl gainControl = (FloatControl) audioLine.getControl(FloatControl.Type.MASTER_GAIN);
            float range = gainControl.getMaximum() - gainControl.getMinimum();
            float gain = (range * volume) + gainControl.getMinimum();
            gainControl.setValue(gain);
          }

          audioLine.start();
        }

        isPlaying = true;

        byte[] buffer = new byte[4096];
        while (!shouldStop && isOutputActive() && bitstream != null) {
          try {
            if (bitstream == null) {
              break;
            }

            SampleBuffer sampleBuffer = (SampleBuffer) decoder.decodeFrame(header, bitstream);
            short[] samples = sampleBuffer.getBuffer();

            int sampleIndex = 0;
            for (int i = 0; i < samples.length && !shouldStop && isOutputActive(); i++) {
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
                break;
              }
            }
          } catch (JavaLayerException e) {
            if (!shouldStop) {
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
                break;
              }
            }
          } catch (RuntimeException e) {
            if (!shouldStop) {
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
                  break;
                }
              } catch (Exception reconnectException) {
                break;
              }
            }
          }
        }

      } catch (Exception e) {
        if (!shouldStop) {
          System.err.println("Fehler beim Abspielen des Radio-Streams: " + e.getMessage());
          e.printStackTrace();
        }
        isPlaying = false;
      } finally {
        cleanup();
        isPlaying = false;
      }
    });
  }

  public void stop() {
    shouldStop = true;
    isPlaying = false;
    currentStreamUrl = null;

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
        System.err.println("Fehler beim Setzen der OpenAL-Lautstärke: " + e.getMessage());
      }
    }

    if (audioLine != null && audioLine.isOpen()) {
      try {
        if (audioLine.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
          FloatControl gainControl = (FloatControl) audioLine.getControl(FloatControl.Type.MASTER_GAIN);
          float range = gainControl.getMaximum() - gainControl.getMinimum();
          float gain = (range * this.volume) + gainControl.getMinimum();
          gainControl.setValue(gain);
        }
      } catch (Exception e) {
        System.err.println("Fehler beim Setzen der Lautstärke: " + e.getMessage());
      }
    }
  }

  public float getVolume() {
    return volume;
  }

  private boolean isOutputActive() {
    if (openAlSession != null) {
      return true;
    }
    return audioLine != null && audioLine.isOpen();
  }

  private void writeAudioSafe(byte[] buffer, int length) {
    if (volume <= 0.0f) {
      return;
    }

    try {
      if (openAlSession != null) {
        openAlSession.queuePcm(buffer, length);
        openAlSession.startIfNeeded();
        return;
      }

      if (audioLine != null && audioLine.isOpen()) {
        audioLine.write(buffer, 0, length);
      }
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

  public boolean isPlaying() {
    return isPlaying;
  }

  public void shutdown() {
    stop();
    cleanup();
    if (executorService != null) {
      executorService.shutdown();
    }
  }
}
