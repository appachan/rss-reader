# CLAUDE.md — RSS Reader

Android (Kotlin) の RSS リーダー。

## Verification loop

ソースコードや Gradle 設定を変更したら、提示する前に変更内容に応じた検証タスクを実行する。
失敗したら原因を読んで修正し、成功してから提示する。

変更タイプ別の目安:

- Kotlin コード変更 → `./gradlew detekt` と該当モジュールの `:<module>:test`
- フォーマット崩れ → `./gradlew ktfmtFormat`（自動修正）→ `./gradlew ktfmtCheck`
- Gradle / convention plugin 変更 → 該当モジュールの `:assemble` などピンポイントで実行
- UI / APK の最終確認が必要なときのみ → `./gradlew :app:assembleDebug`

`./gradlew check` は detekt / ktfmt / 全モジュールの test / Android Lint を一括で実行するため
遅い。日常の検証では使わず、PR 直前など全体確認が必要なときだけ使う。

## Individual tasks

| Task                          | 用途                              |
|-------------------------------|----------------------------------|
| `./gradlew detekt`            | 静的解析                         |
| `./gradlew ktfmtCheck`        | フォーマット検査                 |
| `./gradlew ktfmtFormat`       | フォーマット自動修正             |
| `./gradlew :<module>:test`    | 該当モジュールのユニットテスト   |
| `./gradlew :app:assembleDebug`| Debug APK ビルド                 |

## ADRs

アーキテクチャ判断は `docs/adr/` に集約。一覧は `adr list` で取得できる
（フォールバックは `ls docs/adr/`）。ファイル名のタイトル部分から関連 ADR を絞り、
必要なものだけ本体を開く。毎回全文を読まない。
