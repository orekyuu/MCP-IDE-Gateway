package net.orekyuu.intellijmcp.comment;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Project-level service that manages inline comments and notifies listeners via Message Bus.
 */
@Service(Service.Level.PROJECT)
public final class InlineCommentService {

    public static final Topic<InlineCommentListener> TOPIC =
            Topic.create("Inline Comment", InlineCommentListener.class);

    private final Project project;
    private final List<InlineComment> comments = new CopyOnWriteArrayList<>();

    public InlineCommentService(Project project) {
        this.project = project;
    }

    public static InlineCommentService getInstance(Project project) {
        return project.getService(InlineCommentService.class);
    }

    public InlineComment addComment(String filePath, int line, String comment) {
        return addComment(filePath, line, comment, CommentMessage.Author.AI);
    }

    public InlineComment addComment(String filePath, int line, String comment, CommentMessage.Author author) {
        InlineComment inlineComment = new InlineComment(filePath, line, comment, author);
        comments.add(inlineComment);
        project.getMessageBus().syncPublisher(TOPIC).onCommentAdded(inlineComment);
        return inlineComment;
    }

    public void removeComment(String id) {
        comments.stream()
                .filter(c -> c.getId().equals(id))
                .findFirst()
                .ifPresent(comment -> {
                    comments.remove(comment);
                    project.getMessageBus().syncPublisher(TOPIC).onCommentRemoved(comment);
                });
    }

    public InlineComment addReply(String threadId, CommentMessage.Author author, String text) {
        for (InlineComment comment : comments) {
            if (comment.getId().equals(threadId)) {
                comment.addMessage(new CommentMessage(author, text));
                project.getMessageBus().syncPublisher(TOPIC).onReplyAdded(comment);
                return comment;
            }
        }
        return null;
    }

    public void removeMessage(String threadId, String messageId) {
        for (InlineComment comment : comments) {
            if (comment.getId().equals(threadId)) {
                comment.removeMessage(messageId);
                if (comment.getMessageCount() == 0) {
                    comments.remove(comment);
                    project.getMessageBus().syncPublisher(TOPIC).onCommentRemoved(comment);
                } else {
                    project.getMessageBus().syncPublisher(TOPIC).onCommentEdited(comment);
                }
                return;
            }
        }
    }

    public InlineComment updateMessage(String threadId, String messageId, String newText) {
        for (InlineComment comment : comments) {
            if (comment.getId().equals(threadId)) {
                comment.updateMessage(messageId, newText);
                project.getMessageBus().syncPublisher(TOPIC).onCommentEdited(comment);
                return comment;
            }
        }
        return null;
    }

    @Deprecated
    public InlineComment updateComment(String id, String newComment) {
        for (InlineComment comment : comments) {
            if (comment.getId().equals(id)) {
                List<CommentMessage> msgs = comment.getMessages();
                if (!msgs.isEmpty()) {
                    return updateMessage(id, msgs.get(0).getMessageId(), newComment);
                }
            }
        }
        return null;
    }

    public List<InlineComment> getCommentsForFile(String filePath) {
        return comments.stream()
                .filter(c -> c.getFilePath().equals(filePath))
                .toList();
    }

    public List<InlineComment> getAllComments() {
        return new ArrayList<>(comments);
    }

    public void clearAll() {
        comments.clear();
        project.getMessageBus().syncPublisher(TOPIC).onAllCommentsCleared();
    }

    public interface InlineCommentListener {
        default void onCommentAdded(InlineComment comment) {}
        default void onCommentRemoved(InlineComment comment) {}
        default void onCommentEdited(InlineComment comment) {}
        default void onReplyAdded(InlineComment comment) {}
        default void onAllCommentsCleared() {}
    }
}
