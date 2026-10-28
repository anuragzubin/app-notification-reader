package com.notificationreader;

import static org.junit.Assert.*;

import org.junit.Test;

public class AudioLeaseStateTest {
  @Test
  public void abandonedReplyCannotHoldReadoutsForever() {
    AudioLeaseState s = new AudioLeaseState();
    s.hold("reply", 100);
    assertTrue(s.busy(200));
    assertFalse(s.busy(100 + AudioLeaseState.TTL));
  }

  @Test
  public void processRestartHasNoPersistedMute() {
    AudioLeaseState old = new AudioLeaseState();
    old.hold("dead service", 100);
    AudioLeaseState restarted = new AudioLeaseState();
    assertFalse(restarted.busy(101));
  }

  @Test
  public void oldOwnerCannotReleaseNewConversation() {
    AudioLeaseState s = new AudioLeaseState();
    s.hold("new reply", 100);
    s.release("old reply");
    assertTrue(s.busy(200));
    s.release("new reply");
    assertFalse(s.busy(201));
  }

  @Test
  public void onlyRenewedOwnerRemainsActive() {
    AudioLeaseState s = new AudioLeaseState();
    s.hold("UI", 0);
    s.hold("hands free", 0);
    s.hold("UI", 10000);
    assertTrue(s.busy(21000));
    assertFalse(s.busyExcept("UI", 21000));
    assertFalse(s.busy(30000));
  }

  @Test
  public void ignoringOwnReplyDoesNotIgnoreAnotherScreen() {
    AudioLeaseState s = new AudioLeaseState();
    s.hold("hands free", 0);
    assertFalse(s.busyExcept("hands free", 1));
    s.hold("reply UI", 1);
    assertTrue(s.busyExcept("hands free", 2));
  }

  @Test
  public void resetReleasesAllTemporaryOwners() {
    AudioLeaseState s = new AudioLeaseState();
    s.hold("UI", 0);
    s.hold("service", 0);
    s.clear();
    assertFalse(s.busy(1));
  }
}
