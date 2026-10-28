package com.notificationreader;

import java.util.*;

/** One offer per conversation, after a short pause following its latest completed readout. */
final class ReplyOfferQueue {
  static final long SETTLE_MS = 2500;

  static final class Offer {
    final String stream, token;
    final long due;

    Offer(String stream, String token, long due) {
      this.stream = stream;
      this.token = token;
      this.due = due;
    }
  }

  private final LinkedHashMap<String, Offer> pending = new LinkedHashMap<>();

  void add(String stream, String token, long now) {
    pending.put(stream, new Offer(stream, token, now + SETTLE_MS));
    while (pending.size() > 20) pending.remove(pending.keySet().iterator().next());
  }

  Offer take(long now) {
    return take(now, true);
  }

  Offer take(long now, boolean available) {
    if (!available) return null;
    for (Offer e : new ArrayList<>(pending.values()))
      if (e.due <= now) {
        pending.remove(e.stream);
        return e;
      }
    return null;
  }

  void remove(String token) {
    pending.values().removeIf(e -> e.token.equals(token));
  }

  List<Offer> snapshot() {
    return new ArrayList<>(pending.values());
  }

  void clear() {
    pending.clear();
  }
}
