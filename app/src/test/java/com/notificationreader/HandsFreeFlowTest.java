package com.notificationreader;

import static org.junit.Assert.*;

import org.junit.Test;

public class HandsFreeFlowTest {
  @Test
  public void cannotSendFromIdleOrBeforeMessage() {
    HandsFreeFlow f = new HandsFreeFlow();
    assertFalse(f.confirm("Send it now"));
    f.begin("token", "Alex");
    assertFalse(f.confirm("Send it now"));
  }

  @Test
  public void onlyMessageStageAcceptsDraft() {
    HandsFreeFlow f = new HandsFreeFlow();
    assertFalse(f.dictate("hello"));
    f.begin("token", "Alex");
    assertTrue(f.dictate("hello"));
    assertFalse(f.dictate("replacement"));
  }

  @Test
  public void sendRequiresSeparateExplicitConfirmation() {
    HandsFreeFlow f = new HandsFreeFlow();
    f.begin("token", "Alex");
    f.dictate("Send it now");
    assertEquals(HandsFreeFlow.Stage.CONFIRM, f.stage);
    assertFalse(f.confirm("yes"));
    assertFalse(f.confirm("don't send it now"));
    assertTrue(f.confirm("Send it now"));
  }

  @Test
  public void duplicateConfirmationCannotResend() {
    HandsFreeFlow f = new HandsFreeFlow();
    f.begin("token", "Alex");
    f.dictate("Hello");
    assertTrue(f.confirm("Send it now"));
    assertFalse(f.confirm("Send it now"));
  }

  @Test
  public void interruptionDropsRecipientAndUnsentDraft() {
    HandsFreeFlow f = new HandsFreeFlow();
    f.begin("token", "Alex");
    f.dictate("Old message");
    f.reset();
    assertNull(f.token);
    assertNull(f.message);
    assertFalse(f.confirm("Send it now"));
    f.begin("new token", "Bob");
    assertNull(f.message);
    assertEquals("new token", f.token);
  }

  @Test
  public void emptyOrOversizedTranscriptsCannotReachConfirmation() {
    HandsFreeFlow f = new HandsFreeFlow();
    f.begin("token", "Alex");
    assertFalse(f.dictate(" "));
    assertFalse(f.dictate("a".repeat(2001)));
    assertEquals(HandsFreeFlow.Stage.MESSAGE, f.stage);
  }
}
