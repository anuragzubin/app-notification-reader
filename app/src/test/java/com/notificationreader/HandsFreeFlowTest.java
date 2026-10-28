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
    assertFalse(f.confirm("yes do not send"));
    assertFalse(f.confirm("don't send it now"));
    assertTrue(f.confirm("Yes"));
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

  @Test
  public void firstYesOnlyStartsDictationAndSecondYesSends() {
    HandsFreeFlow f = new HandsFreeFlow();
    f.offer("anita-token", "Anita");
    assertEquals(HandsFreeFlow.Stage.OFFER, f.stage);
    assertFalse(f.confirm("Yes"));
    assertTrue(f.acceptOffer("Yes"));
    assertEquals(HandsFreeFlow.Stage.MESSAGE, f.stage);
    assertNull(f.message);
    assertFalse(f.confirm("Yes"));
    assertTrue(f.dictate("I'll arrive at seven"));
    assertTrue(f.confirm("Yes"));
    assertFalse(f.confirm("Yes"));
  }

  @Test
  public void offerDoesNotMistakeSpeechForDraftOrConsent() {
    HandsFreeFlow f = new HandsFreeFlow();
    f.offer("token", "Anita");
    assertFalse(f.dictate("Hello"));
    assertFalse(f.acceptOffer("No"));
    assertFalse(f.acceptOffer("Don't say yes"));
    assertFalse(f.confirm("Yes"));
  }

  @Test
  public void rejectingDraftRequiresFreshDictationAndConfirmation() {
    HandsFreeFlow f = new HandsFreeFlow();
    f.offer("token", "Anita");
    f.acceptOffer("Yes");
    f.dictate("Wrong draft");
    assertTrue(f.rerecord());
    assertNull(f.message);
    assertFalse(f.confirm("Yes"));
    assertTrue(f.dictate("Correct draft"));
    assertEquals("Correct draft", f.message);
    assertTrue(f.confirm("Yes"));
  }

  @Test
  public void declineOrInvalidatedNotificationDropsOfferedRecipient() {
    HandsFreeFlow f = new HandsFreeFlow();
    f.offer("old-token", "Anita");
    f.reset();
    assertNull(f.token);
    assertFalse(f.acceptOffer("Yes"));
    assertFalse(f.confirm("Yes"));
    f.offer("new-token", "Anita");
    assertTrue(f.acceptOffer("Yes"));
    assertEquals("new-token", f.token);
  }

  @Test
  public void messageYesIsAValidDraftRatherThanSendingEarly() {
    HandsFreeFlow f = new HandsFreeFlow();
    f.offer("token", "Anita");
    f.acceptOffer("Yes");
    assertTrue(f.dictate("Yes"));
    assertEquals(HandsFreeFlow.Stage.CONFIRM, f.stage);
    assertEquals("Yes", f.message);
    assertTrue(f.confirm("Yes"));
  }
}
