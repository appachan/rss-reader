# 共通土台設計メモ — ドメイン / データ層 / DI / ナビゲーション骨格

Date: 2026-05-24
Status: 採用（Issue #17）

## 背景・目的

本メモは TASK-005〜014（#7〜#16）の前提となる共通土台を先に決め、後続タスクが一貫した
設計の上で実装できるようにするための設計メモである。Claude Code web のセッションは
セッション間で記憶を共有しないため、土台の決定を repo に残し、各タスクのセッションが
これを読んで整合性を保てるようにする。

- 本メモは**設計の記述のみ**を行い、機能コードは実装しない（実装は各 TASK）。
- UI 状態管理パターン（パターン①〜④）は `docs/plans/ui-state-management-patterns.md` の別軸であり、
  本メモはそこには踏み込まない。本メモはその下のドメイン／データ／配線を対象とする。
- 関連 ADR: 0002 / 0004 / 0005 / 0006 / 0007 / 0008 / 0009 / 0010 / 0011

現状、6 モジュール（`:app` / `:feature:feed` / `:shared:domain` / `:shared:infrastructure` /
`:shared:ui` / `:shared:testing`）は build ファイルとプレースホルダのみで、ドメインモデル・
Repository・SQLDelight スキーマ・DI・ナビゲーションはいずれも未実装（greenfield）である。

## 0. 確定事項サマリ

| 観点 | 決定 |
|---|---|
| タイムスタンプ | `kotlinx-datetime` の `Instant`（catalog に追加）。DB は epoch-millis `INTEGER`、境界で変換 |
| 既読の保存 | `ArticleReadState` 別テーブル（`article_id` キー）。refresh が既読を壊さない構造的保証 |
| Repository | offline-first / single source of truth。観測 = `Flow`、コマンド = `suspend … : Result` |
| RSS/Atom パーサ | KMP 対応ライブラリ採用方針。第一候補 `prof18/RSS-Parser`。**#13 で ADR 化** |
| Nav3 2 ペイン | 記事一覧 \| 記事詳細（ArticleList \| ArticleDetail） |
| DI | `infrastructureModule`（実装/外部接続）＋ `feedModule`（ViewModel）を `:app` で集約 |

## 1. ドメインモデル（`:shared:domain`）

ADR-0005（簡易クリーンアーキテクチャ）・ADR-0002（KMP 前提）に従い、`:shared:domain` は
**純 Kotlin / Android 非依存**（Parcelable・Context 等を持ち込まない）。package は
`com.github.appachan.rssreader.shared.domain.model`。

設計方針:

- **ID は `@JvmInline value class`** でラップする。実行時オーバーヘッドが無く Android 非依存で、
  `FeedId` と `ArticleId` の取り違えをコンパイル時に防げる。
- **日時は `kotlinx-datetime` の `Instant`**。RSS `pubDate` / Atom `published`/`updated` は
  タイムゾーンを持つ実時刻であり、生の epoch `Long` をドメインに置くとストレージの都合が
  漏れ、比較・整形を誤りやすい。`kotlinx-datetime` は KMP 的にも正しい選択（ADR-0002）。
  DB 保存は epoch-millis `INTEGER` のままとし、`:shared:infrastructure` の mapper で変換する。
- **RSS/Atom が省略しうるフィールドは nullable**。`id` / `feedId` / `url` / `guid` / `title` のみ
  非 null（`title` はパース時にフォールバックを合成し、null を UI に流さない）。

```kotlin
@JvmInline value class FeedId(val value: Long)
@JvmInline value class ArticleId(val value: Long)

data class Feed(
    val id: FeedId,
    val url: String,             // 購読しているフィード URL（unique）
    val title: String,           // <title>。無ければ url で代替（非 null）
    val siteUrl: String?,        // サイトへの <link>（Atom は省略しうる）
    val description: String?,    // <description> / <subtitle>
    val lastFetchedAt: Instant?, // 初回取得成功まで null
)

data class Article(
    val id: ArticleId,
    val feedId: FeedId,          // FK -> Feed
    val guid: String,            // dedup キー：<guid> / <id>、無ければ link 等で代替
    val title: String,
    val url: String,             // 記事の web URL（<link>）
    val author: String?,
    val summary: String?,        // <description> / <summary>
    val content: String?,        // <content:encoded> / <content>（全文 HTML、無いことも多い）
    val publishedAt: Instant?,   // <pubDate> / <published> / <updated>
    val isRead: Boolean,         // 既読状態（読み取り側プロジェクションで付与。保存は別テーブル）
)
```

**既読状態の表現**: ドメインでは独立した `ReadStatus` エンティティを持たず、`Article` の
`isRead: Boolean` 1 つで表す。既読化のトリガ（開いたら／一定時間表示／手動、#14）は
*トリガ*の問題でありモデリングの問題ではないため、boolean 1 つで足りる。将来 #14 が
「N 日後に未読へ戻す」等で `readAt: Instant?` を要するなら追加フィールドで対応できる
（唯一想定される拡張）。なお保存方法は §3 の通り別テーブルとする。

## 2. Repository インタフェース（`:shared:domain`）

**方針: offline-first / single source of truth（SSOT）**。UI は DB の `Flow` を購読し、
ネットワーク取得は DB を書く別コマンドとして実行する（書込により `Flow` が再 emit される）。
これにより pull-to-refresh（既存データを表示しつつ更新）が自然に表現でき、SQLDelight の
coroutines-extensions（`asFlow()`）と素直に噛み合う。

- 観測系は `Flow<T>`（`suspend` も `Result` も付けない。DB ストリームは「失敗」せず再 emit する）。
- コマンド系は `suspend … : Result<Unit>`（あるいは `Result<FeedId>`）。
- エラー型は既存の `ui-state-management-patterns.md` に合わせ **`kotlin.Result`  （`onSuccess` / `onFailure`）を用いる。独自 sealed エラー階層は greenfield では過剰。

```kotlin
package com.github.appachan.rssreader.shared.domain.repository

interface FeedRepository {
    // 観測（SSOT）
    fun observeFeeds(): Flow<List<Feed>>
    fun observeFeed(id: FeedId): Flow<Feed?>

    // コマンド
    suspend fun addFeed(url: String): Result<FeedId>     // 検証＋メタ取得＋insert
    suspend fun removeFeed(id: FeedId): Result<Unit>
    suspend fun refreshFeed(id: FeedId): Result<Unit>    // network → 記事を DB に upsert
    suspend fun refreshAllFeeds(): Result<Unit>          // WorkManager(#16) から呼ぶ
}

interface ArticleRepository {
    // 観測。一覧の単位は #7/#13 で確定するため両方用意する
    fun observeArticles(feedId: FeedId): Flow<List<Article>>  // フィード単位
    fun observeAllArticles(): Flow<List<Article>>             // 全フィード横断
    fun observeArticle(id: ArticleId): Flow<Article?>

    // 既読（#14）
    suspend fun markRead(id: ArticleId): Result<Unit>
    suspend fun markUnread(id: ArticleId): Result<Unit>
}
```

- 既読操作は記事スコープであり、記事を読む画面から同じく操作するため、別 Repository に
  切り出さず `ArticleRepository` に同居させる。
- 一覧の単位（全フィード横断／フィード単位、#7・#13 の未確定事項）は、両シグネチャを
  用意して土台として残し、最終的な一覧仕様は各 TASK で確定する。
- SQLDelight 行 → ドメインの mapper（`Instant` 変換含む）は `:shared:infrastructure` 側に置く。
  ドメインの IF には Android 型も SQLDelight 型も現れない。

## 3. データ層 / SQLDelight スキーマ方針（`:shared:infrastructure`）

ADR-0009。`.sq` ファイルは
`shared/infrastructure/src/main/sqldelight/com/github/appachan/rssreader/shared/infrastructure/db/`
配下に置く（build 設定の `packageName` と一致。当ディレクトリは実装時に新規作成）。

| Table | 主なカラム | 備考 |
|---|---|---|
| `Feed` | `id INTEGER PRIMARY KEY AUTOINCREMENT`<br>`url TEXT NOT NULL UNIQUE`<br>`title TEXT NOT NULL`<br>`site_url TEXT`<br>`description TEXT`<br>`last_fetched_at INTEGER` | `url` UNIQUE = 購読の重複防止。日時は epoch-millis |
| `Article` | `id INTEGER PRIMARY KEY AUTOINCREMENT`<br>`feed_id INTEGER NOT NULL REFERENCES Feed(id) ON DELETE CASCADE`<br>`guid TEXT NOT NULL`<br>`title TEXT NOT NULL`<br>`url TEXT NOT NULL`<br>`author TEXT`<br>`summary TEXT`<br>`content TEXT`<br>`published_at INTEGER` | `UNIQUE(feed_id, guid)`。<br>index `(feed_id, published_at DESC)` で一覧整列 |
| `ArticleReadState` | `article_id INTEGER PRIMARY KEY REFERENCES Article(id) ON DELETE CASCADE`<br>`is_read INTEGER NOT NULL DEFAULT 0`<br>`read_at INTEGER` | refresh では一切書かない。読込は `LEFT JOIN … COALESCE(is_read, 0)` |

関連: `Feed` 1 — * `Article`（`ON DELETE CASCADE`）。`Article` 1 — 0..1 `ArticleReadState`。

**記事の dedup キー**: `guid`（RSS `<guid>` / Atom `<id>` が仕様上の安定 ID）。実フィードは
guid を省略したり重複させたりするため、パーサ側で `guid = rawGuid ?: link ?: hash(title+pubDate)`
を合成する。`UNIQUE(feed_id, guid)` でフィード単位の一意性とする（同一記事が複数フィードに
配信されるのは別行として正当）。upsert は
`INSERT … ON CONFLICT(feed_id, guid) DO UPDATE SET title=…, content=…`（内容は更新し、
identity は不変）。

**既読の保存（重要な edge case）**: `ArticleReadState` 別テーブルを採用する。

- 既読状態を揮発しやすい記事ペイロードから物理的に分離するため、refresh の upsert が
  `Article` 行を上書きしても既読状態は影響を受けない（`article_id` キーで refresh は書かない）。
  「refresh は既読を壊せない」という性質が**規律ではなく構造で保証**される。
- 代替案（`Article` に `is_read` カラム）は JOIN が単純な反面、毎回 `ON CONFLICT DO UPDATE` の
  SET 句から `is_read` を厳密に除外する必要があり、見落とすと無言で既読が 0 に戻る。これを避ける。
- **不変条件**: refresh は delete+reinsert せず必ず upsert する（`article_id` を安定させ、
  `ArticleReadState` を孤立させないため）。本メモではこれをデータ層の hard invariant とする。

`ON DELETE CASCADE` を効かせるため、driver で `PRAGMA foreign_keys = ON` を有効化する
（`AndroidSqliteDriver` は既定で FK 強制を行わない）。

## 4. DI / Koin モジュール構成（`:app` で集約、ADR-0007）

| `module()` | 定義モジュール | 提供物 |
|---|---|---|
| `infrastructureModule` | `:shared:infrastructure` | `HttpClient`(Ktor)、`SqlDriver`(Android)、`FeedDatabase`、RSS パーサ、`FeedRepository`/`ArticleRepository` 実装（ドメイン IF に bind）、refresh worker |
| `feedModule` | `:feature:feed` | ViewModel（`viewModelOf`）。Koin が注入するドメイン IF のみに依存し、build ファイルは `:shared:infrastructure` に依存しない（ADR-0006 順守） |
| domain module | — | ドメインは生成すべきインスタンスが無い（純 IF / POKO）ため置かない |

`:app` で集約（現状 `RssReaderApplication` の modules は空）:

```kotlin
startKoin {
    androidContext(this@RssReaderApplication)
    workManagerFactory()                 // koin-androidx-workmanager
    modules(infrastructureModule, feedModule)
}
```

- `infrastructureModule` だけが実装名を知る唯一の場所であり、`:feature:feed` は実装と
  コンパイル時に切り離されたまま実行時に解決される（Koin の標準形。`:app` が両モジュールに
  依存するため成立）。
- Android 固有の `AndroidSqliteDriver`（`Context` 必須）は `androidContext()` 経由で
  `infrastructureModule` 内に閉じ込める。ドメインは Android 非依存のまま保たれる。

```kotlin
single<SqlDriver> { AndroidSqliteDriver(FeedDatabase.Schema, androidContext(), "FeedDatabase") }
single { FeedDatabase(get()) }
```

- **WorkManager（#16, ADR-0010）**: `koin-androidx-workmanager` の `workerOf(::RefreshWorker)` を
  用い、worker は `FeedRepository.refreshAllFeeds()` を呼ぶ。worker は work-runtime を持つ
  `:shared:infrastructure` に置くのが自然。ただし `workerOf` DSL には `koin-androidx-workmanager`
  が必要で、現状この artifact は `:app` のみが持つ。**推奨は「worker bind を `infrastructureModule`
  に置き、`:shared:infrastructure` に `koin-androidx-workmanager` を追加」**。build ファイル変更を
  伴うため #16 着手時に確定する。

## 5. ナビゲーション骨格（Navigation3、ADR-0004 / 0006）

ADR-0006 の通り `:feature:feed` に一覧と詳細を同居させ、単一ルートの 2 ペインで扱う。
NavKey は `:feature:feed` に `@Serializable` POKO で定義する（ナビゲーションは UI 関心事であり
ドメインには置かない）。`NavBackStack` / `NavDisplay` は `:feature:feed` のトップレベル
composable（`FeedNavDisplay`）に置き、`:app` の `MainActivity` はそれを host するだけにする。

```kotlin
// :feature:feed
sealed interface FeedNavKey : NavKey
@Serializable data object FeedListKey : FeedNavKey                          // 登録フィード一覧
@Serializable data class ArticleListKey(val feedId: Long) : FeedNavKey      // フィードの記事一覧
@Serializable data class ArticleDetailKey(val articleId: Long) : FeedNavKey // WebView/Custom Tab host
```

- 導線: フィード一覧 → 記事一覧 → 記事詳細。
- **大画面 2 ペイン = 記事一覧 \| 記事詳細**（`ListDetailSceneStrategy` を ArticleList / Detail に
  適用）。メール／リーダー型の標準 UX に合わせる。
- 記事詳細を WebView で表示するか Custom Tabs にするか（#15）は `ArticleDetailKey` の composable
  実装の問題として未確定のまま残す。Custom Tabs は back stack の外（外部アプリ）へ出る一方
  WebView はペイン内に留まる点が 2 ペインと相互作用するため、#15 はこのマッピングと併せて判断する。

## 6. RSS/Atom パーサ方針（ADR-0008 の補完）

ADR-0008 は Ktor を**取得のみ**に限定し、パースは未決のまま。ADR-0002（KMP 前提）により、
JVM 専用の ROME や Android 専用 RSS ライブラリは選べない。

- **方針: KMP 対応ライブラリを採用する。第一候補は `prof18/RSS-Parser`  （RSS / Atom / JSON Feed 対応、Kotlin Multiplatform 対応で ADR-0002 と整合）。
- 取得は ADR-0008 の Ktor を使うか、ライブラリ内蔵の取得＋生文字列パースを使うかの 2 択
  （#13 で確定）。
- パーサ出力は mapper でドメインの `Feed` / `Article` に正規化し、§3 の guid / title / 日時の
  フォールバックを 1 箇所に集約する。
- **#13（記事取得）で新規 ADR 化する**。ライブラリ選定は KMP という横断制約と
  ライブラリ vs 自前の保守性トレードオフを伴い、ADR-0008 が意図的にパースを後回しにしている
  ため、専用 ADR が妥当。#13 で採用を確定し、依存を catalog に追加する。本メモは方針提示まで。

## 7. 既存 ADR との整合・追加提案

- §1〜§6 の決定はいずれも既存 Accepted ADR と矛盾しない。
- **新規 ADR を提案**: RSS/Atom パーサ（#13）。理由は §6 の通り。
- **version catalog への追加**（ADR ではないが要記録、いずれも ADR-0002 と整合）:
  - `kotlinx-datetime`（本メモで確定。§1）
  - RSS パーサライブラリ（#13 で確定・追加。§6）

## 8. 各 TASK への入口

| TASK | 本メモの参照箇所 |
|---|---|
| #12 フィード登録・削除 | §1 `Feed` / §2 `FeedRepository.addFeed/removeFeed` / §3 `Feed` テーブル / §4 DI |
| #13 記事一覧取得・表示 | §2 `ArticleRepository` / §3 `Article` 表・upsert / §5 ナビ / §6 パーサ（ADR 化） |
| #14 既読管理 | §1 `isRead` / §2 `markRead`/`markUnread` / §3 `ArticleReadState` |
| #15 WebView 記事表示 | §5 `ArticleDetailKey`（WebView vs Custom Tabs は #15 で確定） |
| #16 バックグラウンド更新 | §2 `refreshAllFeeds` / §4 WorkManager + Koin |
| #7 フィード一覧（パターン①） | §2 観測 `Flow` / §4 `feedModule` / §5 `FeedListKey`（状態管理は別 doc） |

未確定事項（各 TASK 着手時に確定）: 一覧の単位（横断／フィード単位）、既読化トリガ、
WebView vs Custom Tabs、更新間隔・制約・新着通知の要否、WorkManager Koin bind の配置先。
