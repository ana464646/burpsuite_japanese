# Burp Japanese Translator

Burp Suite の **診断結果（Scanner Issue）を日本語で読めるようにする**ための拡張です。

## できること

- **Suiteタブ `日本語（診断結果）`** を追加
  - Site map に存在する Issue を一覧表示
  - Issue を選択すると **原文と日本語訳が自動表示**（翻訳は少し遅延して実行）
- Issue 一覧で Issue を選択して右クリック
  - **`選択Issueを日本語表示`**（ダイアログ表示）

## できないこと（重要）

- Burp Suite 本体の UI（標準メニュー、画面ラベル等）を丸ごと日本語化することはできません  
  （Montoya API では既存UIテキストを置換する手段が基本的にありません）

## 必要要件

- **JDK 17 以上**
- Burp Suite（Montoya API 対応版）
- インターネット接続（翻訳に `translate.googleapis.com` を利用）

## ビルド

PowerShell:

```powershell
cd C:\Users\r3c0n\Documents\github\burpsuite_japanese
$env:JAVA_HOME = "C:\Users\r3c0n\Documents\openjdk-25.0.2_windows-x64_bin\jdk-25.0.2"  # 例
.\gradlew.bat shadowJar
```

生成物:

- `build\libs\BurpJapaneseTranslator-1.0-SNAPSHOT.jar`

## Burp への導入

1. Burp Suite → **Extensions**
2. **Add**
3. **Extension type**: `Java`
4. **Extension file** に `build\libs\BurpJapaneseTranslator-1.0-SNAPSHOT.jar` を指定

## 使い方

- `日本語（診断結果）` タブを開く
  - 上部の「更新」で Site map から Issue を再取得
  - 一覧から Issue を選ぶと、下部に原文と日本語訳が出ます
- 右クリックメニュー
  - Issue を選択した状態で右クリック → **選択Issueを日本語表示**

## 注意

- 無料の翻訳エンドポイントを利用しているため、環境によっては **レート制限/ブロック** される場合があります。
- 翻訳品質は機械翻訳のため完全ではありません。重要な判断には原文も併読してください。

