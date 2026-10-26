package com.notificationreader;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** WhatsApp exposes reaction text, not a documented reaction flag. Match conservatively. */
final class ReactionDetector {
  private static final Pattern REACTION =
      Pattern.compile("^Reacted(?: with)?\\s+(.{1,32}?)\\s+to(?:\\s|:)", Pattern.CASE_INSENSITIVE);

  static boolean isReaction(String text) {
    Matcher match = REACTION.matcher(text.trim());
    if (!match.find()) return false;
    String emoji = match.group(1);
    // Restrict the reaction token to symbols/emoji; ordinary prose must keep speaking.
    boolean hasSymbol = false;
    for (int offset = 0; offset < emoji.length(); ) {
      int point = emoji.codePointAt(offset);
      if (Character.isLetterOrDigit(point)) return false;
      if (Character.getType(point) == Character.OTHER_SYMBOL) hasSymbol = true;
      offset += Character.charCount(point);
    }
    return hasSymbol;
  }
}
