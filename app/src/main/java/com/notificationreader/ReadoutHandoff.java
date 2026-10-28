package com.notificationreader;

import java.util.function.BooleanSupplier;

/** Stop recognition first, then allow the input provider time to release its audio resources. */
final class ReadoutHandoff {
  interface Delay {
    void post(Runnable task, long delay);
  }

  static void start(Delay delay, Runnable reserve, BooleanSupplier current, Runnable speak) {
    if (!current.getAsBoolean()) return;
    reserve.run();
    delay.post(
        () -> {
          if (current.getAsBoolean()) speak.run();
        },
        350);
  }
}
