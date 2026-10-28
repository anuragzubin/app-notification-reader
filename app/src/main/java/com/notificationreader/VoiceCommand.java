package com.notificationreader;

import java.util.Locale;
import java.util.regex.*;

final class VoiceCommand {
  static String normalize(String value) {
    return value
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^\\p{L}\\p{N}]+", " ")
        .trim()
        .replaceAll("\\s+", " ");
  }

  static String recipient(String speech) {
    Matcher match = Pattern.compile("^send text to (.+)$").matcher(normalize(speech));
    return match.matches() ? match.group(1) : null;
  }

  static boolean send(String speech) {
    return normalize(speech).equals("send it now");
  }

  static boolean cancel(String speech) {
    return normalize(speech).equals("cancel");
  }

  static boolean stop(String speech) {
    return normalize(speech).equals("stop listening");
  }
}
