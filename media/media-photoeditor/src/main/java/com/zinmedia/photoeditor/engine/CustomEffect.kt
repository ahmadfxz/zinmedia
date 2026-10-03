package com.zinmedia.photoeditor.engine

import android.text.TextUtils
import java.util.*

/**
 * Define your custom effect using [Builder] class
 *
 * @author [Burhanuddin Rashid](https://github.com/burhanrashid52)
 * @version 0.1.2
 * @since 5/22/2018
 */
internal class CustomEffect private constructor(builder: Builder) {
    /**
     * @return Custom effect name from [android.media.effect.EffectFactory.createEffect]
     */
    internal val effectName: String = builder.mEffectName

    /**
     * @return map of key and value of parameters for [android.media.effect.Effect.setParameter]
     */
    internal val parameters: Map<String, Any>

    /**
     * Set customize effect to image using this builder class
     */
    internal class Builder(effectName: String) {
        internal val mEffectName: String
        internal val parametersMap: MutableMap<String, Any> = HashMap()

        /**
         * set parameter to the attributes with its value
         *
         * @param paramKey   attribute key for [android.media.effect.Effect.setParameter]
         * @param paramValue value for [android.media.effect.Effect.setParameter]
         * @return builder instance to setup multiple parameters
         */
        internal fun setParameter(paramKey: String, paramValue: Any): Builder {
            parametersMap[paramKey] = paramValue
            return this
        }

        /**
         * @return instance for custom effect
         */
        internal fun build(): CustomEffect {
            return CustomEffect(this)
        }

        /**
         * Initiate your custom effect
         *
         * @param effectName custom effect name from [android.media.effect.EffectFactory.createEffect]
         * @throws RuntimeException exception when effect name is empty
         */
        init {
            if (TextUtils.isEmpty(effectName)) {
                throw RuntimeException("Effect name cannot be empty.Please provide effect name from EffectFactory")
            }
            mEffectName = effectName
        }
    }
    
    init {
        parameters = builder.parametersMap
    }
}