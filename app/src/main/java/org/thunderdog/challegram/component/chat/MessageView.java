/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 01/05/2015 at 15:23
 */
package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGMessageBotInfo;
import org.thunderdog.challegram.data.TGMessageChat;
import org.thunderdog.challegram.data.TGMessageLocation;
import org.thunderdog.challegram.data.TGMessageSticker;
import org.thunderdog.challegram.data.TGMessageText;
import org.thunderdog.challegram.data.TGWebPage;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.player.TGPlayerController;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.EditRightsController;
import org.thunderdog.challegram.ui.HashtagChatController;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.DrawableProvider;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.v.MessagesRecyclerView;
import org.thunderdog.challegram.widget.SparseDrawableView;

import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.core.lambda.Destroyable;
import me.vkryl.core.lambda.RunnableData;
import me.vkryl.td.ChatId;
import me.vkryl.td.Td;

public class MessageView extends SparseDrawableView implements Destroyable, DrawableProvider, MessagesManager.MessageProvider {
  private static final int FLAG_USE_COMMON_RECEIVER = 1;
  private static final int FLAG_CAUGHT_CLICK = 1 << 1;
  private static final int FLAG_CAUGHT_MESSAGE_TOUCH = 1 << 2;
  private static final int FLAG_WILL_CALL_LONG_PRESS = 1 << 3;
  private static final int FLAG_LONG_PRESSED = 1 << 4;
  private static final int FLAG_USE_REPLY_RECEIVER = 1 << 5;
  private static final int FLAG_DISABLE_MEASURE = 1 << 6;
  private static final int FLAG_USE_COMPLEX_RECEIVER = 1 << 7;

  private TGMessage msg;

  private int flags;

  private final ImageReceiver avatarReceiver;
  private ImageReceiver contentReceiver;
  private DoubleImageReceiver previewReceiver, replyReceiver;
  private GifReceiver gifReceiver;
  private ComplexReceiver complexReceiver;
  private ComplexReceiver reactionsComplexReceiver;
  private MessageViewGroup parentMessageViewGroup;
  private MessagesManager manager;

  public MessageView (Context context) {
    super(context);
    avatarReceiver = new ImageReceiver(this, Screen.dp(20.5f));
    reactionsComplexReceiver = new ComplexReceiver(this);
    gifReceiver = new GifReceiver(this);
    setUseReplyReceiver();
    setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    if (Config.HARDWARE_MESSAGE_LAYER) {
      Views.setLayerType(this, LAYER_TYPE_HARDWARE);
    }
  }

  public void setManager (MessagesManager manager) {
    this.manager = manager;
  }

  public void setParentMessageViewGroup (MessageViewGroup parentViewGroup) {
    this.parentMessageViewGroup = parentViewGroup;
  }

  public MessageViewGroup getParentMessageViewGroup () {
    return parentMessageViewGroup;
  }

  public void setCustomMeasureDisabled (boolean disabled) {
    this.flags = BitwiseUtils.setFlag(this.flags, FLAG_DISABLE_MEASURE, disabled);
  }

  @Override
  public void performDestroy () {
    destroy();
  }

  public void destroy () {
    if (avatarReceiver != null){
      avatarReceiver.destroy();
    }
    if (contentReceiver != null) {
      contentReceiver.destroy();
    }
    if (previewReceiver != null) {
      previewReceiver.destroy();
    }
    if (replyReceiver != null) {
      replyReceiver.destroy();
    }
    if (gifReceiver != null) {
      gifReceiver.destroy();
    }
    if (complexReceiver != null) {
      complexReceiver.performDestroy();
    }
    if (reactionsComplexReceiver != null) {
      reactionsComplexReceiver.performDestroy();
    }
    if (msg != null) {
      msg.onDestroy();
    }
  }

  public void setUseReceivers () {
    contentReceiver = new ImageReceiver(this, 1);
    contentReceiver.setRadius(0);
    previewReceiver = new DoubleImageReceiver(this, 1);
    previewReceiver.setRadius(0);
    flags |= FLAG_USE_COMMON_RECEIVER;
  }

  public void setUseComplexReceiver () {
    complexReceiver = new ComplexReceiver(this);
    flags |= FLAG_USE_COMPLEX_RECEIVER;
  }

  public void setUseReplyReceiver () {
    //noinspection ContantConditions
    replyReceiver = new DoubleImageReceiver(this, Config.USE_SCALED_ROUNDINGS ? Screen.dp(Theme.getImageRadius()) : 0);
    flags |= FLAG_USE_REPLY_RECEIVER;
  }

  @Override
  public void invalidate () {
    super.invalidate();
    if (msg.needViewGroup()) {
      msg.invalidateOverlay();
    }
  }

  @Override
  public void invalidate (int l, int t, int r, int b) {
    super.invalidate(l, t, r, b);
    if (msg.needViewGroup()) {
      msg.invalidateOverlay();
    }
  }

  public void invalidateReplyReceiver (long chatId, long messageId) {
    if ((flags * FLAG_USE_REPLY_RECEIVER) != 0 && msg != null && msg.getChatId() == chatId && msg.getId() == messageId) {
      msg.requestReply(replyReceiver);
    }
  }

  public void invalidateReplyReceiver () {
    if ((flags & FLAG_USE_REPLY_RECEIVER) != 0 && msg != null) {
      msg.requestReply(replyReceiver);
    }
  }

  private void checkLegacyComponents (MessageView view) {
    if (msg != null) {
      msg.layoutAvatar(view, avatarReceiver);
      if ((flags & FLAG_USE_REPLY_RECEIVER) != 0) {
        msg.requestReply(replyReceiver);
      }
    }
  }

  // message

  @Override
  public TGMessage getMessage () {
    return msg;
  }

  public final long getMessageId () {
    return msg != null ? msg.getId() : 0;
  }

  public final int getCurrentHeight () {
    return msg != null ? msg.getHeight() : 0;
  }

  public void setMessage (TGMessage message) {
    oldHeight = -1;

    int desiredHeight = message.getHeight();
    int currentHeight = getCurrentHeight();

    if (this.msg != null) {
      this.msg.onDetachedFromView(this);
    }

    if (this.msg == null || getMeasuredHeight() != desiredHeight || currentHeight != desiredHeight) {
      this.msg = message;
      if ((flags & FLAG_DISABLE_MEASURE) == 0) {
        requestLayout();
      }
    } else if (this.msg.getHeaderPadding() != message.getHeaderPadding()) {
      this.msg = message;
      checkLegacyComponents(this);
    } else {
      this.msg = message;
    }

    message.resetTransformState();
    message.requestAvatar(avatarReceiver);
    message.requestReactions(reactionsComplexReceiver);

    if ((flags & FLAG_USE_COMMON_RECEIVER) != 0) {
      previewReceiver.setRadius(message.getImageContentRadius(true));
      message.requestPreview(previewReceiver);
      if (message.needGifReceiver()) {
        contentReceiver.requestFile(null);
        message.requestGif(gifReceiver);
      } else {
        gifReceiver.requestFile(null);
        contentReceiver.setRadius(message.getImageContentRadius(false));
        message.requestImage(contentReceiver);
      }
    }

    if ((flags & FLAG_USE_COMPLEX_RECEIVER) != 0) {
      message.requestMediaContent(complexReceiver, false, -1);
    }

    if (this.msg != null) {
      msg.onAttachedToView(this);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && manager.useBubbles()) {
        invalidateOutline();
      }
    }
  }

  public void invalidatePreviewReceiver (long chatId, long messageId) {
    if (msg != null && chatId == msg.getChatId() && messageId == msg.getId() && previewReceiver != null) {
      msg.requestPreview(previewReceiver);
    }
  }

  public void invalidateContentReceiver (long chatId, long messageId, int arg) {
    if (msg != null && chatId == msg.getChatId()) {
      if ((flags & FLAG_USE_COMPLEX_RECEIVER) != 0) {
        if (msg.isDescendantOrSelf(messageId)) {
          msg.requestMediaContent(complexReceiver, true, arg);
        }
      } else if (messageId == msg.getId()) {
        if (gifReceiver != null && msg.needGifReceiver()) {
          msg.requestGif(gifReceiver);
        }
        if (msg.needImageReceiver()) {
          if (contentReceiver != null) {
            msg.requestImage(contentReceiver);
          }
          if (previewReceiver != null && previewReceiver.isEmpty()) {
            msg.requestPreview(previewReceiver);
          }
        }
        if ((flags & FLAG_DISABLE_MEASURE) != 0 && getParent() instanceof MessageViewGroup) {
          ((MessageViewGroup) getParent()).invalidateContent(msg);
        }
      }
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    if ((flags & FLAG_DISABLE_MEASURE) != 0) {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    } else {
      int width = ((View) getParent()).getMeasuredWidth();
      if (msg != null) {
        msg.buildLayout(width);
      }
      setMeasuredDimension(widthMeasureSpec, MeasureSpec.makeMeasureSpec(getCurrentHeight(), MeasureSpec.EXACTLY));
    }
    checkLegacyComponents(this);
  }

  int oldHeight = -1;

  /*
  @Override
  public void requestLayout () {
    if (msg != null) {
      final int height = msg.getHeight();
      if (oldHeight != -1 && oldHeight != height) {
        final MessagesRecyclerView recyclerView = findParentRecyclerView();
        if (recyclerView != null && !recyclerView.isComputingLayout()) {
          final RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
          final RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
          if (layoutManager instanceof LinearLayoutManager && adapter instanceof MessagesAdapter) {
            final LinearLayoutManager linearLayoutManager = (LinearLayoutManager) layoutManager;
            final MessagesAdapter messagesAdapter = (MessagesAdapter) adapter;
            final int parentHeight = recyclerView.getMeasuredHeight();
            final int heightDiff = oldHeight - height;
            final int bottom = getBottom();
            int index = -1;

            boolean needScrollCompensation = (bottom > parentHeight);
            if (needScrollCompensation) {
              index = messagesAdapter.indexOfMessageContainer(getMessageId());
            }

            if (!needScrollCompensation && !recyclerView.getManager().isWasScrollByUser()) {
              final int unreadBadgeIndex = messagesAdapter.indexOfMessageWithUnreadSeparator();
              index = messagesAdapter.indexOfMessageContainer(getMessageId());
              needScrollCompensation = (unreadBadgeIndex != -1 && index <= unreadBadgeIndex);
            }

            //android.util.Log.i("BUILD_LAYOUT", String.format("height layout %d %d %d %d %d %b", height, top, heightDiff, index, unreadBadgeIndex, needScrollCompensation));

            if (needScrollCompensation && index != -1) {
              // recyclerView.scrollBy(0, heightDiff);
              linearLayoutManager.scrollToPositionWithOffset(index, parentHeight - bottom + heightDiff);
            }
          }
        }
      }

      oldHeight = height;
    }

    super.requestLayout();
  }
  */

  /*
  @Override
  protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);
    final int height = bottom - top;
    if (oldHeight != -1 && oldHeight != height) {
      final MessagesRecyclerView recyclerView = findParentRecyclerView();
      if (recyclerView != null) {
        final RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
        final RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
        if (layoutManager instanceof LinearLayoutManager && adapter instanceof MessagesAdapter) {
          final LinearLayoutManager linearLayoutManager = (LinearLayoutManager) layoutManager;
          final MessagesAdapter messagesAdapter = (MessagesAdapter) adapter;
          final int parentHeight = recyclerView.getMeasuredHeight();

          final int index = messagesAdapter.indexOfMessageContainer(getMessageId());
          final int unreadBadgeIndex = messagesAdapter.indexOfMessageWithUnreadSeparator();
          final boolean needScrollCompensation = (bottom > parentHeight) || (unreadBadgeIndex != -1 && index <= unreadBadgeIndex && !recyclerView.getManager().isWasScrollByUser());
          final int heightDiff = oldHeight - height;

          if (needScrollCompensation) {
            if (heightDiff < 0) {
              recyclerView.scrollBy(0, heightDiff);
            }

            // It works, but with a delay. If you remove ui.post scrolling will not work
            // manager.controller().tdlib().ui().post(() -> {
            //   linearLayoutManager.scrollToPositionWithOffset(index, parentHeight - bottom + heightDiff);
            // });

          }
          android.util.Log.i("BUILD_LAYOUT", String.format("height layout %d %d %d %b %d %d %b", height, top, heightDiff, changed, index, unreadBadgeIndex, needScrollCompensation));
        }
      }
    }

    oldHeight = height;
  }
  */

  public final @Nullable MessagesRecyclerView findParentRecyclerView () {
    ViewParent parent = getParent();
    while (parent != null) {
      if (parent instanceof MessagesRecyclerView) {
        return ((MessagesRecyclerView) parent);
      }
      parent = parent.getParent();
    }
    return null;
  }

  public final View findTargetView () {
    View target = this;
    ViewParent parent = target.getParent();
    while (parent != null) {
      if (parent instanceof MessagesRecyclerView) {
        return target;
      }
      target = (View) parent;
      parent = parent.getParent();
    }
    return this;
  }

  @Override
  public void onDraw (Canvas c) {
    msg.draw(this, c, avatarReceiver, replyReceiver, previewReceiver, contentReceiver, gifReceiver, complexReceiver);
  }

  public ImageReceiver getAvatarReceiver () {
    return avatarReceiver;
  }

  public ImageReceiver getContentReceiver () {
    return contentReceiver;
  }

  public Receiver getAnyReceiver () {
    return contentReceiver != null ? contentReceiver : gifReceiver;
  }

  public GifReceiver getGifReceiver () {
    return gifReceiver;
  }

  public ComplexReceiver getComplexReceiver () {
    return complexReceiver;
  }

  public ComplexReceiver getReactionsComplexReceiver () {
    return reactionsComplexReceiver;
  }

  public DoubleImageReceiver getPreviewReceiver () {
    return previewReceiver;
  }

  private boolean isAttached = true;

  public void onAttachedToRecyclerView () {
    if (!isAttached) {
      isAttached = true;
      avatarReceiver.attach();
      gifReceiver.attach();
      reactionsComplexReceiver.attach();
      if ((flags & FLAG_USE_REPLY_RECEIVER) != 0) {
        replyReceiver.attach();
      }
      if ((flags & FLAG_USE_COMMON_RECEIVER) != 0) {
        contentReceiver.attach();
        previewReceiver.attach();
      }
      if ((flags & FLAG_USE_COMPLEX_RECEIVER) != 0) {
        complexReceiver.attach();
      }
    }
  }

  public void onDetachedFromRecyclerView () {
    if (isAttached) {
      isAttached = false;
      avatarReceiver.detach();
      gifReceiver.detach();
      reactionsComplexReceiver.detach();
      if ((flags & FLAG_USE_REPLY_RECEIVER) != 0) {
        replyReceiver.detach();
      }
      if ((flags & FLAG_USE_COMMON_RECEIVER) != 0) {
        contentReceiver.detach();
        previewReceiver.detach();
      }
      if ((flags & FLAG_USE_COMPLEX_RECEIVER) != 0) {
        complexReceiver.detach();
      }
    }
  }

  private float touchX, touchY;

  private static void selectMessage (MessagesController m, TGMessage msg, float touchX, float touchY) {
    long messageId = msg.findMessageIdUnder(touchX, touchY);
    if (messageId == 0) {
      m.selectAllMessages(msg, touchX, touchY);
    } else {
      m.selectMessage(messageId, msg, touchX, touchY);
    }
  }

  private boolean onMessageClick (float x, float y) {
    if (msg == null || msg instanceof TGMessageBotInfo) {
      return false;
    }
    MessagesController m = msg.messagesController();
    if (!m.isFocused() && !m.getParentOrSelf().isFocused()) {
      return false;
    }

    NavigationController n = m.getParentOrSelf().navigationController();

    if (n == null || n.isAnimating() || n.getHeaderView().isAnimating()) {
      return false;
    }

    if (m.inSelectMode()) {
      if (msg.canBeSelected()) {
        selectMessage(m, msg, touchX, touchY);
        return true;
      }
      return false;
    }

    if (m.inPreviewSearchMode()) {
      m.openPreviewMessage(msg);
      return true;
    }

    if (msg.onMessageClick(this, m)) {
      return true;
    }

    /*if (ChatId.isMultiChat(msg.getChatId()) && !msg.isChannel()) {
      TdApi.ChatMemberStatus myStatus = msg.tdlib().chatStatus(msg.getChatId());
      if (myStatus != null && TD.isAdmin(myStatus)) {
        AtomicBoolean done = new AtomicBoolean(false);
        msg.tdlib().client().send(new TdApi.GetChatMember(msg.getChatId(), msg.getMessage().senderId), response -> {
          TdApi.ChatMemberStatus otherStatus = (response.getConstructor() == TdApi.ChatMember.CONSTRUCTOR) ? ((TdApi.ChatMember) response).status : null;
          msg.tdlib().ui().post(() -> {
            if (!msg.isDestroyed()) {
              onMessageClickImpl(x, y, otherStatus);
            }
          });
        });
        return true;
      }
    }*/
    return onMessageClickImpl(x, y, null);
  }

  private boolean onMessageClickImpl (float x, float y, @Nullable TdApi.ChatMember sender) {
    MessagesController m = msg.messagesController();
    boolean isSent = !msg.isNotSent();

    if (msg.isEventLog()) {
      showEventLogOptions(m, msg);
      return true;
    }

    if (msg instanceof TGMessageChat) {
      if (isSent) {
        return showChatOptions(m, (TGMessageChat) msg, sender);
      } else {
        m.showMessageOptions(msg, new int[] {R.id.btn_messageDelete}, new String[] {Lang.getString(R.string.Delete)}, new int[] {R.drawable.baseline_delete_24}, null, sender, true);
        return true;
      }
    }

    IntList ids = new IntList(6);
    IntList icons = new IntList(6);
    StringList strings = new StringList(6);
    Object tag = fillMessageOptions(m, msg, sender, ids, icons, strings, false);
    if (!ids.isEmpty()) {
      boolean withReactions = false;
      TdApi.Chat chat = msg.getChat();
      if (chat != null) {
        if (chat.availableReactions != null) {
          if (chat.availableReactions.length > 0) {
            withReactions = true;
          }
        }
      }

      m.showMessageOptions(msg, ids.get(), strings.get(), icons.get(), tag, sender, false, withReactions);
      return true;
    }
    return false;
  }

  public static Object fillMessageOptions (MessagesController m, TGMessage msg, @Nullable TdApi.ChatMember sender, IntList ids, IntList icons, StringList strings, boolean isMore) {
    TdApi.Message newestMessage = msg.getNewestMessage();
    TdApi.MessageContent content = newestMessage.content;
    int messageCount = msg.getMessageCount();
    boolean isSent = !msg.isNotSent();
    Object tag = null;

    // Promotion

    if (msg.isSponsored()) {
      ids.append(R.id.btn_messageCopy);
      strings.append(R.string.Copy);
      icons.append(R.drawable.baseline_content_copy_24);

      ids.append(R.id.btn_messageSponsorInfo);
      strings.append(R.string.SponsoredInfoMenu);
      icons.append(R.drawable.baseline_info_24);
      return null;
    }

    if (!isMore) {
      if (msg.isScheduled() && !msg.isNotSent()) {
        ids.append(R.id.btn_messageSendNow);
        strings.append(R.string.SendNow);
        icons.append(R.drawable.baseline_send_24);

        ids.append(R.id.btn_messageReschedule);
        strings.append(R.string.Reschedule);
        icons.append(R.drawable.baseline_date_range_24);
      }
      if (msg.canResend()) {
        ids.append(R.id.btn_messageResend);
        strings.append(R.string.Resend);
        icons.append(R.drawable.baseline_repeat_24);
      }
      if (msg.needMessageButton()) {
        ids.append(R.id.btn_messageShowSource);
        strings.append(R.string.ShowSourceMessage);
        icons.append(R.drawable.baseline_forum_24);
      }

      switch (content.getConstructor()) {
        case TdApi.MessagePoll.CONSTRUCTOR: {
          TdApi.Poll poll = ((TdApi.MessagePoll) content).poll;
          if (!poll.isClosed) {
            boolean isQuiz = poll.type.getConstructor() == TdApi.PollTypeQuiz.CONSTRUCTOR;
            if (TD.canRetractVote(poll)) {
              ids.append(R.id.btn_messageRetractVote);
              icons.append(isQuiz ? R.drawable.baseline_help_24 : R.drawable.baseline_poll_24);
              strings.append(R.string.RetractVote);
            }
            if (msg.getMessage().canBeEdited) {
              ids.append(R.id.btn_messagePollStop);
              icons.append(isQuiz ? R.drawable.baseline_help_24 : R.drawable.baseline_poll_24);
              strings.append(isQuiz ? R.string.StopQuiz : R.string.StopPoll);
            }
          }
          break;
        }
        case TdApi.MessageCall.CONSTRUCTOR: {
          ids.append(R.id.btn_messageCall);
          icons.append(R.drawable.baseline_phone_24);
          strings.append(msg.isOutgoing() || ((TdApi.MessageCall) content).duration > 0 ? R.string.CallAgain : R.string.CallBack);
          break;
        }
        case TdApi.MessageContact.CONSTRUCTOR: {
          TdApi.Contact contact = ((TdApi.MessageContact) content).contact;
          if (contact.userId != 0) {
            if (m.tdlib().isSelfUserId(contact.userId)) {
              break;
            }
            TdApi.User user = m.tdlib().cache().user(contact.userId);
            if (user != null && !TD.isContact(user)) {
              ids.append(R.id.btn_messageAddContact);
              icons.append(R.drawable.baseline_person_add_24);
              strings.append(R.string.AddContact);
            }
          }
          ids.append(R.id.btn_messageCallContact);
          icons.append(R.drawable.baseline_phone_24);
          strings.append(R.string.Call);
          break;
        }
        case TdApi.MessageLocation.CONSTRUCTOR: {
          if (((TGMessageLocation) msg).canStopAlive()) {
            ids.append(R.id.btn_messageLiveStop);
            icons.append(R.drawable.baseline_remove_circle_24);
            strings.append(R.string.StopSharingLiveLocation);
          }
          ids.append(R.id.btn_messageDirections);
          icons.append(R.drawable.baseline_directions_24);
          strings.append(R.string.Directions);
          break;
        }
        case TdApi.MessageVenue.CONSTRUCTOR: {
          ids.append(R.id.btn_messageDirections);
          icons.append(R.drawable.baseline_directions_24);
          strings.append(R.string.Directions);

          TdApi.Venue venue = ((TdApi.MessageVenue) content).venue;
          if ("foursquare".equals(venue.provider) && !StringUtils.isEmpty(venue.id)) {
            ids.append(R.id.btn_messageFoursquare);
            icons.append(R.drawable.templarian_baseline_foursquare_24);
            strings.append(R.string.ShowOnFoursquare);
          }
          break;
        }
      }

      if (m.canWriteMessages() && isSent && msg.canReplyTo()) {
        if (msg.getMessage().content.getConstructor() == TdApi.MessageDice.CONSTRUCTOR && !msg.tdlib().hasRestriction(msg.getMessage().chatId, R.id.right_sendStickersAndGifs)) {
          String emoji = ((TdApi.MessageDice) msg.getMessage().content).emoji;
          ids.append(R.id.btn_messageReplyWithDice);
          if (TD.EMOJI_DART.textRepresentation.equals(emoji)) {
            strings.append(R.string.SendDart);
          } else if (TD.EMOJI_DICE.textRepresentation.equals(emoji)) {
            strings.append(R.string.SendDice);
          } else {
            strings.append(R.string.SendUnknownDice);
          }
          icons.append(TD.EMOJI_DART.textRepresentation.equals(emoji) ? R.drawable.baseline_gps_fixed_24 : R.drawable.baseline_casino_24);
        }

        ids.append(R.id.btn_messageReply);
        strings.append(R.string.Reply);
        icons.append(R.drawable.baseline_reply_24);
      }

      if (Config.COMMENTS_SUPPORTED) {
        if (msg.getReplyCount() > 0) {
          ids.append(R.id.btn_messageReplies);
          strings.append(Lang.plural(msg.getSender().isChannel() ? R.string.ViewXComments : R.string.ViewXReplies, msg.getReplyCount()));
          icons.append(msg.getSender().isChannel() ? R.drawable.outline_templarian_comment_multiple_24 : R.drawable.baseline_reply_all_24);
        }
      } else {
        TdApi.Message messageWithThread = msg.findMessageWithThread();
        if (messageWithThread != null && messageWithThread.isChannelPost) {
          ids.append(R.id.btn_messageDiscuss);
          strings.append(R.string.DiscussMessage);
          icons.append(R.drawable.outline_templarian_comment_multiple_24);
        }
      }

      if (msg.canBeForwarded() && isSent) {
        ids.append(R.id.btn_messageShare);
        strings.append(R.string.Share);
        icons.append(R.drawable.baseline_forward_24);
      }
    }

    int moreOptions = 0;

    if (m.canPinAnyMessage(true) && isSent) {
      if (!isMore) {
        int totalCount = msg.getMessageCount();
        int pinnedCount = msg.getPinnedMessageCount();
        int pinRes, unpinRes;
        switch (msg.getMessageAlbumType()) {
          case TGMessage.MESSAGE_ALBUM_TYPE_FILES:
            pinRes = R.string.MessagePinFiles;
            unpinRes = R.string.MessageUnpinFiles;
            break;
          case TGMessage.MESSAGE_ALBUM_TYPE_PLAYLIST:
            pinRes = R.string.MessagePinPlaylist;
            unpinRes = R.string.MessageUnpinPlaylist;
            break;
          case TGMessage.MESSAGE_ALBUM_TYPE_MEDIA:
            pinRes = R.string.MessagePinAlbum;
            unpinRes = R.string.MessageUnpinAlbum;
            break;
          case TGMessage.MESSAGE_ALBUM_TYPE_NONE:
          case TGMessage.MESSAGE_ALBUM_TYPE_MIXED:
          default:
            pinRes = R.string.MessagePin;
            unpinRes = R.string.MessageUnpin;
            break;
        }
        if (pinnedCount > 0) {
          ids.append(R.id.btn_messageUnpin);
          strings.append(unpinRes);
          icons.append(R.drawable.deproko_baseline_pin_undo_24);
        } else {
          ids.append(R.id.btn_messagePin);
          strings.append(pinRes);
          icons.append(R.drawable.deproko_baseline_pin_24);
        }
      }
    }

    // Stats

    if (!isMore && msg.canViewStatistics()) {
      ids.append(R.id.btn_viewStatistics);
      strings.append(R.string.ViewStats);
      icons.append(R.drawable.baseline_bar_chart_24);
    }

    // Edit

    if (!isMore && msg.canEditText() && isSent) {
      ids.append(R.id.btn_messageEdit);
      strings.append(R.string.edit);
      icons.append(R.drawable.baseline_edit_24);
    }

    // Copy, select

    TdApi.Chat chat = m.tdlib().chat(msg.getChatId());
    if (!isMore && m.tdlib().canCopyPostLink(msg.getMessage())) {
      ids.append(R.id.btn_messageCopyLink);
      strings.append(R.string.CopyLink);
      icons.append(R.drawable.baseline_link_24);
    }

    if (!isMore && msg.canBeSaved() && TD.canCopyText(newestMessage)) {
      ids.append(R.id.btn_messageCopy);
      strings.append(R.string.Copy);
      icons.append(R.drawable.baseline_content_copy_24);
    }

    if (messageCount == 1) {
      if (!isMore && msg.getMessage().content.getConstructor() == TdApi.MessageSticker.CONSTRUCTOR) {
        TdApi.Sticker sticker = ((TdApi.MessageSticker) msg.getMessage().content).sticker;
        if (Config.FAVORITE_STICKERS_WITHOUT_SETS || sticker.setId != 0) {
          int stickerId = sticker.sticker.id;
          boolean isFavorite = m.tdlib().isStickerFavorite(stickerId);
          ids.append(isFavorite ? R.id.btn_messageUnfavoriteContent : R.id.btn_messageFavoriteContent);
          strings.append(isFavorite ? R.string.RemoveFromFavorites : R.string.AddToFavorites);
          icons.append(!isFavorite ? R.drawable.baseline_star_border_24 : R.drawable.baseline_star_24);
        }
        if (msg instanceof TGMessageSticker && ((TGMessageSticker) msg).needSuggestOpenStickerPack()) {
          ids.append(R.id.btn_messageStickerSet);
          strings.append(R.string.OpenStickerSet);
          icons.append(R.drawable.deproko_baseline_stickers_24);
        }
      }
    }
    if (isSent && !isMore && (!msg.isHot() || TD.isOut(msg.getMessage()))) {
      int playListAddMode = TdlibManager.instance().player().canAddToPlayList(msg.tdlib(), msg.getMessage());
      TdApi.Message singleMessage = msg.getMessage();
      TdApi.Message[] allMessages = msg.getAllMessages();

      List<TD.DownloadedFile> downloadedFiles = TD.getDownloadedFiles(allMessages);
      if (downloadedFiles.isEmpty() && singleMessage.content.getConstructor() == TdApi.MessageText.CONSTRUCTOR && msg instanceof TGMessageText) {
        TGWebPage webPage = ((TGMessageText) msg).getParsedWebPage();
        if (webPage != null) {
          TD.DownloadedFile downloadedFile = TD.getDownloadedFile(webPage);
          if (downloadedFile != null) {
            downloadedFiles.add(downloadedFile);
          }
        }
      }
      if (!downloadedFiles.isEmpty()) {
        tag = downloadedFiles;

        TD.DownloadedFile baseDownloadedFile = downloadedFiles.get(0);
        if (msg.canBeSaved() && baseDownloadedFile.getFileType().getConstructor() == TdApi.FileTypeAnimation.CONSTRUCTOR) {
          ids.append(R.id.btn_saveGif);
          if (allMessages.length == 1) {
            strings.append(R.string.SaveGif);
          } else {
            strings.append(Lang.plural(R.string.SaveXGifs, downloadedFiles.size()));
          }
          icons.append(R.drawable.deproko_baseline_gif_24);
        }
        switch (baseDownloadedFile.getFileType().getConstructor()) {
          case TdApi.FileTypeVoiceNote.CONSTRUCTOR:
          case TdApi.FileTypeVideoNote.CONSTRUCTOR: {
            break;
          }
          case TdApi.FileTypeAnimation.CONSTRUCTOR:
          case TdApi.FileTypeVideo.CONSTRUCTOR:
          case TdApi.FileTypePhoto.CONSTRUCTOR: {
            if (msg.canBeSaved()) {
              ids.append(R.id.btn_saveFile);
              if (allMessages.length == 1) {
                strings.append(R.string.SaveToGallery);
              } else {
                strings.append(Lang.plural(R.string.SaveXToGallery, downloadedFiles.size()));
              }
              icons.append(R.drawable.baseline_image_24);
            }
            break;
          }
          case TdApi.FileTypeAudio.CONSTRUCTOR: {
            if (msg.canBeSaved()) {
              ids.append(R.id.btn_saveFile);
              if (allMessages.length == 1) {
                strings.append(R.string.SaveToMusic);
              } else {
                strings.append(Lang.plural(R.string.SaveXToMusic, downloadedFiles.size()));
              }
              icons.append(R.drawable.baseline_music_note_24);
            }
            break;
          }
          default: {
            if (allMessages.length == 1 && msg.getMessage().content.getConstructor() == TdApi.MessageDocument.CONSTRUCTOR) {
              TdApi.Document document = ((TdApi.MessageDocument) msg.getMessage().content).document;
              if (TdlibUi.canInstallLanguage(document)) {
                ids.append(R.id.btn_messageApplyLocalization);
                strings.append(R.string.LanguageInstall);
                icons.append(R.drawable.baseline_language_24);
              }
              if (TdlibUi.canInstallTheme(document)) {
                ids.append(R.id.btn_messageInstallTheme);
                strings.append(R.string.ThemeInstallDone);
                icons.append(R.drawable.baseline_palette_24);
              }
            }

            if (msg.canBeSaved()) {
              ids.append(R.id.btn_saveFile);
              if (allMessages.length == 1) {
                strings.append(R.string.SaveToDownloads);
              } else {
                strings.append(Lang.plural(R.string.SaveXToDownloads, downloadedFiles.size()));
              }
              icons.append(R.drawable.baseline_file_download_24);
            }
            break;
          }
        }
      }
      if (allMessages.length == 1 && playListAddMode != TGPlayerController.ADD_MODE_NONE) {
        ids.append(R.id.btn_addToPlaylist);
        if (playListAddMode == TGPlayerController.ADD_MODE_MOVE) {
          strings.append(R.string.PlayListPlayNext);
          icons.append(R.drawable.baseline_queue_music_24);
        } else {
          strings.append(playListAddMode == TGPlayerController.ADD_MODE_RESTORE ? R.string.PlayListRestore : R.string.PlayListAdd);
          icons.append(R.drawable.baseline_playlist_add_24);
        }
      }
    }
    TdApi.File file = TD.getFile(msg.getMessage());
    if (file != null && !file.remote.isUploadingActive) {
      if (Config.useCloudPlayback(msg.getMessage()) && !file.local.isDownloadingCompleted) {
        if (isMore) {
          if (file.local.isDownloadingActive && !TdlibManager.instance().player().isPlayingFileId(file.id)) {
            ids.append(R.id.btn_pauseFile);
            strings.append(R.string.CloudPause);
            icons.append(R.drawable.baseline_cloud_pause_24);
          }
          if (!file.local.isDownloadingActive) {
            ids.append(R.id.btn_downloadFile);
            if (file.local.downloadedSize > 0)
              strings.append(R.string.CloudResume);
            else
              strings.append(Lang.getString(R.string.CloudDownload, Strings.buildSize(file.size)));
            icons.append(R.drawable.baseline_cloud_download_24);
          }
        } else {
          moreOptions++;
        }
      }
    }

    if (!ChatId.isSecret(msg.getChatId())) {
      boolean hasFilesToRemove = false;
      TdApi.Message[] allMessages = msg.getAllMessages();
      for (TdApi.Message message : allMessages) {
        if (TD.canDeleteFile(message)) {
          hasFilesToRemove = true;
          break;
        }
      }
      if (hasFilesToRemove) {
        if (isMore) {
          ids.append(R.id.btn_deleteFile);
          strings.append(R.string.DeleteFromCache);
          icons.append(R.drawable.templarian_baseline_broom_24);
        } else {
          moreOptions++;
        }
      }
    }

    if (msg.canBeReported()) {
      if (isMore) {
        ids.append(R.id.btn_messageReport);
        strings.append(R.string.MessageReport);
        icons.append(R.drawable.baseline_report_24);
      } else {
        moreOptions++;
      }
    }

    if (!isMore && msg.canBeDeletedForSomebody()) {
      ids.append(R.id.btn_messageDelete);
      strings.append(R.string.Delete);
      icons.append(R.drawable.baseline_delete_24);
    }

    // Admin tools inside "More"
    final TdApi.ChatMemberStatus myStatus = m.tdlib().chatStatus(msg.getChatId());
    if (myStatus != null && TD.isAdmin(myStatus) && Td.getSenderId(msg.getMessage().senderId) != msg.getChatId()) {
      if (sender != null) {
        final int restrictMode = TD.canRestrictMember(myStatus, sender.status);
        if (restrictMode != TD.RESTRICT_MODE_NONE) {
          if (!msg.isChannel() && !(restrictMode == TD.RESTRICT_MODE_EDIT && sender.memberId.getConstructor() == TdApi.MessageSenderChat.CONSTRUCTOR)) {
            if (isMore) {
              ids.append(R.id.btn_messageRestrictMember);
              icons.append(R.drawable.baseline_block_24);
              switch (restrictMode) {
                case TD.RESTRICT_MODE_EDIT:
                  strings.append(msg.getMessage().senderId.getConstructor() == TdApi.MessageSenderChat.CONSTRUCTOR ? (msg.tdlib().isChannel(Td.getSenderId(msg.getMessage().senderId)) ? R.string.EditChannelRestrictions : R.string.EditGroupRestrictions) : R.string.EditUserRestrictions);
                  break;
                case TD.RESTRICT_MODE_NEW:
                  strings.append(msg.getMessage().senderId.getConstructor() == TdApi.MessageSenderChat.CONSTRUCTOR ? (msg.tdlib().isChannel(Td.getSenderId(msg.getMessage().senderId)) ? R.string.BanChannel : R.string.BanChat) : R.string.RestrictUser);
                  break;
                case TD.RESTRICT_MODE_VIEW:
                  strings.append(R.string.ViewRestrictions);
                  break;
                default:
                  throw new IllegalStateException();
              }
            } else {
              moreOptions++;
            }
          }

          if (restrictMode != TD.RESTRICT_MODE_VIEW) {
            if (TD.isMember(sender.status)) {
              if (isMore) {
                ids.append(R.id.btn_messageBlockUser);
                icons.append(R.drawable.baseline_remove_circle_24);
                strings.append(msg.isChannel() ? R.string.ChannelRemoveUser : R.string.RemoveFromGroup);
              } else {
                moreOptions++;
              }
            } else {
              boolean canUnblock = false;
              switch (sender.status.getConstructor()) {
                case TdApi.ChatMemberStatusBanned.CONSTRUCTOR:
                case TdApi.ChatMemberStatusRestricted.CONSTRUCTOR: {
                  canUnblock = true;
                  break;
                }
              }
              if (canUnblock) {
                if (isMore) {
                  strings.append(
                    sender.status.getConstructor() == TdApi.ChatMemberStatusRestricted.CONSTRUCTOR ? R.string.RemoveRestrictions :
                      msg.tdlib().cache().senderBot(msg.getMessage().senderId) ? R.string.UnbanMemberBot :
                        msg.getMessage().senderId.getConstructor() == TdApi.MessageSenderChat.CONSTRUCTOR ? (msg.tdlib().isChannel(Td.getSenderId(msg.getMessage().senderId)) ? R.string.UnbanMemberChannel : R.string.UnbanMemberGroup) :
                          R.string.UnbanMember
                  );
                  ids.append(R.id.btn_messageUnblockMember);
                  icons.append(R.drawable.baseline_remove_circle_24);
                } else {
                  moreOptions++;
                }
              }
            }
          }
        }
      } else if (!isMore) {
        moreOptions += 2;
      }
    }

    // Messages from X
    if (chat != null && msg.tdlib().isMultiChat(chat.id)) {
      if (isMore) {
        ids.append(R.id.btn_messageViewList);
        int icon = R.drawable.baseline_person_24;
        if (msg.getSender().isAnonymousGroupAdmin()) {
          strings.append(R.string.ViewMessagesFromAnonymousAdmins);
          icon = R.drawable.baseline_group_24;
        } else if (msg.isOutgoing()) {
          strings.append(R.string.ViewMessagesFromYou);
        } else if (msg.getMessage().senderId.getConstructor() == TdApi.MessageSenderChat.CONSTRUCTOR) {
          strings.append(Lang.getString(R.string.ViewMessagesFromChat, msg.getSender().getNameShort()));
          icon = msg.tdlib().isChannel(Td.getSenderId(msg.getMessage().senderId)) ? R.drawable.baseline_bullhorn_24 : R.drawable.baseline_group_24;
        } else {
          strings.append(Lang.getString(R.string.ViewMessagesFromUser, msg.getSender().getNameShort()));
        }
        icons.append(icon);
      } else {
        moreOptions++;
      }
    }

    if (moreOptions > 1) {
      ids.append(R.id.btn_messageMore);
      icons.append(R.drawable.baseline_more_horiz_24);
      strings.append(R.string.MoreMessageOptions);
    } else if (moreOptions == 1) {
      fillMessageOptions(m, msg, sender, ids, icons, strings, true);
    }

    return tag;
  }

  @Deprecated
  private boolean showChatOptions (MessagesController m, TGMessageChat msg, TdApi.ChatMember messageSender) {
    if (msg.getMessage() != null) {
      TdApi.MessageContent content = msg.getMessage().content;
      if (content != null) {
        switch (content.getConstructor()) {
          case TdApi.MessageChatUpgradeFrom.CONSTRUCTOR: {
            long basicGroupId = ((TdApi.MessageChatUpgradeFrom) content).basicGroupId;
            if (basicGroupId != 0) {
              m.tdlib().ui().openBasicGroupChat(m, basicGroupId, null);
              return true;
            }
            return false;
          }
          case TdApi.MessageChatUpgradeTo.CONSTRUCTOR: {
            long supergroupId = ((TdApi.MessageChatUpgradeTo) content).supergroupId;
            if (supergroupId != 0) {
              m.tdlib().ui().openSupergroupChat(m, supergroupId, null);
              return true;
            }
            return false;
          }
        }
      }
    }

    IntList ids = new IntList(2);
    StringList strings = new StringList(2);
    IntList icons = new IntList(2);

    if (m.canWriteMessages() && msg.canReplyTo()) {
      ids.append(R.id.btn_messageReply);
      strings.append(R.string.Reply);
      icons.append(R.drawable.baseline_reply_24);
    }

    if (Config.COMMENTS_SUPPORTED && msg.getReplyCount() > 0) {
      ids.append(R.id.btn_messageReplies);
      strings.append(Lang.plural(R.string.ViewXReplies, msg.getReplyCount()));
      icons.append(R.drawable.baseline_reply_all_24);
    }

    if (m.tdlib().canCopyPostLink(msg.getMessage())) {
      ids.append(R.id.btn_messageCopyLink);
      strings.append(R.string.CopyLink);
      icons.append(R.drawable.baseline_link_24);
    }

    if (this.msg.canBeDeletedForSomebody()) {
      ids.append(R.id.btn_messageDelete);
      strings.append(R.string.Delete);
      icons.append(R.drawable.baseline_delete_24);
    }

    if (ids.isEmpty()) {
      return false;
    }

    m.showMessageOptions(msg, ids.get(), strings.get(), icons.get(), null, messageSender, true);
    return true;
  }

  private void showEventLogRestrict (MessagesController m, boolean isRestrict, TdApi.MessageSender sender, TdApi.ChatMemberStatus myStatus, TdApi.ChatMember member) {
    if (isRestrict && TD.canRestrictMember(myStatus, member.status) == TD.RESTRICT_MODE_NEW) {
      member = null;
    }

    EditRightsController c = new EditRightsController(m.context(), m.tdlib());
    c.setArguments(new EditRightsController.Args(m.getChatId(), sender, isRestrict, myStatus, member).noFocusLock());
    m.navigateTo(c);
  }

  private void showEventLogOptions (MessagesController m, TGMessage msg) {
    TdApi.MessageSender sender = msg.getMessage().senderId;

    IntList ids = new IntList(2);
    StringList strings = new StringList(2);
    IntList icons = new IntList(2);
    IntList colors = new IntList(2);

    TdApi.ChatMemberStatus myStatus = m.tdlib().chatStatus(m.getChatId());

    RunnableData<TdApi.ChatMember> showOptions = (member) -> {
      m.showOptions(null, ids.get(), strings.get(), colors.get(), icons.get(), (optionItemView, id) -> {
        switch (optionItemView.getId()) {
          case R.id.btn_restrictMember:
            showEventLogRestrict(m, true, sender, myStatus, member);
            break;
          case R.id.btn_editRights:
            showEventLogRestrict(m, false, sender, myStatus, member);
            break;
          case R.id.btn_messageCopy:
            TdApi.FormattedText text;

            if (TD.canCopyText(msg.getMessage())) {
              text = Td.textOrCaption(msg.getMessage().content);
            } else if (msg instanceof TGMessageText) {
              text = ((TGMessageText) msg).getText();
            } else {
              text = null;
            }

            if (text != null)
              UI.copyText(TD.toCopyText(text), R.string.CopiedText);
            break;
          case R.id.btn_messageViewList:
            HashtagChatController c2 = new HashtagChatController(m.context(), m.tdlib());
            c2.setArguments(new HashtagChatController.Arguments(null, m.getChatId(), null, sender, m.tdlib().isChannel(Td.getSenderId(sender))));
            m.navigateTo(c2);
            break;
          case R.id.btn_blockSender:
            m.tdlib().ui().kickMember(m, m.getChatId(), sender, member.status);
            break;
        }

        return true;
      });
    };

    m.tdlib().client().send(new TdApi.GetChatMember(m.getChatId(), sender), result -> {
      if (result.getConstructor() != TdApi.ChatMember.CONSTRUCTOR) return;

      TdApi.ChatMember member = (TdApi.ChatMember) result;
      boolean isChannel = m.tdlib().isChannel(m.getChatId());

      if (TD.canCopyText(msg.getMessage()) || (msg instanceof TGMessageText && ((TGMessageText) msg).getText().text.trim().length() > 0)) {
        ids.append(R.id.btn_messageCopy);
        strings.append(R.string.Copy);
        icons.append(R.drawable.baseline_content_copy_24);
        colors.append(ViewController.OPTION_COLOR_NORMAL);
      }

      if (!isChannel) {
        ids.append(R.id.btn_messageViewList);
        if (m.tdlib().isSelfUserId(Td.getSenderUserId(sender))) {
          strings.append(R.string.ViewMessagesFromYou);
        } else {
          strings.append(Lang.getString(R.string.ViewMessagesFromUser, m.tdlib().senderName(sender, true)));
        }
        icons.append(R.drawable.baseline_person_24);
        colors.append(ViewController.OPTION_COLOR_NORMAL);
      }

      if (myStatus != null && !(TD.isCreator(member.status) && TD.isCreator(myStatus))) {
        int promoteMode = TD.canPromoteAdmin(myStatus, member.status);
        if (promoteMode != TD.PROMOTE_MODE_NONE && promoteMode != TD.PROMOTE_MODE_NEW) {
          ids.append(R.id.btn_editRights);
          icons.append(R.drawable.baseline_stars_24);
          colors.append(ViewController.OPTION_COLOR_NORMAL);
          switch (promoteMode) {
            case TD.PROMOTE_MODE_EDIT:
              strings.append(R.string.EditAdminRights);
              break;
            case TD.PROMOTE_MODE_VIEW:
              strings.append(R.string.ViewAdminRights);
              break;
            default:
              throw new IllegalStateException();
          }
        }

        int restrictMode = TD.canRestrictMember(myStatus, member.status);
        if (restrictMode != TD.RESTRICT_MODE_NONE && !(sender.getConstructor() == TdApi.MessageSenderChat.CONSTRUCTOR && Td.getSenderId(sender) == m.getChatId())) {
          if (!isChannel || (isChannel && restrictMode == TD.RESTRICT_MODE_EDIT)) {
            ids.append(R.id.btn_restrictMember);
            colors.append(restrictMode == TD.RESTRICT_MODE_NEW ? ViewController.OPTION_COLOR_RED : ViewController.OPTION_COLOR_NORMAL);
            icons.append(R.drawable.baseline_block_24);

            switch (restrictMode) {
              case TD.RESTRICT_MODE_EDIT:
                strings.append(sender.getConstructor() == TdApi.MessageSenderChat.CONSTRUCTOR ? (m.tdlib().isChannel(Td.getSenderId(sender)) ? R.string.EditChannelRestrictions : R.string.EditGroupRestrictions) : R.string.EditUserRestrictions);
                break;
              case TD.RESTRICT_MODE_NEW:
                strings.append(sender.getConstructor() == TdApi.MessageSenderChat.CONSTRUCTOR ? (m.tdlib().isChannel(Td.getSenderId(sender)) ? R.string.BanChannel : R.string.BanChat) : R.string.RestrictUser);
                break;
              case TD.RESTRICT_MODE_VIEW:
                strings.append(R.string.ViewRestrictions);
                break;
              default:
                throw new IllegalStateException();
            }
          }

          if (sender.getConstructor() != TdApi.MessageSenderChat.CONSTRUCTOR) {
            ids.append(R.id.btn_blockSender);
            icons.append(R.drawable.baseline_remove_circle_24);
            strings.append(isChannel ? R.string.ChannelRemoveUser : R.string.RemoveFromGroup);
            colors.append(ViewController.OPTION_COLOR_RED);
          }
        }
      }

      m.runOnUiThreadOptional(() -> showOptions.runWithData(member));
    });
  }

  private void setLongPressed (boolean longPressed) {
    if (longPressed) {
      flags |= FLAG_LONG_PRESSED;
      ViewParent parent = getParent();
      if (parent != null) {
        parent.requestDisallowInterceptTouchEvent(true);
      }
      performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    } else {
      flags &= ~FLAG_LONG_PRESSED;
      ViewParent parent = getParent();
      if (parent != null) {
        parent.requestDisallowInterceptTouchEvent(false);
      }
    }
  }

  private boolean performLongPress () {
    if (msg instanceof TGMessageBotInfo || msg.isNotSent()) {
      return false;
    }
    ViewController<?> c = ViewController.findRoot(this);
    if (c == null || !(c instanceof MessagesController)) {
      return false;
    }
    MessagesController m = (MessagesController) c;
    if (msg instanceof TGMessageChat) {
      return onMessageClick(0, 0);
    }
    if (msg.canBeSelected()) {
      selectMessage(m, msg, touchX, touchY);
      return true;
    }
    return false;
  }

  public void shake (boolean isPositive) {
    if (msg == null) {
      return;
    }
    final float scaleDelta, degreeDelta;
    if (isPositive) {
      scaleDelta = .13f;
      degreeDelta = 2f;
    } else {
      scaleDelta = -.03f;
      degreeDelta = 3f;
    }

    setPivotX(msg.centerX());
    setPivotY(msg.centerY());
    FactorAnimator animator;
    if (Settings.instance().getNewSetting(Settings.SETTING_FLAG_BATMAN_POLL_TRANSITIONS) || MathUtils.random(0, 1000) == 500) {
      animator = new FactorAnimator(0, (id, factor, fraction, callee) -> {
        float scale, rotation;
        float stepFactor = factor < .5f ? factor / .5f : 1f - (factor - .5f) / .5f;

        if (isPositive) {
          scale = 1f + .5f * stepFactor;
          rotation = 360f * 2 * factor;
        } else {
          scale = 1f - .6f * stepFactor;
          rotation = -360f * 2 * factor;
        }

        setScaleX(scale);
        setScaleY(scale);
        setRotation(rotation);
      }, AnimatorUtils.DECELERATE_INTERPOLATOR, 900l);
    } else {
      animator = new FactorAnimator(0, (id, factor, fraction, callee) -> {
        final float decreaseStep = .35f;
        final float sourceScaleFactor = AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation(factor);
        final float scaleFactor = sourceScaleFactor < decreaseStep ? sourceScaleFactor / decreaseStep : 1f - (sourceScaleFactor - decreaseStep) / (1f - decreaseStep);
        final float scale = 1f + scaleDelta * scaleFactor;

        setScaleX(scale);
        setScaleY(scale);

        setRotation((float) Math.sin(Math.toRadians(360f * 1.5f * sourceScaleFactor % 360f)) * degreeDelta);
      }, AnimatorUtils.LINEAR_INTERPOLATOR, 600l);
    }
    animator.animateTo(1f);
  }

  private void onLongPress () {
    if ((flags & FLAG_CAUGHT_CLICK) != 0) {
      if (performLongPress()) {
        flags &= ~FLAG_CAUGHT_CLICK;
        setLongPressed(true);
      }
    } else if ((flags & FLAG_CAUGHT_MESSAGE_TOUCH) != 0) {
      flags &= ~FLAG_CAUGHT_MESSAGE_TOUCH;
      if (msg.performLongPress(this, touchX, touchY) || performLongPress()) {
        setLongPressed(true);
      }
    }
  }

  private CancellableRunnable longPressRunnable;

  private void postLongPress () {
    if ((flags & FLAG_WILL_CALL_LONG_PRESS) != 0) {
      return;
    }
    flags |= FLAG_WILL_CALL_LONG_PRESS;
    longPressRunnable = new CancellableRunnable() {
      @Override
      public void act () {
        flags &= ~FLAG_WILL_CALL_LONG_PRESS;
        longPressRunnable = null;
        onLongPress();
      }
    };
    cancelLongPress();
    postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());
  }

  private void preventLongPress () {
    flags &= ~FLAG_WILL_CALL_LONG_PRESS;
    if (longPressRunnable != null) {
      longPressRunnable.cancel();
      removeCallbacks(longPressRunnable);
      longPressRunnable = null;
    }
  }

  private boolean startSwipeIfNeeded (float diffX) {
    if (msg == null || msg.isNotSent() || msg instanceof TGMessageBotInfo || msg instanceof TGMessageChat || msg.isSponsored() || UI.getContext(getContext()).getRecordAudioVideoController().isOpen()) {
      return false;
    }
    MessagesController m = msg.messagesController();
    MessagesRecyclerView recyclerView = findParentRecyclerView();
    if (recyclerView == null) {
      return false;
    }
    if ((msg.getRightQuickReactions().size() > 0 && diffX < 0) || (msg.getLeftQuickReactions().size() > 0 && diffX > 0)) {
      if (touchX > MessagesController.getSlideBackBound()) {
        m.startSwipe(findTargetView());
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    if (msg == null) {
      return false;
    }
    if (msg.getChatId() == 0 && !(msg instanceof TGMessageBotInfo)) {
      return false;
    }
    if (msg.messagesController().isInForceTouchMode()) {
      return false;
    }
    if (msg.messagesController().inPreviewMode() && !msg.manager().isEventLog() && !msg.manager().isSearchPreview()) {
      return false;
    }
    if (UI.getContext(getContext()).getRecordAudioVideoController().isOpen()) {
      return false;
    }
    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        if (msg.shouldIgnoreTap(e)) {
          return false;
        }
        MessagesController c = msg.messagesController();
        if (c.isEditingMessage()) {
          return false;
        }
        touchX = e.getX();
        touchY = e.getY();
        if (msg.allowLongPress(touchX, touchY)) {
          postLongPress();
        } else {
          preventLongPress();
        }
        if (c.inSelectMode() || !msg.onTouchEvent(this, e)) {
          flags |= FLAG_CAUGHT_CLICK;
        } else {
          flags |= FLAG_CAUGHT_MESSAGE_TOUCH;
        }
        return true;
      }
      case MotionEvent.ACTION_CANCEL: {
        if ((flags & FLAG_LONG_PRESSED) != 0) {
          setLongPressed(false);
        }
        if ((flags & FLAG_WILL_CALL_LONG_PRESS) != 0) {
          preventLongPress();
        }
        if ((flags & FLAG_CAUGHT_MESSAGE_TOUCH) != 0) {
          flags &= ~FLAG_CAUGHT_MESSAGE_TOUCH;
          return msg.onTouchEvent(this, e);
        }
        if ((flags & FLAG_CAUGHT_CLICK) != 0) {
          flags &= ~FLAG_CAUGHT_CLICK;
          return true;
        }
        return false;
      }
      case MotionEvent.ACTION_MOVE: {
        float diffX = Math.abs(touchX - e.getX());
        float diffY = Math.abs(touchY - e.getY());
        float diffMax = Math.max(diffX, diffY);

        if ((flags & FLAG_WILL_CALL_LONG_PRESS) != 0) {
          if (diffMax > Screen.getTouchSlop()) {
            preventLongPress();
          }
        }
        MessagesRecyclerView recyclerView = findParentRecyclerView();
        if (recyclerView != null && !recyclerView.disallowInterceptTouchEvent() && diffY < Screen.getTouchSlop() && diffX > Screen.getTouchSlop()) {
          if (startSwipeIfNeeded(e.getX() - touchX)) {
            if ((flags & FLAG_WILL_CALL_LONG_PRESS) != 0) {
              preventLongPress();
            }
            if ((flags & FLAG_CAUGHT_CLICK) != 0) {
              flags &= ~FLAG_CAUGHT_CLICK;
            }
            return false;
          }
        }
        if ((flags & FLAG_CAUGHT_MESSAGE_TOUCH) != 0) {
          return msg.onTouchEvent(this, e);
        }
        if ((flags & FLAG_CAUGHT_CLICK) != 0) {
          if (diffMax > Screen.getTouchSlop()) {
            flags &= ~FLAG_CAUGHT_CLICK;
          } else {
            return true;
          }
        }
        return false;
      }
      case MotionEvent.ACTION_UP: {
        if ((flags & FLAG_LONG_PRESSED) != 0) {
          setLongPressed(false);
        }
        if ((flags & FLAG_WILL_CALL_LONG_PRESS) != 0) {
          preventLongPress();
        }
        if ((flags & FLAG_CAUGHT_MESSAGE_TOUCH) != 0) {
          flags &= ~FLAG_CAUGHT_MESSAGE_TOUCH;
          return msg.onTouchEvent(this, e);
        }
        if ((flags & FLAG_CAUGHT_CLICK) != 0) {
          flags &= ~FLAG_CAUGHT_CLICK;
          if (onMessageClick(e.getX(), e.getY())) {
            ViewUtils.onClick(this);
            return true;
          }
        }
        return false;
      }
    }
    return false;
  }
}
