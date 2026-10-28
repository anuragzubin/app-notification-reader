package com.notificationreader;

import java.util.*;

/** Exact, explicit aliases only: never fuzzy-match recipients or message text. */
final class AppSpeech {
  static String name(String value) {
    String text = VoiceCommand.commandWords(value);
    if (text.startsWith("the ")) text = text.substring(4);
    if (text.endsWith(" app")) text = text.substring(0, text.length() - 4).trim();
    return text;
  }

  static String resolve(String target, Map<String, String> aliases) {
    if (target == null) return null;
    String name = name(target);
    Set<String> matches = new HashSet<>();
    for (AppCatalog.App app : AppCatalog.ALL) {
      if (app.id.equals(name)) matches.add(app.id);
      String alias = aliases.get(app.id);
      if (alias != null && !alias.trim().isEmpty() && name(alias).equals(name)) matches.add(app.id);
    }
    return matches.size() == 1 ? matches.iterator().next() : null;
  }

  static String target(String command, Map<String, String> aliases) {
    String raw = VoiceCommand.openTarget(command);
    if (raw == null) return null;
    if (raw.startsWith("whatsapp chat with ") || raw.startsWith("whatsapp chat of ")) return raw;
    return resolve(raw, aliases);
  }
}
