package de.evilradio.core.snapshot;

import net.labymod.api.client.entity.player.Player;
import net.labymod.api.laby3d.renderer.snapshot.Extras;
import net.labymod.api.laby3d.renderer.snapshot.LabySnapshotFactory;
import net.labymod.api.service.annotation.AutoService;

@AutoService(LabySnapshotFactory.class)
public class EvilRadioUserSnapshotFactory extends LabySnapshotFactory<Player, EvilRadioUser> {

  public EvilRadioUserSnapshotFactory() {
    super(EvilRadioKeys.USER);
  }

  @Override
  protected EvilRadioUser create(Player player, Extras extras) {
    return new EvilRadioUser(player, extras);
  }
}
