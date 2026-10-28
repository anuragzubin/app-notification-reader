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
  public void interruptionClearsAllWaitingOffers() {
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
