package de.evilradio.core.group;

import net.labymod.api.client.component.format.NamedTextColor;
import net.labymod.api.client.component.format.TextColor;
import java.awt.*;

public class Group {

  private String id;
  private int sorting;
  private String displayName;
  private String colorHex;

  private transient Color color = Color.WHITE;
  private transient TextColor textColor = NamedTextColor.WHITE;

  public Group(String id, int sorting, String displayName, String colorHex) {
    this.id = id;
    this.sorting = sorting;
    this.displayName = displayName;
    this.colorHex = colorHex;
  }

  public void initialize() {
    try {
      if(this.colorHex != null && !this.colorHex.isEmpty()) {
        this.color = Color.decode(this.colorHex);
        this.textColor = TextColor.color(this.color.getRGB());
      }
    } catch (Exception ignored) {}
  }

  public String getId() {
    return id;
  }

  public int getSorting() {
    return sorting;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getColorHex() {
    return colorHex;
  }

  public Color getColor() {
    return color;
  }

  public TextColor getTextColor() {
    return textColor;
  }

  @Override
  public String toString() {
    return "Group{" +
        "id=" + id +
        ", sorting='" + sorting + '\'' +
        ", displayName='" + displayName + '\'' +
        ", colorHex='" + colorHex + '\'' +
        ", color=" + color +
        ", textColor=" + textColor +
        '}';
  }

}
