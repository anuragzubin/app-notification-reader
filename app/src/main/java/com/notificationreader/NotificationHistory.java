package com.notificationreader;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;

/** Content identities survive Android notification-key changes, reposts and service restarts. */
final class NotificationHistory {
  final LinkedHashMap<String, Long> identities = new LinkedHashMap<>();
  final LinkedHashMap<String, Long> newest = new LinkedHashMap<>();

  boolean accept(String conversation, String content, long messageTime) {
    String stream = hash(conversation);
    String id = hash(conversation + "\n" + messageTime + "\n" + content);
    boolean duplicate = identities.containsKey(id);
    long high = newest.getOrDefault(stream, 0L);
    identities.put(id, messageTime);
    if (messageTime > high) newest.put(stream, messageTime);
    trim(identities, 4096);
    trim(newest, 512);
    return !duplicate && (messageTime <= 0 || messageTime >= high);
  }

  private static void trim(LinkedHashMap<String, Long> map, int limit) {
    while (map.size() > limit) map.remove(map.keySet().iterator().next());
  }

  static String hash(String input) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder();
      for (byte b : digest) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
      return result.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
