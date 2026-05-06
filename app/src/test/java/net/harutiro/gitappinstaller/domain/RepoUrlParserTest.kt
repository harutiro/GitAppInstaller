package net.harutiro.gitappinstaller.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RepoUrlParserTest {

    @Test
    fun parses_basic_https_url() {
        val result = RepoUrlParser.parse("https://github.com/owner/repo")
        assertNotNull(result)
        assertEquals(GitHost.GITHUB, result!!.host)
        assertEquals("owner", result.owner)
        assertEquals("repo", result.repo)
    }

    @Test
    fun strips_dot_git_suffix() {
        val result = RepoUrlParser.parse("https://github.com/owner/repo.git")
        assertNotNull(result)
        assertEquals("owner", result!!.owner)
        assertEquals("repo", result.repo)
    }

    @Test
    fun parses_url_with_extra_path() {
        val result = RepoUrlParser.parse("https://github.com/owner/repo/releases")
        assertNotNull(result)
        assertEquals("owner", result!!.owner)
        assertEquals("repo", result.repo)
    }

    @Test
    fun parses_ssh_form() {
        val result = RepoUrlParser.parse("git@github.com:owner/repo.git")
        assertNotNull(result)
        assertEquals(GitHost.GITHUB, result!!.host)
        assertEquals("owner", result.owner)
        assertEquals("repo", result.repo)
    }

    @Test
    fun returns_null_for_non_github_host() {
        val result = RepoUrlParser.parse("https://gitlab.com/owner/repo")
        assertNull(result)
    }

    @Test
    fun returns_null_for_random_text() {
        val result = RepoUrlParser.parse("random text")
        assertNull(result)
    }

    @Test
    fun returns_null_for_empty_string() {
        val result = RepoUrlParser.parse("")
        assertNull(result)
    }
}
