package com.ragdocs.provider;

import java.util.List;

public interface ChatProvider {
    ChatResult chat(List<ChatMessage> messages);

    String providerName();

    String modelName();
}
