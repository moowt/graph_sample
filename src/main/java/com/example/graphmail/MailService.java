package com.example.graphmail;

import com.microsoft.graph.models.Message;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import java.util.List;

/**
 * メール取得処理。
 * 委任アクセスでは /me/...、アプリケーションアクセスでは /users/{id}/... を呼ぶ必要があり、
 * SDK 上も別々のリクエストビルダーになるため実装を分けている。
 */
public interface MailService {

    /** 一覧表示用に取得するプロパティ。必要なものだけに絞ると応答が軽くなる。 */
    String[] LIST_SELECT = {"id", "subject", "from", "receivedDateTime", "isRead", "hasAttachments", "bodyPreview"};

    String[] DETAIL_SELECT = {"id", "subject", "from", "toRecipients", "ccRecipients",
            "receivedDateTime", "isRead", "hasAttachments", "body"};

    /** 本文を HTML ではなくテキストで返してもらうためのヘッダー。 */
    String PREFER_TEXT_BODY = "outlook.body-content-type=\"text\"";

    /** 対象メールボックスの表示名 (確認用)。 */
    String describeMailbox();

    /** 指定フォルダーのメッセージを受信日時の新しい順に取得する。 */
    List<Message> listMessages(String folder, int top);

    /** メッセージ 1 件を本文付きで取得する。 */
    Message getMessage(String messageId);

    static MailService create(GraphServiceClient client, AppConfig config) {
        return switch (config.authMode()) {
            case DEVICE_CODE -> new MeMailService(client);
            case CLIENT_SECRET -> new UserMailService(client, config.targetUser());
        };
    }
}
