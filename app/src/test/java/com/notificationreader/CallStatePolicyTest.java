package com.notificationreader;

import static org.junit.Assert.*;

import org.junit.Test;

public class CallStatePolicyTest {
  @Test
  public void microphoneCommunicationModeAloneDoesNotMuteReadouts() {
    assertFalse(CallStatePolicy.blocks(false, false, 3));
  }

  @Test
  public void realCellularAndTelecomCallsStillPause() {
    assertTrue(CallStatePolicy.blocks(true, false, 3));
    assertTrue(CallStatePolicy.blocks(false, true, 3));
  }

  @Test
  public void ringingAndInCallAudioModesStillPause() {
    assertTrue(CallStatePolicy.blocks(false, false, 1));
    assertTrue(CallStatePolicy.blocks(false, false, 2));
    assertFalse(CallStatePolicy.blocks(false, false, 0));
  }
}
