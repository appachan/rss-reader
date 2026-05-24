# CLAUDE.md — RSS Reader

Android (Kotlin) の RSS リーダー。

## Verification loop

ソースコードや Gradle 設定を変更したら、提示する前に変更内容に応じた検証タスクを実行する。
失敗したら原因を読んで修正し、成功してから提示する。

変更タイプ別の目安:

- Kotlin コード変更 → `./gradlew detekt` と該当モジュールの `:<module>:test`
- フォーマット崩れ → `./gradlew spotlessApply`（自動修正）→ `./gradlew spotlessCheck`
- Gradle / convention plugin 変更 → 該当モジュールの `:assemble` などピンポイントで実行
- UI / APK の最終確認が必要なときのみ → `./gradlew :app:assembleDebug`

`./gradlew check` は detekt / spotless / 全モジュールの test / Android Lint を一括で実行するため
遅い。日常の検証では使わず、PR 直前など全体確認が必要なときだけ使う。

## Individual tasks

| Task                          | 用途                              |
|-------------------------------|----------------------------------|
| `./gradlew detekt`            | 静的解析                         |
| `./gradlew spotlessCheck`     | フォーマット検査                 |
| `./gradlew spotlessApply`     | フォーマット自動修正             |
| `./gradlew :<module>:test`    | 該当モジュールのユニットテスト   |
| `./gradlew :app:assembleDebug`| Debug APK ビルド                 |

## VRT / スクリーンショット (Roborazzi)

library Compose モジュールの `@Preview` は Roborazzi で JVM 上スナップショット化できる（エミュレータ不要）。
Composable を実装・変更したら、これで見た目を自分で確認・回帰検査すること。

- 新規・変更した画面の見た目を確認する: `./gradlew :<module>:recordRoborazziDebug` で
  `<module>/src/test/screenshots/*.png` を再生成し、その PNG を Read ツールで開いて意図どおりか目視する。
- 既存画面の回帰を検査する: `./gradlew :<module>:verifyRoborazziDebug`。差分があれば失敗し `build/` 配下に
  差分画像が出る。意図した変更なら record で golden を更新し、再生成した PNG を同じコミットに含める。
  意図しない差分なら実装を直す。

設定は `AndroidComposeConventionPlugin` に集約済みでモジュール側の追記は不要。`@Preview` を足せば自動で
対象になる。

## ADRs

アーキテクチャ判断は `docs/adr/` に集約。一覧は `adr list` で取得できる
（フォールバックは `ls docs/adr/`）。ファイル名のタイトル部分から関連 ADR を絞り、
必要なものだけ本体を開く。毎回全文を読まない。
