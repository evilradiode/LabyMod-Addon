package de.evilradio.core.radio;

/**
 * Echtzeit-Equalizer für PCM-16LE mit kaskadierten Peaking-Biquad-Filtern.
 */
public final class AudioEqualizer {

  public static final float[] BAND_FREQUENCIES_HZ = {60.0F, 250.0F, 1000.0F, 4000.0F, 12000.0F};
  private static final float BAND_Q = 1.15F;
  private static final float GAIN_EPSILON_DB = 0.05F;

  private final BiquadState[] leftFilters = new BiquadState[EqualizerPreset.BAND_COUNT];
  private final BiquadState[] rightFilters = new BiquadState[EqualizerPreset.BAND_COUNT];
  private final float[] targetGainsDb = new float[EqualizerPreset.BAND_COUNT];

  private volatile int sampleRate = 44100;
  private volatile int channels = 2;
  private volatile boolean bypass = true;

  public AudioEqualizer() {
    for (int i = 0; i < EqualizerPreset.BAND_COUNT; i++) {
      this.leftFilters[i] = new BiquadState();
      this.rightFilters[i] = new BiquadState();
    }
  }

  public void configure(int sampleRate, int channels) {
    this.sampleRate = Math.max(8000, sampleRate);
    this.channels = Math.max(1, channels);
    this.rebuildFilters();
  }

  public void setGainsDb(float[] gainsDb) {
    if (gainsDb == null || gainsDb.length < EqualizerPreset.BAND_COUNT) {
      return;
    }
    boolean flat = true;
    for (int i = 0; i < EqualizerPreset.BAND_COUNT; i++) {
      float gain = gainsDb[i];
      this.targetGainsDb[i] = gain;
      if (Math.abs(gain) > GAIN_EPSILON_DB) {
        flat = false;
      }
    }
    this.bypass = flat;
    this.rebuildFilters();
  }

  public void reset() {
    for (int i = 0; i < EqualizerPreset.BAND_COUNT; i++) {
      this.leftFilters[i].resetState();
      this.rightFilters[i].resetState();
    }
  }

  public void process(byte[] pcm, int length) {
    if (this.bypass || pcm == null || length < 2) {
      return;
    }

    int ch = this.channels;
    int frameBytes = ch * 2;
    if (frameBytes <= 0) {
      return;
    }

    for (int offset = 0; offset + frameBytes <= length; offset += frameBytes) {
      float left = readSample(pcm, offset);
      float right = ch == 1 ? left : readSample(pcm, offset + 2);

      for (int band = 0; band < EqualizerPreset.BAND_COUNT; band++) {
        left = this.leftFilters[band].process(left);
        if (ch > 1) {
          right = this.rightFilters[band].process(right);
        }
      }

      writeSample(pcm, offset, left);
      if (ch > 1) {
        writeSample(pcm, offset + 2, right);
      }
    }
  }

  private void rebuildFilters() {
    int rate = this.sampleRate;
    for (int band = 0; band < EqualizerPreset.BAND_COUNT; band++) {
      float gainDb = this.targetGainsDb[band];
      float frequency = BAND_FREQUENCIES_HZ[band];
      this.leftFilters[band].configurePeaking(rate, frequency, BAND_Q, gainDb);
      this.rightFilters[band].configurePeaking(rate, frequency, BAND_Q, gainDb);
    }
  }

  private static float readSample(byte[] pcm, int offset) {
    int sample = (pcm[offset] & 0xFF) | (pcm[offset + 1] << 8);
    return (short) sample / 32768.0F;
  }

  private static void writeSample(byte[] pcm, int offset, float sample) {
    int clamped = Math.round(Math.clamp(sample, -1.0F, 1.0F) * 32767.0F);
    pcm[offset] = (byte) (clamped & 0xFF);
    pcm[offset + 1] = (byte) ((clamped >> 8) & 0xFF);
  }

  private static final class BiquadState {

    private float b0 = 1.0F;
    private float b1;
    private float b2;
    private float a1;
    private float a2;
    private float z1;
    private float z2;

    void configurePeaking(float sampleRate, float frequency, float q, float gainDb) {
      if (Math.abs(gainDb) <= GAIN_EPSILON_DB) {
        this.b0 = 1.0F;
        this.b1 = 0.0F;
        this.b2 = 0.0F;
        this.a1 = 0.0F;
        this.a2 = 0.0F;
        return;
      }

      float a = (float) Math.pow(10.0D, gainDb / 40.0D);
      float w0 = (float) (2.0D * Math.PI * frequency / sampleRate);
      float cosW0 = (float) Math.cos(w0);
      float sinW0 = (float) Math.sin(w0);
      float alpha = sinW0 / (2.0F * q);

      float b0n = 1.0F + alpha * a;
      float b1n = -2.0F * cosW0;
      float b2n = 1.0F - alpha * a;
      float a0n = 1.0F + alpha / a;
      float a1n = -2.0F * cosW0;
      float a2n = 1.0F - alpha / a;

      this.b0 = b0n / a0n;
      this.b1 = b1n / a0n;
      this.b2 = b2n / a0n;
      this.a1 = a1n / a0n;
      this.a2 = a2n / a0n;
    }

    float process(float input) {
      float output = this.b0 * input + this.z1;
      this.z1 = this.b1 * input - this.a1 * output + this.z2;
      this.z2 = this.b2 * input - this.a2 * output;
      return output;
    }

    void resetState() {
      this.z1 = 0.0F;
      this.z2 = 0.0F;
    }
  }

  public enum EqualizerPreset {

    NORMAL(0.0F, 0.0F, 0.0F, 0.0F, 0.0F),
    BASS_BOOST(4.0F, 6.0F, 2.0F, 0.0F, -1.0F),
    TREBLE_BOOST(-1.0F, 0.0F, 1.0F, 4.0F, 6.0F),
    VOCAL(-2.0F, -1.0F, 5.0F, 4.0F, 1.0F),
    ROCK(5.0F, 4.0F, -1.0F, 3.0F, 5.0F),
    POP(2.0F, 3.0F, 1.0F, 3.0F, 4.0F),
    ELECTRONIC(6.0F, 3.0F, 0.0F, 2.0F, 4.0F),
    SOFT(2.0F, 1.0F, 0.0F, -2.0F, -3.0F),
    CUSTOM;

    public static final int BAND_COUNT = 5;

    private final float subBassDb;
    private final float bassDb;
    private final float midDb;
    private final float presenceDb;
    private final float trebleDb;

    EqualizerPreset() {
      this(0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
    }

    EqualizerPreset(float subBassDb, float bassDb, float midDb, float presenceDb, float trebleDb) {
      this.subBassDb = subBassDb;
      this.bassDb = bassDb;
      this.midDb = midDb;
      this.presenceDb = presenceDb;
      this.trebleDb = trebleDb;
    }

    public EqualizerPreset next() {
      EqualizerPreset[] values = values();
      return values[(this.ordinal() + 1) % values.length];
    }

    public boolean isCustom() {
      return this == CUSTOM;
    }

    public float[] presetGainsDb() {
      return new float[] {
          this.subBassDb,
          this.bassDb,
          this.midDb,
          this.presenceDb,
          this.trebleDb
      };
    }
  }

}
