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

  static String commandWords(String speech) {
    return normalize(speech)
        .replaceAll("\\bwhat s app\\b|\\bwhats app\\b|\\bwhat app\\b", "whatsapp")
        .replaceAll("\\bg mail\\b|\\bgoogle mail\\b", "gmail")
        .replaceAll("\\bspot ify\\b", "spotify")
        .replaceAll("\\bdeep stash\\b", "deepstash")
        .replaceAll("\\bduo lingo\\b|\\bdual lingo\\b|\\bdo a lingo\\b", "duolingo")
        .replaceAll("\\bdeep stache\\b", "deepstash")
        .replaceAll("\\btry a\\b|\\btraiya\\b|\\btraya health\\b", "traya");
  }

  static String selectCommand(
      java.util.List<String> candidates, String phrase, boolean requireWake, boolean awake) {
    return selectCommand(candidates, phrase, requireWake, awake, java.util.Collections.emptyMap());
  }

  static String selectCommand(
      java.util.List<String> candidates,
      String phrase,
      boolean requireWake,
      boolean awake,
      java.util.Map<String, String> aliases) {
    if (candidates == null || candidates.isEmpty()) return "";
    String selected = candidates.get(0);
    int highest = -1;
    for (String candidate : candidates) {
      if (candidate == null) continue;
      if (stop(candidate)) return candidate;
      String after = afterWake(candidate, phrase);
      if (requireWake && !awake && after == null) continue;
      String command = after != null ? after : candidate;
      int rank =
          AppSpeech.target(command, aliases) != null
              ? 100
              : recipient(command) != null
                  ? 90
                  : after != null && after.isEmpty()
                      ? 70
                      : after != null ? 10 : openTarget(command) != null ? 5 : 0;
      if (rank > highest) {
        highest = rank;
        selected = candidate;
      }
    }
    return selected;
  }

  static String openTarget(String speech) {
    String words = normalize(speech);
    // Permit common complete request forms, never embedded mentions such as "Anita said open".
    words =
        words.replaceFirst(
            "^(?:can you |could you )?(?:please )?(?:open|launch)(?: up)? +", "open ");
    if (!words.startsWith("open ")) {
      // Some providers join a short command and app name into a single word.
      if (words.startsWith("open")) {
        String joined = commandWords(words.substring(4));
        if (AppCatalog.find(joined) != null) return joined;
      }
      return null;
    }
    String target = words.substring(5).trim();
    Matcher chat =
        Pattern.compile("^(?:whatsapp|whats app|what s app|what app) chat (with|of) (.+)$")
            .matcher(target);
    if (chat.matches()) return "whatsapp chat " + chat.group(1) + " " + chat.group(2);
    target = commandWords(target).replaceFirst(" please$", "").trim();
    return target.isEmpty() ? null : target;
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
