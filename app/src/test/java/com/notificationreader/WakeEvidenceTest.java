package com.notificationreader;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class WakeEvidenceTest {
  @Test
  public void recognizesWakeInAnyPartialAlternative() {
    WakeEvidence evidence = new WakeEvidence();
    evidence.partial(Arrays.asList("a priority", "Hey Priority"), "Hey Priority");
    assertTrue(evidence.consume());
    assertFalse(evidence.consume());
  }

  @Test
  public void doesNotTreatArbitraryPartialCommandAsWake() {
    WakeEvidence evidence = new WakeEvidence();
    evidence.partial(Arrays.asList("open Gmail", "please tell Hey Priority"), "Hey Priority");
    assertFalse(evidence.consume());
  }

  @Test
  public void resetPreventsEvidenceLeakingIntoAnotherSession() {
    WakeEvidence evidence = new WakeEvidence();
    evidence.partial(Collections.singletonList("Hey Priority open Gmail"), "Hey Priority");
    evidence.reset();
    assertFalse(evidence.consume());
  }

  @Test
  public void laterPartialRevisionCanWithdrawWakeEvidence() {
    WakeEvidence evidence = new WakeEvidence();
    evidence.partial(Collections.singletonList("Hey Priority"), "Hey Priority");
    evidence.partial(Collections.singletonList("a priority"), "Hey Priority");
    assertFalse(evidence.consume());
    evidence.partial(null, "Hey Priority");
    assertFalse(evidence.consume());
  }
}
