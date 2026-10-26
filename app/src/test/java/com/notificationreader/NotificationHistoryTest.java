package com.notificationreader;

import static org.junit.Assert.*;

import org.junit.Test;

public class NotificationHistoryTest {
  @Test
  public void repostsDoNotExpireOrDependOnNotificationKey() {
    NotificationHistory h = new NotificationHistory();
    assertTrue(h.accept("WhatsApp|Alice", "Hello", 100));
    assertTrue(h.accept("WhatsApp|Bob", "Hi", 110));
    assertFalse(h.accept("WhatsApp|Alice", "Hello", 100));
  }

  @Test
  public void olderUndismissedMessagesCannotReturn() {
    NotificationHistory h = new NotificationHistory();
    assertTrue(h.accept("Alice", "Newest", 200));
    assertFalse(h.accept("Alice", "Older", 100));
    assertTrue(h.accept("Alice", "Even newer", 300));
  }

  @Test
  public void identicalNewWhatsAppMessagesStillAlert() {
    NotificationHistory h = new NotificationHistory();
    assertTrue(h.accept("Alice", "OK", 100));
    assertTrue(h.accept("Alice", "OK", 200));
  }

  @Test
  public void emailWithoutTimestampDoesNotRepeat() {
    NotificationHistory h = new NotificationHistory();
    assertTrue(h.accept("Gmail|Alice", "Subject", 0));
    assertTrue(h.accept("Gmail|Bob", "Subject", 0));
    assertFalse(h.accept("Gmail|Alice", "Subject", 0));
    assertTrue(h.accept("Gmail|Alice", "New subject", 0));
  }

  @Test
  public void restoredHistorySuppressesReplaysAndOldMessages() {
    NotificationHistory old = new NotificationHistory();
    old.accept("Alice", "Hello", 200);
    NotificationHistory restored = new NotificationHistory();
    restored.identities.putAll(old.identities);
    restored.newest.putAll(old.newest);
    assertFalse(restored.accept("Alice", "Hello", 200));
    assertFalse(restored.accept("Alice", "Older", 100));
    assertTrue(restored.accept("Alice", "New", 300));
  }

  @Test
  public void onlyHashesAndTimestampsAreRetained() {
    NotificationHistory h = new NotificationHistory();
    h.accept("Private sender", "Private message", 200);
    assertTrue(h.identities.keySet().iterator().next().matches("[a-f0-9]{64}"));
    assertTrue(h.newest.keySet().iterator().next().matches("[a-f0-9]{64}"));
  }
}
