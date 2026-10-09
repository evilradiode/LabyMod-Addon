package de.evilradio.core.activity.popup;

import de.evilradio.core.EvilConstants;
import de.evilradio.core.EvilRadioAddon;
import de.evilradio.core.EvilTextures;
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
import net.labymod.api.client.gui.screen.widget.widgets.layout.list.HorizontalListWidget;
import net.labymod.api.client.gui.screen.widget.widgets.renderer.IconWidget;
import net.labymod.api.util.I18n;
import net.labymod.api.util.StringUtil;
import net.labymod.api.util.io.web.request.Request;
import net.labymod.api.util.io.web.request.Request.Method;
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
  private TextFieldWidget songInput;
  private TextFieldWidget wishInput;

  private boolean anonymous = false;

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
    if(currentSong != null && (currentSong.getModeratorName() != null && !currentSong.getModeratorName().isEmpty()) && currentSong.isOnAir() && currentSong.isWishBoxEnabled()) {

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
      anonymousContainer.addContent(ComponentWidget.i18n("evilradio.form.wishbox.anonymous").addId("anonymous-title"));
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
      content.addContent(artistContainer);

      FlexibleContentWidget songContainer = new FlexibleContentWidget().addId("song-container");
      songContainer.addContent(ComponentWidget.component(
          Component.translatable("evilradio.form.wishbox.song.name", NamedTextColor.WHITE)
              .append(Component.text(" *", NamedTextColor.RED))
      ).addId("song-title"));
      this.songInput = new TextFieldWidget().placeholder(Component.translatable("evilradio.form.wishbox.song.placeholder")).addId("song-input");
      songContainer.addContent(this.songInput);
      content.addContent(songContainer);

      FlexibleContentWidget greetContainer = new FlexibleContentWidget().addId("greet-container");
      greetContainer.addContent(ComponentWidget.i18n("evilradio.form.wishbox.greet").addId("greet-title"));
      this.wishInput = new TextFieldWidget().multiline(6).addId("greet-input");
      greetContainer.addContent(this.wishInput);
      content.addContent(greetContainer);

      sendButton = ButtonWidget.i18n("evilradio.form.wishbox.submit.button").addId("submit-button");
      sendButton.setPressable(() -> {
        if(this.submitWish()) {
          this.addon.labyAPI().minecraft().executeNextTick(() -> Laby.labyAPI().minecraft().minecraftWindow().displayScreen(this.previousScreen));
        }
      });

    } else {

      if(currentSong == null) {
        content.addContent(ComponentWidget.i18n("evilradio.form.wishbox.submit.error.notListening", NamedTextColor.GRAY).addId("error-message"));
      } else {
        if(!currentSong.isOnAir()) {
          content.addContent(ComponentWidget.i18n("evilradio.form.wishbox.submit.error.noModerator", NamedTextColor.GRAY).addId("error-message"));
        } else if(!currentSong.isWishBoxEnabled()) {
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

  private boolean submitWish() {

    String interpret = this.artistInput.getText();
    String title = this.songInput.getText();
    String name = this.nameInput.getText();
    String age = this.ageInput.getText();

    if(interpret.isEmpty() || title.isEmpty()) {
      this.addon.notification(
          Component.translatable("evilradio.form.wishbox.submit.error.title", NamedTextColor.RED),
          Component.translatable("evilradio.form.wishbox.submit.error.empty", NamedTextColor.GRAY)
      );
      return false;
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

    AtomicBoolean success = new AtomicBoolean(false);
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
          success.set(true);
        });

    return success.get();
  }

  private String decodeErrorMessage(int statusCode) {
    if(statusCode == 400) return "Server Error";
    if(statusCode == 409) return I18n.translate("evilradio.form.wishbox.submit.error.cooldown");
    if(statusCode == 410) return I18n.translate("evilradio.form.wishbox.submit.error.noModerator");
    if(statusCode == 403) return I18n.translate("evilradio.form.wishbox.submit.error.moderatorDisabled");
    return "Unknown Error (" + statusCode + ")";
  }

  /*
  void submitWish() async {

    var response = await http.post(Uri.parse(Constants.SENDEPLAN_ENDPOINT),
        headers: <String, String> {
          'User-Agent': 'App',
        },
        body: body
    );

    if(response.statusCode == 200) {
      setState(() {
        //Internal.wishSent = true;
      });
      Navigator.of(context).pop();
      Util.dialog(context, AppLocalizations.of(context)!.success, TextStyle(color: Colors.green), AppLocalizations.of(context)!.wishbox_success, TextStyle(color: AppColor.fontColor()));
    } else {
      Navigator.of(context).pop();
      String errorMessage = decodeErrorMessage(response.statusCode);
      Util.dialog(context, AppLocalizations.of(context)!.error, TextStyle(color: AppColor.errorColor()), AppLocalizations.of(context)!.error_occurred + "\n${errorMessage}", TextStyle(color: AppColor.errorColor()));
    }

  }

  bool anonymous = false;
  String userName = '';
  String userAge = '';
  String userInterpret = '';
  String userTitle = '';
  String userWish = '';

  void resetData() {
    setState(() {
      userName = '';
      userAge = '';
      userInterpret = '';
      userTitle = '';
      userWish = '';
      anonymous = false;
    });
  }
   */

}
