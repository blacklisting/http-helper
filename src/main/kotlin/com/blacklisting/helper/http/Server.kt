package com.blacklisting.helper.http

import com.blacklisting.lib.BlacklistIO
import com.blacklisting.lib.Cell
import com.blacklisting.lib.Domain
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.time.LocalDateTime

object Server
{
    @JvmStatic
    fun main(args: Array<String>)
    {
        HttpServer.create(InetSocketAddress("BL".chars().reduce { left, right -> left * 0x100 + right }.asInt), 0).apply {
            println(this.address.port)
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
                                        Cell(rowDef, query[rowDef.fieldName] ?: "")
                                    }.toMutableList()
                                )
                            }
                    )
                }
                it.sendResponseHeaders(200, 0)
                it.responseBody.write("${LocalDateTime.now()} Write OK\n".encodeToByteArray())
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
                it.close()
            }
            createContext("/list") {
                val url = it.requestURI
                val query = url.path.split("/").getOrElse(2) { "domains" }
                when (query)
                {
                    "domains" ->
                    {
                        it.sendResponseHeaders(200, 0)
                        Domain.values().map { domain ->
                            it.responseBody.write((domain.toString() + "\n").encodeToByteArray())
                        }
                        it.close()
                    }
                    in Domain.values().map(Domain::name) ->
                    {
                        it.sendResponseHeaders(200, 0)
                        Domain.valueOf(query).rowDefs.map { rowDef ->
                            it.responseBody.write((rowDef.fieldName + "\n").encodeToByteArray())
                        }
                        it.close()
                    }
                }
            }
            start()
        }
    }
}