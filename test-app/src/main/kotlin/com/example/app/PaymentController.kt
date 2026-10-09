package com.example.app

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.Module
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

/** Money, read and written by the app's own Jackson module as a string such as `"12.34"`. */
data class Money(val cents: Long)

/** A Request Body with a property that only the app's own Jackson module can read. */
data class PaymentRequest(val total: Money, val cardHolder: String)

@RestController
class PaymentController {
    @PostMapping("/payments")
    fun pay(@RequestBody request: PaymentRequest): PaymentRequest = request
}

/** Registers the Money module on the app's ObjectMapper. */
@Configuration(proxyBeanMethods = false)
class MoneyConfiguration {
    @Bean
    fun moneyModule(): Module = SimpleModule("money")
        .addDeserializer(Money::class.java, MoneyDeserializer())
        .addSerializer(Money::class.java, MoneySerializer())
}

class MoneyDeserializer : JsonDeserializer<Money>() {
    override fun deserialize(parser: JsonParser, context: DeserializationContext) =
        Money(BigDecimal(parser.valueAsString).movePointRight(2).longValueExact())
}

class MoneySerializer : JsonSerializer<Money>() {
    override fun serialize(value: Money, generator: JsonGenerator, provider: SerializerProvider) =
        generator.writeString(BigDecimal.valueOf(value.cents, 2).toPlainString())
}
