# Root Debugging Scaffold

動作を確認したバージョンは、Android Gradle Plugin 9.5.0、Kotlin 2.4.21、Compose BOM 2026.09.00、Navigation 3 1.2.0、libxposed API 102.0.0 です。バージョンを引き上げる場合は、先に公式ドキュメントで変更点を確認してください。

# ビルド方法

次のコマンドでビルドします。成果物は `app/build/outputs/apk/debug/app-debug.apk` です。初めてのビルドでは、Dobby の取得のためにネットワーク接続が必要です。

```sh
export JAVA_HOME=<JDK 21 のパス>
export ANDROID_HOME=<Android SDK のパス>
./gradlew :app:assembleDebug
```

署名鍵はリポジトリの外にあります。`app/build.gradle.kts` の `signingConfigs.pinnedDebug` が参照する場所に、自分の鍵ファイルを置いてください。

# どんなときに、どこを弄るか

## 画面を追加したい、画面遷移を変えたい、テーマを変えたい

画面遷移の登録は `app/src/main/java/app/aoki/example/MainActivity.kt` で変更します。遷移先の登録と、選択した項目の ID を詳細画面へ渡す処理が、このファイルにあります。ホーム画面の実装は `app/src/main/java/app/aoki/example/ui/home/` にあり、見た目は `HomeScreen.kt`、行データは `HomeViewModel.kt` が担当します。詳細画面の実装は `app/src/main/java/app/aoki/example/ui/detail/` にあります。新規画面はこの構成に倣い、画面ごとのディレクトリに Screen と ViewModel を置いてください。テーマは `app/src/main/java/app/aoki/example/ui/theme/Theme.kt` で変更します。色、light と dark の切り替え、dynamic color（端末の設定から生成される配色）は、このファイルで扱います。

完了確認は次の手順です。

1. ビルドします。
2. アプリを起動し、ホーム画面の一覧から追加した画面へ遷移できることと、戻る操作でホーム画面に戻れることを確認します。

## モジュールが読み込まれたときの動きを変えたい

モジュールのエントリクラスは `app/src/main/java/app/aoki/example/module/RootDebugModule.kt` です。読み込み時のログ出力とベースラインのフックの設置と、Java メソッドフックの例である通知の表示は、このファイルで変更します。通知の表示に必要な POST_NOTIFICATIONS 権限の宣言は `AndroidManifest.xml` に、実行時の要求は `MainActivity` にあります。モジュールの宣言ファイルは `app/src/main/resources/META-INF/xposed/` に置きます。`module.prop` には API バージョンを、`java_init.list` にはエントリクラスの完全修飾名を記述します。

## フックするパッケージを増やしたい

対象パッケージは `app/src/main/resources/META-INF/xposed/scope.list` に、1 行に 1 つずつ追加します。現在このファイルには自アプリ自身（`app.aoki.example`）が入っており、モジュールが自アプリのプロセスに読み込まれてフックを設置するため、フックの動作を自アプリで検証できます。モジュールは対象プロセスの開始時にだけ読み込まれるため、追加した後は、対象プロセスの再起動、または端末の再起動が必要です。

## フックする対象を libc から別のライブラリに変えたい

対象は `app/src/main/cpp/rootdebug_native.c` の `install_hooks_once` です。現在の対象は libc の `openat` と `close` です。

変更箇所は次の 3 点です。

- `dlopen` に渡すライブラリ名
- `dlsym` に渡す関数名
- 置き換え関数のシグネチャ

設置の仕組みは変更不要です。

シグネチャと戻り値の意味は推測で書かず、対象のヘッダまたは実装で確認してください。契約が違うと、フック先のプロセスが異常終了します。

フックする関数の数が変わる場合は、報告側も合わせて変更します。JNI の `baselineHookCounts` が返す配列の長さ、`NativeBridge.kt` の受け側、デバッグパッケージの `native-hooks.json` のフィールド名が対象です。この内容は、現在の対象が libc でない場合にも同じように当てはまります。

完了確認は次の手順です。

1. ビルドします。
2. フレームワークでモジュールを有効にし、対象プロセスを再起動します。この例では自アプリが対象であり、アプリのプロセス再起動で足ります。
3. デバッグパッケージを書き出し、`native-hooks.json` の設置状態が installed であることと、対象関数のカウンタが増えていることを確認します。

## JNI を追加したい

C 側の関数は `app/src/main/cpp/rootdebug_native.c` に書き、`JNI_OnLoad` の `RegisterNatives` に必ず登録します。`Java_` で始まる関数名による自動の対応付けは使用しません。Kotlin 側の受け口は `app/src/main/java/app/aoki/example/module/NativeBridge.kt` です。`external fun` の宣言と、`System.loadLibrary` の読み込み結果の保持を追加します。ビルド設定は `app/src/main/cpp/CMakeLists.txt` です。Dobby は CMake の FetchContent でビルド時に取得する外部依存であり、取得先と固定コミットはこのファイルに記載されています。ベンダーライブラリをソースツリーに同梱しないでください。

## デバッグパッケージの書き出し内容を変えたい

本体は `app/src/main/java/app/aoki/example/debug/DebugExporter.kt` です。セクションを追加または削除する場合は、このファイルの `sections` リストを編集します。各セクションは、ID、ファイル名、内容を作る関数の組です。ファイル形式は、内容が key と value の組で表現できるものを JSON とし（現在の `app-info.json`、`index.json`、`native-hooks.json` が該当します）、文章のログはテキストのままにします。`index.json` は変更不要です。ジャーナルは `app/src/main/java/app/aoki/example/debug/Journal.kt` が担当する追記専用のテキストファイルです。記録を増やす場合は `append()` の呼び出し側を追加します。

## root 権限でコマンドを実行したい

root 権限でコマンドを実行する例は 2 つあります。1 つはデバッグパッケージの `root.json` セクションであり、`app/src/main/java/app/aoki/example/debug/DebugExporter.kt` にあります。このセクションは libsu を直接使い、`id` コマンドを実行して、root の付与状態、成否、終了コード、標準出力と標準エラー出力を記録します。もう 1 つは、ホーム画面から開く Root Demo 画面であり、`app/src/main/java/app/aoki/example/ui/root/` にあります。この画面は、libsu で `id` コマンドを実行し、結果を画面に表示します。libsu は JitPack から取得する外部依存で、リポジトリの宣言は `settings.gradle.kts` にあります。初めて実行する際は root 管理アプリの承認画面が表示されることがあります。また、シェルの開始は呼び出しをブロックするため、実行は常にバックグラウンドのスレッドで行ってください。

## 名前を変えたい（自分の製品の名前にする）

scaffold 内の名称（パッケージ名 `app.aoki.example`、モジュールクラス `RootDebugModule`、テーマ `RootDebugTheme`、ネイティブライブラリ `rootdebug`、プロジェクト名 `root-debugging-scaffold` など）はすべて仮のものです。改名は、ビルド定義、Kotlin と C のソースコード、Xposed 宣言ファイルに跨ります。

### 1. 事前に決める値

作業の途中で表記が揺れると残存チェックが困難になるため、着手前に以下の新しい値をすべて決定・固定します。

- **プロジェクト名**（例: `my-product`）
- **パッケージ名** / **applicationId**（例: `app.aoki.myproduct`）
- **モジュールクラス名**（例: `ProductModule`）
- **テーマ名**（例: `ProductTheme`）
- **ネイティブライブラリ名** および **Cソース名**（例: `product_native` / `product_native.c`）
- **アプリ表示名**（例: `My Product`）
- **デバッグ ZIP 接頭辞 / ログタグ名**

### 2. 置換対象

| 箇所 | 置換する対象 | 備考 |
| --- | --- | --- |
| `settings.gradle.kts` | `rootProject.name` |  |
| `app/build.gradle.kts` | `namespace`、`applicationId` | 署名設定が参照する鍵ファイルのパスには接頭辞と同じ文字列が含まれているため、全置換の対象に含めないこと |
| Kotlin ソース全体 | `package` 宣言、`import`、ディレクトリ階層 | ディレクトリもパッケージに合わせて移動する |
| `RootDebugModule`、`RootDebugTheme` | クラス名 | ファイル名がクラス名と一致するファイルはファイル名も変更する（`Theme.kt` のように一致しないファイルは変更しない） |
| `java_init.list` | エントリクラスの完全修飾名（1 行） | 旧名のままだとフレームワークがモジュールを読み込めない |
| `module.prop` | 変更不要 | API バージョンのみが書かれている |
| `scope.list` | 自アプリを指す行のパッケージ名 | 他の対象パッケージの行は変更しない |
| `CMakeLists.txt` | `project`、`add_library`、`target_include_directories`、`target_link_libraries` の対象名、C ソースの参照名 | 1 か所でも旧名が残ると CMake の構成が成立しない |
| `NativeBridge.kt` | `System.loadLibrary` の文字列 | ライブラリ名と一致しないと実行時に読み込めない |
| `rootdebug_native.c` | ファイル名、`JNI_OnLoad` の登録先クラスパス、`LOG_TAG`、`diagnosticPing` の返却値 | 登録先クラスパスはスラッシュ区切りで書く |
| `AndroidManifest.xml`、`HomeScreen.kt` | アプリ表示名 | manifest の `label` とトップバーの文言の 2 か所 |
| `DebugExporter.kt` | デバッグ ZIP の接頭辞 | 2 か所 |
| `RootDebugModule.kt` の `TAG` 定数 | ログタグ |  |
| `README.md`、`AGENTS.md` | 旧パスと旧名称 |  |

### 3. 注意点と変更してはいけないもの

`applicationId` を変えると、端末と Xposed からは別アプリとみなされます。付与済み権限やフレームワークへのモジュール有効化設定、旧データは引き継がれません。変更しないものは、各種バージョン番号と Xposed API バージョン、署名鍵そのものです。`scope.list` については、自アプリを指す行がパッケージ名の変更に合わせて書き換えの対象になります。他の対象パッケージの行は、本アプリの名称とは無関係です。

### 4. 完了確認

1. **ツリー全体検索**: プロジェクト全体を旧名で検索し、ファイル本文と、ファイル名・ディレクトリ名の双方に残存がないことを確認します。
2. **クリーンビルドと検証**: 過去のビルド成果物を削除してから再度ビルドを実行し、生成された APK に新ライブラリ名の `.so` が含まれていること、APK 内の `java_init.list` が新完全修飾名になっていること、署名証明書が維持されていることを確認します。

## 依存ライブラリを追加したい、バージョンを上げたい

バージョンと座標は `gradle/libs.versions.toml` に集約されています。追加と変更はこのファイルで行い、各所に直接書かないでください。利用側の宣言は `app/build.gradle.kts` の `dependencies` です。ライブラリの採用は実装時に実際に試してから決め、採用しなかった場合はその理由を記録に残してください。プラットフォームが提供している部品を自前で実装しないでください。

## 署名を変えたい

署名の設定箇所は `app/build.gradle.kts` の `signingConfigs.pinnedDebug` のみです。ビルド環境のデフォルト鍵は使用しません。鍵ファイルは、このリポジトリの外に置きます。証明書が変わると上書きインストールができなくなるため、アンインストールと再インストールが必要になります。

## バージョン番号や最小 SDK を変えたい

バージョン番号や最小 SDK は、`app/build.gradle.kts` の `android` ブロックで変更します。対象は `versionCode`、`versionName`、`compileSdk`、`minSdk`、`targetSdk` です。

# AI に編集させる場合

このリポジトリを AI に編集させる場合の規則は、AGENTS.md に集約されています。編集させる前に、そのファイルを読ませてください。
