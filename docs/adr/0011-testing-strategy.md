# 11. Testing strategy

Date: 2026-05-23

## Status

Accepted

## Context

プロジェクト全体のテスト手法とライブラリ構成を整理する必要がある。Composable の UI テスト（`createComposeRule`）は JUnit4 ランナー前提で、Kotlin ネイティブのテストフレームワーク（Kotest 系）と JUnit ランナーの競合が起きやすいという制約がある。

## Decision

### レイヤー別の構成

| 対象 | 手法 | ライブラリ |
|---|---|---|
| Composable | Preview / VRT | **Roborazzi**（JUnit4 ベース、Kotest と独立した文脈） |
| ViewModel | ユニットテスト | **Kotest** + **kotlinx-coroutines-test** + **Turbine** |
| Domain | ユニットテスト | **Kotest**（data-driven 含む） |
| Infrastructure | ユニットテスト | **Kotest** + **MockK** |

### テストダブルの方針

Fake 優先で組む：

- Repository などプロジェクト固有のインターフェースは、テスト用に in-memory Fake 実装を用意して使う
- MockK は Fake 実装が困難・過剰な箇所（外部 SDK 直接呼び出し、suspend 関数の呼び出し検証 等）に限定して使う

### 理由

- **レイヤー分離**：Composable テストを Preview / VRT に寄せることで JUnit4 との競合問題を回避し、残り全層を Kotest で統一できる。ViewModel は Android 依存がなく Robolectric も不要なため Kotest で問題なく書ける。`:shared:domain` は Android 依存ゼロなので Kotest の制約も発生しない
- **Kotest**：KMP 対応（ADR-2）の Kotlin ネイティブテストフレームワーク。data-driven testing に対応。メインのテストフレームワークから JUnit4 は除外
- **MockK**：Kotlin 向けに設計され suspend 関数・Coroutines のモックに対応。Mockito は Kotlin 対応が劣るため除外
- **Turbine**：`StateFlow` / `SharedFlow` の値をシーケンシャルにアサーション可能。ViewModel で StateFlow を多用する本プロジェクトに適合
- **Roborazzi**：@Preview 関数をそのままスナップショット取得できる。Robolectric ベースのローカル JVM 実行

## Consequences

- Kotest / MockK / Turbine / kotlinx-coroutines-test は KMP 対応のため将来 multiplatform 化しても利用可能
- Compose テストは独立した JUnit4 文脈で共存させる
