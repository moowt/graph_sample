package com.example.graphmail;

import com.microsoft.graph.models.EmailAddress;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.Recipient;
import com.microsoft.graph.models.odataerrors.ODataError;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Microsoft Graph でメールを取得するサンプル。
 *
 * <pre>
 * 使い方:
 *   (引数なし)            受信トレイの最新 10 件を一覧表示
 *   --top N              取得件数 (1〜100)
 *   --folder NAME        フォルダー (inbox, sentitems, drafts, deleteditems, archive, junkemail またはフォルダー ID)
 *   --id MESSAGE_ID      指定メッセージの本文を表示
 *   --user UPN           取得対象のメールボックス (client-secret モードのみ。target.user を上書き)
 *   --config PATH        設定ファイル (既定: ./config.properties)
 * </pre>
 */
public final class App {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    public static void main(String[] args) {
        try {
            Options options = Options.parse(args);
            AppConfig config = AppConfig.load(options.configPath);
            if (options.user != null) {
                config = config.withTargetUser(options.user);
            }

            GraphServiceClient client = GraphClientFactory.create(config);
            MailService mail = MailService.create(client, config);

            System.out.println("メールボックス: " + mail.describeMailbox());
            if (options.messageId != null) {
                printDetail(mail.getMessage(options.messageId));
            } else {
                printList(mail.listMessages(options.folder, options.top), options.folder);
            }
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

    private static void printList(List<Message> messages, String folder) {
        System.out.println("フォルダー: " + folder + " / " + messages.size() + " 件");
        System.out.println("-".repeat(80));
        for (Message m : messages) {
            String mark = Boolean.TRUE.equals(m.getIsRead()) ? " " : "*";
            String clip = Boolean.TRUE.equals(m.getHasAttachments()) ? " [添付]" : "";
            System.out.printf("%s %s  %s%n", mark, formatDate(m), formatSender(m));
            System.out.printf("  件名: %s%s%n", nullToEmpty(m.getSubject()), clip);
            System.out.printf("  概要: %s%n", abbreviate(m.getBodyPreview(), 100));
            System.out.printf("  ID  : %s%n", m.getId());
            System.out.println("-".repeat(80));
        }
        System.out.println("(* = 未読)  本文を見るには --id <ID> を指定してください。");
    }

    private static void printDetail(Message m) {
        System.out.println("-".repeat(80));
        System.out.println("受信日時: " + formatDate(m));
        System.out.println("差出人  : " + formatSender(m));
        System.out.println("宛先    : " + formatRecipients(m.getToRecipients()));
        if (m.getCcRecipients() != null && !m.getCcRecipients().isEmpty()) {
            System.out.println("CC      : " + formatRecipients(m.getCcRecipients()));
        }
        System.out.println("件名    : " + nullToEmpty(m.getSubject()));
        System.out.println("添付    : " + (Boolean.TRUE.equals(m.getHasAttachments()) ? "あり" : "なし"));
        System.out.println("-".repeat(80));
        System.out.println(m.getBody() != null ? nullToEmpty(m.getBody().getContent()) : "");
    }

    private static String formatDate(Message m) {
        return m.getReceivedDateTime() != null ? DATE_FORMAT.format(m.getReceivedDateTime()) : "-";
    }

    private static String formatSender(Message m) {
        return m.getFrom() != null ? formatAddress(m.getFrom().getEmailAddress()) : "(差出人なし)";
    }

    private static String formatRecipients(List<Recipient> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            return "";
        }
        return recipients.stream()
                .map(r -> formatAddress(r.getEmailAddress()))
                .collect(Collectors.joining(", "));
    }

    private static String formatAddress(EmailAddress address) {
        if (address == null) {
            return "";
        }
        String name = address.getName();
        String addr = address.getAddress();
        if (name == null || name.isBlank() || name.equals(addr)) {
            return nullToEmpty(addr);
        }
        return name + " <" + nullToEmpty(addr) + ">";
    }

    private static String abbreviate(String s, int max) {
        String oneLine = nullToEmpty(s).replaceAll("\\s+", " ").strip();
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max) + "…";
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** コマンドライン引数。 */
    private static final class Options {
        Path configPath = Path.of("config.properties");
        String folder = "inbox";
        int top = 10;
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
                    case "--id" -> o.messageId = value(args, ++i, "--id");
                    case "--user" -> o.user = value(args, ++i, "--user");
                    case "--config" -> o.configPath = Path.of(value(args, ++i, "--config"));
                    default -> throw new IllegalArgumentException("不明な引数です: " + args[i]);
                }
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
