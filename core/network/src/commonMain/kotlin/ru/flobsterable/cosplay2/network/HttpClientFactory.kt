package ru.flobsterable.cosplay2.network

import io.ktor.client.HttpClient

expect fun createHttpClient(): HttpClient
