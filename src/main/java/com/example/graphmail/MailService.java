package com.example.graphmail;

import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.MessageCollectionResponse;
import com.microsoft.graph.models.User;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.users.item.UserItemRequestBuilder;
import java.util.List;
import java.util.function.Supplier;

/**
 * メール取得処理。
 * 委任アクセスでは /me、アプリケーションアクセスでは /users/{id} のメールボックスを読む。
 * SDK ではどちらも {@link UserItemRequestBuilder} になるため、取得処理は共通で、対象の選び方だけが異なる。
 */
public final class MailService {

    /** 一覧表示用に取得するプロパティ。必要なものだけに絞ると応答が軽くなる。 */
    private static final String[] LIST_SELECT =
            {"id", "subject", "from", "receivedDateTime", "isRead", "hasAttachments", "bodyPreview"};

    private static final String[] DETAIL_SELECT = {"id", "subject", "from", "toRecipients", "ccRecipients",
            "receivedDateTime", "isRead", "hasAttachments", "body"};

    /** 本文を HTML ではなくテキストで返してもらうためのヘッダー。 */
    private static final String PREFER_TEXT_BODY = "outlook.body-content-type=\"text\"";

    private final UserItemRequestBuilder mailbox;
    private final Supplier<String> mailboxLabel;

    private MailService(UserItemRequestBuilder mailbox, Supplier<String> mailboxLabel) {
        this.mailbox = mailbox;
        this.mailboxLabel = mailboxLabel;
    }

    public static MailService create(GraphServiceClient client, AppConfig config) {
        return switch (config.authMode()) {
            // 委任アクセス: サインインしたユーザー自身のメールボックス
            case DEVICE_CODE -> {
                UserItemRequestBuilder me = client.me();
                yield new MailService(me, () -> describeSignedInUser(me));
            }
            // アプリケーションアクセス: 指定ユーザーのメールボックス。
            // Mail.Read のみ付与している場合 /users/{id} の読み取り権限 (User.Read.All) が無いため、
            // ユーザー情報は取得せず指定値をそのまま表示する
            case CLIENT_SECRET -> new MailService(
                    client.users().byUserId(config.targetUser()), config::targetUser);
        };
    }

    /** 対象メールボックスの表示名 (確認用)。 */
    public String describeMailbox() {
        return mailboxLabel.get();
    }

    /** 指定フォルダーのメッセージを受信日時の新しい順に取得する。 */
    public List<Message> listMessages(String folder, int top) {
        MessageCollectionResponse res = mailbox.mailFolders().byMailFolderId(folder).messages()
                .get(req -> {
                    req.queryParameters.select = LIST_SELECT;
                    req.queryParameters.top = top;
                    req.queryParameters.orderby = new String[]{"receivedDateTime desc"};
                });
        return res.getValue();
    }

    /** メッセージ 1 件を本文付きで取得する。 */
    public Message getMessage(String messageId) {
        return mailbox.messages().byMessageId(messageId).get(req -> {
            req.queryParameters.select = DETAIL_SELECT;
            req.headers.add("Prefer", PREFER_TEXT_BODY);
        });
    }

    private static String describeSignedInUser(UserItemRequestBuilder me) {
        User user = me.get(req -> req.queryParameters.select =
                new String[]{"displayName", "mail", "userPrincipalName"});
        // 個人アカウントでは mail が null のことがあるため UPN にフォールバック
        String address = user.getMail() != null ? user.getMail() : user.getUserPrincipalName();
        return user.getDisplayName() + " <" + address + ">";
    }
}
