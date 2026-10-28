package com.notificationreader;

import java.util.List;

/** Partial recognition can acknowledge a wake phrase, but never execute or send a command. */
final class WakeEvidence {
  private boolean matched;

  void reset() {
    matched = false;
  }

  void partial(List<String> candidates, String phrase) {
    matched = false;
    if (candidates == null) return;
    for (String candidate : candidates) {
      if (candidate != null && VoiceCommand.afterWake(candidate, phrase) != null) {
        matched = true;
        return;
      }
    }
  }

  boolean consume() {
    boolean result = matched;
    reset();
    return result;
  }
}
