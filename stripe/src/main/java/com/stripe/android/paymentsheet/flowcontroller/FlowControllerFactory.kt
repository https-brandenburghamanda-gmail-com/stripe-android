package com.stripe.android.paymentsheet.flowcontroller

import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.stripe.android.PaymentConfiguration
import com.stripe.android.StripePaymentController
import com.stripe.android.networking.StripeApiRepository
import com.stripe.android.payments.DefaultPaymentFlowResultProcessor
import com.stripe.android.paymentsheet.DefaultGooglePayRepository
import com.stripe.android.paymentsheet.DefaultPrefsRepository
import com.stripe.android.paymentsheet.GooglePayRepository
import com.stripe.android.paymentsheet.PaymentOptionCallback
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResultCallback
import com.stripe.android.paymentsheet.analytics.DefaultEventReporter
import com.stripe.android.paymentsheet.analytics.EventReporter
import com.stripe.android.paymentsheet.analytics.SessionId
import kotlinx.coroutines.Dispatchers

internal class FlowControllerFactory(
    private val activity: ComponentActivity,
    private val config: PaymentConfiguration,
    private val paymentOptionCallback: PaymentOptionCallback,
    private val paymentResultCallback: PaymentSheetResultCallback
) {
    fun create(): PaymentSheet.FlowController {
        val sessionId = SessionId()

        val stripeRepository = StripeApiRepository(
            activity,
            config.publishableKey
        )

        val paymentControllerFactory = PaymentControllerFactory { paymentRelayLauncher, paymentAuthWebViewLauncher, stripe3ds2ChallengeLauncher ->
            StripePaymentController(
                activity,
                config.publishableKey,
                stripeRepository,
                enableLogging = true,
                paymentRelayLauncher = paymentRelayLauncher,
                paymentAuthWebViewLauncher = paymentAuthWebViewLauncher,
                stripe3ds2ChallengeLauncher = stripe3ds2ChallengeLauncher
            )
        }

        val paymentFlowResultProcessor = DefaultPaymentFlowResultProcessor(
            activity,
            config.publishableKey,
            stripeRepository,
            enableLogging = false,
            Dispatchers.IO
        )

        val prefsRepositoryFactory = { customerId: String, environment: PaymentSheet.GooglePayConfiguration.Environment? ->
            val googlePayRepository = environment?.let {
                DefaultGooglePayRepository(
                    activity,
                    it
                )
            } ?: GooglePayRepository.Disabled()

            DefaultPrefsRepository(
                activity,
                customerId,
                googlePayRepository,
                Dispatchers.IO
            )
        }

        return DefaultFlowController(
            activity = activity,
            flowControllerInitializer = DefaultFlowControllerInitializer(
                stripeRepository,
                prefsRepositoryFactory = prefsRepositoryFactory,
                publishableKey = config.publishableKey,
                stripeAccountId = config.stripeAccountId,
                workContext = Dispatchers.IO
            ),
            paymentControllerFactory = paymentControllerFactory,
            paymentFlowResultProcessor = paymentFlowResultProcessor,
            eventReporter = DefaultEventReporter(
                mode = EventReporter.Mode.Custom,
                sessionId,
                activity
            ),
            publishableKey = config.publishableKey,
            stripeAccountId = config.stripeAccountId,
            sessionId = sessionId,
            initScope = activity.lifecycleScope,
            paymentOptionCallback = paymentOptionCallback,
            paymentResultCallback = paymentResultCallback
        )
    }
}
