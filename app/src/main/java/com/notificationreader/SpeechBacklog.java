package com.notificationreader;

import java.util.LinkedHashMap;
import java.util.UUID;

/** At most one waiting utterance per sender; newer messages replace that sender's old speech. */
final class SpeechBacklog {
  static final class Item {
    final String sender, text, id;

    Item(String sender, String text) {
      this.sender = sender;
      this.text = text;
      this.id = UUID.randomUUID().toString();
    }
  }

  private final LinkedHashMap<String, String> pending = new LinkedHashMap<>();
  private Item active;

  void clear() {
    pending.clear();
    active = null;
  }

  boolean offer(String sender, String text) {
    pending.put(sender, text);
    if (active != null && active.sender.equals(sender)) {
      active = null;
      return true;
    }
    return false;
  }

  void pause() {
    if (active == null) return;
    // Replay the interrupted utterance from its start; retain only the newest per sender.
    LinkedHashMap<String, String> restored = new LinkedHashMap<>();
    restored.put(active.sender, active.text);
    restored.putAll(pending);
    pending.clear();
    pending.putAll(restored);
    active = null;
  }

  boolean cancel(String sender) {
    pending.remove(sender);
    if (active != null && active.sender.equals(sender)) {
      active = null;
      return true;
    }
    return false;
  }

  java.util.List<Item> snapshot() {
    java.util.List<Item> items = new java.util.ArrayList<>();
    if (active != null) items.add(active);
    for (java.util.Map.Entry<String, String> e : pending.entrySet())
      items.add(new Item(e.getKey(), e.getValue()));
    return items;
  }

  Item next() {
    if (active != null || pending.isEmpty()) return null;
    String key = pending.keySet().iterator().next();
    active = new Item(key, pending.remove(key));
    return active;
  }

  void complete(String id) {
    if (active != null && active.id.equals(id)) active = null;
  }
}
