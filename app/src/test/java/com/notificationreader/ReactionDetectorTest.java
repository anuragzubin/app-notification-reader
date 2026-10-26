package com.notificationreader;

import static org.junit.Assert.*;

import org.junit.Test;

public class ReactionDetectorTest {
  @Test
  public void detectsWhatsAppReactions() {
    assertTrue(ReactionDetector.isReaction("Reacted ❤️ to your message"));
    assertTrue(ReactionDetector.isReaction("Reacted 😂 to: See you soon"));
    assertTrue(ReactionDetector.isReaction("Reacted with 👍🏽 to “Thanks”"));
    assertTrue(ReactionDetector.isReaction("Reacted 👨‍👩‍👦 to your message"));
  }

  @Test
  public void keepsNormalMessagesAndEmojiMessages() {
    assertFalse(ReactionDetector.isReaction("❤️"));
    assertFalse(ReactionDetector.isReaction("I reacted ❤️ to your message"));
    assertFalse(ReactionDetector.isReaction("Reacted positively to the news"));
    assertFalse(ReactionDetector.isReaction("Reacted to your message"));
    assertFalse(ReactionDetector.isReaction("Reacted ❤️"));
    assertFalse(ReactionDetector.isReaction(""));
  }
}
