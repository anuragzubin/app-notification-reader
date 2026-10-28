package com.notificationreader;

import static org.junit.Assert.*;

import org.junit.Test;

public class ReplyOfferQueueTest {
  @Test
  public void waitsAfterFinalReadoutBeforeAsking() {
    ReplyOfferQueue q = new ReplyOfferQueue();
    q.add("Anita", "one", 1000);
    assertNull(q.take(3499));
    assertEquals("one", q.take(3500).token);
    assertNull(q.take(4000));
  }

  @Test
  public void burstFromSameContactResetsPauseAndOffersOnlyLatest() {
    ReplyOfferQueue q = new ReplyOfferQueue();
    q.add("Anita", "first", 1000);
    q.add("Anita", "last", 2000);
    assertNull(q.take(3500));
    assertEquals("last", q.take(4500).token);
    assertNull(q.take(9000));
  }

  @Test
  public void differentContactsKeepSeparateOffers() {
    ReplyOfferQueue q = new ReplyOfferQueue();
    q.add("Anita", "a", 0);
    q.add("Bob", "b", 1000);
    assertEquals("a", q.take(2500).token);
    assertNull(q.take(3000));
    assertEquals("b", q.take(3500).token);
  }

  @Test
  public void revokedOrClearedNotificationCannotBeOffered() {
    ReplyOfferQueue q = new ReplyOfferQueue();
    q.add("Anita", "a", 0);
    q.remove("a");
    assertNull(q.take(9000));
  }

  @Test
  public void lockedReadoutWaitsForUnlockRatherThanLosingTheOffer() {
    ReplyOfferQueue q = new ReplyOfferQueue();
    q.add("Anita", "read-while-locked", 1000);
    assertNull(q.take(5000, false));
    assertEquals(1, q.snapshot().size());
    assertEquals("read-while-locked", q.take(6000, true).token);
    assertNull(q.take(7000, true));
  }

  @Test
  public void newerReadoutWhileLockedReplacesTheOldReplyOffer() {
    ReplyOfferQueue q = new ReplyOfferQueue();
    q.add("Anita", "old", 0);
    assertNull(q.take(5000, false));
    q.add("Anita", "new", 5000);
    assertNull(q.take(7000, true));
    assertEquals("new", q.take(7500, true).token);
  }

  @Test
  public void revokedOfferWhileLockedCannotBeAskedAfterUnlock() {
    ReplyOfferQueue q = new ReplyOfferQueue();
    q.add("Anita", "expired", 0);
    assertNull(q.take(5000, false));
    q.remove("expired");
    assertNull(q.take(6000, true));
  }

  @Test
  public void explicitClearRemovesAllWaitingOffers() {
    ReplyOfferQueue q = new ReplyOfferQueue();
    q.add("Anita", "a", 0);
    q.add("Bob", "b", 0);
    q.clear();
    assertNull(q.take(9000));
  }

  @Test
  public void repeatedUpdateCannotCauseRepeatedQuestions() {
    ReplyOfferQueue q = new ReplyOfferQueue();
    q.add("Anita", "a", 0);
    q.add("Anita", "a", 100);
    assertEquals(1, q.snapshot().size());
    assertEquals("a", q.take(2600).token);
    assertNull(q.take(9000));
  }
}
