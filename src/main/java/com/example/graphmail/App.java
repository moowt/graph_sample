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
 * <pre>
 * 使い方:
 *   (引数なし)            一覧 API: 受信トレイの最新 10 件
 *   --top N              取得件数 (1〜100)
 *   --folder NAME        フォルダー (inbox, sentitems, drafts, deleteditems, archive, junkemail またはフォルダー ID)
 *   --days N             直近 N 日以内のメールに絞り込む
 *   --date-field FIELD   --days の基準: received (受信日時, 既定) / sent (送信日時)
 *   --id MESSAGE_ID      個別取得 API: 指定メッセージ 1 件
 *   --user UPN           取得対象のメールボックス (client-secret モードのみ。target.user を上書き)
 *   --config PATH        設定ファイル (既定: ./config.properties)
 * </pre>
 */
public final class App {

    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static void main(String[] args) {
        try {
            Options options = Options.parse(args);
            AppConfig config = AppConfig.load(options.configPath);
            if (options.user != null) {
                config = config.withTargetUser(options.user);
            }

            GraphServiceClient client = GraphClientFactory.create(config);
            MailService mail = MailService.create(client, config);

            System.err.println("メールボックス: " + mail.describeMailbox());
            String json;
            if (options.messageId != null) {
                json = mail.getMessageJson(options.messageId);
            } else {
                DateFilter filter = options.days == null ? null : DateFilter.lastDays(options.dateField, options.days);
                if (filter != null) {
                    System.err.println("絞り込み: " + filter.toODataFilter());
                }
                json = mail.listMessagesJson(options.folder, options.top, filter);
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

    /** コマンドライン引数。 */
    private static final class Options {
        Path configPath = Path.of("config.properties");
        String folder = "inbox";
        int top = 10;
        Integer days;
        DateFilter.Field dateField = DateFilter.Field.RECEIVED;
        String messageId;
        String user;

        static Options parse(String[] args) {
            Options o = new Options();
            boolean dateFieldSpecified = false;
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--top" -> {
                        o.top = Integer.parseInt(value(args, ++i, "--top"));
                        if (o.top < 1 || o.top > 100) {
                            throw new IllegalArgumentException("--top は 1〜100 で指定してください。");
                        }
                    }
                    case "--folder" -> o.folder = value(args, ++i, "--folder");
                    case "--days" -> {
                        o.days = Integer.parseInt(value(args, ++i, "--days"));
                        if (o.days < 1) {
                            throw new IllegalArgumentException("--days は 1 以上で指定してください。");
                        }
                    }
                    case "--date-field" -> {
                        o.dateField = DateFilter.Field.of(value(args, ++i, "--date-field"));
                        dateFieldSpecified = true;
                    }
                    case "--id" -> o.messageId = value(args, ++i, "--id");
                    case "--user" -> o.user = value(args, ++i, "--user");
                    case "--config" -> o.configPath = Path.of(value(args, ++i, "--config"));
                    default -> throw new IllegalArgumentException("不明な引数です: " + args[i]);
                }
            }
            if (dateFieldSpecified && o.days == null) {
                throw new IllegalArgumentException("--date-field は --days と一緒に指定してください。");
            }
            return o;
        }

        private static String value(String[] args, int i, String name) {
            if (i >= args.length) {
                throw new IllegalArgumentException(name + " に値を指定してください。");
            }
            return args[i];
        }
    }
}
