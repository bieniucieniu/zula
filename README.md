# zula

This project was created using the [Ktor Project Generator](https://start.ktor.io).

Here are some useful links to get you started:

* [Ktor Documentation](https://ktor.io/docs/home.html)
* [Ktor GitHub page](https://github.com/ktorio/ktor)
* [Ktor Slack chat](https://app.slack.com/client/T09229ZC6/C0A974TJ9). [Request an invite](https://surveys.jetbrains.com/s3/kotlin-slack-sign-up).

## Features

Here's a list of features included in this project:

| Name                                                                                  | Description                                                                        |
|---------------------------------------------------------------------------------------|------------------------------------------------------------------------------------|
| [Caching Headers](https://start.ktor.io/p/io.ktor/server-caching-headers)             | Provides options for responding with standard cache-control headers                |
| [Compression](https://start.ktor.io/p/io.ktor/server-compression)                     | Compresses responses using encoding algorithms like GZIP                           |
| [Conditional Headers](https://start.ktor.io/p/io.ktor/server-conditional-headers)     | Skips response body, depending on ETag and LastModified headers                    |
| [Default Headers](https://start.ktor.io/p/io.ktor/server-default-headers)             | Adds a default set of headers to HTTP responses                                    |
| [Forwarded Headers](https://start.ktor.io/p/io.ktor/server-forwarded-header-support)  | Allows handling proxied headers (X-Forwarded-*)                                    |
| [OpenAPI](https://start.ktor.io/p/io.ktor/server-openapi)                             | Serves OpenAPI documentation                                                       |
| [Simple Cache](https://start.ktor.io/p/com.ucasoft/server-simple-cache)               | Provides API for cache management                                                  |
| [Simple Memory Cache](https://start.ktor.io/p/com.ucasoft/server-simple-memory-cache) | Provides memory cache for Simple Cache plugin                                      |
| [Swagger](https://start.ktor.io/p/io.ktor/server-swagger)                             | Serves Swagger UI for your project                                                 |
| [Authentication](https://start.ktor.io/p/io.ktor/server-auth)                         | Provides extension point for handling the Authorization header                     |
| [Authentication JWT](https://start.ktor.io/p/io.ktor/server-auth-jwt)                 | Handles JSON Web Token (JWT) bearer authentication scheme                          |
| [Authentication OAuth](https://start.ktor.io/p/io.ktor/server-auth-oauth)             | Handles OAuth Bearer authentication scheme                                         |
| [Server-Sent Events (SSE)](https://start.ktor.io/p/io.ktor/server-sse)                | Support for server push events                                                     |
| [Static Content](https://start.ktor.io/p/io.ktor/server-static-content)               | Serves static files from defined locations                                         |
| [Status Pages](https://start.ktor.io/p/io.ktor/server-status-pages)                   | Provides exception handling for routes                                             |
| [Content Negotiation](https://start.ktor.io/p/io.ktor/server-content-negotiation)     | Provides automatic content conversion according to Content-Type and Accept headers |
| [kotlinx.serialization](https://start.ktor.io/p/io.ktor/server-kotlinx-serialization) | Handles JSON serialization using kotlinx.serialization library                     |
| [Koin](https://start.ktor.io/p/io.insert-koin/server-koin)                            | Provides dependency injection                                                      |
| [RabbitMQ](https://start.ktor.io/p/io.github.damirdenis-tudor/server-rabbitmq)        | Adds RabbitMQ support to your application                                          |

## Building & Running

To build or run the project, use one of the following tasks:

| Task              | Description       |
|-------------------|-------------------|
| `./gradlew test`  | Run the tests     |
| `./gradlew build` | Build the project |
| `./gradlew run`   | Run the server    |

If the server starts successfully, you'll see the following output:

```
2024-12-04 14:32:45.584 [main] INFO  Application - Application started in 0.303 seconds.
2024-12-04 14:32:45.682 [main] INFO  Application - Responding at http://0.0.0.0:8080
```
