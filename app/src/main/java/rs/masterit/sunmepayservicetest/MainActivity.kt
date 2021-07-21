package rs.masterit.sunmepayservicetest

import android.graphics.Typeface
import android.os.Bundle
import android.os.RemoteException
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sunmi.pay.hardware.aidl.AidlConstants
import com.sunmi.pay.hardware.aidlv2.AidlErrorCodeV2
import com.sunmi.pay.hardware.aidlv2.bean.ApduRecvV2
import com.sunmi.pay.hardware.aidlv2.bean.ApduSendV2
import com.sunmi.pay.hardware.aidlv2.readcard.CheckCardCallbackV2
import com.sunmi.pay.hardware.aidlv2.readcard.ReadCardOptV2
import rs.masterit.sunmepayservicetest.utils.ByteUtil
import sunmi.paylib.SunmiPayKernel
import sunmi.paylib.SunmiPayKernel.ConnectCallback
import java.util.*


class MainActivity : AppCompatActivity() {

    private lateinit var mSMPayKernel: SunmiPayKernel
    private var mReadCardOptV2 : ReadCardOptV2? = null
    private var isDisConnectService = true
    private val cardType = AidlConstants.CardType.PSAM0.value

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        connectPayService()

        findViewById<Button>(R.id.btnSetApplet).setOnClickListener {
            val cb = findViewById<CheckBox>(R.id.cbUseApduTransmit)

            val res = if (cb.isChecked) sendApduTransmit("00A40400", "10", "A000000748464A492D546178436F7265", "00")
                        else sendApduCommand("00A40400", "10", "A000000748464A492D546178436F7265", "00")
            findViewById<TextView>(R.id.tvAppletRes).text = res
        }

        findViewById<Button>(R.id.btnVerifyPin).setOnClickListener {
            val cb = findViewById<CheckBox>(R.id.cbUseApduTransmit)

            val res = if (cb.isChecked) sendApduTransmit("88110400", "04", "02000006", "00")
                        else sendApduCommand("88110400", "04", "02000006", "00")
            findViewById<TextView>(R.id.tvVerifyPinRes).text = res
        }

        findViewById<Button>(R.id.btnExtractCert).setOnClickListener {
            val cb = findViewById<CheckBox>(R.id.cbUseApduTransmit)

            val res = if (cb.isChecked)sendApduTransmit("88040400", "00", "0000", "00")
                        else sendApduCommand("88040400", "00", "0000", "00")

            //cla   ins     p1      p2      lc      data    le
            //88    04      04      00      000000          00FFFF
            findViewById<TextView>(R.id.tvExportCertRes).text = res
        }
    }

    private val mConnectCallback: ConnectCallback = object : ConnectCallback {
        override fun onConnectPaySDK() {
            try {
                mReadCardOptV2 = mSMPayKernel.mReadCardOptV2
                isDisConnectService = false

                checkCard()
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }

        override fun onDisconnectPaySDK() {
            isDisConnectService = true
            Toast.makeText(this@MainActivity, "fail to connect to PAY service", Toast.LENGTH_SHORT).show()
        }
    }


    private fun connectPayService() {
        mSMPayKernel = SunmiPayKernel.getInstance()
        mSMPayKernel.initPaySDK(this, mConnectCallback)
    }

    override fun onDestroy() {
        super.onDestroy()
        mSMPayKernel.destroyPaySDK()
    }

    private fun sendApduTransmit(command: String, lc: String, indata: String, le: String): String {
        return try {
            val recv = ByteArray(260)

            val len: Int = mReadCardOptV2!!.transmitApdu(cardType, ByteUtil.hexStr2Bytes("$command$lc$indata$le"), recv)

            if (len < 0) {
                Toast.makeText(this@MainActivity, "SEND fail with error: ${AidlErrorCodeV2.valueOf(len).msg}", Toast.LENGTH_LONG).show()
                "ERROR"
            } else {
                val valid = recv.copyOf(len)

                val outData: ByteArray = valid.copyOf(valid.size - 2)
                val swa: Byte = valid[valid.size - 2] //swa
                val swb: Byte = valid[valid.size - 1] //swb
                showApduRecv(len, outData, swa, swb)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            e.message ?: ""
        }
    }

    private fun sendApduCommand(command: String, lc: String, indata: String, le: String): String {
        val send = ApduSendV2()
        send.command = ByteUtil.hexStr2Bytes(command)
        send.lc = lc.toShort(16)
        send.dataIn = ByteUtil.hexStr2Bytes(indata)
        send.le = le.toShort(16)

        return try {
            val recv = ApduRecvV2()
            val code: Int = mReadCardOptV2!!.apduCommand(cardType, send, recv)

            if (code < 0) {
                Toast.makeText(this@MainActivity, "SEND fail with code: $code", Toast.LENGTH_LONG).show()
                "ERROR"
            } else {
                showApduRecv(recv.outlen.toInt(), recv.outData, recv.swa, recv.swb)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            e.message ?: ""
        }
    }

    private fun showApduRecv(outLen: Int, outData: ByteArray, swa: Byte, swb: Byte): String {
        val swaStr = ByteUtil.bytes2HexStr(swa)
        val swbStr = ByteUtil.bytes2HexStr(swb)
        val tmp1 = outData.copyOf(outLen)
        val outDataStr = tmp1.toHex()
        return String.format("SWA:%s\nSWB:%s\noutData:%s", swaStr, swbStr, outDataStr)
    }


    private fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

    private fun byteArrayOfInts(vararg ints: Int) = ByteArray(ints.size) { pos -> ints[pos].toByte() }

    fun String.decodeHex(): ByteArray {
        require(length % 2 == 0) { "Must have an even length" }

        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    private fun checkCard() {
        try {
            mReadCardOptV2!!.checkCard(cardType, mCheckCardCallback, 20)
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    private val mCheckCardCallback: CheckCardCallbackV2 = object : CheckCardCallbackV2Wrapper() {
        @Throws(RemoteException::class)
        override fun findMagCard(bundle: Bundle?) {
            //LogUtil.e(Constant.TAG, "findMagCard:track1")
        }

        @Throws(RemoteException::class)
        override fun findICCard(atr: String) {
            //LogUtil.e(Constant.TAG, "findICCard:$atr")
        }

        @Throws(RemoteException::class)
        override fun findRFCard(uuid: String) {
            //LogUtil.e(Constant.TAG, "findRFCard:$uuid")
        }

        @Throws(RemoteException::class)
        override fun onError(code: Int, message: String) {
            val error = "CheckCard error,code:$code,msg:$message"
            Toast.makeText(this@MainActivity, error, Toast.LENGTH_LONG).show()
        }
    }

}