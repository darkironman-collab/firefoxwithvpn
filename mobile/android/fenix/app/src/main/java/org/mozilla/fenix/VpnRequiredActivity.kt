/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Small offline-only screen shown before any Firefox component is initialized.
 */
class VpnRequiredActivity : AppCompatActivity() {
    private lateinit var status: TextView

    private val vpnListener: (Boolean) -> Unit = { connected ->
        runOnUiThread {
            status.text = if (connected) {
                "VPN connected. Firefox is ready."
            } else {
                "No validated VPN connection. Firefox internet access is locked."
            }
            if (connected) {
                continueToFirefox()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "VPN required"

        status = TextView(this).apply {
            text = "No validated VPN connection. Firefox internet access is locked."
            textSize = 18f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
        }
        val openSettings = Button(this).apply {
            text = "Open VPN settings"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
            }
        }
        val retry = Button(this).apply {
            text = "Check again"
            setOnClickListener { continueToFirefox() }
        }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(48, 48, 48, 48)
                addView(status, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                    bottomMargin = 32
                })
                addView(openSettings, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
                addView(retry, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            },
        )
    }

    override fun onStart() {
        super.onStart()
        VpnNetworkGate.addListener(vpnListener)
        vpnListener(VpnNetworkGate.isValidatedVpn(this))
    }

    override fun onStop() {
        VpnNetworkGate.removeListener(vpnListener)
        super.onStop()
    }

    private fun continueToFirefox() {
        val application = application as FenixApplication
        if (!application.initializeFenixAfterVpn()) {
            status.text = "VPN is not connected or has no working internet."
            return
        }
        startActivity(
            Intent(this, HomeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            },
        )
        finish()
    }
}
