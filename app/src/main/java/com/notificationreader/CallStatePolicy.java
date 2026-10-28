package com.notificationreader;

final class CallStatePolicy {
  static boolean blocks(boolean cellularCall, boolean telecomCall, int audioMode) {
    // AudioManager values 1=RINGTONE, 2=IN_CALL. 3=IN_COMMUNICATION is also used by
    // microphones / speech providers and alone does not establish a phone call.
    return cellularCall || telecomCall || audioMode == 1 || audioMode == 2;
  }
}
