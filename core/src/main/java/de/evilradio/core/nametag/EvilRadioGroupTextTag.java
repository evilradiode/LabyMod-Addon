package de.evilradio.core.nametag;

import de.evilradio.core.EvilRadioAddon;
import de.evilradio.core.configuration.NameTagSettings.CustomTextDecoration;
import de.evilradio.core.group.Group;
import de.evilradio.core.snapshot.EvilRadioKeys;
import de.evilradio.core.snapshot.EvilRadioUser;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.format.TextColor;
import net.labymod.api.client.entity.player.tag.tags.ComponentNameTag;
import net.labymod.api.client.render.state.entity.EntitySnapshot;
import org.jetbrains.annotations.NotNull;
import java.util.List;

public class EvilRadioGroupTextTag extends ComponentNameTag {

  private EvilRadioAddon addon;
  private Group group;

  public EvilRadioGroupTextTag(EvilRadioAddon addon) {
    this.addon = addon;
  }

  @Override
  public void begin(EntitySnapshot snapshot) {
    this.group = this.getVisibleGroup(snapshot);
    super.begin(snapshot);
  }

  @Override
  protected @NotNull List<Component> buildComponents(EntitySnapshot snapshot) {
    if(this.group == null) return super.buildComponents(snapshot);
    Component groupDisplayName = Component.text().append(Component.text("EVIL-RADIO",
            TextColor.color(this.addon.configuration().nameTagSettings().staffNameTagColor().get().get())
        )).append(Component.space()).build();
    if(this.addon.configuration().nameTagSettings().staffNameTagDecoration().get() != CustomTextDecoration.NONE) {
      groupDisplayName.decorate(this.addon.configuration().nameTagSettings().staffNameTagDecoration().get().labyDecoration());
    }
    groupDisplayName.append(Component.text(this.group.getDisplayName(), this.group.getTextColor()));
    return List.of(groupDisplayName);
  }

  @Override
  public float getHeight() {
    return super.getHeight();
  }

  @Override
  public float getScale() {
    return 0.5F;
  }

  @Override
  public boolean isVisible() {
    return this.group != null && super.isVisible();
  }

  private Group getVisibleGroup(EntitySnapshot snapshot) {
    if(!visible(snapshot)) return null;
    if(!snapshot.has(EvilRadioKeys.USER)) return null;
    EvilRadioUser userSnapshot = snapshot.get(EvilRadioKeys.USER);
    if(shouldHide(userSnapshot)) return null;
    return userSnapshot.getGroup();
  }

  private boolean visible(EntitySnapshot snapshot) {
    return this.addon.configuration().enabled().get() && this.addon.configuration().nameTagSettings().showStaffNametag().get()
        && !snapshot.isDiscrete() && !snapshot.isInvisible();
  }

  private boolean shouldHide(EvilRadioUser snapshot) {
    return snapshot.getUuid() == null;
  }

}
