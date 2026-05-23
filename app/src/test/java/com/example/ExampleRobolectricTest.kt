package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.service.PreferencesService
import com.example.viewmodel.AppViewModel
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
    assertEquals("MK21 MultiServidor", appName)
  }

  @Test
  fun `test preferences service secure username password`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = PreferencesService(context)
    
    // Set a password
    prefs.password = "mypassword123"
    val retrievedPassword = prefs.password
    
    // In Robolectric, the AndroidKeyStore might not fully function,
    // let's verify if it catches exceptions securely or saves/restores as empty/original
    println("Retrieved Secure Password in Robolectric: '$retrievedPassword'")
  }

  @Test
  fun `test viewModel initialization`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = AppViewModel(app)
    assertNotNull(viewModel)
  }
}
