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
| `app/.../server/LlmHttpServer.kt` | NanoHTTPDによるHTTPサーバー。`/v1/chat/completions`, `/v1/models`, `/health`を提供 |
| `app/.../server/LlmServerService.kt` | `Backend.GPU()`でモデルをロードし、HTTPサーバーを起動するForeground Service |

### 変更したファイル

| ファイル | 変更内容 |
|---------|---------|
| `app/build.gradle.kts` | `nanohttpd:2.3.1`の依存を追加 |
| `AndroidManifest.xml` | Serviceの登録と`FOREGROUND_SERVICE_SPECIAL_USE`パーミッションを追加 |

## 使い方

### 1. APKをビルド

```bash
# Java 21とAndroid SDKが必要です
export JAVA_HOME=/path/to/openjdk-21
cd Android/src
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew :app:assembleDebug
```

### 2. APKのインストールとモデルの転送

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Gemma 4 E2B (2.4GB, RAM 8GBの端末で動作) をダウンロードして転送
curl -L "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm" \
  -o gemma-4-E2B-it.litertlm
adb push gemma-4-E2B-it.litertlm /data/local/tmp/
```

### 3. サーバーを起動

```bash
adb shell am start-foreground-service \
  -n com.google.aiedge.gallery/com.google.ai.edge.gallery.server.LlmServerService \
  --es model_path '/data/local/tmp/gemma-4-E2B-it.litertlm' \
  --ei port 8080
```

### 4. 使う

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
