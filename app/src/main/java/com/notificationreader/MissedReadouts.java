package com.notificationreader;

import java.util.*;

/** Bounded, chronological call-deferred history. Successful readouts are removed. */
final class MissedReadouts {
  static final long MAX_AGE = 24 * 60 * 60 * 1000L;
  static final int LIMIT = 100;

  static final class Entry {
    final String id, stream, type, value, body;
    final long time;
    final boolean reaction;

    Entry(
        String id,
        String stream,
        String type,
        String value,
        String body,
        long time,
        boolean reaction) {
      this.id = id;
      this.stream = stream;
      this.type = type;
      this.value = value;
      this.body = body.substring(0, Math.min(body.length(), 400));
      this.time = time;
      this.reaction = reaction;
    }
  }

  private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();

  void add(Entry e, long now) {
    if (!entries.containsKey(e.id)) entries.put(e.id, e);
    prune(now);
  }

  void remove(String id) {
    entries.remove(id);
  }

  List<Entry> recent(long now) {
    prune(now);
    List<Entry> all = new ArrayList<>(entries.values());
    all.sort(Comparator.comparingLong(e -> e.time));
    return all;
  }

  private void prune(long now) {
    entries.values().removeIf(e -> now - e.time > MAX_AGE);
    while (entries.size() > LIMIT) {
      Entry oldest = Collections.min(entries.values(), Comparator.comparingLong(e -> e.time));
      entries.remove(oldest.id);
    }
  }
}
