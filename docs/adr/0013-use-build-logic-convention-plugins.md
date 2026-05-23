# 13. Use build-logic convention plugins

Date: 2026-05-24

## Status

Accepted

## Context

ADR-6 のマルチモジュール構成で、Android / lint / Detekt / ktfmt / テストスタック等の設定が各モジュールに重複して書かれている。モジュールが増えるたび重複が線形に膨らみ、設定ドリフトのリスクが上がる。

## Decision

`build-logic/` を includeBuild し、共通設定を convention plugin に集約する。各モジュールは plugin ID で convention を取り込み、自分固有のものだけを宣言する。

判断軸は「モジュール固有の identity か、同カテゴリ共通の不変設定か」。identity（namespace, applicationId, モジュール間プロジェクト依存等）は per-module、それ以外は convention。

## Consequences

- 各モジュールの build.gradle.kts は数行〜十数行に縮む
- 新規モジュール追加は plugin id を書くだけで品質ゲートが揃う
- 将来 ADR-2 の KMP 化時は `kotlin.multiplatform` 用 convention を足して吸収する
- build-logic 自身のコンパイル時間が build に乗る（小規模では数秒）
