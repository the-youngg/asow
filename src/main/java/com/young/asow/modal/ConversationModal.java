package com.young.asow.modal;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ConversationModal {
    Long id;

    String conversationId;

    UserInfoModal to;

    UserInfoModal from;

    LastMessage lastMessage;

    LocalDateTime createTime;

    int unread;

    String topPriority;
}
