# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 概要

Microsoft Graph API でメールを取得する、ローカル実行用の Java CLI サンプル。
Microsoft Graph Java SDK v6 と Azure Identity を使用し、HTTP やトークン処理は自前で書かない。
コメント・ユーザー向けメッセージ・README はすべて日本語。

## コマンド

```bash
mvn -q compile                                              # ビルド (Java 17 / maven.compiler.release=17)
mvn -q compile exec:java                                    # 一覧 API (受信トレイ最新10件)
mvn -q compile exec:java -Dexec.args="--top 5 --folder sentitems"
mvn -q compile exec:java -Dexec.args="--days 7 --order oldest"   # 直近7日を古い順に
mvn -q compile exec:java -Dexec.args="--since 2026-10-01 --until 2026-10-05"
mvn -q compile exec:java -Dexec.args="--id <メッセージID>"     # 個別取得 API
mvn -q compile exec:java -Dexec.args="--user <UPN>"           # client-secret モードで対象メールボックスを上書き
```

- テスト・Lint は未整備 (`src/test` なし、プラグインなし)。検証は実行して確認する。
- Javadoc の確認: `mvn -B javadoc:javadoc -Dshow=public -DadditionalOptions=-Xdoclint:all` で警告が出ないこと (生成物 `target/reports` はコミット対象外)。
- 実行にはカレントディレクトリの `config.properties` が必要 (`config.properties.example` をコピー。`.gitignore` 済みで、クライアントシークレット等を含むためコミットしない)。別ファイルは `--config PATH`。
- `exec:java` の出力をパイプで受けると表示が遅れることがある。その場合は Java で直接起動する:
  `mvn -q dependency:build-classpath -Dmdep.outputFile=cp.txt && java -cp target/classes:$(cat cp.txt) com.example.graphmail.App`
- ログは slf4j-simple、既定レベル warn (`src/main/resources/simplelogger.properties`)。認証失敗時の `ClientAuthenticationException` には原因が載らず、`AADSTS` エラーコードは msal4j の WARN ログ行にだけ出る。

## アーキテクチャ

処理の流れ: `App` (引数解析・表示) → `AppConfig.load` (設定読込・検証) → `GraphClientFactory.create` → `MailService.create` → メール取得。

設定は2つの直交する軸で決まり、どちらも `AppConfig` 内の enum で定義している。

**認証モード `auth.mode` (`AppConfig.AuthMode`)**

| モード | 認証 | 呼ぶ API |
|---|---|---|
| `device-code` | 委任 (`DeviceCodeCredential`) | `/me/...` |
| `client-secret` | アプリケーション (`ClientSecretCredential`) | `/users/{target.user}/...` |

- 個人 Microsoft アカウントは委任のみ対応 (`tenant.id=consumers`)。`client-secret` では `consumers`/`common`/`organizations` を起動時に拒否する。
- モードを追加する場合、`GraphClientFactory.create` と `MailService.create` の `switch` 式 (default なし) を両方更新する。漏れるとコンパイルエラーになる。
- SDK 6.x では `client.me()` も `client.users().byUserId(id)` も `UserItemRequestBuilder` を返す。`MailService` はこれを利用し、取得処理を共通化している。モードによって変わるのは、対象メールボックスとその表示名の取り方だけ。

**接続先クラウド `cloud` (`AppConfig.Cloud`、既定 `global`)**

クラウドごとに次の3点を切り替えている (`GraphClientFactory`)。新しいクラウドを足すときも同じ3点が必要。
1. Azure Identity の `authorityHost` (`login.microsoftonline.com` / `login.chinacloudapi.cn`)
2. Graph のベース URL: `getRequestAdapter().setBaseUrl(graphEndpoint + "/v1.0")` (SDK 既定はグローバル固定)
3. スコープ: 委任は `"<graphEndpoint>/Mail.Read"` のように完全修飾、アプリは `"<graphEndpoint>/.default"`。`Mail.Read` のような短縮形はグローバル版 Graph と解釈されるため使わない。

- Graph SDK は許可ホスト (`graph.microsoft.com`、`microsoftgraph.chinacloudapi.cn`、米国政府版など) にしか `Authorization` ヘッダーを付けない。一覧にないホストを使う場合は、`AzureIdentityAuthenticationProvider` に allowedHosts を渡して構築する必要がある。
- `cloud=china` と `tenant.id=consumers` の組み合わせは拒否する (中国版に個人アカウントはない)。

**メール取得と出力 (`MailService` / `App`)**

- 出力は Graph の応答 JSON をそのまま Gson で整形したもの。SDK のモデルには変換せず、SDK でリクエストを組み立てて (`toGetRequestInformation`)、`RequestAdapter.sendPrimitive(..., InputStream.class)` で生の応答を受け取る。エラー応答は `ERROR_MAPPING` で `ODataError` 例外に変換している。
- 標準出力は JSON 専用。メールボックス名・絞り込み条件・デバイスコードの案内は標準エラー出力に出す (`System.out` に補足情報を足さない)。
- 一覧は `LIST_SELECT` で `$select` を絞っている。個別取得は `$select` なし (全プロパティ)。本文は `Prefer: outlook.body-content-type="text"` でテキスト形式。
- 一覧の検索条件は `MailSearchCriteria` (ビルダー) に集約し、`$filter` / `$orderby` / `$top` とフォルダーへの変換もここで行う。CLI 引数 (`--days` / `--since` / `--until` / `--order` / `--folder` / `--top`) は `App.Options.toCriteria()` で変換するだけ。条件を増やすときは `MailSearchCriteria` に追加する。
- メールの日時は `receivedDateTime` に統一している (受信メールは受信日時、送信済みメールは送信日時が入る。Graph に受信・送信をまとめた専用の日時プロパティはない)。
- `$filter` と `$orderby` を併用するときは、`$orderby` のプロパティが `$filter` にも含まれていないと Graph が `400 InefficientFilter` を返す。並び順を別プロパティにする場合は注意。
- 一覧は1リクエスト (最大100件) のみ。ページングとトークンの永続キャッシュは未実装 (実行ごとにサインインが必要)。

## 注意点

- クラス・public メソッドには日本語の Javadoc を書く (概要に加え、`@param` / `@return` / 主な `@throws`)。

- `AppConfig` は record のため、自動生成される `toString()` に `clientSecret` がそのまま含まれる。設定オブジェクトをログ出力しない。
- 引数や設定キーを追加・変更したら、README の引数表・設定例と `config.properties.example` も更新する。
- README には、アプリ登録手順、企業テナントでのメールボックス制限 (Exchange Online RBAC for Applications)、中国版の注意点、AADSTS エラーの対処表がある。
