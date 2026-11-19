package map.project.user.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import map.project.user.chat.repository.ChatMessageRepository;
import map.project.user.moderation.*;
import static org.mockito.Mockito.mock;

/** Existing history/room unit fixtures with no persisted block or restriction. */
final class ModerationTestSupport {
    static ChatModerationGuard guard(ChatMessageRepository messages) {
        return new ChatModerationGuard(mock(UserBlockRepository.class),mock(ChatRestrictionRepository.class),
                messages,new ChatContentPolicy(""),new ObjectMapper().findAndRegisterModules());
    }
}
