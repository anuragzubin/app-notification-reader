package com.notificationreader;

import static org.junit.Assert.*;

import org.junit.Test;

public class MissedReadoutsTest {
  private MissedReadouts.Entry entry(String id, String sender, long time) {
    return new MissedReadouts.Entry(id, sender, "WhatsApp", sender, "Preview " + id, time, false);
  }

  @Test
  public void keepsAllMessagesFromSameSenderForManualReplay() {
    MissedReadouts h = new MissedReadouts();
    h.add(entry("one", "Alice", 100), 100);
    h.add(entry("two", "Alice", 200), 200);
    assertEquals(2, h.recent(300).size());
    assertEquals("one", h.recent(300).get(0).id);
  }

  @Test
  public void notificationRepostsAndRepeatedCallInterruptionsDoNotDuplicateHistory() {
    MissedReadouts h = new MissedReadouts();
    h.add(entry("same", "Alice", 100), 100);
    h.add(entry("same", "Alice", 200), 200);
    assertEquals(1, h.recent(300).size());
    assertEquals(100, h.recent(300).get(0).time);
  }

  @Test
  public void completionRemovesOnlySuccessfullyPlayedItem() {
    MissedReadouts h = new MissedReadouts();
    h.add(entry("one", "Alice", 100), 100);
    h.add(entry("two", "Alice", 200), 200);
    h.remove("two");
    assertEquals("one", h.recent(300).get(0).id);
    h.remove("one");
    assertTrue(h.recent(300).isEmpty());
  }

  @Test
  public void expiresOldPreviews() {
    MissedReadouts h = new MissedReadouts();
    h.add(entry("old", "Alice", 1), 1);
    h.add(entry("recent", "Bob", MissedReadouts.MAX_AGE), MissedReadouts.MAX_AGE);
    assertEquals(1, h.recent(MissedReadouts.MAX_AGE + 2).size());
    assertEquals("recent", h.recent(MissedReadouts.MAX_AGE + 2).get(0).id);
  }

  @Test
  public void limitsHistoryToMostRecentHundredAndOrdersByReceiptTime() {
    MissedReadouts h = new MissedReadouts();
    for (int i = 0; i < 105; i++) h.add(entry("" + i, "Alice", i), 105);
    assertEquals(100, h.recent(106).size());
    assertEquals("5", h.recent(106).get(0).id);
    assertEquals("104", h.recent(106).get(99).id);
  }

  @Test
  public void restoredEntriesPreserveIndependentItemsAndPreviewLimit() {
    MissedReadouts old = new MissedReadouts();
    old.add(entry("one", "Alice", 100), 100);
    old.add(entry("two", "Bob", 200), 200);
    MissedReadouts restored = new MissedReadouts();
    for (MissedReadouts.Entry e : old.recent(300)) restored.add(e, 300);
    assertEquals(2, restored.recent(300).size());
    MissedReadouts.Entry longText =
        new MissedReadouts.Entry("long", "Alice", "Email", "Alice", "a".repeat(500), 300, false);
    assertEquals(400, longText.body.length());
  }
}
