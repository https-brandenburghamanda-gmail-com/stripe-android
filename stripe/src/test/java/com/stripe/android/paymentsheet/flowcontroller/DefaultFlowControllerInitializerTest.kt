package com.stripe.android.paymentsheet.flowcontroller

import com.google.common.truth.Truth.assertThat
import com.stripe.android.ApiKeyFixtures
import com.stripe.android.PaymentSessionPrefs
import com.stripe.android.model.ListPaymentMethodsParams
import com.stripe.android.model.PaymentIntent
import com.stripe.android.model.PaymentIntentFixtures
import com.stripe.android.model.PaymentMethod
import com.stripe.android.model.PaymentMethodFixtures
import com.stripe.android.model.StripeIntent
import com.stripe.android.networking.AbsFakeStripeRepository
import com.stripe.android.networking.ApiRequest
import com.stripe.android.paymentsheet.FakePrefsRepository
import com.stripe.android.paymentsheet.PaymentSheetFixtures
import com.stripe.android.paymentsheet.model.PaymentSelection
import com.stripe.android.paymentsheet.model.SavedSelection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.runBlockingTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
internal class DefaultFlowControllerInitializerTest {
    private val testDispatcher = TestCoroutineDispatcher()

    private val prefsRepository = FakePrefsRepository()
    private val initializer = createInitializer()

    @Test
    fun `init without configuration should return expect result`() = testDispatcher.runBlockingTest {
        assertThat(
            initializer.init(PaymentSheetFixtures.CLIENT_SECRET)
        ).isEqualTo(
            FlowControllerInitializer.InitResult.Success(
                InitData(
                    null,
                    PaymentIntentFixtures.PI_REQUIRES_PAYMENT_METHOD,
                    listOf(PaymentMethod.Type.Card),
                    emptyList(),
                    null
                )
            )
        )
    }

    @Test
    fun `init with configuration should return expect result`() = testDispatcher.runBlockingTest {
        prefsRepository.savePaymentSelection(
            PaymentSelection.Saved(PaymentMethodFixtures.CARD_PAYMENT_METHOD)
        )

        assertThat(
            initializer.init(
                PaymentSheetFixtures.CLIENT_SECRET,
                PaymentSheetFixtures.CONFIG_CUSTOMER_WITH_GOOGLEPAY
            )
        ).isEqualTo(
            FlowControllerInitializer.InitResult.Success(
                InitData(
                    PaymentSheetFixtures.CONFIG_CUSTOMER_WITH_GOOGLEPAY,
                    PaymentIntentFixtures.PI_REQUIRES_PAYMENT_METHOD,
                    listOf(PaymentMethod.Type.Card),
                    PAYMENT_METHODS,
                    SavedSelection.PaymentMethod(
                        id = "pm_123456789"
                    )
                )
            )
        )
    }

    @Test
    fun `init() with customer should set first payment method as saved selection if saved selection is null`() = testDispatcher.runBlockingTest {
        prefsRepository.savePaymentSelection(null)

        val initializer = createInitializer()
        assertThat(
            initializer.init(
                PaymentSheetFixtures.CLIENT_SECRET,
                PaymentSheetFixtures.CONFIG_CUSTOMER_WITH_GOOGLEPAY
            )
        ).isEqualTo(
            FlowControllerInitializer.InitResult.Success(
                InitData(
                    PaymentSheetFixtures.CONFIG_CUSTOMER_WITH_GOOGLEPAY,
                    PaymentIntentFixtures.PI_REQUIRES_PAYMENT_METHOD,
                    listOf(PaymentMethod.Type.Card),
                    PAYMENT_METHODS,
                    SavedSelection.PaymentMethod("pm_123456789")
                )
            )
        )

        assertThat(
            prefsRepository.getSavedSelection()
        ).isEqualTo(
            SavedSelection.PaymentMethod(
                id = "pm_123456789"
            )
        )
    }

    @Test
    fun `init() when PaymentIntent has invalid status should return null`() = testDispatcher.runBlockingTest {
        val result = createInitializer(
            PaymentIntentFixtures.PI_REQUIRES_PAYMENT_METHOD.copy(
                status = StripeIntent.Status.Succeeded
            )
        ).init(
            PaymentSheetFixtures.CLIENT_SECRET,
            PaymentSheetFixtures.CONFIG_CUSTOMER_WITH_GOOGLEPAY
        )
        assertThat(result)
            .isInstanceOf(FlowControllerInitializer.InitResult::class.java)
    }

    @Test
    fun `init() when PaymentIntent has invalid confirmationMethod should return null`() = testDispatcher.runBlockingTest {
        val result = createInitializer(
            PaymentIntentFixtures.PI_REQUIRES_PAYMENT_METHOD.copy(
                confirmationMethod = PaymentIntent.ConfirmationMethod.Manual
            )
        ).init(
            PaymentSheetFixtures.CLIENT_SECRET,
            PaymentSheetFixtures.CONFIG_CUSTOMER_WITH_GOOGLEPAY
        )
        assertThat(result)
            .isInstanceOf(FlowControllerInitializer.InitResult::class.java)
    }

    private fun createInitializer(
        paymentIntent: PaymentIntent = PaymentIntentFixtures.PI_REQUIRES_PAYMENT_METHOD
    ): FlowControllerInitializer {
        return DefaultFlowControllerInitializer(
            FakeStripeRepository(paymentIntent),
            { _, _ -> prefsRepository },
            ApiKeyFixtures.FAKE_PUBLISHABLE_KEY,
            null,
            testDispatcher
        )
    }

    private class FakeStripeRepository(
        private val paymentIntent: PaymentIntent
    ) : AbsFakeStripeRepository() {
        override suspend fun retrievePaymentIntent(
            clientSecret: String,
            options: ApiRequest.Options,
            expandFields: List<String>
        ) = paymentIntent

        override suspend fun getPaymentMethods(
            listPaymentMethodsParams: ListPaymentMethodsParams,
            publishableKey: String,
            productUsageTokens: Set<String>,
            requestOptions: ApiRequest.Options
        ): List<PaymentMethod> {
            return PAYMENT_METHODS
        }
    }

    private class FakePaymentSessionPrefs : PaymentSessionPrefs {
        var paymentMethodId: String? = null
        val savedPaymentMethodIds = mutableListOf<Pair<String, String?>>()

        override fun getPaymentMethodId(
            customerId: String?
        ): String? = paymentMethodId

        override fun savePaymentMethodId(
            customerId: String,
            paymentMethodId: String?
        ) {
            savedPaymentMethodIds.add(customerId to paymentMethodId)
        }
    }

    private companion object {
        private val PAYMENT_METHODS = listOf(PaymentMethodFixtures.CARD_PAYMENT_METHOD) + PaymentMethodFixtures.createCards(5)
    }
}
