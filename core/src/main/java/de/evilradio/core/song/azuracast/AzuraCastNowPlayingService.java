package de.evilradio.core.song.azuracast;

import com.google.gson.JsonObject;
import de.evilradio.core.song.CurrentSong;
import de.evilradio.core.song.NowPlayingConnectionState;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.labymod.api.util.logging.Logging;

/**
 * Dynamischer AzuraCast-/Centrifugo-Now-Playing-WebSocket-Client.
 * Strategie B: Bei Senderwechsel Verbindung schließen und mit neuer Subscription neu verbinden.
 */
public final class AzuraCastNowPlayingService {

  public static final String WEBSOCKET_URL =
      "wss://broadcast.evil-radio.de/api/live/nowplaying/websocket";

  private static final long[] BACKOFF_MS = {1000L, 2000L, 5000L, 10000L, 20000L, 30000L};

  private final Logging logging = Logging.create("EvilRadio-AzuraCastNowPlaying");
  private final HttpClient httpClient = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(10))
      .build();
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread thread = new Thread(r, "EvilRadio-AzuraCast-WS");
    thread.setDaemon(true);
    return thread;
  });

  private final SubscriptionGenerationGuard guard = new SubscriptionGenerationGuard();
  private final AtomicBoolean started = new AtomicBoolean(false);
  private final AtomicBoolean stopped = new AtomicBoolean(false);
  private final AtomicInteger backoffIndex = new AtomicInteger(0);
  private final AtomicReference<WebSocket> webSocket = new AtomicReference<>();
  private final AtomicReference<ScheduledFuture<?>> reconnectFuture = new AtomicReference<>();
  private final AtomicReference<NowPlayingConnectionState> connectionState =
      new AtomicReference<>(NowPlayingConnectionState.IDLE);
  private final StringBuilder textBuffer = new StringBuilder();

  private volatile Consumer<CurrentSong> songListener = song -> {
  };
  private volatile BiConsumer<NowPlayingConnectionState, String> stateListener = (state, shortcode) -> {
  };

  public void setSongListener(Consumer<CurrentSong> songListener) {
    this.songListener = songListener == null ? song -> {
    } : songListener;
  }

  public void setStateListener(BiConsumer<NowPlayingConnectionState, String> stateListener) {
    this.stateListener = stateListener == null ? (state, shortcode) -> {
    } : stateListener;
  }

  public NowPlayingConnectionState connectionState() {
    return connectionState.get();
  }

  public String activeShortcode() {
    return guard.activeShortcode();
  }

  public long currentGeneration() {
    return guard.currentGeneration();
  }

  public void start() {
    if (!started.compareAndSet(false, true)) {
      return;
    }
    stopped.set(false);
    logging.info("AzuraCast NowPlaying service started");
  }

  public void switchStation(String shortcode) {
    if (stopped.get()) {
      return;
    }
    if (!started.get()) {
      start();
    }
    if (shortcode == null) {
      logging.warn("Ignoring invalid (null) AzuraCast shortcode");
      long generation = guard.clear();
      closeSocket(false);
      publishState(NowPlayingConnectionState.IDLE, null);
      return;
    }

    String normalized = shortcode.trim();
    String current = guard.activeShortcode();
    if (normalized.equals(current) && webSocket.get() != null
        && connectionState.get() == NowPlayingConnectionState.CONNECTED) {
      return;
    }

    long generation = guard.switchTo(normalized);
    cancelReconnect();
    closeSocket(false);
    publishState(NowPlayingConnectionState.LOADING, normalized);
    logging.info("Switching NowPlaying subscription to station:" + normalized + " generation=" + generation);
    connect(generation, normalized, false);
  }

  public void stop() {
    stopped.set(true);
    started.set(false);
    cancelReconnect();
    guard.clear();
    closeSocket(false);
    publishState(NowPlayingConnectionState.IDLE, null);
    logging.info("AzuraCast NowPlaying service stopped");
  }

  public void shutdown() {
    stop();
    scheduler.shutdownNow();
  }

  private void connect(long generation, String shortcode, boolean isReconnect) {
    if (stopped.get()) {
      return;
    }
    if (generation != guard.currentGeneration()) {
      return;
    }
    if (!Objects.equals(shortcode, guard.activeShortcode())) {
      return;
    }

    publishState(isReconnect ? NowPlayingConnectionState.RECONNECTING : NowPlayingConnectionState.LOADING, shortcode);

    WebSocket.Listener listener = new WebSocket.Listener() {
      @Override
      public void onOpen(WebSocket webSocket) {
        WebSocket.Listener.super.onOpen(webSocket);
        if (!guard.accepts(generation, shortcode) || stopped.get()) {
          webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "stale");
          return;
        }
        AzuraCastNowPlayingService.this.webSocket.set(webSocket);
        String subscribe = "{\"subs\":{\"station:" + shortcode + "\":{\"recover\":true}}}";
        webSocket.sendText(subscribe, true);
        backoffIndex.set(0);
        publishState(NowPlayingConnectionState.CONNECTED, shortcode);
        logging.info("WebSocket connected, subscribed to station:" + shortcode);
      }

      @Override
      public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        textBuffer.append(data);
        if (last) {
          String message = textBuffer.toString();
          textBuffer.setLength(0);
          handleMessage(generation, shortcode, message);
        }
        return WebSocket.Listener.super.onText(webSocket, data, last);
      }

      @Override
      public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
        return WebSocket.Listener.super.onBinary(webSocket, data, last);
      }

      @Override
      public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        AzuraCastNowPlayingService.this.webSocket.compareAndSet(webSocket, null);
        if (!stopped.get() && generation == guard.currentGeneration()
            && Objects.equals(shortcode, guard.activeShortcode())) {
          scheduleReconnect(generation, shortcode);
        }
        return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
      }

      @Override
      public void onError(WebSocket webSocket, Throwable error) {
        logging.warn("WebSocket error for station:" + shortcode + " – " + error.getMessage());
        AzuraCastNowPlayingService.this.webSocket.compareAndSet(webSocket, null);
        if (!stopped.get() && generation == guard.currentGeneration()
            && Objects.equals(shortcode, guard.activeShortcode())) {
          scheduleReconnect(generation, shortcode);
        }
      }
    };

    httpClient.newWebSocketBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .buildAsync(URI.create(WEBSOCKET_URL), listener)
        .whenComplete((socket, error) -> {
          if (error != null) {
            logging.warn("WebSocket connect failed for station:" + shortcode + " – " + error.getMessage());
            if (!stopped.get() && generation == guard.currentGeneration()
                && Objects.equals(shortcode, guard.activeShortcode())) {
              scheduleReconnect(generation, shortcode);
            }
          }
        });
  }

  private void handleMessage(long generation, String shortcode, String raw) {
    if (!guard.accepts(generation, shortcode) || stopped.get()) {
      return;
    }

    var parsedMessage = NowPlayingMessageParser.parseJsonObject(raw);
    if (parsedMessage.isEmpty()) {
      return;
    }

    JsonObject message = parsedMessage.get();
    if (NowPlayingMessageParser.isKeepalive(message)) {
      return;
    }

    var publication = NowPlayingMessageParser.extractPublication(message);
    if (publication.isEmpty()) {
      return;
    }

    String channel = publication.get().channel();
    CurrentSong song = publication.get().song();
    String compareTarget = channel != null ? channel : song.getStationShortcode();
    if (!guard.accepts(generation, compareTarget == null ? shortcode : compareTarget)) {
      return;
    }
    if (song.getStationShortcode() != null
        && !song.getStationShortcode().equals(shortcode)
        && !guard.accepts(generation, song.getStationShortcode())) {
      return;
    }

    songListener.accept(song);
  }

  private void scheduleReconnect(long generation, String shortcode) {
    if (stopped.get()) {
      return;
    }
    if (generation != guard.currentGeneration() || !Objects.equals(shortcode, guard.activeShortcode())) {
      return;
    }

    cancelReconnect();
    publishState(NowPlayingConnectionState.RECONNECTING, shortcode);

    int index = Math.min(backoffIndex.getAndIncrement(), BACKOFF_MS.length - 1);
    long delay = BACKOFF_MS[index];
    logging.info("Scheduling NowPlaying reconnect for station:" + shortcode + " in " + delay + "ms");

    ScheduledFuture<?> future = scheduler.schedule(
        () -> connect(generation, shortcode, true),
        delay,
        TimeUnit.MILLISECONDS
    );
    reconnectFuture.set(future);
  }

  private void cancelReconnect() {
    ScheduledFuture<?> future = reconnectFuture.getAndSet(null);
    if (future != null) {
      future.cancel(false);
    }
  }

  private void closeSocket(boolean triggerReconnect) {
    WebSocket socket = webSocket.getAndSet(null);
    if (socket != null) {
      try {
        CompletableFuture<?> close = socket.sendClose(WebSocket.NORMAL_CLOSURE, "switch");
        if (close != null) {
          close.orTimeout(2, TimeUnit.SECONDS).exceptionally(error -> null);
        }
        socket.abort();
      } catch (RuntimeException ignored) {
        try {
          socket.abort();
        } catch (RuntimeException ignoredAgain) {
        }
      }
    }
    if (!triggerReconnect) {
      // no-op: caller decides whether to reconnect
    }
  }

  private void publishState(NowPlayingConnectionState state, String shortcode) {
    connectionState.set(state);
    stateListener.accept(state, shortcode);
  }
}
