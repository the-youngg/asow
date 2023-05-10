package com.young.asow.service;

import com.young.asow.entity.Conversation;
import com.young.asow.entity.Message;
import com.young.asow.entity.User;
import com.young.asow.entity.UserConversation;
import com.young.asow.exception.BusinessException;
import com.young.asow.modal.ConversationModal;
import com.young.asow.modal.MessageModal;
import com.young.asow.repository.ConversationRepository;
import com.young.asow.repository.MessageRepository;
import com.young.asow.repository.UserConversationRepository;
import com.young.asow.repository.UserRepository;
import com.young.asow.util.ConvertUtil;
import com.young.asow.util.GenerateUtil;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Log4j2
@Service
public class ChatService {
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final UserConversationRepository userConversationRepository;

    public ChatService(
            ConversationRepository conversationRepository,
            UserRepository userRepository,
            MessageRepository messageRepository,
            UserConversationRepository userConversationRepository
    ) {
        this.conversationRepository = conversationRepository;
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
        this.userConversationRepository = userConversationRepository;
    }

    public List<ConversationModal> getConversations(Long userId) {
        List<UserConversation> userConversations = userConversationRepository.findByUserId(userId);
        List<ConversationModal> modals = new ArrayList<>();

        for (UserConversation userConversation : userConversations) {
            Conversation conversation = userConversation.getConversation();
            User from = conversation.getFrom();
            User to = conversation.getTo();
            Message last = conversation.getLastMessage();

            ConversationModal modal = ConvertUtil.Conversation2Modal(conversation);
            modal.setUnread(userConversation.getUnread());
            modal.setFrom(ConvertUtil.User2Modal(from));
            modal.setTo(ConvertUtil.User2Modal(to));
            modal.setLastMessage(ConvertUtil.Message2Modal(last));
            modals.add(modal);
        }

        return modals;
    }


    public List<MessageModal> getConversationMessages(Long conversationId) {
        // 先从数据库根据id降序排列拿到最近的消息，然后将这些数据升序排列以保证前端看到的是自上而下的
        // page往上增加就相当于获取上一段记录
        Sort sort = Sort.by(Sort.Direction.DESC, "id");
        Pageable pageable = PageRequest.of(0, 100, sort);
        return messageRepository
                .findMessagesByConversationId(pageable, conversationId)
                .stream()
                .map(ConvertUtil::Message2Modal)
                .sorted(Comparator.comparing(MessageModal::getId))
                .collect(Collectors.toList());
    }

    @Transactional
    public void saveMessageWithConversation(MessageModal messageModal, Long fromId) {
        Long findConversationId = messageModal.getConversationId();
        Long toId = messageModal.getToId();

        Conversation dbConversation = conversationRepository.findById(findConversationId)
                .orElseThrow(() -> new BusinessException("[" + findConversationId + "]" + " is not found"));

        User from = userRepository.findById(fromId)
                .orElseThrow(() -> new BusinessException("[" + fromId + "]" + " is not found"));

        User to = userRepository.findById(toId)
                .orElseThrow(() -> new BusinessException("[" + toId + "]" + " is not found"));

        Message message = new Message();

        // 将原本的最后一条消息的isLatest设置成false
        dbConversation.getLastMessage().setIsLatest(false);

        // 保存本次消息，并设置为最后一条消息
        message.setIsLatest(true);
        message.setConversation(dbConversation);
        message.setFrom(from);
        message.setTo(to);
        message.setSendTime(messageModal.getSendTime());
        message.setType(messageModal.getType());
        message.setContent(messageModal.getContent());
        messageRepository.save(message);

        // 将最后一条消息的id绑定到会话中
        dbConversation.setLastMessage(message);
        conversationRepository.save(dbConversation);

        // 给对方增加未读数量
        UserConversation userConversation =
                userConversationRepository.findByUserIdAndConversationId(toId, dbConversation.getId());
        userConversation.setUnread(userConversation.getUnread() + 1);
        userConversationRepository.save(userConversation);

    }
}
