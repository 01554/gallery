# Gallery + OpenAI互換APIサーバー (Fork)

> **[English README](README.md)**

> **Google AI Edge GalleryにlocalhostのHTTPサーバーを追加し、端末のGPUで動くLLM推論にOpenAI互換APIでアクセスできるようにしたforkです。**

## このforkで追加したもの

Google AI Edge Galleryは、TFLite GPU delegateを使って端末上でLLMを動かすAndroidアプリです。このforkでは**Foreground Service**を追加し、推論エンジンを**OpenAI互換のHTTP API**として`localhost:8080`で公開します。

つまり、スマホのGPUでGemma 4などを推論しながら、TermuxやOpenClawなどから`curl`でアクセスできます。

### なぜ作ったのか

Qualcomm Adreno 6xx GPU（Snapdragon 888など）では、llama.cppのVulkan/OpenCLバックエンドがドライバー互換性の問題でクラッシュします。TFLite GPU delegateはGoogleがQualcommと協力して互換性を確保しているため、これらの端末でGPU推論ができる唯一の信頼できる方法です。

### 追加したファイル

| ファイル | 説明 |
|---------|------|
| `app/.../server/LlmHttpServer.kt` | NanoHTTPDによるHTTPサーバー。テキスト・画像（base64/URL）・Thinkingモード対応 |
| `app/.../server/LlmServerService.kt` | `Backend.GPU()`でモデルをロードし、HTTPサーバーを起動するForeground Service。サーバー状態を`StateFlow`で公開 |
| `app/.../server/ServerDrawerItem.kt` | ナビゲーションドロワー用のサーバー起動/停止項目。状態に応じて色が変わる。ログビューアーへのアクセス |
| `app/.../server/ServerLogsScreen.kt` | リアルタイムログビューアー（全画面、自動スクロール、色分け表示） |
| `app/.../server/ServerLog.kt` | 共有ログバッファ（シングルトン、100行）。ServiceとUIの両方からアクセス可能 |
| `app/.../server/CommunityModelBrowser.kt` | HuggingFaceの`litert-community`から`.litertlm`モデルを動的に取得 |
| `app/.../server/ServerButton.kt` | フローティングボタン版（参考用に残存、現UIでは未使用） |

### 変更したファイル

| ファイル | 変更内容 |
|---------|---------|
| `app/build.gradle.kts` | `nanohttpd:2.3.1`の依存を追加 |
| `AndroidManifest.xml` | Serviceの登録と`FOREGROUND_SERVICE_SPECIAL_USE`パーミッションを追加 |
| `HomeScreen.kt` | ナビゲーションドロワーに「API Server」項目追加、トップバーにログアイコン追加 |
| `GalleryAppTopBar.kt` | `SERVER_LOGS`アクションタイプ追加（色付きターミナルアイコン） |
| `AppBarAction.kt` | `SERVER_LOGS` enum値を追加 |
| `ModelManagerViewModel.kt` | `litert-community`モデルをallowlistに統合、RAM警告付き |

## 使い方

### 1. ビルドとインストール

```bash
# Java 21とAndroid SDKが必要です
export JAVA_HOME=/path/to/openjdk-21
cd Android/src
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 2. モデルをダウンロード

アプリを開いて、左上の**ハンバーガーメニュー**をタップし、**Models**を選択。一覧からモデル（例: **Gemma 4 E2B**）をダウンロードしてください。サーバーはここでダウンロードしたモデルを使います。

手動で`.litertlm`ファイルを転送することもできます:

```bash
curl -L "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm" \
  -o gemma-4-E2B-it.litertlm
adb push gemma-4-E2B-it.litertlm /data/local/tmp/
```

### 3. サーバーを起動（アプリUIから）

左上の**ハンバーガーメニュー**を開くと、SettingsとModelsの下に**「API Server」**があります。

| サーバー状態 | 色 | 操作 |
|------------|-----|------|
| 停止中 | グレーの枠線・アイコン | タップで起動 |
| モデルロード中 | オレンジの枠線・アイコン | ロード完了を待つ |
| 動作中 | 緑の枠線・アイコン（ポート番号表示） | タップで停止 |
| エラー | 赤の枠線・アイコン | タップで再試行 |

「API Server」をタップすると:
- モデルが**1つ**だけの場合、すぐにサーバーが起動します
- モデルが**複数**ある場合、どのモデルを使うか選択ダイアログが表示されます
- 選択したモデルを**GPU**にロードし、`localhost:8080`でリクエストを受け付けます

モデルは以下の2箇所から検出されます:
- アプリの**Models**ページからダウンロードしたモデル
- `/data/local/tmp/`にある`.litertlm`ファイル

### 4. サーバーを起動（adbコマンド、代替手段）

コマンドラインからも起動・停止できます:

```bash
# 起動
adb shell am start-foreground-service \
  -n com.google.aiedge.gallery/com.google.ai.edge.gallery.server.LlmServerService \
  --es model_path '/data/local/tmp/gemma-4-E2B-it.litertlm' \
  --ei port 8080

# 停止
adb shell am stop-service \
  -n com.google.aiedge.gallery/com.google.ai.edge.gallery.server.LlmServerService
```

### 5. 使う

端末上のTermuxなどから:

```bash
# ヘルスチェック
curl http://127.0.0.1:8080/health

# チャット補完 (OpenAI互換)
curl -X POST http://127.0.0.1:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gemma",
    "messages": [{"role": "user", "content": "こんにちは！"}],
    "max_tokens": 128
  }'

# Thinkingモード（レスポンスにreasoning_contentが含まれる）
curl -X POST http://127.0.0.1:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gemma",
    "messages": [{"role": "user", "content": "2+2は？"}],
    "enable_thinking": true
  }'
```

### リクエストパラメータ

### 画像入力（マルチモーダル）

Gemma 4は画像入力に対応。OpenAI互換のbase64データURLまたはHTTP URLが使えます:

```bash
IMG_B64=$(base64 -w 0 photo.jpg)
curl -X POST http://127.0.0.1:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d "{
    \"model\": \"gemma\",
    \"messages\": [{
      \"role\": \"user\",
      \"content\": [
        {\"type\": \"image_url\", \"image_url\": {\"url\": \"data:image/jpeg;base64,$IMG_B64\"}},
        {\"type\": \"text\", \"text\": \"この画像に何が写っていますか？\"}
      ]
    }]
  }"
```

### サーバーログ

ホーム画面右上のターミナルアイコンをタップすると、リアルタイムログビューアーが開きます。アイコンの色でサーバー状態が分かります:
- グレー = 停止中
- オレンジ = ロード中
- 緑 = 稼働中
- 赤 = エラー

起動ログ、リクエスト（プロンプトのプレビュー）、生成トークン数が表示されます。

### リクエストパラメータ

| パラメータ | デフォルト | 説明 |
|-----------|-----------|------|
| `messages` | (必須) | `{"role": "user", "content": "..."}` の配列 |
| `temperature` | 0.7 | ランダム性の制御（0.0=確定的、1.0+=創造的） |
| `top_k` | 64 | サンプリングの候補数 |
| `top_p` | 0.95 | nucleus sampling の閾値 |
| `enable_thinking` | false | trueにすると、レスポンスにモデルの思考プロセス（`reasoning_content`）が含まれる |

`enable_thinking` を true にした場合、レスポンスの message に `content`（最終回答）と `reasoning_content`（思考過程）の両方が含まれます:

```json
{
  "message": {
    "role": "assistant",
    "content": "4",
    "reasoning_content": "Thinking Process:\n1. 問題を分析..."
  }
}
```

### 対応モデル

`litert-community`の公式モデルのみ動作確認済みです:

| モデル | サイズ | 必要RAM | ダウンロード |
|-------|--------|---------|------------|
| Gemma 4 E2B | 2.4GB | 8GB | [litert-community/gemma-4-E2B-it-litert-lm](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm) |
| Gemma 3 1B | 557MB | 6GB | [litert-community/Gemma3-1B-IT](https://huggingface.co/litert-community/Gemma3-1B-IT) |
| Gemma 4 E4B | 3.4GB | 12GB | [litert-community/gemma-4-E4B-it-litert-lm](https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm) |

サードパーティの`.litertlm`ファイルはクラッシュする場合があります。`litert-community`または`google`が公開しているモデルを使ってください。

### 動作確認済み環境

- Galaxy Z Flip3 (Snapdragon 888 / Adreno 660, RAM 8GB, Android 14)
- Gemma 4 E2B: GPU使用率 約40%、モデルロード 約12秒

### ビルド環境 (動作確認済み)

| 項目 | バージョン |
|------|-----------|
| ホストOS | macOS 15.6.1 (Apple Silicon / arm64) |
| Java | OpenJDK 21.0.10 (Homebrew) |
| Gradle | 8.10.2 |
| Kotlin | 1.9.24 |
| Android SDK | compileSdk 35, minSdk 31, targetSdk 35 |
| NDK | 27.2.12479018 |

**注意:** Java 17では動きません（Hilt/KAPTが`NullPointerException: processingEnv must not be null`を投げます）。Java 21を使ってください。

### 動作確認した端末

| 項目 | 詳細 |
|------|------|
| 端末 | Galaxy Z Flip3 (SM-F711N) |
| SoC | Snapdragon 888 |
| GPU | Adreno 660 |
| RAM | 8GB |
| OS | Android 14 |
| モデル | Gemma 4 E2B (2.4GB) |
| GPU使用率 | 推論中 約40% |
| モデルロード時間 | 約12秒 |

### サーバーの停止

```bash
adb shell am stop-service \
  -n com.google.aiedge.gallery/com.google.ai.edge.gallery.server.LlmServerService
```

---

以下、元のGoogle AI Edge Galleryの説明です。

---
