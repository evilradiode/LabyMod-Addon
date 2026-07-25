package de.evilradio.core.song.azuracast;

import com.google.gson.JsonObject;
import de.evilradio.core.song.CurrentSong;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
import net.labymod.api.util.logging.Logging;

/**
 * Kurzlebige Multi-Station-WebSocket-Session für den Sender-Picker.
 * Unabhängig vom HUD-{@link AzuraCastNowPlayingService}.
 */
public final class PickerNowPlayingSession {

  private static final Logging LOGGING = Logging.create("EvilRadio-PickerNowPlaying");
  private static final long[] BACKOFF_MS = {1000L, 2000L, 5000L, 10000L, 20000L, 30000L};

  private final HttpClient httpClient = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(10))
      .build();
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread thread = new Thread(r, "EvilRadio-PickerNowPlaying-WS");
    thread.setDaemon(true);
    return thread;
  });
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final AtomicReference<WebSocket> webSocket = new AtomicReference<>();
  private final AtomicReference<ScheduledFuture<?>> reconnectFuture = new AtomicReference<>();
  private final AtomicInteger backoffIndex = new AtomicInteger(0);
  private final StringBuilder textBuffer = new StringBuilder();
  private final Set<String> shortcodes;
  private final Set<String> normalizedShortcodes;
  private final BiConsumer<String, CurrentSong> songListener;

  public PickerNowPlayingSession(
      Collection<String> shortcodes,
      BiConsumer<String, CurrentSong> songListener
  ) {
    this.shortcodes = new LinkedHashSet<>();
    this.normalizedShortcodes = new LinkedHashSet<>();
    for (String shortcode : shortcodes) {
      if (shortcode != null && !shortcode.isBlank()) {
        String trimmed = shortcode.trim();
        this.shortcodes.add(trimmed);
        this.normalizedShortcodes.add(normalize(trimmed));
      }
    }
    this.songListener = songListener == null ? (code, song) -> {
    } : songListener;
  }

  public void open() {
    if (this.closed.get() || this.shortcodes.isEmpty()) {
      return;
    }
    this.connect(false);
  }

  public void close() {
    if (!this.closed.compareAndSet(false, true)) {
      return;
    }
    this.cancelReconnect();
    this.closeSocket();
    this.scheduler.shutdownNow();
  }

  private void connect(boolean reconnect) {
    if (this.closed.get() || this.shortcodes.isEmpty()) {
      return;
    }

    WebSocket.Listener listener = new WebSocket.Listener() {
      @Override
      public void onOpen(WebSocket webSocket) {
        WebSocket.Listener.super.onOpen(webSocket);
        if (closed.get()) {
          webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "closed");
          return;
        }
        PickerNowPlayingSession.this.webSocket.set(webSocket);
        PickerNowPlayingSession.this.backoffIndex.set(0);
        webSocket.sendText(buildSubscribePayload(), true);
        webSocket.request(1);
        LOGGING.info((reconnect ? "Picker WS reconnected" : "Picker WS connected")
            + ", subscribed to " + shortcodes.size() + " stations");
      }

      @Override
      public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        textBuffer.append(data);
        if (last) {
          String message = textBuffer.toString();
          textBuffer.setLength(0);
          handleMessage(message);
        }
        webSocket.request(1);
        return WebSocket.Listener.super.onText(webSocket, data, last);
      }

      @Override
      public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
        return WebSocket.Listener.super.onBinary(webSocket, data, last);
      }

      @Override
      public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        PickerNowPlayingSession.this.webSocket.compareAndSet(webSocket, null);
        if (!closed.get()) {
          LOGGING.warn("Picker WS closed (" + statusCode + ") – scheduling reconnect");
          scheduleReconnect();
        }
        return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
      }

      @Override
      public void onError(WebSocket webSocket, Throwable error) {
        LOGGING.warn("Picker WS error – " + (error == null ? "unknown" : error.getMessage()));
        PickerNowPlayingSession.this.webSocket.compareAndSet(webSocket, null);
        if (!closed.get()) {
          scheduleReconnect();
        }
      }
    };

    this.httpClient.newWebSocketBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .buildAsync(URI.create(AzuraCastNowPlayingService.WEBSOCKET_URL), listener)
        .whenComplete((socket, error) -> {
          if (error != null && !closed.get()) {
            LOGGING.warn("Picker WS connect failed – " + error.getMessage());
            scheduleReconnect();
          }
        });
  }

  private void scheduleReconnect() {
    if (this.closed.get()) {
      return;
    }
    this.cancelReconnect();
    int index = Math.min(this.backoffIndex.getAndIncrement(), BACKOFF_MS.length - 1);
    long delay = BACKOFF_MS[index];
    ScheduledFuture<?> future = this.scheduler.schedule(
        () -> {
          if (!closed.get()) {
            connect(true);
          }
        },
        delay,
        TimeUnit.MILLISECONDS
    );
    this.reconnectFuture.set(future);
  }

  private void cancelReconnect() {
    ScheduledFuture<?> future = this.reconnectFuture.getAndSet(null);
    if (future != null) {
      future.cancel(false);
    }
  }

  private void closeSocket() {
    WebSocket socket = this.webSocket.getAndSet(null);
    if (socket == null) {
      return;
    }
    try {
      CompletableFuture<?> close = socket.sendClose(WebSocket.NORMAL_CLOSURE, "picker-close");
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

  private String buildSubscribePayload() {
    StringBuilder builder = new StringBuilder("{\"subs\":{");
    boolean first = true;
    for (String shortcode : this.shortcodes) {
      if (!first) {
        builder.append(',');
      }
      first = false;
      builder.append("\"station:").append(shortcode).append("\":{\"recover\":true}");
    }
    builder.append("}}");
    return builder.toString();
  }

  private void handleMessage(String raw) {
    if (this.closed.get()) {
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

    List<NowPlayingMessageParser.ParsedPublication> publications =
        NowPlayingMessageParser.extractAllPublications(message);
    for (NowPlayingMessageParser.ParsedPublication publication : publications) {
      CurrentSong song = publication.song();
      if (song == null || !song.isValid()) {
        continue;
      }
      String shortcode = resolveShortcode(publication.channel(), song);
      if (shortcode == null || !this.normalizedShortcodes.contains(shortcode)) {
        continue;
      }
      this.songListener.accept(shortcode, song);
    }
  }

  private static String resolveShortcode(String channel, CurrentSong song) {
    if (song.getStationShortcode() != null && !song.getStationShortcode().isBlank()) {
      return normalize(song.getStationShortcode());
    }
    if (channel != null && channel.regionMatches(true, 0, "station:", 0, "station:".length())) {
      return normalize(channel.substring("station:".length()));
    }
    return null;
  }

  private static String normalize(String shortcode) {
    return shortcode.trim().toLowerCase(Locale.ROOT);
  }
}
