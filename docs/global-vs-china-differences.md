# Graph API によるメール取得: グローバル版と中国版 (21Vianet) の差分

Microsoft Graph API でメールを取得する観点から、グローバル版 Microsoft 365 と中国版 (21Vianet 運営) の差分を整理する。
Microsoft の公開情報に基づくため、導入前に公式ドキュメント (中国版は 21Vianet の情報を含む) で最新の仕様を確認すること。

---

## 1. 環境の違い

中国版は、グローバル版とは **独立した別のクラウド** である。テナント・アカウント・アプリ登録を共有できない。

| 項目 | グローバル版 | 中国版 (21Vianet) |
|---|---|---|
| 運営・販売 | Microsoft | 21Vianet (上海藍雲) が独自に運営・販売 |
| サポート窓口 | Microsoft | 21Vianet |
| テナント・アカウント | グローバル版のテナント | **別物**。グローバル版のテナントやアカウントは使えない |
| アプリ登録 | Entra 管理センター / Azure ポータル | 中国版の Azure ポータル (`https://portal.azure.cn`) で **別途登録**。グローバル版のクライアント ID は使えない |
| Microsoft 365 管理センター | `https://admin.microsoft.com` | `https://portal.partner.microsoftonline.cn` |
| 初期ドメインの例 | `xxx.onmicrosoft.com` | `xxx.partner.onmschina.cn` |
| 個人の Microsoft アカウント (outlook.com 等) | あり | **なし** |

---

## 2. 接続先 (エンドポイント) の違い

| 項目 | グローバル版 | 中国版 (21Vianet) |
|---|---|---|
| 認証 (トークン発行) | `https://login.microsoftonline.com` | `https://login.chinacloudapi.cn` または `https://login.partner.microsoftonline.cn` |
| Graph API | `https://graph.microsoft.com` | `https://microsoftgraph.chinacloudapi.cn` |
| スコープ (アプリケーションアクセス) | `https://graph.microsoft.com/.default` | `https://microsoftgraph.chinacloudapi.cn/.default` |
| スコープ (委任アクセス) | `Mail.Read` (短縮形も可) | `https://microsoftgraph.chinacloudapi.cn/Mail.Read` (**完全な形で指定**) |
| Exchange Online PowerShell | `Connect-ExchangeOnline` | `Connect-ExchangeOnline -ExchangeEnvironmentName O365China` |

- **認証エンドポイント**: 中国版には 2 種類あり、Microsoft の資料には両方が記載されている。
  - Azure SDK for Java の定数 (`AzureAuthorityHosts.AZURE_CHINA`) は `login.chinacloudapi.cn`。
  - どちらかが廃止されたという情報はない。
- **トークンの互換性**: 中国版で取得したトークンはグローバル版の Graph では使えない (逆も同様)。
- **委任アクセスのスコープ**: `Mail.Read` のような短縮形はグローバル版の Graph と解釈されるため、Graph のリソース URL を付けて指定する。

---

## 3. 機能・制約の違い

### 3.1 認証方式

| 項目 | グローバル版 | 中国版 (21Vianet) |
|---|---|---|
| アプリケーションアクセス (クライアントシークレット / 証明書) | 利用可 | 利用可 (仕組みは同じ) |
| 委任アクセス (企業アカウント) | 利用可 | 利用可 |
| 委任アクセス (個人アカウント、`tenant.id=consumers`) | 利用可 | **利用不可** (個人アカウントがない) |

### 3.2 Graph API・権限

| 項目 | グローバル版 | 中国版 (21Vianet) |
|---|---|---|
| メールの一覧取得・個別取得 | 利用可 | 利用可の見込み |
| メールの差分取得 (`message: delta` / `mailFolder: delta`、v1.0) | 利用可 | **非対応** (API リファレンスの提供状況で中国版が対象外) |
| `Mail.ReadBasic` などの各権限 | 利用可 | 中国版の提供状況を明記した情報なし (**要確認**) |
| API の機能・プロパティ | 最新 | グローバル版より反映が遅れることがある (一部プロパティが存在しない報告あり) |

- Microsoft のサービス説明では、中国版には「まだ有効になっていない機能がある」とされている。
- 中国版でメールの新着を取得する場合は、delta の代わりに `$filter=receivedDateTime gt {前回の取得日時}` による取得を検討する。この方法では、削除・移動は検知できない。

### 3.3 テナント側でメールボックスを絞る設定

アプリケーションアクセスでは既定で全メールボックスが読めるため、対象を絞る手段の有無は権限設計に大きく影響する。

| 項目 | グローバル版 | 中国版 (21Vianet) |
|---|---|---|
| Exchange Online RBAC for Applications | 利用可 (推奨方式) | 中国版の提供状況を明記した情報なし (**要確認**) |
| Application Access Policy (旧方式) | 利用可 | 中国版の提供状況を明記した情報なし (**要確認**) |
| Exchange Online の役割ベースの権限管理 (一般) | 利用可 | 利用可 (サービス説明に記載あり) |
| Exchange Online PowerShell のアプリ専用接続 | 利用可 | 利用はできるが「**サポート対象外の構成**」とされている (管理者が手動で設定する分には影響しない) |

- 中国版でメールボックスを絞る手段が使えない場合、アプリケーションアクセスでは全メールボックスが読める状態になる。
  - その場合は、委任アクセスを使う、専用のメールボックスに必要なメールを集めて運用でカバーする、などの代替策を検討する必要がある。

#### RBAC for Applications の設定コマンドの違い

中国版で RBAC for Applications が使える場合、設定コマンド自体はグローバル版と同じ想定である。
主に違うのは、**Exchange Online PowerShell への接続方法** と、**指定する ID・ドメイン** である。

| 手順 | グローバル版 | 中国版 (21Vianet) |
|---|---|---|
| PowerShell への接続 | `Connect-ExchangeOnline` | `Connect-ExchangeOnline -ExchangeEnvironmentName O365China` (**ここが異なる**) |
| サービスプリンシパルの登録 | `New-ServicePrincipal -AppId ... -ObjectId ...` | 同じコマンド。ID は中国版テナントのアプリ登録・エンタープライズ アプリケーションの値を使う (`portal.azure.cn` で確認) |
| スコープの作成 | `New-ManagementScope -RecipientRestrictionFilter ...` | 同じコマンド |
| ロールの割り当て | `New-ManagementRoleAssignment -App ... -Role "Application Mail.Read" ...` | 同じコマンド |
| 確認 | `Test-ServicePrincipalAuthorization ...` | 同じコマンド |
| メールボックスの指定例 | `user@contoso.onmicrosoft.com` | `user@contoso.partner.onmschina.cn` |

```powershell
# 中国版: 接続だけが異なり、以降のコマンドはグローバル版と同じ
Connect-ExchangeOnline -ExchangeEnvironmentName O365China

New-ServicePrincipal -AppId <中国版アプリのクライアントID> -ObjectId <中国版エンタープライズアプリのオブジェクトID> -DisplayName "graph-mail-sample"
New-ManagementScope -Name "GraphMailAllowed" -RecipientRestrictionFilter "CustomAttribute1 -eq 'GraphMailAllowed'"
New-ManagementRoleAssignment -App <中国版アプリのクライアントID> -Role "Application Mail.Read" -CustomResourceScope "GraphMailAllowed"
```

- **コマンドとロールが中国版に存在するかは要確認**: 中国版で RBAC for Applications が使えるかを明記した公式情報はない。設定の前に、必要なコマンドとロールがあるかを確認する (6 章の手順 2)。
- **設定作業を自動化する場合**: スクリプトがアプリ専用認証で PowerShell に接続するときは、中国版では接続パラメーターの追加が必要とされている。
  - また、この構成は「利用はできるがサポート対象外」とされている。
  - 管理者が手動でサインインして設定する場合は関係しない。
- **管理単位でスコープを決める場合**: `-RecipientAdministrativeUnitScope` を使うときは、中国版の Entra ID で管理単位が使えるかも別途確認する。

### 3.4 その他

| 項目 | グローバル版 | 中国版 (21Vianet) |
|---|---|---|
| EWS (Exchange Web Services) | 2026 年 10 月から段階的に無効化、2027 年 4 月に完全停止 | 中国版の日程は明記なし。Microsoft は「Exchange Online の全環境が対象」としている。いずれにせよ新規利用はしない |

---

## 4. 実装上の留意点

### 4.1 SDK は共通で使える

グローバル版と中国版で、**同じ SDK・同じコードを使える**。中国版専用の SDK はない。

| SDK | 中国版での使い方 |
|---|---|
| Microsoft Graph Java SDK (`microsoft-graph`) | 同じものを使う。接続先 (ベース URL) を中国版に設定する |
| Azure Identity (`azure-identity`) | 同じものを使う。資格情報ごとに認証エンドポイント (`authorityHost`) を中国版に設定する |

メール取得などの API を呼ぶコード (`client.users().byUserId(id).messages().get()` など) は変更不要である。
SDK はベース URL に続くパス (`/users/{id}/messages` など) だけを組み立てるため、ベース URL を中国版に設定すれば、同じコードで中国版の Graph に送られる。

違いは「接続先の設定」と「中国版で提供されていない機能の扱い」に集約される。

### 4.2 切り替えが必要な 3 点

SDK の既定値はすべて **グローバル版** なので、中国版では次の 3 点を設定する。**3 点はそろえて切り替える** 必要があり、1 つでも食い違うと認証や API 呼び出しが失敗する。

| # | 設定箇所 | グローバル版 (既定値) | 中国版 |
|---|---|---|---|
| 1 | 資格情報の `authorityHost` (認証エンドポイント) | `https://login.microsoftonline.com` | `https://login.chinacloudapi.cn` (`AzureAuthorityHosts.AZURE_CHINA`) |
| 2 | Graph クライアントのベース URL | `https://graph.microsoft.com/v1.0` | `https://microsoftgraph.chinacloudapi.cn/v1.0` |
| 3 | トークンのスコープ | `https://graph.microsoft.com/.default` | `https://microsoftgraph.chinacloudapi.cn/.default` |

```java
// 1. 認証エンドポイント: 資格情報のビルダーごとに設定する (シークレット・証明書・デバイスコードとも同じ)
TokenCredential credential = new ClientSecretCredentialBuilder()
        .clientId(clientId)
        .tenantId(tenantId)
        .clientSecret(secret)
        .authorityHost(AzureAuthorityHosts.AZURE_CHINA)
        .build();

// 3. スコープ: 中国版の Graph リソースを指定する
GraphServiceClient client = new GraphServiceClient(credential, "https://microsoftgraph.chinacloudapi.cn/.default");

// 2. ベース URL: 中国版の Graph に向ける (API を呼ぶ前に設定する)
client.getRequestAdapter().setBaseUrl("https://microsoftgraph.chinacloudapi.cn/v1.0");
```

- 3 点を 1 か所 (設定値や enum など) にまとめ、クラウドの指定 1 つで一緒に切り替わるようにしておくと、食い違いを防げる。
- ベース URL は、`GraphServiceClient` のコンストラクターに `BaseGraphRequestAdapter.Clouds.CHINA_CLOUD` を渡して設定する方法もある。
  - この場合は、認証プロバイダー (`AzureIdentityAuthenticationProvider`) と HTTP クライアントを自分で組み立てて渡す。
- **委任アクセスのスコープ**: `Mail.Read` のような短縮形はグローバル版の Graph と解釈される。`https://microsoftgraph.chinacloudapi.cn/Mail.Read` のように、Graph のリソース URL を付けて指定する。
- **認証ヘッダーの付与先**: Graph SDK は、許可された接続先 (`graph.microsoft.com`、`microsoftgraph.chinacloudapi.cn` など) にだけ `Authorization` ヘッダーを付ける。
  - 中国版の Graph は最初から許可されているため、追加設定は不要。
  - 独自のプロキシなど、一覧にない接続先を経由する場合は、許可する接続先を明示して認証プロバイダーを作る必要がある。

### 4.3 URL・資格情報を共有しない

- **URL を直書きしない**: コード中に `https://graph.microsoft.com` を直接書くと、中国版で動かない。
  - URL は SDK のリクエストビルダーに組み立てさせる。
  - 続きのページは、応答の `@odata.nextLink` (接続先と同じクラウドの URL が返る) をそのまま使う。
- **アプリ登録・資格情報はクラウドごとに別**: クライアント ID、テナント ID、シークレット・証明書は、クラウドごとに用意する。

### 4.4 機能差への備え

- **中国版で提供されていない API がある**: たとえば、メールの差分取得 (delta) は中国版では使えない。
  - 同じコードで両方の版に対応する場合は、クラウドに応じて処理を切り替える (例: 中国版では日時による取得にする)。
  - 未提供の API を呼ぶと、エラー (400 / 404 など) になる。
- **プロパティの反映が遅れることがある**: SDK のモデルはグローバル版の定義から生成されている。
  - 中国版では、一部のプロパティが返らない (値が null になる) 可能性がある。
  - 新しいプロパティに依存する処理や、`$select` で指定するプロパティは、中国版で動作を確認しておく。
- **権限**: 使う権限 (`Mail.Read` など) が中国版のアプリ登録で選択できるかを確認しておく。

### 4.5 実行環境・テスト

- **ネットワーク**: ファイアウォールやプロキシで接続先を制限している環境では、中国版の接続先を許可する必要がある。
  - 認証: `login.chinacloudapi.cn` (または `login.partner.microsoftonline.cn`)
  - Graph: `microsoftgraph.chinacloudapi.cn`
- **テスト**: 中国版の動作は、グローバル版のテナントでは確認できない。中国版のテナントとアプリ登録を用意して確認する。
- **管理系の操作**: Exchange Online PowerShell は、`-ExchangeEnvironmentName O365China` を付けて中国版に接続する。

---

## 5. 本サンプルでの対応

| 項目 | 対応内容 |
|---|---|
| 接続先の切り替え | `config.properties` の `cloud=global` / `cloud=china` で切り替える |
| 切り替わる内容 | 認証エンドポイント (`authorityHost`)、Graph のベース URL、スコープ |
| 認証エンドポイント | 中国版は `https://login.chinacloudapi.cn` (Azure SDK の定数と同じ値) |
| Graph SDK の設定 | Graph SDK は中国版の Graph (`microsoftgraph.chinacloudapi.cn`) を最初から認証ヘッダーを付ける接続先として扱うため、追加設定は不要 |
| 個人アカウントの拒否 | `cloud=china` と `tenant.id=consumers` の組み合わせは起動時にエラーにする |
| 資格情報 | クライアントシークレット・証明書・デバイスコードとも、グローバル版と同じコードで動作する作り |
| メールの差分取得 | 未実装 (中国版では delta が使えないため、実装する場合は日時による取得が必要) |

---

## 6. 中国版テナントで確認すべき事項

公開情報だけでは確定できないため、中国版テナントを用意したうえで、次の順に確認する。

1. **基本動作**: 中国版でアプリ登録し、本サンプル (`cloud=china`) でトークン取得とメールの一覧・個別取得ができること。
2. **メールボックスを絞る手段**: Exchange Online PowerShell (`-ExchangeEnvironmentName O365China`) で、RBAC for Applications に必要なコマンドとロールが存在するか確認する。

   ```powershell
   Get-Command New-ServicePrincipal, New-ManagementRoleAssignment, Test-ServicePrincipalAuthorization
   Get-ManagementRole -Identity "Application Mail.Read"
   ```

3. **使用する権限**: アプリ登録の API のアクセス許可で、使いたい権限 (`Mail.Read`、`Mail.ReadBasic` など) が選択できること。
4. 2 が使えない場合は、メールボックスを絞る手段について 21Vianet に問い合わせる。

---

## 参考資料

- [Microsoft Graph national cloud deployments](https://learn.microsoft.com/en-us/graph/deployments)
- [Microsoft Entra authentication & national clouds](https://learn.microsoft.com/en-us/entra/identity-platform/authentication-national-cloud)
- [Service availability and roadmaps for Azure in China](https://learn.microsoft.com/en-us/azure/china/concepts-service-availability)
- [Microsoft 365 operated by 21Vianet - Service Descriptions](https://learn.microsoft.com/en-us/office365/servicedescriptions/office-365-platform-service-description/microsoft-365-operated-by-21vianet)
- [Exchange Online Operated by 21Vianet](https://learn.microsoft.com/en-us/office365/servicedescriptions/office-365-platform-service-description/exchange-online-operated-by-21vianet)
- [Role Based Access Control for Applications in Exchange Online](https://learn.microsoft.com/en-us/exchange/permissions-exo/application-rbac)
- [App-only authentication in Exchange Online PowerShell](https://learn.microsoft.com/en-us/powershell/exchange/app-only-auth-powershell-v2?view=exchange-ps)
- [Use delta query to track changes in Microsoft Graph data](https://learn.microsoft.com/en-us/graph/delta-query-overview)
- [message: delta - Microsoft Graph v1.0](https://learn.microsoft.com/en-us/graph/api/message-delta)
- [mailFolder: delta - Microsoft Graph v1.0](https://learn.microsoft.com/en-us/graph/api/mailfolder-delta?view=graph-rest-1.0)
- [Will GraphAPI delta be supported in Azure China (21Vianet)? - Microsoft Q&A](https://learn.microsoft.com/en-gb/answers/questions/5709700/will-graphapi-delta-be-supported-in-azure-china-21)
- [Does Graph China (21Vianet) support singleValueExtendedProperties on contact? - Microsoft Q&A](https://learn.microsoft.com/en-us/answers/questions/5807000/does-graph-china-21vianet-support-singlevalueexten)
- [Azure SDK for Java issue #44804 (China authority host)](https://github.com/Azure/azure-sdk-for-java/issues/44804)
- [Exchange Online EWS, Your Time is Almost Up](https://techcommunity.microsoft.com/blog/exchange/exchange-online-ews-your-time-is-almost-up/4492361)
