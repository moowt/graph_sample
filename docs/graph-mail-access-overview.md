# Microsoft Graph によるメール取得: 認証方式・権限・メールボックス制限の概要

Graph API でメールを取得する際の、認証方式、メール関連の権限、テナント側でアクセス対象のメールボックスを絞る方法を整理する。
Microsoft の公開情報に基づくため、導入前に公式ドキュメントで最新の仕様を確認すること。

---

## 1. メールボックスの構造

### 1.1 全体像

Exchange Online では、テナントの中にユーザーごとのメールボックスがあり、メールボックスの中がフォルダーで階層化されている。
メール (メッセージ) は、必ずいずれか 1 つのフォルダーに入っている。

```
テナント
├─ ユーザー A のメールボックス
│   ├─ 受信トレイ (inbox)
│   │   └─ (ユーザーが作ったサブフォルダー)
│   ├─ 送信済みアイテム (sentitems)
│   ├─ 下書き (drafts)
│   ├─ 削除済みアイテム (deleteditems)
│   ├─ 迷惑メール (junkemail)
│   ├─ アーカイブ (archive)
│   ├─ 送信トレイ (outbox)
│   ├─ 会話の履歴 (conversationhistory)
│   └─ (ユーザーが作ったフォルダー)
├─ ユーザー B のメールボックス
└─ 共有メールボックス (例: 問い合わせ窓口)
```

### 1.2 メールボックスの種類

| 種類 | 概要 | ライセンス |
|---|---|---|
| ユーザーメールボックス | 各ユーザーのメールボックス | Exchange Online のライセンスが必要 (Exchange Online (Plan 1) など) |
| 共有メールボックス | 複数人で使うメールボックス (問い合わせ窓口など)。直接サインインはせず、アクセス権を与えられたユーザーが利用する | 不要 (容量などの条件あり) |

- Entra ID にユーザーを作っただけではメールボックスは作られない。Exchange Online のライセンスを割り当てると作成される。
- Graph API では、どちらも `/users/{UPN またはアドレス}` で同じように扱える。

### 1.3 フォルダー

- **既定のフォルダー**: 受信トレイ・送信済みアイテムなどは全メールボックスに最初からある。
  - Graph API では、`inbox`、`sentitems`、`drafts`、`deleteditems`、`junkemail`、`archive` などの **既定の名前** で指定できる。
  - 画面上の表示名 (「受信トレイ」など) は言語設定によって変わるため、プログラムからは既定の名前を使う。
- **ユーザーが作ったフォルダー**: 名前は自由で、受信トレイの下などに階層を作れる。
  - Graph API では **フォルダー ID** で指定する。ID はフォルダー一覧 (`GET /users/{UPN}/mailFolders`、サブフォルダーは `.../childFolders`) で調べる。
- フォルダーを指定してメールを取得すると、**そのフォルダー直下のメールだけ** が対象になる (サブフォルダーのメールは含まれない)。
- メールボックス内にはメール以外のデータ (予定表・連絡先など) もあるが、Graph API では別の API・別の権限で扱う (`Mail.Read` では読めない)。

### 1.4 メール (メッセージ)

- 各メールは、件名・差出人・宛先・日時・本文・添付ファイルなどのプロパティを持つ。
  - 所属するフォルダーは `parentFolderId` で分かる。
- 受信したメールは受信トレイに、送信したメールは送信済みアイテムに入る。
  - その後、ユーザーの操作や仕分けルールで、別のフォルダーに移動されることがある。
- メールの ID は、フォルダーを移動すると変わる場合がある。
  - 変わらない ID が必要な場合は、リクエストに `Prefer: IdType="ImmutableId"` ヘッダーを付けて取得する。

### 1.5 権限との関係

- メールの読み取り権限 (3 章) は **メールボックス単位** で効く。
  - あるメールボックスを読める場合、その中の **全フォルダー** が対象になる。
- テナント側で対象を絞る設定 (4 章) も、絞り込みの単位はメールボックスまで。フォルダー単位では絞れない。

---

## 2. 選択可能な認証方式

Graph API の呼び出しは、どの方式でも次の 2 段階で行う。

1. Entra ID のトークンエンドポイントからアクセストークン (JWT、有効期限は通常約 1 時間) を取得する
2. トークンを `Authorization: Bearer <トークン>` ヘッダーに付けて Graph API を呼ぶ

方式の違いは、1 の「誰の権限でトークンをもらうか」にある。

### 2.1 方式の比較

| | 委任アクセス (Delegated) | アプリケーションアクセス (Application / App-only) |
|---|---|---|
| 誰の権限で動くか | サインインしたユーザー | アプリ自身 |
| ユーザーのサインイン | 必要 | 不要 |
| 読めるメールボックス | サインインしたユーザー自身のもの | 権限を与えたメールボックス (既定ではテナント内の全員) |
| 呼び出す API の例 | `GET /me/messages` | `GET /users/{UPN}/messages` |
| 必要なアクセス許可 | 委任されたアクセス許可 (`Mail.Read` など) | アプリケーションの許可 (`Mail.Read` など) + 管理者の同意 |
| トークン内の権限 | `scp` クレーム (例: `"Mail.Read User.Read"`) | `roles` クレーム (例: `["Mail.Read"]`) |
| 個人の Microsoft アカウント (outlook.com 等) | 対応 | **非対応** (企業テナント専用) |
| 向いている用途 | ユーザーが操作するアプリ | バッチ処理・サーバー間連携 |

### 2.2 委任アクセスのフロー

| フロー | 概要 | 本サンプル |
|---|---|---|
| デバイスコードフロー | 表示された URL とコードをブラウザで入力してサインインする。画面のない CLI 向け | 実装済み (`auth.mode=device-code`) |
| 認可コードフロー (ブラウザでのリダイレクト) | Web アプリ・デスクトップアプリの標準的な方式 | 未実装 |

デバイスコードフローの注意点:

- アプリ登録で「パブリック クライアント フローを許可する」を有効にする必要がある (無効だと `AADSTS7000218`)。
- 個人アカウントで使うには、アプリ登録の「サポートされているアカウントの種類」に個人用 Microsoft アカウントを含め、`tenant.id=consumers` を指定する。
  - 既存のアプリをこの種類に変更する場合、先にマニフェストの `requestedAccessTokenVersion` を `2` にしないと保存できない。
  - 組織専用のアプリ登録に個人アカウントでサインインすると、アクセス権がないエラーになる。
- 本サンプルはトークンをメモリ上だけで保持するため、実行のたびにサインインが必要。
  - リフレッシュトークンを永続化すれば、2 回目以降のサインインを省略できる (未実装)。

### 2.3 アプリケーションアクセス (クライアント資格情報フロー) の資格情報

| 資格情報 | 概要 | 本サンプル |
|---|---|---|
| クライアントシークレット | アプリ登録で発行する文字列。有効期限あり | 実装済み (`auth.mode=client-secret`) |
| 証明書 | 秘密鍵で署名して認証する。シークレットより安全で、本番向け | 未実装 |

クライアントシークレットの注意点:

- 発行直後の画面でしか値を確認できない。「シークレット ID」ではなく「値」を使う。
- 誤った値では `AADSTS7000215` になる。期限切れでは `AADSTS7000222` になる。
- アプリ登録はメールボックスがあるテナントで行い、シングルテナント (この組織ディレクトリのみ) でよい。
- スコープには `https://graph.microsoft.com/.default` を指定する。これで、付与済みのアプリケーションの許可がすべて使われる。

### 2.4 接続先クラウド

中国版 (21Vianet) はグローバル版と別のクラウドで、テナント・アカウント・アプリ登録も別になる。

| | グローバル版 | 中国版 (21Vianet) |
|---|---|---|
| 認証 | `https://login.microsoftonline.com` | `https://login.chinacloudapi.cn` |
| Graph API | `https://graph.microsoft.com` | `https://microsoftgraph.chinacloudapi.cn` |
| アプリ用スコープ | `https://graph.microsoft.com/.default` | `https://microsoftgraph.chinacloudapi.cn/.default` |
| 個人アカウント | あり | なし |

- 本サンプルは `cloud=global|china` で切り替える。

---

## 3. メール関連の権限

### 3.1 各種権限でできること

| 権限 | できること | 備考 |
|---|---|---|
| `Mail.ReadBasic` 系 | メールを読む。ただし **本文 (`body`)・プレビュー (`bodyPreview`)・添付ファイル・拡張プロパティは読めない** | 件名・差出人・宛先・日時などのメタデータは読める。`hasAttachments` (添付の有無) も取得できる |
| `Mail.Read` 系 | メールを読む (本文・添付ファイルを含む) | 本サンプルで使用 |
| `Mail.ReadWrite` 系 | 読み取りに加え、作成・更新 (既読化など)・削除・フォルダー移動 | **送信は含まない** |
| `Mail.Send` 系 | メールを送信する | `Mail.ReadWrite` がなくても、送信済みアイテムへの保存は可能 |
| `MailboxSettings.Read` / `.ReadWrite` | メールボックスの設定 (自動応答、タイムゾーン、言語、勤務時間など) の読み取り / 変更 | メールの内容は読めない |
| `MailboxFolder.*` / `MailboxItem.*` | Exchange 管理者向けのメールボックス インポート・エクスポート API (`/admin/exchange/mailboxes/...`) | 比較的新しい API。Exchange 管理者ロールが必要とする情報もあり、利用前に公式ドキュメントで要確認 |
| `User.Read` | サインインしたユーザーのプロフィールを読む | メールの権限ではないが、委任アクセスで `/me` を使う場合に必要 |

権限名の末尾による違い:

- **`.Shared`**: 自分のメールボックスに加え、自分に共有された (アクセス権を与えられた) 他人のメールボックスや共有メールボックスも対象になる。
- **`.All`**: アプリケーション版で、全メールボックスが対象であることを表す。

### 3.2 委任とアプリケーションでの提供状況と対象範囲

同じ権限名でも、委任とアプリケーションで対象範囲が異なる。また、**片方にしか提供されていない権限** がある。

| 権限 | 委任 | アプリケーション |
|---|---|---|
| `Mail.ReadBasic` | ○ 自分のメールボックス | ○ 全メールボックス |
| `Mail.ReadBasic.All` | **提供なし** | ○ 全メールボックス (アプリ版の `Mail.ReadBasic` と実質同じ。追加時期が違うだけとみられる) |
| `Mail.ReadBasic.Shared` | ○ 自分 + 共有されたメールボックス | **提供なし** |
| `Mail.Read` | ○ 自分のメールボックス | ○ 全メールボックス |
| `Mail.Read.Shared` | ○ 自分 + 共有されたメールボックス (職場・学校アカウントのみ) | **提供なし** |
| `Mail.ReadWrite` | ○ 自分のメールボックス | ○ 全メールボックス |
| `Mail.ReadWrite.Shared` | ○ 自分 + 共有されたメールボックス | **提供なし** |
| `Mail.Send` | ○ 自分として送信 | ○ **組織内の任意のユーザーとして送信** |
| `Mail.Send.Shared` | ○ 共有メールボックスとして (または代理で) 送信 | **提供なし** |
| `MailboxSettings.Read` / `.ReadWrite` | ○ 自分のメールボックス | ○ 全メールボックス |
| `MailboxFolder.*` / `MailboxItem.*` | ○ `MailboxFolder.Read` / `MailboxItem.Read` など (自分のメールボックス) | ○ `MailboxFolder.Read.All` / `MailboxItem.Read.All` など (全メールボックス) |
| `User.Read` | ○ 自分のプロフィール | **提供なし** (アプリでは `User.Read.All` などの別の権限を使う) |
| **同意** | ユーザー (テナントの設定により管理者) | **管理者の同意が必須** |

- **委任**: 実際に読めるのは「アプリに与えた権限」と「サインインしたユーザー自身のアクセス権」の重なる部分になる。
- **アプリケーション**: 既定でテナント内の **全メールボックス** が対象になる (誰のメールでも読める状態)。企業利用では、4 章の方法で対象を絞る前提で考える。
- **`.Shared` は委任専用**: アプリケーションには `.Shared` 版がない。アプリで共有メールボックスを読む場合は、通常の `Mail.Read` などを使う (全メールボックスに共有メールボックスも含まれる)。

### 3.3 読める単位

- `Mail.Read` などのメール権限で読める単位は **メールボックス単位** で、メールボックス内の全フォルダー (受信トレイ・送信済み・削除済み・下書きなど) が対象になる (1.5 を参照)。
- フォルダー単位の権限はない。特定のフォルダーだけを読ませたい場合は、4.4 の代替案を使う。

### 3.4 前提となるライセンス

- 取得対象のユーザーに **Exchange Online のメールボックス** が必要 (1.2 を参照)。メール取得には Exchange Online (Plan 1) で十分。
- 共有メールボックスはライセンス不要だが、テナントに Exchange Online のライセンスが 1 つ以上必要。
- Graph API の利用自体に追加料金はかからない。

### 3.5 権限の選び方 (読み取り用途)

必要最小限の権限を選ぶ。

| 必要なもの | 選ぶ権限 |
|---|---|
| 件名・差出人・日時など、メールの一覧情報だけ | `Mail.ReadBasic` (アプリは `Mail.ReadBasic` / `Mail.ReadBasic.All`) |
| 本文や添付ファイルも | `Mail.Read` |
| 既読化・移動・削除などの変更 | `Mail.ReadWrite` (必要な場合のみ) |
| 送信 | `Mail.Send` (必要な場合のみ。アプリ版は誰にでもなりすまして送信できるため、特に慎重に) |

本サンプルの一覧は `bodyPreview`、個別取得は `body` を取得している。
`Mail.ReadBasic` に切り替える場合は、`$select` からこれらを外すなどの見直しが必要になる。

---

## 4. テナント側でメールボックスを絞る設定

### 4.1 方式の比較

| 方式 | 概要 | 位置づけ |
|---|---|---|
| **Exchange Online RBAC for Applications** | Exchange 側でアプリにロールを割り当て、スコープで対象メールボックスを限定する | **現行の推奨方式** |
| Application Access Policy | `New-ApplicationAccessPolicy` で、アプリがアクセスできるメールボックスをグループで制限する | RBAC for Applications の前身 (旧方式) |

どちらも、絞り込みの単位はメールボックス (人・共有メールボックス) までになる。

### 4.2 RBAC for Applications の仕組み

```
[Entra ID で付与]  アプリ → Mail.Read (アプリケーションの許可)  → 全メールボックス
[RBAC で付与]      アプリ → Application Mail.Read (Exchange のロール) → スコープ内のメールボックスだけ
```

- 権限を Entra ID ではなく、Exchange Online 側で付与する。
- **Entra ID の許可と RBAC の割り当ては足し算 (OR) で評価される。** Entra ID 側に `Mail.Read` (アプリケーションの許可) が残っていると全メールボックスが読めるため、RBAC で絞る場合は **Entra ID 側の許可を外す**。
- アプリのコード変更は不要 (スコープは `.default` のまま)。
- スコープ外のメールボックスを読もうとすると、Graph API は `403 ErrorAccessDenied` を返す。
- 設定は Exchange Online PowerShell で行う (管理画面の GUI では設定できない)。
- 反映にはキャッシュの影響で 30 分〜2 時間程度かかることがある。
- 制御できるのは Exchange のデータ (メール・予定表・連絡先など) だけで、Teams や SharePoint などは対象外。
- `Mail.Read` 以外の権限にも、対応するロールがある。たとえば `Application Mail.ReadBasic`、`Application Mail.ReadWrite`、`Application Mail.Send`、`Application MailboxSettings.Read`、`Application EWS.AccessAsApp` など。
  - 一覧は `Get-ManagementRole | Where-Object { $_.Name -like "Application *" }` で確認できる。
  - 本文が不要なら、`Application Mail.ReadBasic` をスコープ付きで割り当てると、「対象メールボックス」と「読める項目」の両方を絞れる。

### 4.3 設定手順の概要

```powershell
Connect-ExchangeOnline

# 1. アプリのサービスプリンシパルを Exchange に登録
#    ObjectId は「エンタープライズ アプリケーション」側のオブジェクト ID (アプリ登録のオブジェクト ID ではない)
New-ServicePrincipal -AppId <クライアントID> -ObjectId <エンタープライズアプリのオブジェクトID> -DisplayName "<表示名>"

# 2. 対象メールボックスの条件 (スコープ) を作成 (例: カスタム属性で目印を付ける)
Set-Mailbox -Identity allowed@contoso.onmicrosoft.com -CustomAttribute1 "GraphMailAllowed"
New-ManagementScope -Name "GraphMailAllowed" -RecipientRestrictionFilter "CustomAttribute1 -eq 'GraphMailAllowed'"

# 3. スコープ付きでロールを割り当て
New-ManagementRoleAssignment -App <クライアントID> -Role "Application Mail.Read" -CustomResourceScope "GraphMailAllowed"

# 4. 確認 (InScope が True / False になる)
Test-ServicePrincipalAuthorization -Identity <クライアントID> -Resource allowed@contoso.onmicrosoft.com
```

スコープの決め方は 2 通りある。

| スコープ | 指定方法 | 備考 |
|---|---|---|
| 管理スコープ | `-CustomResourceScope` (メールボックスの属性で条件指定) | Exchange Online (Plan 1) で利用可。検証にはこちらが手軽 |
| 管理単位 (Administrative Unit) | `-RecipientAdministrativeUnitScope` | 部署単位の管理に向く。管理単位の機能に Entra ID P1 などが必要になる場合がある |

検証時は、拒否確認用にライセンス不要の共有メールボックスを使うと費用がかからない。

### 4.4 フォルダー単位で絞りたい場合の代替案

RBAC for Applications ではフォルダー単位に絞れないため、必要なら次の方法をとる。

| 方法 | 内容 | 注意点 |
|---|---|---|
| **専用メールボックスに集める (推奨)** | 読ませたいメールだけを、メールフロールール (トランスポートルール) などで専用の共有メールボックスにコピーし、RBAC でそのメールボックスだけに絞る | 権限の境界がメールボックス単位で明確になる |
| フォルダーの共有権限 + 委任アクセス | サービス用アカウントに対象フォルダーだけ閲覧権限を付け (`Add-MailboxFolderPermission`)、アプリはそのアカウントの委任アクセス (`Mail.Read.Shared`) で読む | サインインなしのクレデンシャルフローではなくなる。サービスアカウントのトークン管理が必要 |
| アプリ側で読むフォルダーを限定 | コード上で特定のフォルダーだけを読む | 権限上は全フォルダーが読めるため、セキュリティの境界にはならない |

### 4.5 中国版 (21Vianet) での利用可否

- 中国版で RBAC for Applications が使えるかを明記した公開情報は、確認できなかった。
- 中国版テナントで `New-ServicePrincipal` などのコマンドや `Application Mail.Read` ロールが存在するかを確認し、必要に応じて 21Vianet のサポートに問い合わせる。

---

## 5. 参考: メール取得 API の補足

| 用途 | API |
|---|---|
| フォルダー内の一覧 | `GET /users/{UPN}/mailFolders/{folder}/messages` (`folder` は `inbox`、`sentitems` などの名前、またはフォルダー ID) |
| メールボックス全体の一覧 | `GET /users/{UPN}/messages` (全フォルダーが対象) |
| 個別取得 | `GET /users/{UPN}/messages/{id}` |
| フォルダー一覧 | `GET /users/{UPN}/mailFolders` (本サンプルでは未実装) |

- **日時**: 受信・送信をまとめた専用の日時プロパティはない。
  - `receivedDateTime` には、受信メールでは受信日時、送信済みメールでは送信日時 (送信済みアイテムに入った日時) が入るため、共通の「メールの日時」として使える。
- **`$filter` と `$orderby` の併用**: `$orderby` のプロパティが `$filter` にも含まれていないと、`400 InefficientFilter` になる。
- **受信トレイと送信済みアイテムをまとめて取得**: 1 回の呼び出しで取得できる。

  ```
  GET /users/{UPN}/messages
    ?$filter=receivedDateTime ge 2026-09-01T00:00:00Z
             and (parentFolderId eq '{受信トレイのID}' or parentFolderId eq '{送信済みアイテムのID}')
    &$orderby=receivedDateTime desc
  ```

  ただし、ユーザーが自分で作ったフォルダーや、受信トレイのサブフォルダーに移したメールは含まれない。
- **削除済みアイテム・下書きを除いた全体の取得**: `isDraft eq false and parentFolderId ne '{削除済みアイテムのID}'` を付ける。

---

## 付録: Graph 以外のメール取得方法 (IMAP / POP / SMTP / EWS)

本資料の趣旨 (Graph API でのメール取得) からは外れるため、概要のみ記載する。

Graph API ではなく、従来のメールプロトコルで Exchange Online に接続するための権限もある。
これらは Microsoft Graph ではなく、アプリ登録の「所属する組織で使用している API」→ **Office 365 Exchange Online** で追加する。

| プロトコル | 委任 | アプリケーション (クライアント資格情報) | 備考 |
|---|---|---|---|
| IMAP | `IMAP.AccessAsUser.All` | `IMAP.AccessAsApp` | アプリ版は、管理者の同意に加えて `New-ServicePrincipal` での登録と、**メールボックスごとの権限付与** (`Add-MailboxPermission -AccessRights FullAccess`) が必要。そのため、最初からメールボックス単位で限定される |
| POP | `POP.AccessAsUser.All` | `POP.AccessAsApp` | 同上 |
| SMTP (送信) | `SMTP.Send` | `SMTP.SendAsApp` | 同上 |
| EWS | `EWS.AccessAsUser.All` | `full_access_as_app` | **廃止予定**。Exchange Online では 2026 年 10 月 1 日から段階的な無効化が始まっており、2027 年 4 月 1 日に完全停止。新規開発では使わず、Graph を使う |

---

## 参考資料

- [Microsoft Graph permissions reference](https://learn.microsoft.com/en-us/graph/permissions-reference)
- [New basic read access to a users mailbox (Microsoft 365 Developer Blog)](https://devblogs.microsoft.com/microsoft365dev/new-basic-read-access-to-a-users-mailbox/)
- [Role Based Access Control for Applications in Exchange Online](https://learn.microsoft.com/en-us/exchange/permissions-exo/application-rbac)
- [Limiting application permissions to specific Exchange Online mailboxes](https://github.com/microsoft/microsoft-graph-docs-1/blob/main/concepts/auth-limit-mailbox-access.md)
- [Deprecation of Exchange Web Services in Exchange Online](https://learn.microsoft.com/en-us/exchange/clients-and-mobile-in-exchange-online/deprecation-of-ews-exchange-online)
- [MC1227454 - Exchange Web Services (EWS) retirement update](https://mc.merill.net/message/MC1227454)
- [Exchange Online POP and IMAP OAuth 2.0 Client Credentials Flow (Icewolf Blog)](https://blog.icewolf.ch/archive/2022/12/17/exchange-online-pop-and-imap-oauth-2-0-client-credentials/)
- [Graph Permissions Explorer (Merill)](https://graphpermissions.merill.net/permission/Mail.ReadBasic)
