package de.evilradio.core.snapshot;

import de.evilradio.core.EvilRadioAddon;
import de.evilradio.core.group.Group;
import de.evilradio.core.group.GroupService;
import de.evilradio.core.sharing.RadioStreamSharingController;
import net.labymod.api.client.entity.player.Player;
import net.labymod.api.laby3d.renderer.snapshot.AbstractLabySnapshot;
import net.labymod.api.laby3d.renderer.snapshot.Extras;
import java.util.UUID;

public class EvilRadioUser extends AbstractLabySnapshot {

  private UUID uuid;
  private final RadioStreamSharingController.SharedRadioStream sharedRadioStream;
  private Group group;

  public EvilRadioUser(Player player, Extras extras) {
    super(extras);
    this.uuid = player.getUniqueId();
    this.sharedRadioStream = EvilRadioAddon.instance().sharingController().getSharedStreamOf(player.getUniqueId());
    this.group = GroupService.getGroupFromUser(uuid);
  }

  public UUID getUuid() {
    return uuid;
  }

  public RadioStreamSharingController.SharedRadioStream getSharedRadioStream() {
    return sharedRadioStream;
  }

  public Group getGroup() {
    return group;
  }

}
