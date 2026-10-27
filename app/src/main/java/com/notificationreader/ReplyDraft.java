package com.notificationreader;

/**
 * Confirmation belongs to an exact draft; editing or retrying always requires fresh confirmation.
 */
final class ReplyDraft {
  private String text = "";
  private boolean confirmed, attempted;

  void edit(String value) {
    text = value.trim();
    confirmed = false;
    attempted = false;
  }

  boolean confirm(String snapshot) {
    confirmed = !text.isEmpty() && text.equals(snapshot.trim()) && !attempted;
    return confirmed;
  }

  boolean beginSend() {
    if (!confirmed || attempted) return false;
    attempted = true;
    confirmed = false;
    return true;
  }

  void failed() {
    attempted = false;
    confirmed = false;
  }
}
