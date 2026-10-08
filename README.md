# graph_sample

Microsoft Graph API でメールを取得する Java サンプル (ローカル実行用 CLI)。

- **アプリケーションアクセス** (クライアント資格情報フロー) で、ユーザーのサインインなしに指定したメールボックスのメールを取得
- 取得対象のメールボックスは、Exchange Online の **RBAC for Applications** でテナント側から限定可能
- 接続先を **グローバル版 / 中国版 (21Vianet)** で切り替え可能
- 参考として、サインインしたユーザー自身のメールを取得する **委任アクセス** (`/me`) にも対応 ([付録](#付録-委任アクセス-me-でサインインしたユーザー自身のメールを取得する))

## 構成

| ファイル | 役割 |
|---|---|
| `App.java` | エントリーポイント。設定に応じて一覧 / 個別取得を行い、応答 JSON を出力 |
| `AppConfig.java` | `config.properties` の読み込み・検証 (認証・接続先・取得内容)、接続先クラウド (グローバル版 / 中国版) の定義 |
| `GraphClientFactory.java` | 資格情報・接続先クラウドに応じた `GraphServiceClient` の生成 |
| `MailService.java` | メール取得 (`/users/{target.user}` のメールボックスを読む。委任アクセスでは `/me`) |
| `MailSearchCriteria.java` | 一覧の検索条件 (フォルダー・期間・並び順・件数) |

使用ライブラリ: [Microsoft Graph Java SDK](https://github.com/microsoftgraph/msgraph-sdk-java) v6、Azure Identity

### 処理の流れ

1. クライアント ID とクライアントシークレットで Entra ID からアクセストークンを取得する (ユーザーのサインインは不要)
2. トークンを `Authorization: Bearer ...` ヘッダーに付けて Graph API を呼ぶ

どちらもライブラリが行うため、コード上にトークン取得やヘッダー組み立ての処理はありません。

## 前提

- Java 17 以上
- Maven 3.9 以上
- Exchange Online のメールボックスがある Entra ID テナント
  - 取得対象のユーザーに Exchange Online のライセンス (Exchange Online (Plan 1) など) が割り当てられていること
- テナントの管理者権限 (アクセス許可への管理者の同意、Exchange Online PowerShell の操作に必要)

## 手順 1: アプリ登録

[Microsoft Entra 管理センター](https://entra.microsoft.com) で、メールボックスがあるテナントの管理者としてサインインし、
**アプリの登録 → 新規登録** を開きます。

| 項目 | 設定 |
|---|---|
| 名前 | 任意 (例: `graph-mail-sample`) |
| サポートされているアカウントの種類 | **この組織ディレクトリのみ** (シングルテナント) |
| リダイレクト URI | 不要 |

登録後、**概要** の次の 2 つを控えます。

- アプリケーション (クライアント) ID
- ディレクトリ (テナント) ID

## 手順 2: クライアントシークレットの作成

**証明書とシークレット** → **新しいクライアント シークレット** で作成します。

- 作成直後に表示される **「値」** を控えます (隣の「シークレット ID」ではありません)。値は **この画面でしか表示されません**。
- 有効期限が切れると認証できなくなります。検証用なら短めの期限にし、不要になったら削除してください。
- 本番では、クライアントシークレットより証明書認証の利用を検討してください。

## 手順 3: メールボックスへのアクセス権の付与

アプリにメールの読み取り権限を与えます。方法は 2 つあります。

| 方法 | 読めるメールボックス | 用途 |
|---|---|---|
| **3-A. Entra ID でアプリケーションの許可を付与** | **テナント内の全メールボックス** | 動作確認用 |
| **3-B. Exchange Online の RBAC for Applications で付与** | スコープで指定したメールボックスのみ | **本番 (推奨)** |

> **注意**: 3-A と 3-B は **足し算 (OR)** で評価されます。3-B で絞る場合、3-A の許可が残っていると全メールボックスが読めてしまうため、**3-A の許可は外してください**。

### 3-A. Entra ID でアプリケーションの許可を付与 (動作確認用)

1. アプリ登録の **API のアクセス許可** → **アクセス許可の追加** → **Microsoft Graph** → **アプリケーションの許可** (「委任されたアクセス許可」ではない方)
2. `Mail.Read` を追加
3. **「<テナント名> に管理者の同意を与えます」** を押し、状態が緑のチェックになることを確認

### 3-B. RBAC for Applications で対象メールボックスを限定 (本番推奨)

アプリに与える権限を Entra ID ではなく **Exchange Online 側** で付与し、対象メールボックスをスコープで限定します。
(従来の Application Access Policy の後継となる方式です。) アプリのコード・設定は変更不要です。

- 絞り込みの単位は **メールボックス (人・共有メールボックス)** です。フォルダー単位では絞れません。
- 3-A の許可を付けている場合は、先に外してください (上記の注意を参照)。

Exchange Online PowerShell で実行します。

```powershell
Install-Module ExchangeOnlineManagement   # 初回のみ
Connect-ExchangeOnline

# 1. アプリのサービスプリンシパルを Exchange に登録
#    ObjectId は「エンタープライズ アプリケーション」側のオブジェクト ID (アプリ登録のオブジェクト ID ではない)
New-ServicePrincipal -AppId <クライアントID> -ObjectId <エンタープライズアプリのオブジェクトID> -DisplayName "graph-mail-sample"

# 2. 許可するメールボックスに目印を付け、それを条件にスコープを作成
Set-Mailbox -Identity allowed@contoso.onmicrosoft.com -CustomAttribute1 "GraphMailAllowed"
New-ManagementScope -Name "GraphMailAllowed" -RecipientRestrictionFilter "CustomAttribute1 -eq 'GraphMailAllowed'"

# 3. スコープ付きで "Application Mail.Read" ロールを割り当て
New-ManagementRoleAssignment -App <クライアントID> -Role "Application Mail.Read" -CustomResourceScope "GraphMailAllowed"

# 4. 確認 (InScope が True / False になる)
Test-ServicePrincipalAuthorization -Identity <クライアントID> -Resource allowed@contoso.onmicrosoft.com
Test-ServicePrincipalAuthorization -Identity <クライアントID> -Resource denied@contoso.onmicrosoft.com
```

- 反映までキャッシュにより **30 分〜2 時間程度** かかることがあります。
- 検証用の「許可しない」メールボックスには、ライセンス不要の **共有メールボックス** が使えます。
- 対象を部署単位で管理したい場合は、`-CustomResourceScope` の代わりに管理単位 (`-RecipientAdministrativeUnitScope`) も使えます。

## 手順 4: 設定ファイル

```bash
cp config.properties.example config.properties
```

`config.properties` を編集します (`.gitignore` 済み。シークレットを含むためコミットしないこと)。

```properties
auth.mode=client-secret
client.id=<手順1のクライアントID>
tenant.id=<手順1のテナントID>
client.secret=<手順2のシークレットの値>
target.user=user@contoso.onmicrosoft.com
```

`target.user` には、取得対象のメールボックスの UPN (またはユーザー ID) を指定します。

## 手順 5: 実行

Graph API の応答 JSON を整形して **標準出力** に出します。
メールボックス名・検索条件などの補足情報は **標準エラー出力** に出るため、
`> result.json` でリダイレクトすると JSON だけを保存できます。

```bash
mvn -q compile exec:java                                        # ./config.properties を使う
mvn -q compile exec:java -Dexec.args="--config other.properties" # 別の設定ファイルを使う
```

取得内容は `config.properties` の `search.*` で指定します (すべて任意)。

```properties
# 直近7日間の受信トレイを新しい順に10件
search.folder=inbox
search.days=7
search.order=newest
search.top=10

# 期間を指定して古い順に (search.days は空にする)
search.days=
search.since=2026-10-01
search.until=2026-10-05
search.order=oldest

# 個別取得 (指定すると一覧ではなく個別取得 API を呼ぶ。ID は一覧の "id" の値)
search.message-id=AAMkAGMwMjZmNGYx...
```

RBAC (手順 3-B) の確認は、`target.user` を許可したメールボックス / 許可していないメールボックスに切り替えて実行します。
許可していない場合は `403 ErrorAccessDenied` になります。

### 呼び出す API と出力

| 実行内容 | 呼び出す API | 出力 |
|---|---|---|
| 一覧 (既定) | `GET /users/{target.user}/mailFolders/{folder}/messages` | 応答 JSON そのまま (`value` 配列。続きがあれば `@odata.nextLink`) |
| 個別 (`search.message-id` を指定) | `GET /users/{target.user}/messages/{id}` | 応答 JSON そのまま (全プロパティ。本文はテキスト形式) |

- 一覧は `$select` で主要プロパティ (件名・差出人・宛先・受信/送信日時・既読・添付有無・プレビュー) に絞っています。
- 日時の条件と並び順には `receivedDateTime` を使います。Exchange はこの値を、受信メールでは受信日時、送信済みメールでは送信日時 (送信済みアイテムに入った日時) に設定するため、受信・送信を区別せず「メールの日時」として扱えます。
- 本文は `Prefer: outlook.body-content-type="text"` ヘッダーでテキスト形式にしています (HTML で欲しい場合はヘッダーを外す)。

一覧の出力例:

```json
{
  "@odata.context": "https://graph.microsoft.com/v1.0/$metadata#users('...')/mailFolders('inbox')/messages(id,subject,...)",
  "value": [
    {
      "@odata.etag": "W/\"CQAAABYAAAA...\"",
      "id": "AAMkAGMwMjZmNGYx...",
      "receivedDateTime": "2026-10-03T09:51:04Z",
      "sentDateTime": "2026-10-03T09:50:47Z",
      "hasAttachments": false,
      "subject": "テスト",
      "bodyPreview": "メール受信確認\r\n\r\ntest",
      "isRead": true,
      "from": {
        "emailAddress": {
          "name": "Taro Yamada",
          "address": "taro@example.com"
        }
      },
      "toRecipients": [ ... ]
    }
  ]
}
```

### 取得内容の設定 (`search.*`)

引数は `--config PATH` (設定ファイルの指定) のみです。取得内容はすべて `config.properties` で指定します。
未記載または空欄の項目は既定値になります。

| キー | 説明 | 既定値 |
|---|---|---|
| `search.folder` | `inbox` / `sentitems` / `drafts` / `deleteditems` / `archive` / `junkemail` またはフォルダー ID | `inbox` |
| `search.days` | 直近 N 日以内のメールに絞り込む (`search.since` と同時指定不可) | 絞り込みなし |
| `search.since` | この日時以降 (`2026-10-01` または `2026-10-01T09:00:00+09:00`。日付のみはシステムのタイムゾーンの 0 時) | - |
| `search.until` | この日時より前 (形式は `search.since` と同じ) | - |
| `search.order` | 並び順。`newest` (新しい順) / `oldest` (古い順) | `newest` |
| `search.top` | 取得件数 (1〜100) | `10` |
| `search.message-id` | 個別取得 API で取得するメッセージ ID。指定すると他の `search.*` は使わない | - |

### プログラムから検索条件を指定する

設定ファイルの `search.*` は `MailSearchCriteria` (検索条件オブジェクト) に変換して `MailService` に渡しています。
プロダクトのコードからは、ビルダーで直接組み立てて使えます。

```java
MailSearchCriteria criteria = MailSearchCriteria.builder()
        .folder("inbox")                                   // 既定: inbox
        .since(Instant.parse("2026-10-01T00:00:00Z"))      // この日時以降
        .until(Instant.parse("2026-10-08T00:00:00Z"))      // この日時より前
        // .withinLast(Duration.ofDays(7))                 // 直近7日 (since の代わり)
        .sortOrder(MailSearchCriteria.SortOrder.NEWEST_FIRST) // 既定: 新しい順
        .top(20)                                           // 既定: 10 (1〜100)
        .build();

String json = mailService.listMessagesJson(criteria);
```

## 中国版 (21Vianet) で使う場合

中国版 Microsoft 365 は、グローバル版とは別のクラウドで、エンドポイントが異なります。
`config.properties` の `cloud` で切り替えます (未指定時は `global`)。

| | `cloud=global` (既定) | `cloud=china` |
|---|---|---|
| 認証 (トークン発行) | `https://login.microsoftonline.com` | `https://login.chinacloudapi.cn` |
| Graph API | `https://graph.microsoft.com/v1.0` | `https://microsoftgraph.chinacloudapi.cn/v1.0` |
| スコープ | `https://graph.microsoft.com/.default` | `https://microsoftgraph.chinacloudapi.cn/.default` |

```properties
cloud=china
auth.mode=client-secret
client.id=<中国版で登録したアプリのクライアントID>
tenant.id=<中国版のテナントID>
client.secret=<シークレットの値>
target.user=user@contoso.partner.onmschina.cn
```

- テナント・アカウント・アプリ登録はグローバル版と **別物** です。アプリ登録は中国版の Azure ポータル (https://portal.azure.cn) で行い、グローバル版のクライアント ID は使えません。
- Exchange Online PowerShell は `Connect-ExchangeOnline -ExchangeEnvironmentName O365China` で接続します。
- Graph API や Exchange の機能 (RBAC for Applications など) は、グローバル版と提供状況が異なる場合があります。利用前に中国版のドキュメントで確認してください。

## トラブルシューティング

認証エラー時は、ログの `WARN ... msal4j` 行に `AADSTSxxxxx` コードが出ます。

| エラー | 主な原因 |
|---|---|
| `AADSTS700016` / `AADSTS700038` | `client.id` の誤り、または `tenant.id` のテナントにアプリが存在しない |
| `AADSTS7000215` (Invalid client secret) | `client.secret` の誤り (シークレット ID を設定している、値のコピー漏れなど) |
| `AADSTS7000222` | クライアントシークレットの有効期限切れ。新しいシークレットを作成する |
| Graph API エラー `403 ErrorAccessDenied` | アクセス権が無い (手順 3-A の管理者の同意が未実施、または 3-B のスコープ外のメールボックス) |
| Graph API エラー `MailboxNotEnabledForRESTAPI` など | `target.user` に Exchange Online のメールボックスが無い (ライセンス未割り当て、作成直後で未反映) |
| Graph API エラー `400 ErrorInvalidIdMalformed` | `search.message-id` の値が不正 |

## 補足・今後の拡張候補

- 取得件数は 1 回のリクエスト分 (最大 100) のみです。全件取得には `PageIterator` によるページングを追加します。
- 新着のみ取得したい場合は `delta` クエリや `$filter=receivedDateTime ge ...` が利用できます。

## 付録: 委任アクセス (`/me`) でサインインしたユーザー自身のメールを取得する

アプリケーションアクセスの代わりに、ユーザーがブラウザでサインインし、**そのユーザー自身のメールボックス** を読む方式です
(デバイスコードフロー)。個人の Microsoft アカウント (outlook.com など) はこちらの方式のみ対応しています。

### 違い

| | アプリケーションアクセス (本編) | 委任アクセス (付録) |
|---|---|---|
| `auth.mode` | `client-secret` | `device-code` |
| サインイン | 不要 | **実行のたびに** ブラウザでサインインが必要 (トークンはメモリ上のみで保持) |
| 読めるメールボックス | `target.user` で指定したメールボックス | サインインしたユーザー自身のみ |
| 呼び出す API | `/users/{target.user}/...` | `/me/...` |
| 必要なアクセス許可 | Microsoft Graph の **アプリケーションの許可** `Mail.Read` (または RBAC for Applications) | Microsoft Graph の **委任されたアクセス許可** `User.Read` / `Mail.Read` |

取得内容の設定 (`search.*`) と出力形式は本編と同じです。

### アプリ登録の追加設定

本編の手順 1 のアプリ登録に、次を設定します。クライアントシークレットは不要です。

1. **認証** (Authentication) → 設定 → **パブリック クライアント フローを許可する** を有効にして保存 (デバイスコードフローに必須)
2. **API のアクセス許可** → Microsoft Graph → **委任されたアクセス許可** で `User.Read` (既定で追加済み) と `Mail.Read` を追加
   - テナントの「ユーザーの同意」設定によっては、管理者の同意が必要です。
3. 個人の Microsoft アカウントで使う場合のみ: **サポートされているアカウントの種類** を
   「**任意の組織ディレクトリ内のアカウントと個人用 Microsoft アカウント**」にする
   - `Property api.requestedAccessTokenVersion is invalid` で保存できない場合は、先に **マニフェスト** の
     `"requestedAccessTokenVersion": null` を `2` に変えて保存してから変更します。

### 設定ファイル

```properties
auth.mode=device-code
client.id=<クライアントID>
# 企業アカウント: テナント ID (または organizations) / 個人の Microsoft アカウント: consumers
tenant.id=<テナントID>
```

`client.secret` と `target.user` は使いません。中国版 (`cloud=china`) では `consumers` は使えません。

### 実行

実行すると標準エラー出力に次のように表示されるので、ブラウザで URL を開いてコードを入力し、サインイン・同意します。

```
To sign in, use a web browser to open the page https://www.microsoft.com/link and enter the code XXXXXXXX to authenticate.
```

### よくあるエラー

| エラー | 主な原因 |
|---|---|
| `AADSTS7000218` | 「パブリック クライアント フローを許可する」が無効のまま |
| `AADSTS700016` (`tenant.id=consumers` 時) | アプリが個人アカウント非対応 (アカウントの種類が「この組織ディレクトリのみ」) |
| `AADSTS50020` など (アカウントがテナントに存在しない) | サインインしたアカウントが `tenant.id` のテナントに属していない (個人アカウントは `consumers`) |
| `AADSTS65001` | 同意が未実施。企業テナントでは管理者の同意が必要な場合あり |
