# graph_sample

Microsoft Graph API でメールを取得する Java サンプル (ローカル実行用 CLI)。

- まず **個人の Microsoft アカウント** (outlook.com / hotmail.com など) で動作確認
- 設定を切り替えるだけで **企業アカウント (Entra ID / Exchange Online)** でも検証可能

## 構成

| ファイル | 役割 |
|---|---|
| `App.java` | エントリーポイント。引数解析と表示 |
| `AppConfig.java` | `config.properties` の読み込み・検証 |
| `GraphClientFactory.java` | 認証モードに応じた `GraphServiceClient` の生成 |
| `MailService.java` / `MeMailService.java` / `UserMailService.java` | メール取得 (`/me` 版と `/users/{id}` 版) |

使用ライブラリ: [Microsoft Graph Java SDK](https://github.com/microsoftgraph/msgraph-sdk-java) v6、Azure Identity

### 認証モード

| モード | 方式 | 対象 | 呼び出す API |
|---|---|---|---|
| `device-code` (既定) | 委任アクセス。ブラウザでサインイン | 個人アカウント / 企業アカウント | `/me/mailFolders/{folder}/messages` |
| `client-secret` | アプリケーションアクセス。サインイン不要 | **企業テナントのみ** | `/users/{id}/mailFolders/{folder}/messages` |

> 個人の Microsoft アカウントはアプリケーションアクセス (client credentials) に対応していないため、
> 個人メールで試す場合は `device-code` を使います。

## 前提

- Java 17 以上
- Maven 3.9 以上

## 手順 1: アプリ登録 (初回のみ)

1. [Microsoft Entra 管理センター](https://entra.microsoft.com) (または Azure ポータル) にサインインし、
   **アプリの登録 → 新規登録** を開く
   - アプリの登録にはディレクトリ (テナント) が必要です。個人アカウントでディレクトリが無い場合は、
     無料の Azure アカウントを作成すると既定のディレクトリが用意されます。
2. 登録内容
   - 名前: 任意 (例: `graph-mail-sample`)
   - **サポートされているアカウントの種類**:
     「**任意の組織ディレクトリ内のアカウントと個人用 Microsoft アカウント**」
     (これで個人・企業の両方で同じアプリ登録を使えます)
   - リダイレクト URI: 不要
3. 作成後、**認証** (Authentication) → 詳細設定 → **パブリック クライアント フローを許可する** を「**はい**」にして保存
   (デバイスコードフローに必須)
4. **API のアクセス許可** → アクセス許可の追加 → Microsoft Graph → **委任されたアクセス許可** で次を追加
   - `User.Read` (既定で追加済み)
   - `Mail.Read`
5. **概要** の「アプリケーション (クライアント) ID」を控える

## 手順 2: 設定ファイル

```bash
cp config.properties.example config.properties
```

`config.properties` を編集します (個人アカウントの場合):

```properties
auth.mode=device-code
client.id=<手順1で控えたクライアントID>
tenant.id=consumers
```

`config.properties` は `.gitignore` 済みです。

## 手順 3: 実行

```bash
# 受信トレイの最新10件
mvn -q compile exec:java

# 件数・フォルダーを指定
mvn -q compile exec:java -Dexec.args="--top 5 --folder sentitems"

# 1件の本文を表示 (ID は一覧に出力されます)
mvn -q compile exec:java -Dexec.args="--id <メッセージID>"
```

実行すると次のように表示されるので、ブラウザで URL を開いてコードを入力し、サインイン・同意します。

```
To sign in, use a web browser to open the page https://www.microsoft.com/link and enter the code XXXXXXXX to authenticate.
```

出力例:

```
メールボックス: Taro Yamada <taro@outlook.com>
フォルダー: inbox / 2 件
--------------------------------------------------------------------------------
* 2026-09-30 09:12  Microsoft アカウント チーム <account-security-noreply@accountprotection.microsoft.com>
  件名: 新しいアプリが Microsoft アカウントに接続されました
  概要: ...
  ID  : AQMkADAwATM3...
--------------------------------------------------------------------------------
```

### 引数

| 引数 | 説明 | 既定値 |
|---|---|---|
| `--top N` | 取得件数 (1〜100) | 10 |
| `--folder NAME` | `inbox` / `sentitems` / `drafts` / `deleteditems` / `archive` / `junkemail` またはフォルダー ID | `inbox` |
| `--id ID` | 指定メッセージの本文をテキストで表示 | - |
| `--config PATH` | 設定ファイル | `./config.properties` |

## 企業アカウントで検証する場合

### A. 委任アクセス (サインインしたユーザー自身のメール)

コードの変更は不要です。`tenant.id` を企業テナントの ID (または `organizations`) に変えるだけです。

```properties
auth.mode=device-code
client.id=<クライアントID>
tenant.id=<企業テナントID>
```

- アプリ登録を企業テナント側で行う場合も、手順 1 と同じ設定 (パブリック クライアント フロー有効、委任 `Mail.Read`) にします。
- テナントの「ユーザーの同意」設定によっては、管理者の同意が必要になります。

### B. アプリケーションアクセス (サインインなしで特定ユーザーのメールを読む)

バッチ処理など、ユーザー操作なしで動かしたい場合の方式です。

1. アプリ登録の **API のアクセス許可** で、Microsoft Graph の **アプリケーションの許可** `Mail.Read` を追加し、
   **管理者の同意を与える**
2. **証明書とシークレット** → 新しいクライアント シークレットを作成し、値を控える
3. `config.properties`:

   ```properties
   auth.mode=client-secret
   client.id=<クライアントID>
   tenant.id=<企業テナントID>
   client.secret=<シークレットの値>
   target.user=user@contoso.com
   ```

> **注意**: アプリケーションの許可 `Mail.Read` は、既定では **テナント内の全メールボックス** を読めます。
> 企業環境では Exchange Online の **RBAC for Applications** (または Application Access Policy) で
> 対象メールボックスを制限することを強く推奨します。
> また本番ではクライアントシークレットより証明書認証の利用を検討してください。

## トラブルシューティング

認証エラー時は、ログの `WARN ... msal4j` 行に `AADSTSxxxxx` コードが出ます。

| エラー | 主な原因 |
|---|---|
| `AADSTS700016` / `AADSTS700038` | `client.id` の誤り、またはテナントにアプリが存在しない |
| `AADSTS7000218` | 「パブリック クライアント フローを許可する」が「いいえ」のまま |
| `AADSTS50020` など (アカウントがテナントに存在しない) | アカウントの種類が個人アカウント非対応、または `tenant.id` の誤り (個人は `consumers`) |
| `AADSTS65001` | 同意が未実施。企業テナントでは管理者の同意が必要な場合あり |
| Graph API エラー `403 ErrorAccessDenied` | `Mail.Read` が未付与 / 同意されていない |
| Graph API エラー `MailboxNotEnabledForRESTAPI` | 対象アカウントに Outlook/Exchange Online メールボックスが無い |

## 補足・今後の拡張候補

- トークンはメモリ上のみで保持しているため、実行のたびにサインインが必要です。
  必要に応じて Azure Identity のトークンキャッシュ永続化 (`TokenCachePersistenceOptions`) を検討してください。
- 取得件数は 1 回のリクエスト分 (最大 100) のみです。全件取得には `PageIterator` によるページングを追加します。
- 新着のみ取得したい場合は `delta` クエリや `$filter=receivedDateTime ge ...` が利用できます。
