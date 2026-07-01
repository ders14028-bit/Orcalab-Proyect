package com.orcalab.realtime.repository;

import com.orcalab.realtime.model.ChatMessage;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {
}
