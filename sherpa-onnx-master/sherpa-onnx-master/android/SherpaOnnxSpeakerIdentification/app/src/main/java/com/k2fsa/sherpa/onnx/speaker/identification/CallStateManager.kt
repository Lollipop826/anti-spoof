package com.k2fsa.sherpa.onnx.speaker.identification

import android.content.Context
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import androidx.lifecycle.MutableLiveData

class CallStateManager(context: Context) : PhoneStateListener() {
    private val telephonyManager: TelephonyManager
    val isCallActive = MutableLiveData(false)

    init {
        telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        try {
            telephonyManager.listen(this, LISTEN_CALL_STATE)
            Log.i("CallStateManager", "Phone state listener registered")
        } catch (e: SecurityException) {
            Log.e("CallStateManager", "Permission denied for phone state: ${e.message}")
        }
    }

    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
        super.onCallStateChanged(state, phoneNumber)
        Log.d("CallStateManager", "Call state changed: $state")

        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> { // 通话开始
                Log.i("CallStateManager", "Call answered")
                isCallActive.postValue(true)
            }
            TelephonyManager.CALL_STATE_IDLE -> { // 通话结束
                Log.i("CallStateManager", "Call ended")
                isCallActive.postValue(false)
            }
            // 其他状态可以忽略
        }
    }

    fun release() {
        telephonyManager.listen(this, LISTEN_NONE)
    }
}