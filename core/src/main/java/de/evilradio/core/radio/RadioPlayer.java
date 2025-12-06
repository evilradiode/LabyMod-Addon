package de.evilradio.core.radio;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
  private InputStream audioStream;
  private Bitstream bitstream;
  private ExecutorService executorService;
  private Future<?> playbackTask;
  private volatile boolean isPlaying;
  private volatile boolean shouldStop;
  private volatile boolean isPaused;
  private volatile float volume = 0.5f; // Standard-Lautstärke (50%)

  public RadioPlayer() {
    this.executorService = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "RadioPlayer-Thread");
      t.setDaemon(true);
      return t;
    });
    this.isPlaying = false;
    this.shouldStop = false;
    this.isPaused = false;
  }

  public void play(String streamUrl) {
    if (isPlaying) {
      stop();
    }

    shouldStop = false;
    isPlaying = true;

    playbackTask = executorService.submit(() -> {
      try {
        URI uri = URI.create(streamUrl);
        URL url = uri.toURL();
        audioStream = new BufferedInputStream(url.openStream());

        // Verwende JLayer für MP3-Streams
        bitstream = new Bitstream(audioStream);
        Decoder decoder = new Decoder();
        
        // Lese den ersten Frame, um die Audio-Parameter zu erhalten
        Header header = bitstream.readFrame();
        if (header == null) {
          throw new Exception("Kein gültiger MP3-Stream gefunden");
        }

        // Erstelle AudioFormat basierend auf MP3-Header
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

        // Erstelle DataLine für die Wiedergabe
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
        if (!AudioSystem.isLineSupported(info)) {
          throw new Exception("Audio-Format wird nicht unterstützt");
        }

        audioLine = (SourceDataLine) AudioSystem.getLine(info);
        audioLine.open(audioFormat);
        
        // Setze Lautstärke
        if (audioLine.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
          FloatControl gainControl = (FloatControl) audioLine.getControl(FloatControl.Type.MASTER_GAIN);
          float range = gainControl.getMaximum() - gainControl.getMinimum();
          float gain = (range * volume) + gainControl.getMinimum();
          gainControl.setValue(gain);
        }
        
        audioLine.start();

        // Wiedergabe-Loop für MP3-Stream
        byte[] buffer = new byte[4096];
        while (!shouldStop) {
          // Pause-Handling
          if (isPaused) {
            if (audioLine != null) {
              audioLine.stop();
            }
            while (isPaused && !shouldStop) {
              Thread.sleep(100);
            }
            if (!shouldStop && audioLine != null) {
              audioLine.start();
            }
          }
          
          try {
            // Dekodiere den Frame
            SampleBuffer sampleBuffer = (SampleBuffer) decoder.decodeFrame(header, bitstream);
            short[] samples = sampleBuffer.getBuffer();
            
            // Konvertiere 16-bit Samples zu Bytes
            int sampleIndex = 0;
            for (int i = 0; i < samples.length && !shouldStop; i++) {
              short sample = samples[i];
              buffer[sampleIndex++] = (byte) (sample & 0xFF);
              buffer[sampleIndex++] = (byte) ((sample >> 8) & 0xFF);
              
              // Wenn Buffer voll ist, schreibe ihn
              if (sampleIndex >= buffer.length) {
                audioLine.write(buffer, 0, sampleIndex);
                sampleIndex = 0;
              }
            }
            
            // Schreibe verbleibende Samples
            if (sampleIndex > 0) {
              audioLine.write(buffer, 0, sampleIndex);
            }
            
            // Lese nächsten Frame
            bitstream.closeFrame();
            header = bitstream.readFrame();
            
            if (header == null) {
              // Stream-Ende erreicht, versuche erneut zu lesen (für kontinuierliche Streams)
              Thread.sleep(100);
              try {
                if (bitstream != null) {
                  bitstream.close();
                }
                if (audioStream != null) {
                  audioStream.close();
                }
              } catch (Exception ignored) {}
              audioStream = new BufferedInputStream(url.openStream());
              bitstream = new Bitstream(audioStream);
              header = bitstream.readFrame();
              if (header == null) {
                break; // Stream wirklich beendet
              }
            }
          } catch (JavaLayerException e) {
            if (!shouldStop) {
              // Bei Fehlern, versuche den Stream neu zu verbinden
              Thread.sleep(1000);
              try {
                if (bitstream != null) {
                  bitstream.close();
                }
                if (audioStream != null) {
                  audioStream.close();
                }
              } catch (Exception ignored) {}
              audioStream = new BufferedInputStream(url.openStream());
              bitstream = new Bitstream(audioStream);
              header = bitstream.readFrame();
              if (header == null) {
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
      } finally {
        cleanup();
      }
    });
  }

  public void stop() {
    shouldStop = true;
    isPlaying = false;
    isPaused = false;

    if (playbackTask != null && !playbackTask.isDone()) {
      playbackTask.cancel(true);
    }

    cleanup();
  }

  public void pause() {
    if (isPlaying && !isPaused) {
      isPaused = true;
    }
  }

  public void resume() {
    if (isPlaying && isPaused) {
      isPaused = false;
    }
  }

  public boolean isPaused() {
    return isPaused;
  }

  public void setVolume(float volume) {
    // Volume zwischen 0.0 und 1.0 begrenzen
    this.volume = Math.max(0.0f, Math.min(1.0f, volume));
    
    // Aktualisiere Lautstärke, wenn AudioLine bereits geöffnet ist
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

  private void cleanup() {
    try {
      if (bitstream != null) {
        bitstream.close();
        bitstream = null;
      }
      if (audioLine != null) {
        try {
          audioLine.stop();
        } catch (Exception ignored) {}
        try {
          audioLine.close();
        } catch (Exception ignored) {}
        audioLine = null;
      }
      if (audioStream != null) {
        audioStream.close();
        audioStream = null;
      }
    } catch (Exception e) {
      // Ignoriere Fehler beim Aufräumen
    }
  }

  public boolean isPlaying() {
    return isPlaying;
  }

  public void shutdown() {
    stop();
    if (executorService != null) {
      executorService.shutdown();
    }
  }
}

