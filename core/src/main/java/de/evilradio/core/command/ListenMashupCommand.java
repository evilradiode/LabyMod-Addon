package de.evilradio.core.command;

import de.evilradio.core.EvilRadioAddon;
import de.evilradio.core.activity.picker.StationPickerController;
import de.evilradio.core.radio.RadioStream;
import net.labymod.api.client.chat.command.Command;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.format.NamedTextColor;

/**
 * Client-Command zum Starten von Mashup (z. B. aus der Live-Chat-Nachricht).
 */
public class ListenMashupCommand extends Command {

  public static final String COMMAND_NAME = "evilradiolisten";

  private final EvilRadioAddon addon;

  public ListenMashupCommand(EvilRadioAddon addon) {
    super(COMMAND_NAME, "erlive");
    this.addon = addon;
  }

  @Override
  public boolean execute(String prefix, String[] arguments) {
    if (!this.addon.configuration().enabled().get()) {
      this.displayMessage(Component.translatable("evilradio.schedule.listenDisabled")
          .color(NamedTextColor.RED));
      return true;
    }

    RadioStream mashup = null;
    for (RadioStream stream : this.addon.radioStreamService().streams()) {
      if (StationPickerController.isMashup(stream)) {
        mashup = stream;
        break;
      }
    }

    if (!StationPickerController.isPlayable(mashup)) {
      this.displayMessage(Component.translatable("evilradio.schedule.listenUnavailable")
          .color(NamedTextColor.RED));
      return true;
    }

    boolean alreadyPlaying = this.addon.radioManager().isPlaying()
        && this.addon.radioManager().getCurrentStream() != null
        && this.addon.radioManager().getCurrentStream().getId() == mashup.getId();

    this.addon.radioManager().playStream(mashup);

    if (alreadyPlaying) {
      this.displayMessage(Component.translatable("evilradio.schedule.listenAlreadyPlaying")
          .color(NamedTextColor.GRAY));
    } else {
      this.displayMessage(Component.translatable(
              "evilradio.schedule.listenStarted",
              Component.text(mashup.getDisplayName()).color(NamedTextColor.GOLD))
          .color(NamedTextColor.GREEN));
    }
    return true;
  }
}
