# graph_sample

Microsoft Graph API でメールを取得する Java サンプル (ローカル実行用 CLI)。

- まず **個人の Microsoft アカウント** (outlook.com / hotmail.com など) で動作確認
- 設定を切り替えるだけで **企業アカウント (Entra ID / Exchange Online)** でも検証可能
- 接続先を **グローバル版 / 中国版 (21Vianet)** で切り替え可能

## 構成

| ファイル | 役割 |
|---|---|
| `App.java` | エントリーポイント。引数解析と表示 |
| `AppConfig.java` | `config.properties` の読み込み・検証、接続先クラウド (グローバル版 / 中国版) の定義 |
| `GraphClientFactory.java` | 認証モード・接続先クラウドに応じた `GraphServiceClient` の生成 |
| `MailService.java` | メール取得 (認証モードに応じて `/me` または `/users/{id}` のメールボックスを読む) |

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
   - 作成済みのアプリを「この組織ディレクトリのみ」から変更する場合、
     `Property api.requestedAccessTokenVersion is invalid` で保存できないことがあります。
     先に **マニフェスト** の `"requestedAccessTokenVersion": null` を `2` に変えて保存してから、
     アカウントの種類を変更してください。
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
| `--user UPN` | 取得対象のメールボックス (client-secret モードのみ。`target.user` を上書き) | - |
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

> **注意**: Entra ID でアプリケーションの許可 `Mail.Read` を与えると **テナント内の全メールボックス** を読めます。
> 企業環境では次の「アクセスできるメールボックスを絞る」の方式を使ってください。
> また本番ではクライアントシークレットより証明書認証の利用を検討してください。

### アクセスできるメールボックスを絞る (Exchange Online RBAC for Applications)

アプリに与える権限を Entra ID ではなく **Exchange Online 側** で付与し、対象メールボックスをスコープで限定します。
(従来の Application Access Policy の後継となる方式です。)

**ポイント**: Entra ID の `Mail.Read` (アプリケーションの許可) と Exchange の RBAC は **足し算 (OR)** で評価されます。
Entra ID 側に `Mail.Read` が残っていると全メールボックスが読めてしまうため、**Entra ID 側の許可は付けない (外す)** でください。
アプリのコードは変更不要です (`.default` スコープのまま)。

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

アプリで確認します。反映までキャッシュにより **30 分〜2 時間程度** かかることがあります。

```bash
mvn -q compile exec:java -Dexec.args="--user allowed@contoso.onmicrosoft.com"   # 取得できる
mvn -q compile exec:java -Dexec.args="--user denied@contoso.onmicrosoft.com"    # 403 ErrorAccessDenied
```

- 検証用の「許可しない」メールボックスには、ライセンス不要の **共有メールボックス** が使えます。
- 対象を部署単位で管理したい場合は、`-CustomResourceScope` の代わりに管理単位 (`-RecipientAdministrativeUnitScope`) も使えます。

## 中国版 (21Vianet) で使う場合

中国版 Microsoft 365 は、グローバル版とは別のクラウドで、エンドポイントが異なります。
`config.properties` の `cloud` で切り替えます (未指定時は `global`)。

| | `cloud=global` (既定) | `cloud=china` |
|---|---|---|
| 認証 (トークン発行) | `https://login.microsoftonline.com` | `https://login.chinacloudapi.cn` |
| Graph API | `https://graph.microsoft.com/v1.0` | `https://microsoftgraph.chinacloudapi.cn/v1.0` |
| スコープ (委任) | `https://graph.microsoft.com/Mail.Read` など | `https://microsoftgraph.chinacloudapi.cn/Mail.Read` など |
| スコープ (アプリケーション) | `https://graph.microsoft.com/.default` | `https://microsoftgraph.chinacloudapi.cn/.default` |

```properties
cloud=china
auth.mode=client-secret          # device-code も可
client.id=<中国版で登録したアプリのクライアントID>
tenant.id=<中国版のテナントID>
client.secret=<シークレットの値>
target.user=user@contoso.partner.onmschina.cn
```

- テナント・アカウント・アプリ登録はグローバル版と **別物** です。アプリ登録は中国版の Azure ポータル (https://portal.azure.cn) で行い、グローバル版のクライアント ID は使えません。
- 中国版には個人用 Microsoft アカウントが無いため、`tenant.id=consumers` は使えません (起動時にエラーにしています)。
- Exchange Online PowerShell は `Connect-ExchangeOnline -ExchangeEnvironmentName O365China` で接続します。
- Graph API や Exchange の機能 (RBAC for Applications など) は、グローバル版と提供状況が異なる場合があります。利用前に中国版のドキュメントで確認してください。

## トラブルシューティング

認証エラー時は、ログの `WARN ... msal4j` 行に `AADSTSxxxxx` コードが出ます。

| エラー | 主な原因 |
|---|---|
| `AADSTS700016` / `AADSTS700038` | `client.id` の誤り、またはテナントにアプリが存在しない |
| `AADSTS7000218` | 「パブリック クライアント フローを許可する」が「いいえ」のまま |
| `AADSTS50020` など (アカウントがテナントに存在しない) | アカウントの種類が個人アカウント非対応、または `tenant.id` の誤り (個人は `consumers`) |
| `AADSTS700016` (`tenant.id=consumers` 時) | アプリが個人アカウント非対応 (アカウントの種類が「この組織ディレクトリのみ」) |
| `AADSTS65001` | 同意が未実施。企業テナントでは管理者の同意が必要な場合あり |
| Graph API エラー `403 ErrorAccessDenied` | `Mail.Read` が未付与 / 同意されていない |
| Graph API エラー `MailboxNotEnabledForRESTAPI` | 対象アカウントに Outlook/Exchange Online メールボックスが無い |

## 補足・今後の拡張候補

- トークンはメモリ上のみで保持しているため、実行のたびにサインインが必要です。
  必要に応じて Azure Identity のトークンキャッシュ永続化 (`TokenCachePersistenceOptions`) を検討してください。
- 取得件数は 1 回のリクエスト分 (最大 100) のみです。全件取得には `PageIterator` によるページングを追加します。
- 新着のみ取得したい場合は `delta` クエリや `$filter=receivedDateTime ge ...` が利用できます。
