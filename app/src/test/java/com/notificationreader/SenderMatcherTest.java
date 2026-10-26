package com.notificationreader;

import static org.junit.Assert.*;

import org.junit.Test;

public class SenderMatcherTest {
  @Test
  public void namesMustMatchExactly() {
    assertTrue(SenderMatcher.matches("Mom", " mom "));
    assertFalse(SenderMatcher.matches("Mom", "Mom work"));
  }

  @Test
  public void phoneFormattingIsIgnoredButCountryCodeIsRequired() {
    assertTrue(SenderMatcher.matches("+91 98765 43210", "+91-98765-43210"));
    assertFalse(SenderMatcher.matches("+91 98765 43210", "9876543210"));
  }

  @Test
  public void phoneDigitsInNamesCannotMatch() {
    assertFalse(SenderMatcher.matches("12345678", "Order 12345678"));
  }

  @Test
  public void emailCanBeInsideSenderHeader() {
    assertTrue(SenderMatcher.matches("boss@example.com", "Boss <BOSS@example.com>"));
  }

  @Test
  public void emailDoesNotMatchOtherAddresses() {
    assertFalse(SenderMatcher.matches("boss@example.com", "notboss@example.com"));
    assertFalse(SenderMatcher.matches("boss@example.com", "boss@example.com.evil"));
    assertFalse(SenderMatcher.matches("boss@example.com", "boss@example.com-extra"));
  }

  @Test
  public void hiddenAddressCannotMatchDisplayName() {
    assertFalse(SenderMatcher.matches("boss@example.com", "Boss"));
  }
}
