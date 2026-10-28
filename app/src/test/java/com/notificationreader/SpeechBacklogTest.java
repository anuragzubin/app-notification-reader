package com.notificationreader;

import static org.junit.Assert.*;

import org.junit.Test;

public class SpeechBacklogTest {
  @Test
  public void waitingSenderKeepsOnlyLatestMessage() {
    SpeechBacklog q = new SpeechBacklog();
    q.offer("Alice", "A");
    SpeechBacklog.Item active = q.next();
    q.offer("Bob", "old");
    q.offer("Bob", "latest");
    assertNull(q.next());
    q.complete(active.id);
    assertEquals("latest", q.next().text);
  }

  @Test
  public void newMessageReplacesSameSendersActiveSpeech() {
    SpeechBacklog q = new SpeechBacklog();
    q.offer("Alice", "old");
    SpeechBacklog.Item old = q.next();
    assertTrue(q.offer("Alice", "new"));
    SpeechBacklog.Item next = q.next();
    assertEquals("new", next.text);
    q.complete(old.id); // Late completion from interrupted speech must not clear the new one.
    assertNull(q.next());
    q.complete(next.id);
    assertNull(q.next());
  }

  @Test
  public void otherSendersAreNotDiscarded() {
    SpeechBacklog q = new SpeechBacklog();
    q.offer("Alice", "A");
    SpeechBacklog.Item first = q.next();
    assertFalse(q.offer("Bob", "B"));
    q.complete(first.id);
    assertEquals("Bob", q.next().sender);
  }

  @Test
  public void cancelDropsSupersededSpeechButKeepsOtherSenders() {
    SpeechBacklog q = new SpeechBacklog();
    q.offer("Alice", "old");
    q.next();
    q.offer("Bob", "B");
    assertTrue(q.cancel("Alice"));
    assertEquals("Bob", q.next().sender);
    q.offer("Alice", "waiting");
    assertFalse(q.cancel("Alice"));
  }

  @Test
  public void callPauseRequeuesInterruptedSpeechAndIgnoresLateCompletion() {
    SpeechBacklog q = new SpeechBacklog();
    q.offer("Alice", "Interrupted");
    SpeechBacklog.Item old = q.next();
    q.offer("Bob", "During call");
    q.pause();
    q.complete(old.id);
    SpeechBacklog.Item resumed = q.next();
    assertEquals("Interrupted", resumed.text);
    assertNotEquals(old.id, resumed.id);
    q.complete(resumed.id);
    assertEquals("During call", q.next().text);
  }

  @Test
  public void latestMessageDuringCallReplacesInterruptedMessage() {
    SpeechBacklog q = new SpeechBacklog();
    q.offer("Alice", "Old");
    q.next();
    q.pause();
    q.cancel("Alice");
    q.offer("Alice", "Latest during call");
    q.pause();
    SpeechBacklog.Item latest = q.next();
    assertEquals("Latest during call", latest.text);
    q.complete(latest.id);
    assertNull(q.next());
  }

  @Test
  public void repeatedCallStatesDoNotDuplicateSpeech() {
    SpeechBacklog q = new SpeechBacklog();
    q.offer("Alice", "Hello");
    q.next();
    q.pause();
    q.pause();
    SpeechBacklog.Item resumed = q.next();
    q.complete(resumed.id);
    assertNull(q.next());
  }

  @Test
  public void quietPeriodDiscardsLiveAndQueuedSpeechWithoutReplayingAtStop() {
    SpeechBacklog q = new SpeechBacklog();
    q.offer("Alice", "Current");
    SpeechBacklog.Item old = q.next();
    q.offer("Bob", "Pending");
    q.clear();
    q.complete(old.id);
    assertNull(q.next());
    q.offer("Alice", "New after quiet hours");
    assertEquals("New after quiet hours", q.next().text);
  }

  @Test
  public void retryRejectsOldDelayedPlaybackAndPreservesMessage() {
    SpeechBacklog q = new SpeechBacklog();
    q.offer("Anita", "Read this");
    SpeechBacklog.Item first = q.next();
    assertTrue(q.isActive(first.id));
    q.pause();
    assertFalse(q.isActive(first.id));
    SpeechBacklog.Item retried = q.next();
    assertNotEquals(first.id, retried.id);
    assertEquals("Read this", retried.text);
    assertTrue(q.isActive(retried.id));
    q.complete(first.id);
    assertTrue(q.isActive(retried.id));
  }
}
