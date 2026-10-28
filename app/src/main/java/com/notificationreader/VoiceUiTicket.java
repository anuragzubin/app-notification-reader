package com.notificationreader;

/** One command dialog may return once, and only within its live session's 90-second window. */
final class VoiceUiTicket {
  private String token;
  private long expires;

  void issue(String value, long now) {
    token = value;
    expires = now + 90000;
  }

  boolean valid(String value, long now) {
    return value != null && value.equals(token) && now < expires;
  }

  boolean consume(String value, long now) {
    if (!valid(value, now)) return false;
    clear();
    return true;
  }

  void clear() {
    token = null;
    expires = 0;
  }
}
