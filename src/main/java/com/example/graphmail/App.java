package com.example.graphmail;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.microsoft.graph.models.odataerrors.ODataError;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

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
 *   --since DATE         この日時以降 (例: 2026-10-01 または 2026-10-01T09:00:00+09:00)
 *   --until DATE         この日時より前
 *   --order ORDER        並び順: newest (新しい順, 既定) / oldest (古い順)
 *   --id MESSAGE_ID      個別取得 API: 指定メッセージ 1 件
 *   --user UPN           取得対象のメールボックス (client-secret モードのみ。target.user を上書き)
 *   --config PATH        設定ファイル (既定: ./config.properties)
 * </pre>
 */
public final class App {

    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private App() {
    }

    /**
     * エントリーポイント。結果の JSON を標準出力に出し、エラー時は終了コード 1 で終了する。
     *
     * @param args コマンドライン引数 (クラスの説明を参照)
     */
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
                MailSearchCriteria criteria = options.toCriteria();
                System.err.println("検索条件: " + criteria);
                json = mail.listMessagesJson(criteria);
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
        Instant since;
        Instant until;
        MailSearchCriteria.SortOrder sortOrder = MailSearchCriteria.SortOrder.NEWEST_FIRST;
        String messageId;
        String user;

        static Options parse(String[] args) {
            Options o = new Options();
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
                    case "--since" -> o.since = parseDateTime(value(args, ++i, "--since"), "--since");
                    case "--until" -> o.until = parseDateTime(value(args, ++i, "--until"), "--until");
                    case "--order" -> o.sortOrder = switch (value(args, ++i, "--order")) {
                        case "newest" -> MailSearchCriteria.SortOrder.NEWEST_FIRST;
                        case "oldest" -> MailSearchCriteria.SortOrder.OLDEST_FIRST;
                        default -> throw new IllegalArgumentException("--order は newest か oldest を指定してください。");
                    };
                    case "--id" -> o.messageId = value(args, ++i, "--id");
                    case "--user" -> o.user = value(args, ++i, "--user");
                    case "--config" -> o.configPath = Path.of(value(args, ++i, "--config"));
                    default -> throw new IllegalArgumentException("不明な引数です: " + args[i]);
                }
            }
            if (o.days != null && o.since != null) {
                throw new IllegalArgumentException("--days と --since は同時に指定できません。");
            }
            return o;
        }

        MailSearchCriteria toCriteria() {
            MailSearchCriteria.Builder b = MailSearchCriteria.builder()
                    .folder(folder)
                    .top(top)
                    .sortOrder(sortOrder)
                    .since(since)
                    .until(until);
            if (days != null) {
                b.withinLast(Duration.ofDays(days));
            }
            return b.build();
        }

        /** 日付のみ (システムのタイムゾーンの 0 時) またはオフセット付き日時を受け付ける。 */
        private static Instant parseDateTime(String value, String name) {
            try {
                if (value.length() == 10) {
                    return LocalDate.parse(value).atStartOfDay(ZoneId.systemDefault()).toInstant();
                }
                return OffsetDateTime.parse(value).toInstant();
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException(name + " は 2026-10-01 または 2026-10-01T09:00:00+09:00 の形式で指定してください: " + value);
            }
        }

        private static String value(String[] args, int i, String name) {
            if (i >= args.length) {
                throw new IllegalArgumentException(name + " に値を指定してください。");
            }
            return args[i];
        }
    }
}
