package net.harutiro.gitappinstaller.domain

data class ParsedRepo(val host: GitHost, val owner: String, val repo: String)

object RepoUrlParser {
    private val GITHUB_HTTPS = Regex("""^https?://(?:www\.)?github\.com/([^/\s]+)/([^/\s?#]+?)(?:\.git)?(?:[/?#].*)?$""", RegexOption.IGNORE_CASE)
    private val GITHUB_SSH = Regex("""^git@github\.com:([^/\s]+)/([^/\s]+?)(?:\.git)?$""", RegexOption.IGNORE_CASE)

    fun parse(input: String): ParsedRepo? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val match = GITHUB_HTTPS.matchEntire(trimmed) ?: GITHUB_SSH.matchEntire(trimmed) ?: return null
        val owner = match.groupValues[1]
        val repo = match.groupValues[2].removeSuffix(".git")
        if (owner.isBlank() || repo.isBlank()) return null
        return ParsedRepo(GitHost.GITHUB, owner, repo)
    }
}
