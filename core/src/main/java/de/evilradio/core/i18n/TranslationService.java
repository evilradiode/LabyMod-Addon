package de.evilradio.core.i18n;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.evilradio.core.EvilRadioAddon;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class TranslationService {

  private static TranslationService instance;
  private final EvilRadioAddon addon;
  private Map<String, String> translations = new HashMap<>();
  private String currentLanguage = "de_de";

  private TranslationService(EvilRadioAddon addon) {
    this.addon = addon;
  }

  public static TranslationService getInstance() {
    return instance;
  }

  public static void initialize(EvilRadioAddon addon) {
    instance = new TranslationService(addon);
    instance.loadTranslations();
  }

  private void loadTranslations() {
    // Bestimme die aktuelle Sprache basierend auf der System-Sprache
    String systemLanguage = System.getProperty("user.language", "en");
    
    // Konvertiere System-Sprachcode zu unserem Format (z.B. "de" -> "de_de", "en" -> "en_us")
    if (systemLanguage.startsWith("de")) {
      currentLanguage = "de_de";
    } else {
      currentLanguage = "en_us";
    }

    loadLanguageFile(currentLanguage);
  }

  private void loadLanguageFile(String language) {
    String resourcePath = "assets/evilradio/i18n/" + language + ".json";
    
    try {
      InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath);
      if (inputStream == null) {
        addon.logger().warn("Could not find translation file: " + resourcePath);
        // Fallback zu Deutsch
        if (!language.equals("de_de")) {
          loadLanguageFile("de_de");
        }
        return;
      }

      JsonObject jsonObject = JsonParser.parseReader(
          new InputStreamReader(inputStream, StandardCharsets.UTF_8)
      ).getAsJsonObject();

      translations.clear();
      flattenJson("", jsonObject, translations);

      addon.logger().info("Loaded translations for language: " + language);
    } catch (Exception e) {
      addon.logger().error("Failed to load translation file: " + resourcePath, e);
      // Fallback zu Deutsch
      if (!language.equals("de_de")) {
        loadLanguageFile("de_de");
      }
    }
  }

  private void flattenJson(String prefix, JsonObject jsonObject, Map<String, String> map) {
    for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
      String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
      
      if (entry.getValue().isJsonObject()) {
        flattenJson(key, entry.getValue().getAsJsonObject(), map);
      } else {
        map.put(key, entry.getValue().getAsString());
      }
    }
  }

  public String translate(String key) {
    return translations.getOrDefault(key, key);
  }

  public String translate(String key, Object... args) {
    String translation = translate(key);
    
    // Einfache String-Ersetzung für %s Platzhalter
    for (Object arg : args) {
      translation = translation.replaceFirst("%s", String.valueOf(arg));
    }
    
    return translation;
  }

  public String getCurrentLanguage() {
    return currentLanguage;
  }

  public void reload() {
    loadTranslations();
  }
}

