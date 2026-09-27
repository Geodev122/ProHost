package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.ProHostRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("ProHost", appName)
  }


  @Test
  fun `test financial calculations`() {
    val repo = ProHostRepository()
    assertEquals(1.80, repo.pricingState.value.monthlySubscriptionFeeUsd, 0.01)
    val mrr = repo.calculateActiveMrr()
    assert(mrr >= 0)
  }
}

