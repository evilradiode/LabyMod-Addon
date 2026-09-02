package de.evilradio.core.configuration;

import de.evilradio.core.radio.AudioEqualizer;
import net.labymod.api.client.gui.screen.widget.widgets.input.SliderWidget.SliderSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.dropdown.DropdownWidget.DropdownEntryTranslationPrefix;
import net.labymod.api.client.gui.screen.widget.widgets.input.dropdown.DropdownWidget.DropdownSetting;
import net.labymod.api.configuration.loader.Config;
import net.labymod.api.configuration.loader.annotation.ConfigName;
import net.labymod.api.configuration.loader.annotation.IntroducedIn;
import net.labymod.api.configuration.loader.annotation.ShowSettingInParent;
import net.labymod.api.configuration.loader.property.ConfigProperty;

@ConfigName("audioEqualizer")
public class AudioEqualizerSubSettings extends Config {

  @ShowSettingInParent
  @IntroducedIn(namespace = "evilradio", value = "1.1.0")
  @DropdownSetting
  @DropdownEntryTranslationPrefix("evilradio.settings.audioEqualizer.preset.type")
  private final ConfigProperty<AudioEqualizer.EqualizerPreset> preset = new ConfigProperty<>(AudioEqualizer.EqualizerPreset.NORMAL);

  @IntroducedIn(namespace = "evilradio", value = "1.1.0")
  @SliderSetting(min = -12, max = 12, steps = 0.5f)
  private final ConfigProperty<Float> customSubBass = new ConfigProperty<>(0.0F);

  @IntroducedIn(namespace = "evilradio", value = "1.1.0")
  @SliderSetting(min = -12, max = 12, steps = 0.5f)
  private final ConfigProperty<Float> customBass = new ConfigProperty<>(0.0F);

  @IntroducedIn(namespace = "evilradio", value = "1.1.0")
  @SliderSetting(min = -12, max = 12, steps = 0.5f)
  private final ConfigProperty<Float> customMid = new ConfigProperty<>(0.0F);

  @IntroducedIn(namespace = "evilradio", value = "1.1.0")
  @SliderSetting(min = -12, max = 12, steps = 0.5f)
  private final ConfigProperty<Float> customPresence = new ConfigProperty<>(0.0F);

  @IntroducedIn(namespace = "evilradio", value = "1.1.0")
  @SliderSetting(min = -12, max = 12, steps = 0.5f)
  private final ConfigProperty<Float> customTreble = new ConfigProperty<>(0.0F);

  public ConfigProperty<AudioEqualizer.EqualizerPreset> preset() {
    return this.preset;
  }

  public ConfigProperty<Float> customSubBass() {
    return this.customSubBass;
  }

  public ConfigProperty<Float> customBass() {
    return this.customBass;
  }

  public ConfigProperty<Float> customMid() {
    return this.customMid;
  }

  public ConfigProperty<Float> customPresence() {
    return this.customPresence;
  }

  public ConfigProperty<Float> customTreble() {
    return this.customTreble;
  }

  public float[] resolveBandGainsDb() {
    if (this.preset.get().isCustom()) {
      return new float[] {
          this.customSubBass.get(),
          this.customBass.get(),
          this.customMid.get(),
          this.customPresence.get(),
          this.customTreble.get()
      };
    }
    return this.preset.get().presetGainsDb();
  }
}
