package com.example.graphmail;

import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.MessageCollectionResponse;
import com.microsoft.graph.models.User;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import java.util.List;

/** 委任アクセス: サインインしたユーザー自身のメールボックス (/me) を読む。 */
final class MeMailService implements MailService {

    private final GraphServiceClient client;

    MeMailService(GraphServiceClient client) {
        this.client = client;
    }

    @Override
    public String describeMailbox() {
        User me = client.me().get(req -> req.queryParameters.select =
                new String[]{"displayName", "mail", "userPrincipalName"});
        // 個人アカウントでは mail が null のことがあるため UPN にフォールバック
        String address = me.getMail() != null ? me.getMail() : me.getUserPrincipalName();
        return me.getDisplayName() + " <" + address + ">";
    }

    @Override
    public List<Message> listMessages(String folder, int top) {
        MessageCollectionResponse res = client.me().mailFolders().byMailFolderId(folder).messages()
                .get(req -> {
                    req.queryParameters.select = LIST_SELECT;
                    req.queryParameters.top = top;
                    req.queryParameters.orderby = new String[]{"receivedDateTime desc"};
                });
        return res.getValue();
    }

    @Override
    public Message getMessage(String messageId) {
        return client.me().messages().byMessageId(messageId).get(req -> {
            req.queryParameters.select = DETAIL_SELECT;
            req.headers.add("Prefer", PREFER_TEXT_BODY);
        });
    }
}
