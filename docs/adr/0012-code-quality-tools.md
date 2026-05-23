# 12. Code quality tools

Date: 2026-05-23

## Status

Accepted

## Context

コードの自動チェックには、フォーマット・Kotlin 静的解析・Android 固有問題の検出という 3 つの関心事がある。それぞれに専用ツールを割り当てる必要がある。

## Decision

3 種類のツールを役割分担で並行運用する：

| 関心事 | ツール | 補足 |
|---|---|---|
| フォーマット | **ktfmt** | Kotlin コードの統一フォーマット |
| Kotlin 静的解析 | **Detekt** | 複雑度・潜在バグ・スタイル違反。`detekt-formatting` プラグイン等のフォーマット系ルールは無効化し、lint 用途に限定 |
| Android 固有問題 | **Android Lint** | API 互換 / リソース / レイアウト / manifest / accessibility / permission 等 |

Android Lint は warning も build を fail させる：

```kotlin
android {
    lint {
        warningsAsErrors = true
        abortOnError = true
    }
}
```

理由：

- **ktfmt**：Google/Meta 製で設定の余地が少なく一意なフォーマット結果を出す。ktlint より一意性が高い
- **Detekt**：Kotlin 専用の静的解析ツール。フォーマットは ktfmt に任せ、Detekt の formatting 系ルールとは併用しない
- **Android Lint**：AGP 標準で追加依存なし。Detekt の範囲外である Android 固有問題を補完。技術検証プロジェクトとして warning も早期に潰す

## Consequences

- 3 ツールはそれぞれ独立した責務を持ち、衝突しない
- CI（GitHub Actions）の必須ステップに各ツール実行を含める：`./gradlew lint`, ktfmt check, Detekt
- Android Lint baseline（`lint-baseline.xml`）は使用しない。新規プロジェクトとして最初から warning 0 を維持する
