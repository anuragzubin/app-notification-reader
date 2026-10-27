package com.notificationreader;

import static org.junit.Assert.*;

import org.junit.Test;

public class ReplyDraftTest {
  @Test
  public void transcriptionNeverSendsWithoutConfirmation() {
    ReplyDraft draft = new ReplyDraft();
    draft.edit("I'll call later");
    assertFalse(draft.beginSend());
  }

  @Test
  public void onlyExactReviewedDraftCanBeConfirmed() {
    ReplyDraft draft = new ReplyDraft();
    draft.edit("Yes");
    assertFalse(draft.confirm("No"));
    assertFalse(draft.beginSend());
    assertTrue(draft.confirm("Yes"));
    assertTrue(draft.beginSend());
  }

  @Test
  public void editsInvalidateOldConfirmation() {
    ReplyDraft draft = new ReplyDraft();
    draft.edit("Yes");
    assertTrue(draft.confirm("Yes"));
    draft.edit("No");
    assertFalse(draft.beginSend());
    assertFalse(draft.confirm("Yes"));
    assertTrue(draft.confirm("No"));
    assertTrue(draft.beginSend());
  }

  @Test
  public void blankDraftCannotBeSent() {
    ReplyDraft draft = new ReplyDraft();
    draft.edit("   ");
    assertFalse(draft.confirm("   "));
    assertFalse(draft.beginSend());
  }

  @Test
  public void doubleTapCannotSendTwice() {
    ReplyDraft draft = new ReplyDraft();
    draft.edit("Hello");
    draft.confirm("Hello");
    assertTrue(draft.beginSend());
    assertFalse(draft.beginSend());
    assertFalse(draft.confirm("Hello"));
  }

  @Test
  public void retryRequiresNewConfirmation() {
    ReplyDraft draft = new ReplyDraft();
    draft.edit("Hello");
    draft.confirm("Hello");
    draft.beginSend();
    draft.failed();
    assertFalse(draft.beginSend());
    assertTrue(draft.confirm("Hello"));
    assertTrue(draft.beginSend());
  }
}
