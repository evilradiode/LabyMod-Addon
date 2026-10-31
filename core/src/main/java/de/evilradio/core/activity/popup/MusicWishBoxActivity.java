package de.evilradio.core.activity.popup;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.evilradio.core.EvilConstants;
import de.evilradio.core.EvilRadioAddon;
import de.evilradio.core.EvilTextures;
import de.evilradio.core.activity.widget.AutoCompleteWidget;
import de.evilradio.core.song.CurrentSong;
import net.labymod.api.Laby;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.format.NamedTextColor;
import net.labymod.api.client.gui.screen.Parent;
import net.labymod.api.client.gui.screen.ScreenInstance;
import net.labymod.api.client.gui.screen.activity.AutoActivity;
import net.labymod.api.client.gui.screen.activity.Link;
import net.labymod.api.client.gui.screen.activity.types.SimpleActivity;
import net.labymod.api.client.gui.screen.widget.widgets.ComponentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.ButtonWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.CheckBoxWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.TextFieldWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.FlexibleContentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.ScrollWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.list.HorizontalListWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.list.VerticalListWidget;
import net.labymod.api.client.gui.screen.widget.widgets.renderer.IconWidget;
import net.labymod.api.util.Debounce;
import net.labymod.api.util.I18n;
import net.labymod.api.util.StringUtil;
import net.labymod.api.util.ThreadSafe;
import net.labymod.api.util.io.web.request.Request;
import net.labymod.api.util.io.web.request.Request.Method;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Link("popup/wishbox.lss")
@AutoActivity
public class MusicWishBoxActivity extends SimpleActivity {

  private EvilRadioAddon addon;

  private ScreenInstance previousScreen;

  private ComponentWidget nameTitle;
  private TextFieldWidget nameInput;

  private TextFieldWidget ageInput;

  private TextFieldWidget artistInput;
  private VerticalListWidget<AutoCompleteWidget> artistCompleteWidget;

  private TextFieldWidget songInput;
  private VerticalListWidget<AutoCompleteWidget> songCompleteWidget;

  private TextFieldWidget wishInput;

  private boolean anonymous = false;
  private final AtomicBoolean submitting = new AtomicBoolean(false);

  public MusicWishBoxActivity(EvilRadioAddon addon, ScreenInstance previousScreen) {
    this.addon = addon;
    this.previousScreen = previousScreen;
  }

  @Override
  public void initialize(Parent parent) {
    super.initialize(parent);

    FlexibleContentWidget container = new FlexibleContentWidget().addId("container");
    HorizontalListWidget header = new HorizontalListWidget().addId("header");

    IconWidget headWidget = new IconWidget(EvilTextures.LOGO).addId("head");
    ComponentWidget titleWidget = ComponentWidget.i18n("evilradio.form.wishbox.title").addId("title");
    header.addEntry(headWidget);
    header.addEntry(titleWidget);
    container.addContent(header);

    FlexibleContentWidget content = new FlexibleContentWidget().addId("content");

    ButtonWidget sendButton = null;

    CurrentSong currentSong = this.addon.currentSongService().getCurrentSong();
    if(currentSong != null && currentSong.isOnAir() && currentSong.isWishBoxEnabled()) {

      HorizontalListWidget nameAgeContainer = new HorizontalListWidget().addId("name-age-container");

      FlexibleContentWidget nameContainer = new FlexibleContentWidget().addId("name-container");
      nameContainer.addContent(this.nameTitle = ComponentWidget.component(
              Component.translatable("evilradio.form.wishbox.name"
              ).append(!this.anonymous ? Component.text(" *", NamedTextColor.RED) : Component.empty()))
          .addId("name-title"));
      this.nameInput = new TextFieldWidget().addId("name-input");
      nameContainer.addContent(this.nameInput);
      nameContainer.addContent(ComponentWidget.i18n("evilradio.form.wishbox.nameInfo").addId("name-info"));
      nameAgeContainer.addEntry(nameContainer);

      FlexibleContentWidget ageContainer = new FlexibleContentWidget().addId("age-container");
      ageContainer.addContent(ComponentWidget.component(
              Component.translatable("evilradio.form.wishbox.age"))
          .addId("age-title"));
      this.ageInput = new TextFieldWidget().addId("age-input");
      this.ageInput.validator(StringUtil::isNumeric);
      ageContainer.addContent(this.ageInput);
      nameAgeContainer.addEntry(ageContainer);

      FlexibleContentWidget anonymousContainer = new FlexibleContentWidget().addId("anonymous-container");
      anonymousContainer.addContent(ComponentWidget.component(
              Component.translatable("evilradio.form.wishbox.anonymous.name")
                  .append(Component.text(" *", NamedTextColor.YELLOW))
          )
          .addId("anonymous-title"));
      CheckBoxWidget anonymousInput = new CheckBoxWidget().addId("anonymous-input");
      anonymousInput.setPressable(this::updateAnonymous);
      anonymousContainer.addContent(anonymousInput);
      nameAgeContainer.addEntry(anonymousContainer);

      content.addContent(nameAgeContainer);

      FlexibleContentWidget artistContainer = new FlexibleContentWidget().addId("artist-container");
      artistContainer.addContent(ComponentWidget.component(
          Component.translatable("evilradio.form.wishbox.artist.name", NamedTextColor.WHITE)
              .append(Component.text(" *", NamedTextColor.RED))
      ).addId("artist-title"));
      this.artistInput = new TextFieldWidget().placeholder(Component.translatable("evilradio.form.wishbox.artist.placeholder")).addId("artist-input");
      artistContainer.addContent(this.artistInput);
      this.artistCompleteWidget = new VerticalListWidget<>().addId("artist-autocomplete");
      artistContainer.addContent(new ScrollWidget(this.artistCompleteWidget).addId("artist-autocomplete-scroll"));
      content.addContent(artistContainer);

      FlexibleContentWidget songContainer = new FlexibleContentWidget().addId("song-container");
      songContainer.addContent(ComponentWidget.component(
          Component.translatable("evilradio.form.wishbox.song.name", NamedTextColor.WHITE)
              .append(Component.text(" *", NamedTextColor.RED))
      ).addId("song-title"));
      this.songInput = new TextFieldWidget().placeholder(Component.translatable("evilradio.form.wishbox.song.placeholder")).addId("song-input");
      songContainer.addContent(this.songInput);
      this.songCompleteWidget = new VerticalListWidget<>().addId("song-autocomplete");
      songContainer.addContent(new ScrollWidget(this.songCompleteWidget).addId("song-autocomplete-scroll"));
      content.addContent(songContainer);

      this.setupArtistAutoComplete();
      this.setupSongAutoComplete();

      FlexibleContentWidget greetContainer = new FlexibleContentWidget().addId("greet-container");
      greetContainer.addContent(ComponentWidget.i18n("evilradio.form.wishbox.greet").addId("greet-title"));
      this.wishInput = new TextFieldWidget().multiline(6).addId("greet-input");
      greetContainer.addContent(this.wishInput);
      content.addContent(greetContainer);

      content.addContent(ComponentWidget.component(
              Component.text("* ", NamedTextColor.RED)
                  .append(Component.translatable("evilradio.form.wishbox.required", NamedTextColor.WHITE))
          )
          .addId("required-info"));
      content.addContent(ComponentWidget.component(
              Component.text("* ", NamedTextColor.YELLOW)
                  .append(Component.translatable("evilradio.form.wishbox.anonymous.spamInfo", NamedTextColor.WHITE))
          )
          .addId("anonymous-spam-info"));

      sendButton = ButtonWidget.i18n("evilradio.form.wishbox.submit.button").addId("submit-button");
      sendButton.setPressable(this::submitWish);

    } else {

      if(currentSong == null) {
        content.addContent(ComponentWidget.i18n("evilradio.form.wishbox.submit.error.notListening", NamedTextColor.GRAY).addId("error-message"));
      } else {
        if(!currentSong.isOnAir()) {
          content.addContent(ComponentWidget.i18n("evilradio.form.wishbox.submit.error.noModerator", NamedTextColor.GRAY).addId("error-message"));
        } else {
          content.addContent(ComponentWidget.i18n("evilradio.form.wishbox.submit.error.moderatorDisabled", NamedTextColor.GRAY).addId("error-message"));
        }
      }

    }

    ButtonWidget closeButton = ButtonWidget.i18n("evilradio.form.wishbox.abort").addId("close-button");
    closeButton.setPressable(() -> {
      Laby.labyAPI().minecraft().minecraftWindow().displayScreen(this.previousScreen);
    });

    FlexibleContentWidget buttonContainer = new FlexibleContentWidget().addId("button-container");
    if(sendButton != null) {
      buttonContainer.addContent(sendButton);
    }
    buttonContainer.addContent(closeButton);
    content.addContent(buttonContainer);

    container.addContent(content);

    this.document.addChild(container);
  }

  private boolean suppressAutoComplete = false;
  private String selectedArtist;

  private void setupArtistAutoComplete() {
    if(this.artistInput == null || this.songInput == null || this.artistCompleteWidget == null) return;
    this.artistInput.updateListener((query) -> {
      if(this.suppressAutoComplete) return;
      if(query.equals(this.selectedArtist)) return;
      if(query.length() < 3) {
        ThreadSafe.executeOnRenderThread(() ->this.artistCompleteWidget.getChildren().clear());
        return;
      }

      Debounce.of("evilArtistAutoComplete", 2*1000, () -> {
        this.addon.logger().info("Requesting auto complete data for query: {}", query);
        Request.ofGson(JsonObject.class)
            .url(EvilConstants.DEEZER_SEARCH_ENDPOINT + URLEncoder.encode(query, StandardCharsets.UTF_8))
            .method(Method.GET)
            .userAgent(this.addon.apiUserAgent())
            .connectTimeout(5*1000)
            .readTimeout(5*1000)
            .async()
            .execute(response -> {
              if(!query.equals(this.artistInput.getText())) return;
              ThreadSafe.executeOnRenderThread(() ->this.artistCompleteWidget.getChildren().clear());
              if(response.hasException()) {
                this.addon.logger().error("Failed to request auto complete data.", response.exception());
                return;
              }
              JsonObject jsonObject = response.get();
              this.addon.logger().info("Received auto complete data: {}", jsonObject);
              if(!jsonObject.has("recordings")) return;
              if(!jsonObject.get("recordings").isJsonArray()) return;
              JsonArray recordings = jsonObject.get("recordings").getAsJsonArray();
              if(recordings.isEmpty()) return;
              recordings.forEach((recording) -> {
                if(!recording.isJsonObject()) return;
                JsonObject recordingObject = recording.getAsJsonObject();
                ThreadSafe.executeOnRenderThread(() -> {
                  AutoCompleteWidget widget = new AutoCompleteWidget(
                      recordingObject.has("image") ? recordingObject.get("image").getAsString() : "",
                      recordingObject.has("artist-credit") ?
                          recordingObject.get("artist-credit").getAsJsonArray().get(0).getAsJsonObject().get("name").getAsString()
                          : "Unknown",
                      recordingObject.get("title").getAsString(),
                      recordingObject.has("album_name") ? recordingObject.get("album_name").getAsString() : "",
                      recordingObject.has("release_date") ? recordingObject.get("release_date").getAsString() : "Unknown",
                      recordingObject.has("duration_ms") ? recordingObject.get("duration_ms").getAsLong() : 0L,
                      recordingObject.has("preview") ? recordingObject.get("preview").getAsString() : ""
                  );
                  widget.setPressable(() -> {
                    this.selectedArtist = widget.getArtist();
                    this.selectedTitle = widget.getTitle();
                    this.suppressAutoComplete = true;
                    try {
                      this.artistInput.setText(widget.getArtist());
                      this.songInput.setText(widget.getTitle());
                    } finally {
                      this.suppressAutoComplete = false;
                    }
                    this.artistCompleteWidget.getChildren().clear();
                    this.songCompleteWidget.getChildren().clear();
                  });
                  this.artistCompleteWidget.addChildInitialized(widget);
                });
              });
            });
      });

    });
  }

  private String selectedTitle;

  private void setupSongAutoComplete() {
    if(this.artistInput == null || this.songInput == null || this.songCompleteWidget == null) return;
    this.songInput.updateListener((query) -> {
      if(this.suppressAutoComplete) return;
      if(query.equals(this.selectedTitle)) return;
      if(query.length() < 3) {
        ThreadSafe.executeOnRenderThread(() ->this.songCompleteWidget.getChildren().clear());
        return;
      }

      Debounce.of("evilSongAutoComplete", 2*1000, () -> {
        this.addon.logger().info("Requesting auto complete data for query: {}", query);
        Request.ofGson(JsonObject.class)
            .url(EvilConstants.DEEZER_SEARCH_ENDPOINT + URLEncoder.encode(query, StandardCharsets.UTF_8))
            .method(Method.GET)
            .userAgent(this.addon.apiUserAgent())
            .connectTimeout(5*1000)
            .readTimeout(5*1000)
            .async()
            .execute(response -> {
              if(!query.equals(this.songInput.getText())) return;
              ThreadSafe.executeOnRenderThread(() ->this.songCompleteWidget.getChildren().clear());
              if(response.hasException()) {
                this.addon.logger().error("Failed to request auto complete data.", response.exception());
                return;
              }
              JsonObject jsonObject = response.get();
              this.addon.logger().info("Received auto complete data: {}", jsonObject);
              if(!jsonObject.has("recordings")) return;
              if(!jsonObject.get("recordings").isJsonArray()) return;
              JsonArray recordings = jsonObject.get("recordings").getAsJsonArray();
              if(recordings.isEmpty()) return;
              recordings.forEach((recording) -> {
                if(!recording.isJsonObject()) return;
                JsonObject recordingObject = recording.getAsJsonObject();
                ThreadSafe.executeOnRenderThread(() -> {
                  AutoCompleteWidget widget = new AutoCompleteWidget(
                      recordingObject.has("image") ? recordingObject.get("image").getAsString() : "",
                      recordingObject.has("artist-credit") ?
                          recordingObject.get("artist-credit").getAsJsonArray().get(0).getAsJsonObject().get("name").getAsString()
                          : "Unknown",
                      recordingObject.get("title").getAsString(),
                      recordingObject.has("album_name") ? recordingObject.get("album_name").getAsString() : "",
                      recordingObject.has("release_date") ? recordingObject.get("release_date").getAsString() : "Unknown",
                      recordingObject.has("duration_ms") ? recordingObject.get("duration_ms").getAsLong() : 0L,
                      recordingObject.has("preview") ? recordingObject.get("preview").getAsString() : ""
                  );
                  widget.setPressable(() -> {
                    this.selectedArtist = widget.getArtist();
                    this.selectedTitle = widget.getTitle();
                    this.suppressAutoComplete = true;
                    try {
                      this.artistInput.setText(widget.getArtist());
                      this.songInput.setText(widget.getTitle());
                    } finally {
                      this.suppressAutoComplete = false;
                    }
                    this.artistCompleteWidget.getChildren().clear();
                    this.songCompleteWidget.getChildren().clear();
                    this.addon.radioManager().stopPreview();
                  });
                  this.songCompleteWidget.addChildInitialized(widget);
                });
              });
            });
      });

    });
  }

  @Override
  public void onCloseScreen() {
    this.addon.radioManager().stopPreview();
    super.onCloseScreen();
  }

  private void updateAnonymous() {
    this.anonymous = !this.anonymous;
    this.nameTitle.setComponent(Component.translatable("evilradio.form.wishbox.name"
        ).append(!this.anonymous ? Component.text(" *", NamedTextColor.RED) : Component.empty()));
    if(this.anonymous) {
      this.nameInput.setText("");
      this.ageInput.setText("");
      this.nameInput.setEditable(false);
      this.ageInput.setEditable(false);
    } else {
      this.nameInput.setEditable(true);
      this.ageInput.setEditable(true);
    }
  }

  private void submitWish() {
    if(this.submitting.get()) return;

    String interpret = this.artistInput.getText();
    String title = this.songInput.getText();
    String name = this.nameInput.getText();
    String age = this.ageInput.getText();

    if(interpret.isEmpty() || title.isEmpty()) {
      this.addon.notification(
          Component.translatable("evilradio.form.wishbox.submit.error.title", NamedTextColor.RED),
          Component.translatable("evilradio.form.wishbox.submit.error.empty", NamedTextColor.GRAY)
      );
      return;
    }

    String finalName = name;
    String finalAge = age;
    if(this.anonymous) {
      finalName = "Anonym";
      finalAge = "0";
    }
    if(!this.anonymous && name.isEmpty()) {
      finalName = this.addon.labyAPI().getName();
    }

    HashMap<String, String> body = new HashMap<>();
    body.put("wishbox_submit", "submit");
    body.put("application", "Laby Addon (" + this.addon.addonVersion() + ")");
    body.put("name", finalName);
    body.put("age", finalAge);
    body.put("interpret", interpret);
    body.put("title", title);
    body.put("wish", this.wishInput.getText().isEmpty() ? "! Keine Angabe !" : this.wishInput.getText());
    // This will only be used for blocking the user, for example, if they are spamming the wish box
    body.put("mc_username", this.addon.labyAPI().getName());
    body.put("mc_uuid", this.addon.labyAPI().getUniqueId().toString());

    if(!this.submitting.compareAndSet(false, true)) return;

    Request.ofString()
        .url(EvilConstants.SCHEDULE_ENDPOINT)
        .userAgent("EvilRadio Laby Addon (" + this.addon.addonVersion() + ")")
        .method(Method.POST)
        .async()
        .body(body)
        .execute(response -> {
          if(response.hasException() || response.getStatusCode() != 200) {
            this.addon.notification(
                Component.translatable("evilradio.form.wishbox.submit.error.title", NamedTextColor.RED),
                Component.text(decodeErrorMessage(response.getStatusCode()), NamedTextColor.GRAY)
            );
            this.addon.logger().info("Wishbox Status: {}", response.getStatusCode());
            this.submitting.set(false);
            return;
          }
          this.addon.notification(
              Component.translatable("evilradio.form.wishbox.title", NamedTextColor.GREEN),
              Component.translatable("evilradio.form.wishbox.submit.success", NamedTextColor.GRAY)
          );
          if(!this.anonymous && name.isEmpty()) {
            this.addon.notification(
                Component.translatable("evilradio.form.wishbox.title", NamedTextColor.YELLOW),
                Component.translatable("evilradio.form.wishbox.submit.nameInformation", NamedTextColor.GRAY)
            );
          }
          this.addon.labyAPI().minecraft().executeNextTick(
              () -> Laby.labyAPI().minecraft().minecraftWindow().displayScreen(this.previousScreen));
        });
  }

  private String decodeErrorMessage(int statusCode) {
    if(statusCode == 400) return "Server Error";
    if(statusCode == 409) return I18n.translate("evilradio.form.wishbox.submit.error.cooldown");
    if(statusCode == 410) return I18n.translate("evilradio.form.wishbox.submit.error.noModerator");
    if(statusCode == 403) return I18n.translate("evilradio.form.wishbox.submit.error.moderatorDisabled");
    return "Unknown Error (" + statusCode + ")";
  }

}
