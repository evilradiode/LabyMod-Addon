package de.evilradio.core.radio;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import de.evilradio.core.EvilRadioAddon;
import net.labymod.api.client.Minecraft;
import net.labymod.api.client.options.MinecraftOptions;

public final class MinecraftSoundDeviceProvider {

  private static final Pattern OPTIONS_SOUND_DEVICE_PATTERN =
      Pattern.compile("^soundDevice:\"(.*)\"\\s*$");

  public static String getSelectedSoundDevice(Minecraft minecraft) {
    if (minecraft == null) {
      return null;
    }

    String fromOptions = readFromMinecraftOptions(minecraft.options());
    if (fromOptions != null) {
      return fromOptions;
    }

    return readFromOptionsFile(EvilRadioAddon.instance().labyAPI().labyModLoader().getGameDirectory().toFile());
  }

  private static String readFromMinecraftOptions(MinecraftOptions options) {
    if (options == null) {
      return null;
    }

    try {
      Method soundDeviceMethod = options.getClass().getMethod("soundDevice");
      Object optionInstance = soundDeviceMethod.invoke(options);
      if (optionInstance == null) {
        return null;
      }

      Object value = optionInstance.getClass().getMethod("get").invoke(optionInstance);
      if (value instanceof String soundDevice && !soundDevice.isBlank()) {
        return soundDevice;
      }
    } catch (ReflectiveOperationException ignored) {
    }

    return null;
  }

  private static String readFromOptionsFile(File optionsFile) {
    if (optionsFile == null || !optionsFile.isFile()) {
      return null;
    }

    try (BufferedReader reader = new BufferedReader(new FileReader(optionsFile))) {
      String line;
      while ((line = reader.readLine()) != null) {
        Matcher matcher = OPTIONS_SOUND_DEVICE_PATTERN.matcher(line.trim());
        if (matcher.matches()) {
          String device = matcher.group(1);
          return device == null || device.isBlank() ? null : device;
        }
      }
    } catch (Exception ignored) {
    }

    return null;
  }
}
