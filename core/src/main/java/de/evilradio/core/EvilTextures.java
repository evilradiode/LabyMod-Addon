package de.evilradio.core;

import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.resources.ResourceLocation;
import net.labymod.api.client.resources.texture.ThemeTextureLocation;

public class EvilTextures {

  public static final Icon LOGO = Icon.texture(
      ResourceLocation.create("evilradio", "textures/logo.png"));

  public static class SpriteControls {

    public static final ThemeTextureLocation TEXTURE = ThemeTextureLocation.of("evilradio:controls", 20, 20);

    public static final Icon PAUSE = Icon.sprite(TEXTURE, 0, 0, 10);
    public static final Icon PLAY = Icon.sprite(TEXTURE, 1, 0, 10);
    public static final Icon NEXT = Icon.sprite(TEXTURE, 0, 1, 10);
    public static final Icon PREVIOUS = Icon.sprite(TEXTURE, 1, 1, 10);

  }

}
