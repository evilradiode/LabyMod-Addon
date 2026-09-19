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
import java.util.ArrayList;
import java.util.List;

public class RadioStreamSharedTag extends ComponentNameTag {

  private final EvilRadioAddon addon;

  private static final boolean INVERSE_DEPTH = MinecraftVersions.V1_20_6.orOlder();
  private static final float BACKGROUND_DEPTH = -0.03F;

  private Icon icon;
  private HorizontalAlignment alignment = HorizontalAlignment.CENTER;

  public RadioStreamSharedTag(EvilRadioAddon addon) {
    this.addon = addon;
  }

  @Override
  protected @NotNull List<Component> buildComponents(EntitySnapshot snapshot) {
    if (!(this.snapshot instanceof AvatarSnapshot avatar) || avatar.isDiscrete()) {
      return super.buildComponents(snapshot);
    }

    if (!this.snapshot.has(EvilRadioKeys.USER)) return super.buildComponents(snapshot);

    EvilRadioUser evilRadioUser = this.snapshot.get(EvilRadioKeys.USER);
    RadioStreamSharingController.SharedRadioStream sharedRadioStream = evilRadioUser.getSharedRadioStream();
    if (sharedRadioStream == null) return super.buildComponents(snapshot);

    RadioStream radioStream = this.addon.radioStreamService().findStreamByName(
        sharedRadioStream.getStreamId());
    if (radioStream == null) return super.buildComponents(snapshot);

    this.icon = radioStream.getIcon();
    this.alignment = this.icon == null ? HorizontalAlignment.CENTER : HorizontalAlignment.LEFT;

    List<Component> components = new ArrayList<>();

    components.add(Component.translatable("evilradio.widget.currentlyListening"));
    components.add(Component.text(radioStream.getDisplayName()));

    return components;
  }

  @Override
  public void render(
      Stack stack,
      SubmissionCollector submissionCollector,
      EntitySnapshot snapshot
  ) {
    float size = this.getHeight();
    float backgroundWidth = this.getWidth();

    Material guiMaterial = LevelMaterial.builder(RenderStates.GUI).build();

    int backgroundArgb = Laby.labyAPI()
        .minecraft()
        .options()
        .getBackgroundColorWithOpacity(DEFAULT_BACKGROUND_COLOR);
    submissionCollector.submitCustomGeometry(
        stack,
        guiMaterial,
        new ColoredRectangle(
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

  static class ColoredRectangle implements CustomGeometryRenderer {

    private final float left;
    private final float top;
    private final float right;
    private final float bottom;
    private final float depth;
    private final int argb;

    public ColoredRectangle(
        float left, float top, float right, float bottom,
        float depth,
        int argb
    ) {
      this.left = left;
      this.top = top;
      this.right = right;
      this.bottom = bottom;
      this.depth = depth;
      this.argb = argb;
    }

    @Override
    public void render(Matrix4f pose, VertexConsumer consumer) {
      consumer.addVertex(pose, this.left, this.top, this.depth).setBlankUv().setColor(this.argb);
      consumer.addVertex(pose, this.left, this.bottom, this.depth).setBlankUv().setColor(this.argb);
      consumer.addVertex(pose, this.right, this.bottom, this.depth).setBlankUv()
          .setColor(this.argb);
      consumer.addVertex(pose, this.right, this.top, this.depth).setBlankUv().setColor(this.argb);
    }
  }

}
