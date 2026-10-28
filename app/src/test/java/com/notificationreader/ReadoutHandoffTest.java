package com.notificationreader;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class ReadoutHandoffTest {
  @Test
  public void releasesMicrophoneBeforeTtsWithoutWaitingForTtsOnStart() {
    List<String> calls = new ArrayList<>();
    List<Runnable> delayed = new ArrayList<>();
    ReadoutHandoff.start(
        (task, delay) -> {
          assertEquals(350, delay);
          delayed.add(task);
        },
        () -> calls.add("microphone released"),
        () -> true,
        () -> calls.add("tts speak"));
    assertEquals(Collections.singletonList("microphone released"), calls);
    delayed.get(0).run();
    assertEquals(Arrays.asList("microphone released", "tts speak"), calls);
  }

  @Test
  public void canceledOrReplacedReadoutCannotStartAfterDelay() {
    boolean[] valid = {true};
    List<Runnable> delayed = new ArrayList<>();
    List<String> calls = new ArrayList<>();
    ReadoutHandoff.start(
        (task, delay) -> delayed.add(task),
        () -> calls.add("reserved"),
        () -> valid[0],
        () -> calls.add("unexpected speech"));
    valid[0] = false;
    delayed.get(0).run();
    assertEquals(Collections.singletonList("reserved"), calls);
  }

  @Test
  public void alreadyInvalidReadoutDoesNotReserveAudio() {
    ReadoutHandoff.start(
        (task, delay) -> fail("scheduled stale readout"),
        () -> fail("reserved stale audio"),
        () -> false,
        () -> fail("spoke stale message"));
  }
}
