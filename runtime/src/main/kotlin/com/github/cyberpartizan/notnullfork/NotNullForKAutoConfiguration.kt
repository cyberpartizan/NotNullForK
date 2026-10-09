package com.github.cyberpartizan.notnullfork

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.logging.LogFactory
import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.BeanFactoryAware
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Import
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.validation.SmartValidator
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean
import org.springframework.validation.beanvalidation.SpringValidatorAdapter
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter
import jakarta.validation.Validator as JakartaValidator

/** Puts the Mirror-aware `@RequestBody` resolver in front of Spring MVC's own. */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(RequestMappingHandlerAdapter::class, ObjectMapper::class)
@Import(MirrorResolverInstaller::class)
class NotNullForKAutoConfiguration

internal class MirrorResolverInstaller : BeanPostProcessor, BeanFactoryAware {

    private lateinit var beanFactory: BeanFactory

    override fun setBeanFactory(beanFactory: BeanFactory) {
        this.beanFactory = beanFactory
    }

    override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
        if (bean !is RequestMappingHandlerAdapter) return bean

        // The same Jackson converter, and so the same ObjectMapper, that Spring MVC uses for request bodies.
        val converter = bean.messageConverters.filterIsInstance<MappingJackson2HttpMessageConverter>().firstOrNull()
        if (converter == null) {
            log.warn("NotNullForK is disabled for '$beanName': it has no Jackson message converter")
            return bean
        }
        bean.argumentResolvers = listOf(MirrorRequestBodyArgumentResolver(converter, requiredFieldValidator())) +
            bean.argumentResolvers.orEmpty()
        return bean
    }

    // Always Bean Validation, even if the app's MVC validator is something else, so the Mirror's
    // must-not-be-null constraints are always checked.
    private fun requiredFieldValidator(): SmartValidator =
        when (val validator = beanFactory.getBeanProvider(JakartaValidator::class.java).getIfUnique()) {
            is SmartValidator -> validator
            null -> LocalValidatorFactoryBean().apply { afterPropertiesSet() }
            else -> SpringValidatorAdapter(validator)
        }

    private companion object {
        val log = LogFactory.getLog(MirrorResolverInstaller::class.java)
    }
}
