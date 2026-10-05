package com.example.graphmail;

import com.microsoft.graph.models.odataerrors.ODataError;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.users.item.UserItemRequestBuilder;
import com.microsoft.kiota.RequestAdapter;
import com.microsoft.kiota.RequestInformation;
import com.microsoft.kiota.serialization.Parsable;
import com.microsoft.kiota.serialization.ParsableFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.function.Supplier;

/**
 * メール取得処理。
 * 委任アクセスでは /me、アプリケーションアクセスでは /users/{id} のメールボックスを読む。
 * SDK ではどちらも {@link UserItemRequestBuilder} になるため、取得処理は共通で、対象の選び方だけが異なる。
 *
 * <p>結果は SDK のモデルに変換せず、Graph API が返した JSON をそのまま返す。
 * リクエストの組み立て (URL・クエリ・認証ヘッダー) は SDK に任せている。
 */
public final class MailService {

    /** 一覧で取得するプロパティ。必要なものだけに絞ると応答が軽くなる。 */
    private static final String[] LIST_SELECT = {"id", "subject", "from", "toRecipients",
            "receivedDateTime", "sentDateTime", "isRead", "hasAttachments", "bodyPreview"};

    /** 本文を HTML ではなくテキストで返してもらうためのヘッダー。 */
    private static final String PREFER_TEXT_BODY = "outlook.body-content-type=\"text\"";

    /** エラー応答 (4xx/5xx) を ODataError 例外に変換する。 */
    private static final HashMap<String, ParsableFactory<? extends Parsable>> ERROR_MAPPING = new HashMap<>();

    static {
        ERROR_MAPPING.put("XXX", ODataError::createFromDiscriminatorValue);
    }

    private final RequestAdapter requestAdapter;
    private final UserItemRequestBuilder mailbox;
    private final Supplier<String> mailboxLabel;

    private MailService(RequestAdapter requestAdapter, UserItemRequestBuilder mailbox,
                        Supplier<String> mailboxLabel) {
        this.requestAdapter = requestAdapter;
        this.mailbox = mailbox;
        this.mailboxLabel = mailboxLabel;
    }

    /**
     * 認証モードに応じた対象メールボックスで MailService を生成する。
     *
     * @param client Graph クライアント
     * @param config 設定 (認証モードと、client-secret モードでは target.user を使う)
     * @return MailService
     */
    public static MailService create(GraphServiceClient client, AppConfig config) {
        RequestAdapter adapter = client.getRequestAdapter();
        return switch (config.authMode()) {
            // 委任アクセス: サインインしたユーザー自身のメールボックス
            case DEVICE_CODE -> {
                UserItemRequestBuilder me = client.me();
                yield new MailService(adapter, me, () -> describeSignedInUser(me));
            }
            // アプリケーションアクセス: 指定ユーザーのメールボックス。
            // Mail.Read のみ付与している場合 /users/{id} の読み取り権限 (User.Read.All) が無いため、
            // ユーザー情報は取得せず指定値をそのまま表示する
            case CLIENT_SECRET -> new MailService(
                    adapter, client.users().byUserId(config.targetUser()), config::targetUser);
        };
    }

    /**
     * 対象メールボックスの表示名を返す (確認用)。
     * device-code モードでは /me を呼んで取得し、client-secret モードでは指定値をそのまま返す。
     *
     * @return 表示名
     */
    public String describeMailbox() {
        return mailboxLabel.get();
    }

    /**
     * 一覧 API (GET .../mailFolders/{folder}/messages) を呼び、応答 JSON をそのまま返す。
     * 続きがある場合、応答の {@code @odata.nextLink} に次ページの URL が入る。
     *
     * @param criteria 検索条件
     * @return 応答 JSON (整形前)
     * @throws com.microsoft.graph.models.odataerrors.ODataError Graph API がエラーを返した場合
     */
    public String listMessagesJson(MailSearchCriteria criteria) {
        RequestInformation request = mailbox.mailFolders().byMailFolderId(criteria.folder()).messages()
                .toGetRequestInformation(req -> {
                    req.queryParameters.select = LIST_SELECT;
                    req.queryParameters.top = criteria.top();
                    req.queryParameters.filter = criteria.toODataFilter();
                    req.queryParameters.orderby = new String[]{criteria.toODataOrderBy()};
                });
        return send(request);
    }

    /**
     * 個別取得 API (GET .../messages/{id}) を呼び、応答 JSON をそのまま返す。
     * 全プロパティを取得し、本文はテキスト形式で返してもらう。
     *
     * @param messageId メッセージ ID (一覧の "id" の値)
     * @return 応答 JSON (整形前)
     * @throws com.microsoft.graph.models.odataerrors.ODataError Graph API がエラーを返した場合
     */
    public String getMessageJson(String messageId) {
        RequestInformation request = mailbox.messages().byMessageId(messageId)
                .toGetRequestInformation(req -> req.headers.add("Prefer", PREFER_TEXT_BODY));
        return send(request);
    }

    private String send(RequestInformation request) {
        try (InputStream body = requestAdapter.sendPrimitive(request, ERROR_MAPPING, InputStream.class)) {
            return new String(body.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String describeSignedInUser(UserItemRequestBuilder me) {
        var user = me.get(req -> req.queryParameters.select =
                new String[]{"displayName", "mail", "userPrincipalName"});
        // 個人アカウントでは mail が null のことがあるため UPN にフォールバック
        String address = user.getMail() != null ? user.getMail() : user.getUserPrincipalName();
        return user.getDisplayName() + " <" + address + ">";
    }
}
