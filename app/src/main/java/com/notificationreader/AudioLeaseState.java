package com.notificationreader;

import java.util.*;

/** Temporary interaction ownership. Never persisted; each owner must renew a short lease. */
final class AudioLeaseState {
  static final long TTL = 20000;
  private final Map<String, Long> owners = new HashMap<>();

  void hold(String owner, long now) {
    owners.put(owner, now + TTL);
  }

  void release(String owner) {
    owners.remove(owner);
  }

  boolean busy(long now) {
    return busyExcept(null, now);
  }

  boolean busyExcept(String owner, long now) {
    owners.values().removeIf(until -> until <= now);
    for (String key : owners.keySet()) if (!key.equals(owner)) return true;
    return false;
  }

  void clear() {
    owners.clear();
  }
}
