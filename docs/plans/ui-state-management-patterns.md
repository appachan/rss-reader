# Android UI状態管理パターン 議論まとめ

## 背景

RSSリーダーのアーキテクチャ検討中に発展した議論。「1画面で複数APIを並列・依存関係あり・取得したところから表示する」という要件に対して、Androidコミュニティが明確な解を持っていないという問題意識から始まった。

---

## 問題の核心

Reactのようにコンポーネントごとにfetchする設計と異なり、AndroidはViewModelが画面単位で一元管理する思想のため、複雑なデータ取得要件をViewModelに詰め込むと`load()`が肥大化しやすい。

具体的に問題になるケース：
- 複数APIを並列で叩いて取得したところから表示したい
- APIに依存関係がある（AのレスポンスでB・Cを並列、など）
- pull-to-refreshで既存データを表示しつつローディングを表示したい

---

## 各パターンの整理

### UiStateを部分的に持つ（Async型）

AirbnbのMavericks（旧MvRx）が広めたパターン。

```kotlin
sealed interface Async<out T> {
    data object Idle : Async<Nothing>
    data object Loading : Async<Nothing>
    data class Success<T>(val data: T) : Async<T>
    data class Failure(val error: Throwable) : Async<Nothing>
}

data class FeedUiState(
    val profile: Async<Profile> = Idle,
    val feeds: Async<List<Feed>> = Idle,
    val isRefreshing: Boolean = false, // ← ここが妥協点
)

val uiState = combine(profileFlow, feedsFlow) { profile, feeds ->
    FeedUiState(profile = profile, feeds = feeds)
}.stateIn(...)
```

**利点**
- 複数ソースをcombineで合成しやすい
- 取得したところから部分的にUIを更新できる

**問題点**
- `Refreshing`状態を型で表現できない
- `Refreshing`をAsyncに追加すると`Success`と`Refreshing`で`data`を二重に持つ構造になり`when`分岐が冗長になる
- 結局`isRefreshing: Boolean`をフラットなBooleanで別管理する妥協をすることが多い
- 「不正な状態の組み合わせを型で防ぐ」という動機が半分消える

**Throwableについて**
`Failure(val error: Throwable)`はParcelableではないが、UiStateをSavedStateHandleに保存しないのが一般的なのでこれで問題ない。エラー状態はプロセスデスから復帰時に復元する必要がなく、再フェッチすればよい。

---

### State Reducer

Reduxから来た概念。「現在のState」と「何が起きたか（Action）」を受け取って「次のState」を返す純粋関数。

```kotlin
data class FeedState(
    val feeds: List<Feed> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

sealed interface FeedAction {
    object LoadStarted : FeedAction
    object RefreshStarted : FeedAction
    data class LoadSuccess(val feeds: List<Feed>) : FeedAction
    data class LoadFailed(val error: String) : FeedAction
}

fun reduce(state: FeedState, action: FeedAction): FeedState = when (action) {
    is FeedAction.LoadStarted    -> state.copy(isLoading = true, error = null)
    is FeedAction.RefreshStarted -> state.copy(isRefreshing = true, error = null)
    is FeedAction.LoadSuccess    -> state.copy(feeds = action.feeds, isLoading = false, isRefreshing = false)
    is FeedAction.LoadFailed     -> state.copy(isLoading = false, isRefreshing = false, error = action.error)
}
```

```kotlin
class FeedViewModel : ViewModel() {
    private val _state = MutableStateFlow(FeedState())
    val state = _state.asStateFlow()

    private fun dispatch(action: FeedAction) {
        _state.update { reduce(it, action) }
    }

    fun load() {
        viewModelScope.launch {
            dispatch(FeedAction.LoadStarted)
            feedRepository.getFeeds()
                .onSuccess { dispatch(FeedAction.LoadSuccess(it)) }
                .onFailure { dispatch(FeedAction.LoadFailed(it.message ?: "")) }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            dispatch(FeedAction.RefreshStarted) // ← これだけ違う
            feedRepository.getFeeds()
                .onSuccess { dispatch(FeedAction.LoadSuccess(it)) }
                .onFailure { dispatch(FeedAction.LoadFailed(it.message ?: "")) }
        }
    }
}
```

**利点**
- pull-to-refreshが自然に表現できる（既存データを保ちつつ`isRefreshing = true`）
- 状態遷移のロジックがreducer一箇所に集まる
- 不正な状態の組み合わせが発生しにくい
- reducerが純粋関数なのでテストが簡単

**問題点**
- 素のViewModelで書くとAction定義のボイラープレートが増える
- Async型+combineと比べると複数ソースの合成が若干冗長

---

### Orbit MVI

State reducerのパターンをDSLで包んだライブラリ。KMP対応済み。

```kotlin
class FeedViewModel : ContainerHost<FeedState, FeedSideEffect>, ViewModel() {
    override val container = container<FeedState, FeedSideEffect>(FeedState())

    fun refresh() = intent {
        reduce { state.copy(isRefreshing = true) }
        runCatching { feedRepository.getFeeds() }
            .onSuccess { feeds -> reduce { state.copy(feeds = feeds, isRefreshing = false) } }
            .onFailure { reduce { state.copy(isRefreshing = false, error = it.message) } }
    }
}
```

**利点**
- state reducerの恩恵をより少ないボイラープレートで得られる
- SideEffect（ナビゲーション・Snackbar等の一回限りのイベント）の仕組みが組み込まれている
- KMP対応

---

### Molecule / Circuit（参考）

RepositoryのFlowをComposable関数内で`collectAsState`して、UIロジックと同じモデルでデータロジックを書くCashApp式のアプローチ。Reactのコンポーネントごとfetchに最も近い思想。複数API依存関係も`if`で自然に書ける。まだマイナリティ。

---

## 各パターンの比較

| | Async + combine | State Reducer | Orbit MVI |
|---|---|---|---|
| 複数ソース合成 | ◎ | △ | △ |
| Refreshing表現 | △（Boolean別管理） | ○ | ○ |
| 型安全性 | △（中途半端） | ○ | ○ |
| ボイラープレート | 少 | 中 | 少 |
| テストしやすさ | △ | ◎ | ○ |
| KMP対応 | ○ | ○ | ◎ |

---

## 検証方針（パターン①〜④の定義）

RSSリーダーのフィード一覧画面を同じ要件（pull-to-refresh・複数API依存・エラーハンドリング）で各パターンに実装し直して比較する。
このドキュメントおよび GitHub Project の各タスクで「パターン①〜④」と書いた場合、番号は必ず以下を指す（このリストが正典。上の「各パターンの整理」の見出し順とは無関係）。

| 番号 | パターン | 対応タスク |
|---|---|---|
| ① | UiState部分持ち + isRefreshing Boolean（Async型を使わないフラットな UiState） | TASK-005（①固定でフィード一覧を実装） |
| ② | Async型 + combine | TASK-011 |
| ③ | State reducer（素のViewModel） | TASK-012 |
| ④ | Orbit MVI | TASK-013 |

①②はどちらも「UiStateを部分的に持つ」系統（①=フラットな Boolean フラグのみ／②=Async型ラッパ + combine）、③④は state reducer 系統。技法の背景は上の「各パターンの整理」の各節を参照。Molecule / Circuit は参考であり実装対象外。
TASK-014 で①〜④を比較し、採用パターンを決めて ADR 化する。

各実装で pull-to-refresh・依存関係あり並列 API・エラー時の挙動を比較し、実プロジェクトへの適用判断材料とする。
