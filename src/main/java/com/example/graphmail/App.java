package com.example.graphmail;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.microsoft.graph.models.odataerrors.ODataError;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import java.nio.file.Path;

/**
 * Microsoft Graph でメールを取得するサンプル。
 * Graph API の応答 JSON を整形して標準出力に出す (補足情報は標準エラー出力)。
 *
 * <p>取得内容 (検索条件・個別取得するメッセージ ID) はすべて設定ファイルの search.* で指定する。
 * 引数は設定ファイルのパスのみ。
 *
 * <pre>
 * 使い方:
 *   (引数なし)            ./config.properties を使う
 *   --config PATH        設定ファイルを指定する
 * </pre>
 */
public final class App {

    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private App() {
    }

    /**
     * エントリーポイント。結果の JSON を標準出力に出し、エラー時は終了コード 1 で終了する。
     *
     * @param args コマンドライン引数 ({@code --config PATH} のみ)
     */
    public static void main(String[] args) {
        try {
            AppConfig config = AppConfig.load(parseConfigPath(args));

            GraphServiceClient client = GraphClientFactory.create(config);
            MailService mail = MailService.create(client, config);

            System.err.println("メールボックス: " + mail.describeMailbox());
            String json;
            if (config.messageId() != null) {
                System.err.println("個別取得: " + config.messageId());
                json = mail.getMessageJson(config.messageId());
            } else {
                System.err.println("検索条件: " + config.searchCriteria());
                json = mail.listMessagesJson(config.searchCriteria());
            }
            System.out.println(PRETTY.toJson(JsonParser.parseString(json)));
        } catch (ODataError e) {
            // Graph API がエラーを返した場合 (権限不足、存在しない ID など)
            System.err.println("Graph API エラー: HTTP " + e.getResponseStatusCode());
            if (e.getError() != null) {
                System.err.println("  code   : " + e.getError().getCode());
                System.err.println("  message: " + e.getError().getMessage());
            }
            System.exit(1);
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.err.println("エラー: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            // 認証失敗などはここに来る。原因 (AADSTS エラーコード等) まで表示する
            System.err.println("エラー: " + e);
            for (Throwable c = e.getCause(); c != null; c = c.getCause()) {
                System.err.println("  原因: " + c);
            }
            System.exit(1);
        }
    }

    private static Path parseConfigPath(String[] args) {
        if (args.length == 0) {
            return Path.of("config.properties");
        }
        if (args.length == 2 && args[0].equals("--config")) {
            return Path.of(args[1]);
        }
        throw new IllegalArgumentException("引数は --config PATH のみ指定できます。検索条件は設定ファイルの search.* で指定してください。");
    }
}
