package de.evilradio.core.nametag;

import de.evilradio.core.EvilRadioAddon;
import de.evilradio.core.radio.RadioStream;
import de.evilradio.core.sharing.RadioStreamSharingController;
import de.evilradio.core.snapshot.EvilRadioKeys;
import de.evilradio.core.snapshot.EvilRadioUser;
import net.labymod.api.Laby;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.entity.player.tag.tags.ComponentNameTag;
import net.labymod.api.client.gfx.pipeline.renderer.text.FontFlags;
import net.labymod.api.client.gui.HorizontalAlignment;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.render.matrix.Stack;
import net.labymod.api.client.render.state.entity.AvatarSnapshot;
import net.labymod.api.client.render.state.entity.EntitySnapshot;
import net.labymod.api.laby3d.pipeline.RenderStates;
import net.labymod.api.laby3d.pipeline.material.LevelMaterial;
import net.labymod.api.laby3d.pipeline.material.Material;
import net.labymod.api.laby3d.render.queue.CustomGeometryRenderer;
import net.labymod.api.laby3d.render.queue.SubmissionCollector;
import net.labymod.api.laby3d.render.queue.submissions.IconSubmission.DisplayMode;
import net.labymod.api.loader.MinecraftVersions;
import net.labymod.laby3d.api.vertex.VertexConsumer;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RadioStreamSharedTag extends ComponentNameTag {

  private static final boolean INVERSE_DEPTH = MinecraftVersions.V1_20_6.orOlder();
  private static final float BACKGROUND_DEPTH = -0.03F;
  private static final Material GUI_MATERIAL = LevelMaterial.builder(RenderStates.GUI).build();
  private static final Component LISTENING_COMPONENT =
      Component.translatable("evilradio.widget.currentlyListening");
  private static final Map<String, CachedSharedTag> COMPONENT_CACHE = new ConcurrentHashMap<>();
  private static final ArrayDeque<ColoredRectangle> RECTANGLE_POOL = new ArrayDeque<>();

  private final EvilRadioAddon addon;

  private Icon icon;
  private HorizontalAlignment alignment = HorizontalAlignment.CENTER;

  public RadioStreamSharedTag(EvilRadioAddon addon) {
    this.addon = addon;
  }

  @Override
  protected @NotNull List<Component> buildComponents(EntitySnapshot snapshot) {
    this.icon = null;
    this.alignment = HorizontalAlignment.CENTER;

    if (!this.addon.configuration().enabled().get()) {
      return super.buildComponents(snapshot);
    }
    if (!(this.snapshot instanceof AvatarSnapshot avatar) || avatar.isDiscrete()) {
      return super.buildComponents(snapshot);
    }
    if (!this.snapshot.has(EvilRadioKeys.USER)) {
      return super.buildComponents(snapshot);
    }

    EvilRadioUser evilRadioUser = this.snapshot.get(EvilRadioKeys.USER);
    RadioStreamSharingController.SharedRadioStream sharedRadioStream =
        evilRadioUser.getSharedRadioStream();
    if (sharedRadioStream == null) {
      return super.buildComponents(snapshot);
    }

    String streamId = sharedRadioStream.streamId();
    RadioStream radioStream = this.addon.radioStreamService().findStreamByName(streamId);
    if (radioStream == null) {
      COMPONENT_CACHE.remove(streamId);
      return super.buildComponents(snapshot);
    }

    CachedSharedTag cached = COMPONENT_CACHE.get(streamId);
    String displayName = radioStream.getDisplayName();
    if (cached == null || !cached.displayName.equals(displayName)
        || cached.icon != radioStream.getIcon()) {
      cached = new CachedSharedTag(displayName, radioStream.getIcon());
      COMPONENT_CACHE.put(streamId, cached);
    }

    this.icon = cached.icon;
    this.alignment = this.icon == null ? HorizontalAlignment.CENTER : HorizontalAlignment.LEFT;
    return cached.components;
  }

  @Override
  public void render(
      Stack stack,
      SubmissionCollector submissionCollector,
      EntitySnapshot snapshot
  ) {
    float size = this.getHeight();
    float backgroundWidth = this.getWidth();

    int backgroundArgb = Laby.labyAPI()
        .minecraft()
        .options()
        .getBackgroundColorWithOpacity(DEFAULT_BACKGROUND_COLOR);
    submissionCollector.submitCustomGeometry(
        stack,
        GUI_MATERIAL,
        ColoredRectangle.obtain(
            -3.0F, -1.0F,
            backgroundWidth + 2.0F, size + 1.0F,
            INVERSE_DEPTH ? -BACKGROUND_DEPTH : BACKGROUND_DEPTH,
            backgroundArgb
        )
    );

    super.render(stack, submissionCollector, snapshot);
    if (this.icon != null) {
      submissionCollector.submitIcon(
          stack,
          this.icon,
          DisplayMode.NORMAL,
          -2, 0,
          size, size,
          -1
      );
    }

  }

  @Override
  protected void submitText(
      Stack stack,
      SubmissionCollector submissionCollector,
      EntitySnapshot snapshot,
      Component component,
      float xOffset, float yOffset
  ) {
    if (this.alignment == HorizontalAlignment.LEFT) {
      xOffset = this.getHeight() + 1.0F;
    } else if (this.alignment == HorizontalAlignment.CENTER) {
      xOffset = (this.getWidth() - this.fontRenderer.getWidth(component)) / 2.0F;
    }

    submissionCollector.order(3).submitComponent(
        stack,
        component,
        xOffset, yOffset,
        DEFAULT_TEXT_COLOR,
        snapshot.lightCoords(),
        this.getBackgroundColor(snapshot),
        FontFlags.DISPLAY_MODE_NORMAL
    );
  }

  @Override
  protected int getBackgroundColor(EntitySnapshot snapshot) {
    return 0;
  }

  @Override
  public float getScale() {
    return 0.5F;
  }

  @Override
  public float getWidth() {
    return super.getWidth() + (this.icon != null ? this.getHeight() : 0);
  }

  @Override
  public float getHeight() {
    return super.getHeight();
  }

  @Override
  public boolean isDiscrete(EntitySnapshot snapshot) {
    return true;
  }

  private static final class CachedSharedTag {

    private final String displayName;
    private final Icon icon;
    private final List<Component> components;

    private CachedSharedTag(String displayName, Icon icon) {
      this.displayName = displayName;
      this.icon = icon;
      this.components = List.of(LISTENING_COMPONENT, Component.text(displayName));
    }
  }

  static class ColoredRectangle implements CustomGeometryRenderer {

    private float left;
    private float top;
    private float right;
    private float bottom;
    private float depth;
    private int argb;

    static ColoredRectangle obtain(
        float left, float top, float right, float bottom,
        float depth,
        int argb
    ) {
      ColoredRectangle rectangle = RECTANGLE_POOL.pollFirst();
      if (rectangle == null) {
        rectangle = new ColoredRectangle();
      }
      rectangle.left = left;
      rectangle.top = top;
      rectangle.right = right;
      rectangle.bottom = bottom;
      rectangle.depth = depth;
      rectangle.argb = argb;
      return rectangle;
    }

    @Override
    public void render(Matrix4f pose, VertexConsumer consumer) {
      consumer.addVertex(pose, this.left, this.top, this.depth).setBlankUv().setColor(this.argb);
      consumer.addVertex(pose, this.left, this.bottom, this.depth).setBlankUv().setColor(this.argb);
      consumer.addVertex(pose, this.right, this.bottom, this.depth).setBlankUv()
          .setColor(this.argb);
      consumer.addVertex(pose, this.right, this.top, this.depth).setBlankUv().setColor(this.argb);
      RECTANGLE_POOL.offerLast(this);
    }
  }

}
