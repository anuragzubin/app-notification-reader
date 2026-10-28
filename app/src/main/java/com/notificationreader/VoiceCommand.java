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

  static String afterWake(String speech, String phrase) {
    String words = normalize(speech), wake = normalize(phrase);
    if (wake.isEmpty()) return null;
    if (words.equals(wake)) return "";
    return words.startsWith(wake + " ") ? words.substring(wake.length() + 1) : null;
  }

  static String openTarget(String speech) {
    String words = normalize(speech);
    return words.startsWith("open ") ? words.substring(5) : null;
  }

  static String internationalPhone(String value) {
    String number = value.trim().replaceAll("[\\s().-]", "");
    return number.matches("\\+[1-9][0-9]{6,14}") ? number.substring(1) : null;
  }

  static boolean yes(String speech) {
    return normalize(speech).equals("yes");
  }

  static boolean no(String speech) {
    return normalize(speech).equals("no");
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
