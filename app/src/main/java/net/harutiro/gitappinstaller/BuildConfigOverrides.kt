package net.harutiro.gitappinstaller

/**
 * GitHub OAuth App 設定。
 *
 * Device Flow（"SSOログイン"）を使うには、自分のアカウントで OAuth App を1つ登録し、
 *  - Application name: 何でもOK
 *  - Homepage URL: 何でもOK
 *  - Authorization callback URL: 何でもOK（Device Flow では使われない）
 *  - "Enable Device Flow" にチェック
 * を有効にして、発行された Client ID を [CLIENT_ID] にコピーしてください。
 *
 * Client ID は公開しても問題ありません（Client Secret を持たないため）。
 *
 * ここを書き換えずに Login ボタンを押すと、設定が未構成である旨のエラーが表示されます。
 * その場合は Personal Access Token を直接入力するルートを使ってください。
 */
object GitHubOAuthConfig {
    const val CLIENT_ID = "Ov23li5umK6jSjCOgJAG"

    /** Private リポジトリも読みたいので `repo` スコープをリクエストする。 */
    const val SCOPE = "repo"

    val isConfigured: Boolean get() = true
}
