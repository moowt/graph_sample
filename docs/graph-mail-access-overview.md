# Microsoft Graph によるメール取得: 認証方式・権限・メールボックス制限の概要

Graph API でメールを取得する際の、認証方式、`Mail.Read` 権限、テナント側でアクセス対象のメールボックスを絞る方法を整理する。

**表記について**: 本サンプルの検証で実際に確認した内容には **【検証済み】** を付けた。印のないものは Microsoft の公開情報に基づく整理で、導入前に公式ドキュメントで最新の仕様を確認すること。

---

## 1. 選択可能な認証方式

Graph API の呼び出しは、どの方式でも次の 2 段階で行う。

1. Entra ID のトークンエンドポイントからアクセストークン (JWT、有効期限は通常約 1 時間) を取得する
2. トークンを `Authorization: Bearer <トークン>` ヘッダーに付けて Graph API を呼ぶ

方式の違いは、1 の「誰の権限でトークンをもらうか」にある。

### 1.1 方式の比較

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

### 1.2 委任アクセスのフロー

| フロー | 概要 | 本サンプル |
|---|---|---|
| デバイスコードフロー | 表示された URL とコードをブラウザで入力してサインインする。画面のない CLI 向け | 実装済み (`auth.mode=device-code`) 【検証済み】 |
| 認可コードフロー (ブラウザでのリダイレクト) | Web アプリ・デスクトップアプリの標準的な方式 | 未実装 |

デバイスコードフローの注意点 【検証済み】:

- アプリ登録で「パブリック クライアント フローを許可する」を有効にする必要がある (無効だと `AADSTS7000218`)。
- 個人アカウントで使うには、アプリ登録の「サポートされているアカウントの種類」に個人用 Microsoft アカウントを含め、`tenant.id=consumers` を指定する。
  - 既存のアプリをこの種類に変更する場合、先にマニフェストの `requestedAccessTokenVersion` を `2` にしないと保存できない。
  - 組織専用のアプリ登録に個人アカウントでサインインすると、アクセス権がないエラーになる。
- 本サンプルはトークンをメモリ上だけで保持するため、実行のたびにサインインが必要。
  - リフレッシュトークンを永続化すれば、2 回目以降のサインインを省略できる (未実装)。

### 1.3 アプリケーションアクセス (クライアント資格情報フロー) の資格情報

| 資格情報 | 概要 | 本サンプル |
|---|---|---|
| クライアントシークレット | アプリ登録で発行する文字列。有効期限あり | 実装済み (`auth.mode=client-secret`) 【検証済み】 |
| 証明書 | 秘密鍵で署名して認証する。シークレットより安全で、本番向け | 未実装 |

クライアントシークレットの注意点:

- 発行直後の画面でしか値を確認できない。「シークレット ID」ではなく「値」を使う。
- 誤った値では `AADSTS7000215` になる 【検証済み】。期限切れでは `AADSTS7000222` になる。
- アプリ登録はメールボックスがあるテナントで行い、シングルテナント (この組織ディレクトリのみ) でよい。
- スコープには `https://graph.microsoft.com/.default` を指定する。これで、付与済みのアプリケーションの許可がすべて使われる。

### 1.4 接続先クラウド

中国版 (21Vianet) はグローバル版と別のクラウドで、テナント・アカウント・アプリ登録も別になる。

| | グローバル版 | 中国版 (21Vianet) |
|---|---|---|
| 認証 | `https://login.microsoftonline.com` | `https://login.chinacloudapi.cn` |
| Graph API | `https://graph.microsoft.com` | `https://microsoftgraph.chinacloudapi.cn` |
| アプリ用スコープ | `https://graph.microsoft.com/.default` | `https://microsoftgraph.chinacloudapi.cn/.default` |
| 個人アカウント | あり | なし |

- 本サンプルは `cloud=global|china` で切り替える。
- 中国版の実接続は未検証で、確認できたのはエンドポイントの切り替えまで。

---

## 2. メール取得周辺の権限 (`Mail.Read`)

### 2.1 `Mail.Read` の種類と読める範囲

| 権限 | 種類 | 読める範囲 | 同意 |
|---|---|---|---|
| `Mail.Read` | 委任 | サインインしたユーザー自身のメールボックス | ユーザー (テナント設定により管理者) |
| `Mail.Read.Shared` | 委任 | 上記に加え、そのユーザーに共有されたメールボックス・フォルダー | 同上 |
| `Mail.Read` | アプリケーション | **テナント内の全メールボックス** | 管理者の同意が必須 |
| `User.Read` | 委任 | サインインしたユーザーのプロフィール (`/me` の取得に使用) | ユーザー |

- 委任の場合、実際に読めるのは「アプリに与えた権限」と「ユーザー自身の権限」の重なる部分になる。
- アプリケーションの `Mail.Read` を Entra ID で付与すると、**社内の誰のメールでも読める状態** になる (検証で取得したのは 1 メールボックスのみ)。企業利用では、3 章の方法で対象を絞る前提で考える。

### 2.2 読める単位

- `Mail.Read` で読める単位は **メールボックス単位** で、メールボックス内の全フォルダー (受信トレイ・送信済み・削除済み・下書きなど) が対象になる。
- フォルダー単位の権限はない。特定のフォルダーだけを読ませたい場合は、3.4 の代替案を使う。

### 2.3 前提となるライセンス

- 取得対象のユーザーに **Exchange Online のメールボックス** が必要。Exchange Online (Plan 1) で十分 【検証済み】。
- Entra ID にユーザーを作っただけではメールボックスがなく、Graph でメールを読めない。
- 共有メールボックスはライセンス不要で作成でき、`/users/{共有メールボックスのアドレス}/...` で同様に読める (テナントに Exchange Online のライセンスが 1 つ以上必要)。
- Graph API の利用自体に追加料金はかからない。

---

## 3. テナント側でメールボックスを絞る設定

### 3.1 方式の比較

| 方式 | 概要 | 位置づけ |
|---|---|---|
| **Exchange Online RBAC for Applications** | Exchange 側でアプリにロールを割り当て、スコープで対象メールボックスを限定する | **現行の推奨方式** |
| Application Access Policy | `New-ApplicationAccessPolicy` で、アプリがアクセスできるメールボックスをグループで制限する | RBAC for Applications の前身 (旧方式) |

どちらも、絞り込みの単位はメールボックス (人・共有メールボックス) までになる。

### 3.2 RBAC for Applications の仕組み

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

### 3.3 設定手順の概要

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

### 3.4 フォルダー単位で絞りたい場合の代替案

RBAC for Applications ではフォルダー単位に絞れないため、必要なら次の方法をとる。

| 方法 | 内容 | 注意点 |
|---|---|---|
| **専用メールボックスに集める (推奨)** | 読ませたいメールだけを、メールフロールール (トランスポートルール) などで専用の共有メールボックスにコピーし、RBAC でそのメールボックスだけに絞る | 権限の境界がメールボックス単位で明確になる |
| フォルダーの共有権限 + 委任アクセス | サービス用アカウントに対象フォルダーだけ閲覧権限を付け (`Add-MailboxFolderPermission`)、アプリはそのアカウントの委任アクセス (`Mail.Read.Shared`) で読む | サインインなしのクレデンシャルフローではなくなる。サービスアカウントのトークン管理が必要 |
| アプリ側で読むフォルダーを限定 | コード上で特定のフォルダーだけを読む | 権限上は全フォルダーが読めるため、セキュリティの境界にはならない |

### 3.5 中国版 (21Vianet) での利用可否

- 中国版で RBAC for Applications が使えるかを明記した公開情報は、確認できなかった。
- 中国版テナントで `New-ServicePrincipal` などのコマンドや `Application Mail.Read` ロールが存在するかを確認し、必要に応じて 21Vianet のサポートに問い合わせる。

---

## 4. 参考: メール取得 API の補足 【検証済み】

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
  - クエリが通ることは確認したが、除外が効くこと自体は該当データがなく未確認。

---

## 5. 検証状況のまとめ

| 項目 | 状況 |
|---|---|
| デバイスコードフロー (個人アカウント、`/me`) でのメール取得 | 検証済み |
| クライアントシークレット (Exchange Online Plan 1 テナント、`/users/{UPN}`) でのメール取得 | 検証済み |
| Entra ID のアプリケーションの許可 `Mail.Read` + 管理者の同意での取得 | 検証済み |
| RBAC for Applications による許可・拒否 | **未検証** (手順のみ整理) |
| 証明書による認証 | 未実装・未検証 |
| 中国版 (21Vianet) での実接続・RBAC for Applications | 未検証 |
