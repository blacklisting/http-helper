package com.blacklisting.helper.http

import com.blacklisting.lib.BlacklistIO
import com.blacklisting.lib.Cell
import com.blacklisting.lib.Domain
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.time.LocalDateTime

object Server
{
    @JvmStatic
    fun main(args: Array<String>)
    {
        HttpServer.create(InetSocketAddress("BL".chars().reduce { left, right -> left * 0x100 + right }.asInt), 0).apply {
            println(this.address.port)
            createContext("/page") {
                println("${LocalDateTime.now()} /page ${it.remoteAddress}")
                it.sendResponseHeaders(200, 0)
                Thread.currentThread().contextClassLoader.resources("web.html").findFirst().get().openStream().transferTo(it.responseBody)
                it.close()
            }
            createContext("/add") {
                val url = it.requestURI
                val queryDomain = url.path.split("/")[2]
                val key = url.path.split("/")[3]
                val query = if (it.requestMethod == "GET")
                {
                    url.query.split("&")
                        .associate { queryString ->
                            with(queryString.split("=")) {
                                this[0] to this[1]
                            }
                        }
                }
                else
                {
                    it.requestBody.readBytes().toString(Charsets.UTF_8)
                        .split("&")
                        .associate { param ->
                            with(param.split("=")) {
                                this[0] to this[1]
                            }
                        }
                }
                val domain = Domain.valueOf(queryDomain)
                println("${LocalDateTime.now()} $domain $key $query")
                BlacklistIO(domain).apply blacklistIO@ {
                    write(
                        File("${domain.folderName}/$key.csv"),
                        read("${domain.folderName}/$key.csv")
                            .toMutableList()
                            .apply read@ {
                                add(
                                    this@blacklistIO.domain.rowDefs.map { rowDef ->
                                        Cell(rowDef, URLDecoder.decode(query[rowDef.fieldName], Charsets.UTF_8).replace("\n", "\\n") ?: "")
                                    }.toMutableList()
                                )
                            }
                            .groupBy { cols -> cols[domain.sortIndex] }
                            .map { cols -> cols.value[0] }
                    )
                }
                it.responseHeaders.add("Content-Type", "text/plain")
                it.sendResponseHeaders(200, 0)
                it.responseBody.write("${LocalDateTime.now()} Write OK\n".encodeToByteArray())
                gitAdd(key, domain, it)
                gitCommit(domain, it)
                gitPush(domain, it)
                it.close()
            }
            createContext("/list") {
                val url = it.requestURI
                val query = url.path.split("/")
                when (query.getOrElse(2) { "domains" })
                {
                    "domains" ->
                    {
                        it.responseHeaders.add("Content-Type", "text/plain")
                        it.sendResponseHeaders(200, 0)
                        Domain.values().map { domain ->
                            it.responseBody.write((domain.toString() + "\n").encodeToByteArray())
                        }
                        it.close()
                    }
                    in Domain.values().map(Domain::name) ->
                    {
                        when (query.getOrElse(3) { "fields" })
                        {
                            "fields" ->
                            {
                                it.responseHeaders.add("Content-Type", "text/plain")
                                it.sendResponseHeaders(200, 0)
                                Domain.valueOf(query[2]).rowDefs.map { rowDef ->
                                    it.responseBody.write((rowDef.fieldName + ": " + rowDef.representName + "\n").encodeToByteArray())
                                }
                                it.close()
                            }
                            "blacklists" ->
                            {
                                it.responseHeaders.add("Content-Type", "text/plain")
                                it.sendResponseHeaders(200, 0)
                                File("./${Domain.valueOf(query[2]).folderName}/").listFiles()?.filterNot { file ->
                                    file.nameWithoutExtension.isBlank()
                                }?.map { file ->
                                    it.responseBody.write((file.nameWithoutExtension + "\n").toByteArray(Charsets.UTF_8))
                                }
                                it.close()
                            }
                        }
                    }
                }
            }
            start()
        }
    }

    private fun gitAdd(key: String, domain: Domain, it: HttpExchange)
    {
        Runtime.getRuntime().exec(arrayOf("git", "add", "$key.csv"), emptyArray(), File(domain.folderName)).apply {
            waitFor()
            if (exitValue() == 0)
            {
                it.responseBody.write("${LocalDateTime.now()} Git add OK\n".encodeToByteArray())
            }
            else
            {
                it.responseBody.write("${LocalDateTime.now()} Git add error\n".encodeToByteArray())
                it.responseBody.write(errorStream.readBytes())
                it.responseBody.write("\n".encodeToByteArray())
                it.responseBody.write(inputStream.readBytes())
                it.responseBody.write("\n".encodeToByteArray())
                it.close()
            }
        }
    }

    private fun gitCommit(domain: Domain, it: HttpExchange)
    {
        Runtime.getRuntime().exec(arrayOf("git", "commit", "--no-gpg-sign", "-m", "Update with a comment just seen."), emptyArray(), File(domain.folderName)).apply {
            waitFor()
            if (exitValue() == 0)
            {
                it.responseBody.write("${LocalDateTime.now()} Git commit OK\n".encodeToByteArray())
            }
            else
            {
                it.responseBody.write("${LocalDateTime.now()} Git commit error\n".encodeToByteArray())
                it.responseBody.write(errorStream.readBytes())
                it.responseBody.write("\n".encodeToByteArray())
                it.responseBody.write(inputStream.readBytes())
                it.responseBody.write("\n".encodeToByteArray())
                it.close()
            }
        }
    }

    private fun gitPush(domain: Domain, it: HttpExchange)
    {
        Runtime.getRuntime().exec(arrayOf("git", "push"), emptyArray(), File(domain.folderName)).apply {
            waitFor()
            if (exitValue() == 0)
            {
                it.responseBody.write("${LocalDateTime.now()} Git push OK\n".encodeToByteArray())
            }
            else
            {
                it.responseBody.write("${LocalDateTime.now()} Git push error\n".encodeToByteArray())
                it.responseBody.write(errorStream.readBytes())
                it.responseBody.write("\n".encodeToByteArray())
                it.responseBody.write(inputStream.readBytes())
                it.responseBody.write("\n".encodeToByteArray())
                it.close()
            }
        }
    }
}
