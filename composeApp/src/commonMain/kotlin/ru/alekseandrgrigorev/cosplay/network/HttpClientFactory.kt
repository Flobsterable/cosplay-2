package ru.alekseandrgrigorev.cosplay.network

import io.ktor.client.HttpClient

expect fun createHttpClient(): HttpClient
