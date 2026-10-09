package com.example.app

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/** A Request Body with lists, sets and maps of non-null and nullable element types. */
data class TagPostRequest(
    val tags: List<String>,
    val scores: Map<String, Int>,
    val labels: Set<String>,
    val notes: List<String?>,
    val ratings: Map<String, Int?>,
    val aliases: MutableList<String>?,
)

/** The Request Body's Kotlin values, and the classes of its collections. */
data class Received(val request: String, val types: Map<String, String?>)

@RestController
class CollectionController {
    @PostMapping("/posts")
    fun post(@RequestBody request: TagPostRequest) = Received(
        request.toString(),
        mapOf(
            "tags" to request.tags.javaClass.simpleName,
            "scores" to request.scores.javaClass.simpleName,
            "labels" to request.labels.javaClass.simpleName,
            "aliases" to request.aliases?.javaClass?.simpleName,
        ),
    )
}

data class LineItem(val name: String, val quantity: Int, val note: String?)

/** A Request Body with lists, sets and maps of a Nested Type. */
data class CartRequest(
    val items: List<LineItem>,
    val gifts: Set<LineItem>?,
    val byCode: Map<String, LineItem>,
    val maybe: List<LineItem?>,
)

@RestController
class CartController {
    @PostMapping("/carts")
    fun post(@RequestBody request: CartRequest) = Received(
        request.toString(),
        mapOf(
            "items" to request.items.javaClass.simpleName,
            "gifts" to request.gifts?.javaClass?.simpleName,
            "byCode" to request.byCode.javaClass.simpleName,
            "maybe" to request.maybe.javaClass.simpleName,
        ),
    )
}

typealias Codes = List<String>

typealias MaybeText = String?

/** A Request Body with collections whose types are written through type aliases. */
data class AliasedCollectionsRequest(val codes: Codes, val texts: List<MaybeText>)

@RestController
class AliasedCollectionController {
    @PostMapping("/aliased-collections")
    fun post(@RequestBody request: AliasedCollectionsRequest): String = request.toString()
}
