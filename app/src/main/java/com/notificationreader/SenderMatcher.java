package com.notificationreader;

import java.util.Locale;

final class SenderMatcher {
  static String normalize(String s) {
    return s.trim().toLowerCase(Locale.ROOT);
  }

  static boolean matches(String rule, String sender) {
    if (normalize(rule).equals(normalize(sender))) return true;
    if (rule.matches("\\+?[0-9 ()-]{7,}")) {
      String a = rule.replaceAll("[^0-9]", ""), b = sender.replaceAll("[^0-9]", "");
      return sender.matches("\\+?[0-9 ()-]{7,}") && a.equals(b);
    }
    if (rule.contains("@"))
      return java.util.regex.Pattern.compile(
              "(?i)(?<![a-z0-9._%+@-])"
                  + java.util.regex.Pattern.quote(rule.trim())
                  + "(?![a-z0-9._%+@-])")
          .matcher(sender)
          .find();
    return false;
  }
}
