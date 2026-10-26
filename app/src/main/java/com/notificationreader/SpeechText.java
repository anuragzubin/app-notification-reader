package com.notificationreader;

final class SpeechText {
  static String compose(boolean sender, boolean message, String source, String name, String body) {
    String heading = sender ? source + " from " + name : "";
    String preview = message ? body.substring(0, Math.min(body.length(), 400)) : "";
    return heading.isEmpty() ? preview : preview.isEmpty() ? heading : heading + ". " + preview;
  }
}
