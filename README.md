# GitAppInstaller

GitHub リポジトリの URL を入力すると、そのリポジトリの Releases を確認し、最新版の APK が公開されていればダウンロード〜インストールまでを案内する Android アプリです。

将来的には GitLab やプライベートリポジトリにも対応する想定ですが、本ドキュメントでは **第一段階として「パブリックな GitHub リポジトリ」のみを対象** とした実装の流れを整理します。

---

## 1. 実現したいユーザー体験

1. ユーザーがアプリにリポジトリ URL（例: `https://github.com/owner/repo`）を登録する。
2. アプリが Releases を取得し、登録済みアプリの **インストール済みバージョン** と **最新リリースバージョン** を比較する。
3. 更新があればカードに「アップデートあり」と表示し、**インストールボタン**を出す。
4. ボタンを押すと、Release の Asset（`.apk`）を自動でダウンロードし、Android のパッケージインストーラを起動する。
5. ユーザーが OS のダイアログで承認するとインストールが完了する。

---

## 2. 技術スタック

現状の `app/build.gradle.kts` 構成を踏まえ、以下を採用します。

| 領域 | 採用予定 | 補足 |
|---|---|---|
| 言語 / UI | Kotlin + Jetpack Compose（Material3） | 既存構成を踏襲 |
| 非同期 | Kotlin Coroutines + Flow | ViewModel と組み合わせて状態管理 |
| 通信 | Retrofit + OkHttp + kotlinx.serialization（または Moshi） | GitHub REST API v3 を叩く |
| ダウンロード | `DownloadManager`（標準）または OkHttp | Asset の URL リダイレクト追従に注意 |
| インストール | `PackageInstaller` API（推奨） / 互換用に `ACTION_VIEW` + `application/vnd.android.package-archive` | minSdk 24 のため両対応を検討 |
| 永続化 | Room もしくは DataStore | 登録済みリポジトリ一覧を保存 |
| アーキテクチャ | UI（Compose） / ViewModel / Repository / DataSource の4層 | テスト容易性のため |

> minSdk = 24, targetSdk = 36 を維持します。

---

## 3. 全体フロー

```
[URL 入力]
     │
     ▼
[URL パース] ── owner / repo を抽出
     │
     ▼
[GitHub API: GET /repos/{owner}/{repo}/releases/latest]
     │
     ▼
[最新タグ + Asset(.apk) URL を取得]
     │
     ▼
[端末にインストール済みの versionName と比較]
     │
     ├─ 同一 → "最新です" を表示
     └─ 新しい → "アップデートあり" + [インストール]ボタン
                      │
                      ▼
              [APK をキャッシュにダウンロード]
                      │
                      ▼
              [PackageInstaller でインストールセッション開始]
                      │
                      ▼
              [OS のインストール確認ダイアログ]
                      │
                      ▼
              [完了 → バージョン情報を更新]
```

---

## 4. 画面構成

最低限、以下の3画面（または相当の Composable）を用意します。

1. **リポジトリ一覧画面**
   - 登録したリポジトリのカード一覧
   - 各カードに「現在のバージョン / 最新バージョン / 状態（最新・更新あり・未取得）」を表示
   - FAB から追加画面へ遷移
   - Pull to Refresh で全件再チェック
2. **リポジトリ追加画面**
   - URL 入力欄（GitHub URL のバリデーションを実装）
   - 紐づける **applicationId**（任意）の入力欄。指定されればインストール済みバージョン取得に使う
   - 保存ボタン
3. **詳細画面（任意）**
   - リリースノート（`body`）の Markdown 表示
   - APK サイズ・公開日
   - インストールボタン

---

## 5. ドメインモデル（暫定）

```kotlin
data class TrackedRepo(
    val id: Long,
    val host: GitHost,            // GITHUB（将来 GITLAB を追加）
    val owner: String,
    val repo: String,
    val applicationId: String?,   // 端末に入っている対象アプリの packageName
    val displayName: String
)

data class ReleaseInfo(
    val tagName: String,          // 例: "v1.2.3"
    val versionName: String,      // 例: "1.2.3"（tagName から正規化）
    val publishedAt: Instant,
    val notes: String?,
    val apkAsset: ApkAsset?
)

data class ApkAsset(
    val name: String,             // 例: "app-release.apk"
    val downloadUrl: String,      // browser_download_url
    val sizeBytes: Long
)

enum class UpdateState { UNKNOWN, UP_TO_DATE, UPDATE_AVAILABLE, NOT_INSTALLED }
```

---

## 6. 主要コンポーネントと責務

```
ui/
  RepoListScreen.kt          # 一覧 Composable
  RepoAddScreen.kt           # URL 追加 Composable
  RepoDetailScreen.kt        # 詳細 Composable
  RepoListViewModel.kt
  RepoAddViewModel.kt

domain/
  TrackedRepo.kt
  ReleaseInfo.kt
  CheckUpdateUseCase.kt      # API 呼び出し→比較→UpdateState 算出
  InstallApkUseCase.kt       # ダウンロード→インストール起動

data/
  remote/
    GitHubApi.kt             # Retrofit interface
    GitHubReleaseDto.kt
    ReleaseRemoteDataSource.kt
  local/
    AppDatabase.kt           # Room
    TrackedRepoDao.kt
  repository/
    RepoRepository.kt        # 一覧 CRUD
    ReleaseRepository.kt     # API → ドメインへ変換

installer/
  ApkDownloader.kt           # DownloadManager ラッパー
  ApkInstaller.kt            # PackageInstaller ラッパー
  InstallSessionReceiver.kt  # PendingIntent コールバック
```

---

## 7. GitHub API 連携

### エンドポイント
- 最新版: `GET https://api.github.com/repos/{owner}/{repo}/releases/latest`
- 一覧（プレリリース含めるなら）: `GET /repos/{owner}/{repo}/releases`

### ヘッダ
```
Accept: application/vnd.github+json
X-GitHub-Api-Version: 2022-11-28
User-Agent: GitAppInstaller/<versionName>
```

### URL パース
受け付けたい形式の例:
- `https://github.com/owner/repo`
- `https://github.com/owner/repo/`
- `https://github.com/owner/repo/releases`
- `git@github.com:owner/repo.git`（任意）

→ 共通正規表現で `owner` と `repo`（末尾 `.git` は除去）を抜き出す。

### Asset の選定
`assets[]` から `name` が `.apk` で終わるものを採用。複数ある場合は端末の ABI に合致するものを優先。なければ `universal` を含むもの → 先頭のもの、の順でフォールバック。

### レート制限
未認証だと 60 req/h/IP。一覧画面で多数同時更新するとすぐ枯渇するため、
- ETag をキャッシュして `If-None-Match` で再問い合わせ
- 連続更新時は最低 X 秒のクールダウン
を入れます。

---

## 8. バージョン比較

Release 側のタグは `v1.2.3`, `1.2.3`, `1.2.3-rc1` などゆらぎが大きいので、
1. 先頭の `v` を除去
2. `-` 以降（プレリリース識別子）を分離
3. `.` で split し整数として比較
4. 同値ならプレリリース識別子を文字列比較（無印 > rc > beta > alpha）

の順でセマンティックに比較する `VersionComparator` を用意します。

端末側のバージョン取得:
```kotlin
val pkgInfo = packageManager.getPackageInfo(applicationId, 0)
val installedVersion = pkgInfo.versionName
```
未インストールなら `NameNotFoundException` → `UpdateState.NOT_INSTALLED` として扱う。

---

## 9. APK ダウンロード

### 採用案: `DownloadManager`
- 進捗・通知が標準で出る
- Wi-Fi 限定オプションが簡単
- 保存先はアプリ専用の外部キャッシュ（`getExternalFilesDir(null)` / `Environment.DIRECTORY_DOWNLOADS`）

注意点:
- GitHub の `browser_download_url` は 302 で S3 にリダイレクトする。`DownloadManager` はリダイレクトを追従するので問題なし。
- 完了は `ACTION_DOWNLOAD_COMPLETE` ブロードキャストを `BroadcastReceiver` で受ける（Android 14+ では `RECEIVER_NOT_EXPORTED` を指定）。

---

## 10. APK インストール

### `PackageInstaller` を使った推奨フロー
1. `packageManager.packageInstaller.createSession(SessionParams(MODE_FULL_INSTALL))`
2. `session.openWrite()` に APK ストリームを書き込む
3. `PendingIntent` を渡して `session.commit()`
4. OS の確認ダイアログがユーザーに表示される
5. コールバック（`InstallSessionReceiver`）で成功 / 失敗を受け取る

### 必要な権限・宣言（AndroidManifest.xml）
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
<!-- DownloadManager 通知用（任意）-->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<!-- API 32 以下で外部公開された APK を共有する場合 FileProvider を使うため、外部ストレージ権限は不要 -->
```

### 「不明なアプリのインストール」許可
Android 8.0 (API 26) 以降、ユーザーごとに「このアプリからのインストール」を ON にしてもらう必要があります。
```kotlin
if (!packageManager.canRequestPackageInstalls()) {
    startActivity(
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
               Uri.parse("package:$packageName"))
    )
}
```
初回インストール時にこの導線を案内します。

### FileProvider（互換 ACTION_VIEW を使う場合）
`AndroidManifest.xml`:
```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_provider_paths" />
</provider>
```
`res/xml/file_provider_paths.xml` でキャッシュ配下を公開し、`Intent.FLAG_GRANT_READ_URI_PERMISSION` を付与して APK の Uri を渡す方式。

---

## 11. エラーハンドリングと UX

| 状況 | 表示 / 挙動 |
|---|---|
| URL が GitHub 形式でない | 入力欄にインラインエラー |
| リポジトリが存在しない / Private（404） | 「リポジトリが見つかりません。Public のみ対応しています」 |
| Release が無い | 「リリースがまだ公開されていません」 |
| `.apk` Asset が無い | 「APK が添付されていません」 |
| レート制限（403） | 「GitHub のレート制限に達しました。しばらくしてから再試行してください」 |
| ネットワークエラー | スナックバー + リトライボタン |
| ダウンロード失敗 | キャッシュを掃除して再試行 |
| インストール拒否 | エラーコードを表示し、設定への導線を出す |

---

## 12. 拡張ポイント（将来対応）

- **GitLab 対応**: `GitHost` を抽象化し、`ReleaseRemoteDataSource` のインタフェースを共通化。GitLab では `GET /api/v4/projects/:id/releases` を使用。
- **プライベートリポジトリ対応**: GitHub は OAuth Device Flow か PAT、GitLab は PAT を `EncryptedSharedPreferences` に保存し `Authorization` ヘッダに付与。
- **バックグラウンド定期チェック**: `WorkManager` で日次更新チェック → 通知を出す。
- **署名検証**: 既知のアプリの署名証明書ハッシュを保存しておき、APK の署名と一致しないインストールを拒否（なりすまし防止）。
- **複数 ABI 対応**: 端末の `Build.SUPPORTED_ABIS` に合わせて Asset を選択。
- **プレリリースのオプトイン**: 設定でプレリリースも対象にできるトグル。

---

## 13. 実装ステップ（マイルストーン）

進めやすい順に分割した提案です。

### M1: 単発の更新確認 PoC
- `https://github.com/owner/repo` を 1 件だけハードコードで持つ
- `releases/latest` を Retrofit で叩いて Compose 画面に最新タグを表示する
- まずは API 通信〜JSON パースを成立させる

### M2: バージョン比較
- 端末にインストールされたパッケージのバージョン取得
- `VersionComparator` を実装し、`UpdateState` を画面に表示
- ユニットテストでバージョン比較の網羅ケースを書く

### M3: ダウンロード
- `DownloadManager` で Asset を取得し、完了通知を受け取る
- ダウンロードキャンセル / リトライ UI

### M4: インストール
- `REQUEST_INSTALL_PACKAGES` の許可導線
- `PackageInstaller` でインストールセッションを実装
- インストール結果を画面に反映

### M5: 永続化と一覧
- Room で `TrackedRepo` を CRUD
- 一覧画面と追加画面を実装
- Pull to Refresh で一括チェック

### M6: 仕上げ
- エラーハンドリングの網羅
- 通知 / 進捗バー
- README とスクリーンショットの整備

### M7 以降（将来）
- GitLab 抽象化
- プライベートリポジトリ対応
- WorkManager による自動チェック

---

## 14. 確認事項 / 設計の論点

実装前に決めておきたいポイント:

1. **対象 APK の特定方法**
   どのリポジトリのどのアプリかを特定するために `applicationId` をユーザーに入力させるか、自動推定するか（リリース名 / asset 名から推定する案もありますが精度に難あり）。
2. **複数 APK Asset がある場合の選択**
   ABI 別にビルドされた APK が並ぶケースで、自動選択 or ユーザー選択にするか。
3. **AAB 配布のリポジトリは対象外**
   サイドロードでは AAB を扱えないため、APK アセットがないリポジトリはそもそも非対応とする旨を README に明記。
4. **署名整合性チェックを M1〜M6 に入れるか**
   セキュリティ上は早めに入れたいが、UX への影響と実装コストとのバランスを取る。
5. **自身のアップデート**
   GitAppInstaller 自体を GitAppInstaller でアップデートする場合、自プロセスを終了させる必要があるため検討要。

---

## 15. 参考リンク

- GitHub REST API: <https://docs.github.com/ja/rest/releases/releases>
- PackageInstaller: <https://developer.android.com/reference/android/content/pm/PackageInstaller>
- DownloadManager: <https://developer.android.com/reference/android/app/DownloadManager>
- 不明なアプリのインストール: <https://developer.android.com/reference/android/Manifest.permission#REQUEST_INSTALL_PACKAGES>
