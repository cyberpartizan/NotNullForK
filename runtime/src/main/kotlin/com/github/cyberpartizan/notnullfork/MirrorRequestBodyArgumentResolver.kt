package com.github.cyberpartizan.notnullfork

import com.fasterxml.jackson.core.JsonProcessingException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.Conventions
import org.springframework.core.MethodParameter
import org.springframework.http.HttpInputMessage
import org.springframework.http.MediaType
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.BindingResult
import org.springframework.validation.Errors
import org.springframework.validation.Validator
import org.springframework.validation.annotation.ValidationAnnotationUtils
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import java.io.IOException
import java.io.InputStreamReader
import java.io.PushbackInputStream

/**
 * Resolves a `@RequestBody` parameter whose Request Body has a Mirror: reads the JSON into the Mirror
 * with the ObjectMapper of the app's Jackson [converter], checks the Required Fields with
 * [requiredFieldValidator] (whether or not the parameter has `@Valid`), and maps the Mirror to the
 * Request Body. A Request Body with `@Valid` or `@Validated` is then validated as Spring would.
 *
 * Unlike Spring's own resolver, `RequestBodyAdvice` (and so `@JsonView`) is not applied.
 */
internal class MirrorRequestBodyArgumentResolver(
    private val converter: MappingJackson2HttpMessageConverter,
    private val requiredFieldValidator: Validator,
) : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(RequestBody::class.java) &&
            Mirrors.mirrorClassFor(parameter.nestedParameterType) != null

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Any? {
        val mirrorClass = Mirrors.mirrorClassFor(parameter.nestedParameterType)!!
        val inputMessage = ServletServerHttpRequest(webRequest.getNativeRequest(HttpServletRequest::class.java)!!)
        val mirror = read(inputMessage, mirrorClass)
        if (mirror == null) checkBodyNotRequired(parameter, inputMessage)
        val name = Conventions.getVariableNameForParameter(parameter)

        val requestBody = mirror?.let {
            checkRequiredFields(it, name, parameter)
            toRequestBody(it, parameter, inputMessage)
        }
        bindAndValidate(requestBody, name, parameter, mavContainer, webRequest, binderFactory)
        return requestBody
    }

    /**
     * Reads the body into a Mirror, or returns `null` if the body is empty. As in Spring, an empty body
     * without a content type is not an unsupported media type.
     */
    private fun read(inputMessage: HttpInputMessage, mirrorClass: Class<out Mirror<*>>): Mirror<*>? {
        val contentType = inputMessage.headers.contentType
        return try {
            val body = PushbackInputStream(inputMessage.body)
            val first = body.read()
            when {
                converter.canRead(mirrorClass, contentType ?: MediaType.APPLICATION_OCTET_STREAM) ->
                    if (first == -1) {
                        null
                    } else {
                        body.unread(first)
                        // As in Spring: Jackson detects the Unicode encodings itself, others need a reader.
                        val charset = contentType?.charset
                        if (charset == null || charset.name().startsWith("UTF-")) {
                            converter.objectMapper.readValue(body, mirrorClass)
                        } else {
                            converter.objectMapper.readValue(InputStreamReader(body, charset), mirrorClass)
                        }
                    }
                contentType == null && first == -1 -> null
                else -> throw HttpMediaTypeNotSupportedException(
                    contentType ?: MediaType.APPLICATION_OCTET_STREAM,
                    converter.supportedMediaTypes,
                )
            }
        } catch (e: JsonProcessingException) {
            throw HttpMessageNotReadableException("JSON parse error: ${e.originalMessage}", e, inputMessage)
        } catch (e: IOException) {
            throw HttpMessageNotReadableException("I/O error while reading input message", e, inputMessage)
        }
    }

    // As in Spring, a nullable parameter is optional even with `required = true`.
    private fun checkBodyNotRequired(parameter: MethodParameter, inputMessage: HttpInputMessage) {
        if (parameter.getParameterAnnotation(RequestBody::class.java)!!.required && !parameter.isOptional) {
            throw HttpMessageNotReadableException(
                "Required request body is missing: ${parameter.executable.toGenericString()}",
                inputMessage,
            )
        }
    }

    // Jackson reports an exception of the Request Body's constructor (e.g. a `require` in an `init` block)
    // as a value instantiation problem, which Spring answers with a 400; so does the Mirror.
    private fun toRequestBody(mirror: Mirror<*>, parameter: MethodParameter, inputMessage: HttpInputMessage): Any =
        try {
            mirror.toRequestBody()
        } catch (e: Exception) {
            throw HttpMessageNotReadableException(
                "JSON parse error: Cannot construct instance of `${parameter.nestedParameterType.name}`, problem: ${e.message}",
                e,
                inputMessage,
            )
        }

    private fun checkRequiredFields(mirror: Mirror<*>, name: String, parameter: MethodParameter) {
        val result = BeanPropertyBindingResult(mirror, name)
        requiredFieldValidator.validate(mirror, result)
        if (result.hasErrors()) throw MethodArgumentNotValidException(parameter, result)
    }

    // Same as Spring's RequestResponseBodyMethodProcessor: the binding result is also added for a missing body,
    // and the validation is AbstractMessageConverterMethodArgumentResolver.validateIfApplicable.
    private fun bindAndValidate(
        requestBody: Any?,
        name: String,
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ) {
        if (binderFactory == null) return
        val binder = binderFactory.createBinder(webRequest, requestBody, name)
        val hints = parameter.parameterAnnotations.firstNotNullOfOrNull {
            ValidationAnnotationUtils.determineValidationHints(it)
        }
        if (requestBody != null && hints != null) {
            binder.validate(*hints)
            if (binder.bindingResult.hasErrors() && isBindExceptionRequired(parameter)) {
                throw MethodArgumentNotValidException(parameter, binder.bindingResult)
            }
        }
        mavContainer?.addAttribute(BindingResult.MODEL_KEY_PREFIX + name, binder.bindingResult)
    }

    private fun isBindExceptionRequired(parameter: MethodParameter): Boolean {
        val parameterTypes = parameter.executable.parameterTypes
        val next = parameter.parameterIndex + 1
        return next >= parameterTypes.size || !Errors::class.java.isAssignableFrom(parameterTypes[next])
    }
}
