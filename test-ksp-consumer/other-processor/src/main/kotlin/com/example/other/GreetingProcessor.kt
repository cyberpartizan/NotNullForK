package com.example.other

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated

/** Generates `com.example.other.GREETING`, which the consumer's code uses. */
class GreetingProcessor(private val codeGenerator: CodeGenerator) : SymbolProcessor {
    private var generated = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (!generated) {
            generated = true
            codeGenerator.createNewFile(Dependencies(aggregating = false), "com.example.other", "Greeting")
                .bufferedWriter()
                .use { it.write("package com.example.other\n\nconst val GREETING = \"hello from another processor\"\n") }
        }
        return emptyList()
    }
}

class GreetingProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment) = GreetingProcessor(environment.codeGenerator)
}
