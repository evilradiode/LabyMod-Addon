package de.evilradio.core.radio;

import java.util.Arrays;

/**
 * Leichtgewichtiger Spektrum-Analyzer für PCM-16LE.
 * Baut Band-Pegel aus einer 1024-Punkt-FFT mit logarithmischer Band-Zuordnung.
 */
public final class AudioSpectrumAnalyzer {

  private static final int FFT_SIZE = 1024;
  public static final int BAND_COUNT = 32;
  /** Unteres dB-Ende der Anzeige (darunter = 0). */
  private static final float DB_FLOOR = -58.0F;
  /** Leicht über 1 für Dynamik, aber nicht so stark gestaucht. */
  private static final float DISPLAY_GAMMA = 1.2F;
  /** Nachbearbeitung, damit Peaks die Höhe nutzen. */
  private static final float DISPLAY_GAIN = 1.35F;

  private final float[] ring = new float[FFT_SIZE];
  private final float[] window = new float[FFT_SIZE];
  private final float[] real = new float[FFT_SIZE];
  private final float[] imag = new float[FFT_SIZE];
  private final float[] magnitudes = new float[FFT_SIZE / 2];
  private final float[] smoothed = new float[BAND_COUNT];
  private final float[] snapshot = new float[BAND_COUNT];

  private final int[] bandStart = new int[BAND_COUNT];
  private final int[] bandEnd = new int[BAND_COUNT];

  private int writeIndex;
  private int filled;
  private volatile int sampleRate = 44100;
  private volatile int channels = 2;

  public AudioSpectrumAnalyzer() {
    for (int i = 0; i < FFT_SIZE; i++) {
      this.window[i] = 0.5F * (1.0F - (float) Math.cos(2.0D * Math.PI * i / (FFT_SIZE - 1)));
    }
    this.rebuildBands(44100);
  }

  public void configure(int sampleRate, int channels) {
    this.sampleRate = Math.max(8000, sampleRate);
    this.channels = Math.max(1, channels);
    this.rebuildBands(this.sampleRate);
  }

  public void reset() {
    synchronized (this) {
      this.writeIndex = 0;
      this.filled = 0;
      for (int i = 0; i < BAND_COUNT; i++) {
        this.smoothed[i] = 0.0F;
        this.snapshot[i] = 0.0F;
      }
    }
  }

  public void copyBands(float[] out) {
    if (out == null || out.length == 0) {
      return;
    }
    synchronized (this) {
      int n = Math.min(out.length, BAND_COUNT);
      System.arraycopy(this.snapshot, 0, out, 0, n);
    }
  }

  /**
   * Liefert die letzten Samples als Waveform in {@code [-1, 1]}, gleichmäßig über den Ring verteilt.
   */
  public void copyWaveform(float[] out) {
    if (out == null || out.length == 0) {
      return;
    }
    synchronized (this) {
      if (this.filled <= 0) {
        Arrays.fill(out, 0.0F);
        return;
      }
      int available = Math.min(this.filled, FFT_SIZE);
      for (int i = 0; i < out.length; i++) {
        float t = out.length == 1 ? 0.0F : i / (float) (out.length - 1);
        int index = Math.round(t * (available - 1));
        int ringIndex = (this.writeIndex - available + index + FFT_SIZE) % FFT_SIZE;
        out[i] = Math.clamp(this.ring[ringIndex], -1.0F, 1.0F);
      }
    }
  }

  public void feedPcm(byte[] pcm, int length) {
    if (pcm == null || length < 2) {
      return;
    }

    int ch = this.channels;
    int frameBytes = ch * 2;
    if (frameBytes <= 0) {
      return;
    }

    synchronized (this) {
      for (int offset = 0; offset + frameBytes <= length; offset += frameBytes) {
        int sum = 0;
        for (int c = 0; c < ch; c++) {
          int idx = offset + c * 2;
          int sample = (pcm[idx] & 0xFF) | (pcm[idx + 1] << 8);
          sum += (short) sample;
        }
        float mono = (sum / (float) ch) / 32768.0F;
        this.ring[this.writeIndex] = mono;
        this.writeIndex = (this.writeIndex + 1) % FFT_SIZE;
        if (this.filled < FFT_SIZE) {
          this.filled++;
        }
      }

      if (this.filled >= FFT_SIZE) {
        this.analyzeLocked();
      }
    }
  }

  private void analyzeLocked() {
    int start = this.writeIndex;
    for (int i = 0; i < FFT_SIZE; i++) {
      float sample = this.ring[(start + i) % FFT_SIZE] * this.window[i];
      this.real[i] = sample;
      this.imag[i] = 0.0F;
    }

    fft(this.real, this.imag);

    // Amplitude auf ~[0,1] für Vollaussteuerung normalisieren
    float magScale = 2.0F / FFT_SIZE;
    int half = FFT_SIZE / 2;
    for (int i = 0; i < half; i++) {
      float re = this.real[i];
      float im = this.imag[i];
      this.magnitudes[i] = (float) Math.sqrt(re * re + im * im) * magScale;
    }

    for (int band = 0; band < BAND_COUNT; band++) {
      int from = this.bandStart[band];
      int to = this.bandEnd[band];
      // Peak statt RMS: hohe Bänder haben oft nur schmale Spitzen
      float peak = 0.0F;
      for (int bin = from; bin < to; bin++) {
        float mag = this.magnitudes[bin];
        if (mag > peak) {
          peak = mag;
        }
      }

      // dB-Fenster: typische Musik landet in der Mitte, nur Peaks gehen hoch
      float db = 20.0F * (float) Math.log10(Math.max(peak, 1.0E-7F));
      float level = (db - DB_FLOOR) / -DB_FLOOR;
      level = Math.clamp(level, 0.0F, 1.0F);
      level = (float) Math.pow(level, DISPLAY_GAMMA) * DISPLAY_GAIN;
      // Höhen anheben – Radio-Streams sind oben oft leise
      float t = band / (float) (BAND_COUNT - 1);
      float bandGain = 0.68F + 0.95F * t;
      level = Math.min(1.0F, level * bandGain);

      float previous = this.smoothed[band];
      if (level >= previous) {
        this.smoothed[band] = previous * 0.35F + level * 0.65F;
      } else {
        this.smoothed[band] = previous * 0.70F + level * 0.30F;
      }
      this.snapshot[band] = this.smoothed[band];
    }
  }

  private void rebuildBands(int rate) {
    int half = FFT_SIZE / 2;
    float nyquist = rate * 0.5F;
    // Obergrenze bewusst unter Nyquist: viele Streams sind ab ~12 kHz leer
    float minHz = 50.0F;
    float maxHz = Math.min(11000.0F, nyquist * 0.85F);

    int previousEnd = 1;
    for (int band = 0; band < BAND_COUNT; band++) {
      float t0 = band / (float) BAND_COUNT;
      float t1 = (band + 1) / (float) BAND_COUNT;
      float f0 = minHz * (float) Math.pow(maxHz / minHz, t0);
      float f1 = minHz * (float) Math.pow(maxHz / minHz, t1);
      int start = Math.max(previousEnd, Math.round(f0 / nyquist * half));
      int end = Math.max(start + 1, Math.round(f1 / nyquist * half));
      start = Math.min(start, half - 1);
      end = Math.min(end, half);
      this.bandStart[band] = start;
      this.bandEnd[band] = end;
      previousEnd = end;
    }
  }

  private static void fft(float[] real, float[] imag) {
    int n = real.length;
    int j = 0;
    for (int i = 1; i < n; i++) {
      int bit = n >> 1;
      while (j >= bit) {
        j -= bit;
        bit >>= 1;
      }
      j += bit;
      if (i < j) {
        float tmpRe = real[i];
        real[i] = real[j];
        real[j] = tmpRe;
        float tmpIm = imag[i];
        imag[i] = imag[j];
        imag[j] = tmpIm;
      }
    }

    for (int len = 2; len <= n; len <<= 1) {
      float angle = (float) (-2.0D * Math.PI / len);
      float wlenRe = (float) Math.cos(angle);
      float wlenIm = (float) Math.sin(angle);
      for (int i = 0; i < n; i += len) {
        float wRe = 1.0F;
        float wIm = 0.0F;
        int halfLen = len >> 1;
        for (int k = 0; k < halfLen; k++) {
          int even = i + k;
          int odd = even + halfLen;
          float oRe = real[odd] * wRe - imag[odd] * wIm;
          float oIm = real[odd] * wIm + imag[odd] * wRe;
          real[odd] = real[even] - oRe;
          imag[odd] = imag[even] - oIm;
          real[even] += oRe;
          imag[even] += oIm;
          float nextWRe = wRe * wlenRe - wIm * wlenIm;
          wIm = wRe * wlenIm + wIm * wlenRe;
          wRe = nextWRe;
        }
      }
    }
  }
}
