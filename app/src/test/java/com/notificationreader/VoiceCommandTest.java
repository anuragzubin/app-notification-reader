package com.notificationreader;

import static org.junit.Assert.*;

import org.junit.Test;

public class VoiceCommandTest {
  @Test
  public void recognizesOnlyFullCommandWithRecipient() {
    assertEquals("alex smith", VoiceCommand.recipient("Send text to Alex Smith."));
    assertNull(VoiceCommand.recipient("Send text to"));
    assertNull(VoiceCommand.recipient("Don't send text to Alex"));
    assertNull(VoiceCommand.recipient("I said send text to Alex"));
  }

  @Test
  public void normalizesCaseWhitespaceAndPunctuation() {
    assertEquals("alex", VoiceCommand.recipient("  SEND  TEXT TO Alex!  "));
    assertEquals("désirée", VoiceCommand.recipient("Send text to Désirée"));
  }

  @Test
  public void confirmationMustBeExactNotQuotedOrNegated() {
    assertTrue(VoiceCommand.send("Send it now."));
    assertFalse(VoiceCommand.send("Don't send it now"));
    assertFalse(VoiceCommand.send("She said send it now"));
    assertFalse(VoiceCommand.send("yes"));
  }

  @Test
  public void cancellationCommandsAreExact() {
    assertTrue(VoiceCommand.cancel("Cancel!"));
    assertTrue(VoiceCommand.stop("Stop listening."));
    assertFalse(VoiceCommand.cancel("Cancel my meeting"));
    assertFalse(VoiceCommand.stop("Please don't stop listening"));
  }

  @Test
  public void conversationalAnswersMustBeStandaloneYesOrNo() {
    assertTrue(VoiceCommand.yes("Yes."));
    assertTrue(VoiceCommand.no("No!"));
    assertFalse(VoiceCommand.yes("Don't say yes"));
    assertFalse(VoiceCommand.yes("yes no"));
    assertFalse(VoiceCommand.no("no problems"));
    assertFalse(VoiceCommand.no("I said no"));
  }
}
