package com.example.graphmail;

import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.MessageCollectionResponse;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import java.util.List;

/** アプリケーションアクセス: 指定ユーザーのメールボックス (/users/{id}) を読む。 */
final class UserMailService implements MailService {

    private final GraphServiceClient client;
    private final String userId;

    UserMailService(GraphServiceClient client, String userId) {
        this.client = client;
        this.userId = userId;
    }

    @Override
    public String describeMailbox() {
        // Mail.Read のみ付与している場合 /users/{id} の読み取り権限 (User.Read.All) が無いため、
        // ユーザー情報は取得せず指定値をそのまま表示する
        return userId;
    }

    @Override
    public List<Message> listMessages(String folder, int top) {
        MessageCollectionResponse res = client.users().byUserId(userId)
                .mailFolders().byMailFolderId(folder).messages()
                .get(req -> {
                    req.queryParameters.select = LIST_SELECT;
                    req.queryParameters.top = top;
                    req.queryParameters.orderby = new String[]{"receivedDateTime desc"};
                });
        return res.getValue();
    }

    @Override
    public Message getMessage(String messageId) {
        return client.users().byUserId(userId).messages().byMessageId(messageId).get(req -> {
            req.queryParameters.select = DETAIL_SELECT;
            req.headers.add("Prefer", PREFER_TEXT_BODY);
        });
    }
}
