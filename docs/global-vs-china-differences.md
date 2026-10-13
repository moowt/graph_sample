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

### 3.4 その他

| 項目 | グローバル版 | 中国版 (21Vianet) |
|---|---|---|
| EWS (Exchange Web Services) | 2026 年 10 月から段階的に無効化、2027 年 4 月に完全停止 | 中国版の日程は明記なし。Microsoft は「Exchange Online の全環境が対象」としている。いずれにせよ新規利用はしない |

---

## 4. 本サンプルでの対応

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

## 5. 中国版テナントで確認すべき事項

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
