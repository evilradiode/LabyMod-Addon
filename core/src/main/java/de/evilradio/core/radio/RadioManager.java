package de.evilradio.core.radio;

import de.evilradio.core.EvilRadioAddon;
import de.evilradio.core.hudwidget.CurrentSongHudWidget;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class RadioManager {

  private RadioStream currentStream;
  private RadioPlayer radioPlayer;
  private EvilRadioAddon addon;

  public RadioManager(EvilRadioAddon addon) {
    this.addon = addon;
    this.radioPlayer = new RadioPlayer();
  }

  public RadioStream getCurrentStream() {
    return currentStream;
  }

  public void setCurrentStream(RadioStream stream) {
    this.currentStream = stream;
  }

  public boolean isPlaying() {
    return radioPlayer != null && radioPlayer.isPlaying();
  }

  public AudioSpectrumAnalyzer spectrum() {
    return this.radioPlayer.spectrum();
  }

  public void playStream(RadioStream stream) {
    if(stream == null) return;
    boolean playbackActive = isPlaying();

    boolean isSameStream = currentStream != null && currentStream.getId() == stream.getId();

    if (isSameStream && playbackActive && currentStream.getUrl().equals(stream.getUrl())) return;

    addon.setUserManuallyStopped(false);

    if (isSameStream && playbackActive) {
      currentStream = stream;
      this.addon.radioStreamService().setLastSelectedStream(stream);
      addon.configuration().lastStreamId().set(stream.getId());
      return;
    }

    currentStream = null;
    stopStream(false);
    currentStream = stream;
    this.addon.radioStreamService().setLastSelectedStream(stream);
    addon.configuration().lastStreamId().set(stream.getId());

    if (addon.configuration().usageStatistics().enabled().get()) {
      addon.configuration().usageStatistics().incrementStreamUsage(stream.getId());
      addon.radioStreamService().refreshSortOrder();
    }

    if (radioPlayer != null) {
      radioPlayer.setSharedContextSupplier(this::fetchSharedOpenAlContext);
      radioPlayer.setOutputDeviceName(
          MinecraftSoundDeviceProvider.getSelectedSoundDevice(addon.labyAPI().minecraft())
      );
      radioPlayer.play(stream.getUrl());

      if (addon.currentSongService() != null) {
        addon.currentSongService().switchStation(stream);
      }

      addon.requestHudWidgetUpdate(CurrentSongHudWidget.SONG_CHANGE_REASON);
    }
  }

  public void stopStream() {
    stopStream(false);
  }

  public void stopStream(boolean manual) {
    if (manual && addon != null) {
      addon.setUserManuallyStopped(true);
    }

    if (radioPlayer != null) {
      radioPlayer.stop();
    }

    if (addon != null && addon.currentSongService() != null) {
      addon.currentSongService().resetCurrentSong();
      addon.currentSongService().nowPlayingService().clearSubscription();
      addon.requestHudWidgetUpdate(CurrentSongHudWidget.SONG_CHANGE_REASON);
    }
  }

  public void togglePlayStop() {
    if (isPlaying()) {
      stopStream(true);
    } else if (currentStream != null) {
      if (addon != null) {
        addon.setUserManuallyStopped(false);
      }
      playStream(currentStream);
    }
  }

  public void setVolume(float volume) {
    if (radioPlayer != null) {
      radioPlayer.setVolume(volume);
    }
  }

  public void applyEqualizerGains(float[] gainsDb) {
    if (radioPlayer != null) {
      radioPlayer.setEqualizerGainsDb(gainsDb);
    }
  }

  public float getVolume() {
    return radioPlayer != null ? radioPlayer.getVolume() : 0.5f;
  }

  public void shutdown() {
    stopStream();
    if (addon != null && addon.currentSongService() != null) {
      addon.currentSongService().shutdown();
    }
    if (radioPlayer != null) {
      radioPlayer.shutdown();
    }
  }

  private long fetchSharedOpenAlContext() {
    if (addon == null || !OpenAlAudioSession.isAvailable()) {
      return 0L;
    }

    var minecraft = addon.labyAPI().minecraft();
    if (minecraft.isOnRenderThread()) {
      try {
        return OpenAlAudioSession.getCurrentContext();
      } catch (Exception ignored) {
        return 0L;
      }
    }

    AtomicLong context = new AtomicLong(0L);
    CountDownLatch latch = new CountDownLatch(1);
    minecraft.executeOnRenderThread(() -> {
      try {
        context.set(OpenAlAudioSession.getCurrentContext());
      } catch (Exception ignored) {
      } finally {
        latch.countDown();
      }
    });

    try {
      latch.await(250, TimeUnit.MILLISECONDS);
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    }

    return context.get();
  }
}
