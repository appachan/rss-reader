# 15. Derive SQLDelight schema from migrations

Date: 2026-06-14

## Status

Accepted

## Context

SQLDelight にはスキーマ進化の運用方法が2つある。

- 既定: 現行スキーマを `.sq` の CREATE 文で宣言し、差分を `.sqm` マイグレーションファイルに書く。
  fresh install は `Schema.create()` が `.sq` の CREATE を実行、更新は `Schema.migrate()` が
  `.sqm` を順次適用する。CREATE 文と差分 DDL が二箇所に手書きで存在するため、
  両者を同期させる規律が要る。create 経路と migrate 経路で同じバージョンが異なるスキーマに
  到達する状態（drift）を防ぐには、`verifyMigrations` ゲートとバージョンごとの `.db`
  スキーマスナップショットを CI に組み込む必要がある。

- `deriveSchemaFromMigrations = true`: 現行スキーマを `.sq` で宣言せず、全 DDL を順序付きの
  `.sqm` ファイルに書く。`Schema.create()` は全マイグレーションの再生、`Schema.migrate()` は
  その接尾辞の再生となり、両経路が同じソースを通る。`.sq` にはラベル付きクエリだけが残る。

## Decision

`deriveSchemaFromMigrations = true` を採用する。

理由:

- create 経路と migrate 経路が同じ `.sqm` 群を再生するため、drift は構造的に発生し得ない。
  新規インストール端末と更新端末でスキーマが食い違う事故が、規律ではなく構造で防がれる
- CREATE 文の二重管理が無くなる
- `.db` スキーマスナップショットの生成・コミット・保守が不要
- `verifyMigrations` 設定を CI に維持する必要も無くなる（比較対象が一系統しか存在しないため
  検証する余地が無い）

トレードオフとして、現行スキーマを1ファイルで一望できなくなる。読者は `.sqm` 群をバージョン順に
追う必要がある。マイグレーション1つあたりの差分は小さい想定で、許容する。

## Consequences

- `.sq` ファイルはラベル付きクエリのみを持つ。CREATE / ALTER / DROP / CREATE INDEX 等の DDL は
  記述しない
- スキーマ変更は新しい `<n>.sqm` ファイルを末尾に追加する形で表現する
- `verifyMigrations` 設定およびスキーマスナップショット `.db` ファイルは使わない
- 純粋な DDL 変更に対する手書きのマイグレーションテストは不要。フレームワークが create と
  migrate の一致を保証する。データを保持・変換するマイグレーション（バックフィル、型変換、
  カラム分割など）には個別にテストを書く
