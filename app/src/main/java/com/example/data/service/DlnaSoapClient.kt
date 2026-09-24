package com.example.data.service

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class DlnaSoapClient {
    companion object {
        private const val TAG = "DlnaSoapClient"
    }

    /**
     * Send direct media URL block to TV via UPnP DLNA SOAP Request
     */
    fun castUrlToDlna(controlUrl: String, streamUrl: String, title: String) {
        Thread {
            try {
                // SOAP Envelope format for SetAVTransportURI
                val soapSetUri = """
                    <?xml version="1.0" encoding="utf-8"?>
                    <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                        <s:Body>
                            <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                                <InstanceID>0</InstanceID>
                                <CurrentURI>$streamUrl</CurrentURI>
                                <CurrentURIMetaData><![CDATA[<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"><item id="0" parentID="-1" restricted="false"><dc:title>$title</dc:title><upnp:class>object.item.videoItem.movie</upnp:class><res protocolInfo="http-get:*:video/mp4:*">$streamUrl</res></item></DIDL-Lite>]]></CurrentURIMetaData>
                            </u:SetAVTransportURI>
                        </s:Body>
                    </s:Envelope>
                """.trimIndent()

                sendSoapAction(controlUrl, "urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI", soapSetUri)

                // Brief pause to allow the renderer buffers to register the new media endpoint
                Thread.sleep(800)

                // Play Action to start streaming the newly set media URI
                val soapPlay = """
                    <?xml version="1.0" encoding="utf-8"?>
                    <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                        <s:Body>
                            <u:Play xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                                <InstanceID>0</InstanceID>
                                <Speed>1</Speed>
                            </u:Play>
                        </s:Body>
                    </s:Envelope>
                """.trimIndent()

                sendSoapAction(controlUrl, "urn:schemas-upnp-org:service:AVTransport:1#Play", soapPlay)
                Log.d(TAG, "Cast commands fired successfully to DLNA target: $controlUrl")
            } catch (e: Exception) {
                Log.e(TAG, "DLNA Cast sequence failed", e)
            }
        }.start()
    }

    /**
     * Pause DLNA Media Playback
     */
    fun pauseDlna(controlUrl: String) {
        Thread {
            val soapPause = """
                <?xml version="1.0" encoding="utf-8"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                    <s:Body>
                        <u:Pause xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                            <InstanceID>0</InstanceID>
                        </u:Pause>
                    </s:Body>
                </s:Envelope>
            """.trimIndent()
            sendSoapAction(controlUrl, "urn:schemas-upnp-org:service:AVTransport:1#Pause", soapPause)
        }.start()
    }

    /**
     * Resume DLNA Media Playback
     */
    fun resumeDlna(controlUrl: String) {
        Thread {
            val soapPlay = """
                <?xml version="1.0" encoding="utf-8"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                    <s:Body>
                        <u:Play xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                            <InstanceID>0</InstanceID>
                            <Speed>1</Speed>
                        </u:Play>
                    </s:Body>
                </s:Envelope>
            """.trimIndent()
            sendSoapAction(controlUrl, "urn:schemas-upnp-org:service:AVTransport:1#Play", soapPlay)
        }.start()
    }

    /**
     * Seek to a specific timestamp in seconds
     */
    fun seekDlna(controlUrl: String, positionSeconds: Long) {
        Thread {
            val hh = positionSeconds / 3600
            val mm = (positionSeconds % 3600) / 60
            val ss = positionSeconds % 60
            val timeStr = String.format(Locale.US, "%02d:%02d:%02d", hh, mm, ss)

            val soapSeek = """
                <?xml version="1.0" encoding="utf-8"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                    <s:Body>
                        <u:Seek xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                            <InstanceID>0</InstanceID>
                            <Unit>REL_TIME</Unit>
                            <Target>$timeStr</Target>
                        </u:Seek>
                    </s:Body>
                </s:Envelope>
            """.trimIndent()
            sendSoapAction(controlUrl, "urn:schemas-upnp-org:service:AVTransport:1#Seek", soapSeek)
        }.start()
    }

    /**
     * Stop DLNA Media Stream completely
     */
    fun stopDlna(controlUrl: String) {
        Thread {
            val soapStop = """
                <?xml version="1.0" encoding="utf-8"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                    <s:Body>
                        <u:Stop xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                            <InstanceID>0</InstanceID>
                        </u:Stop>
                    </s:Body>
                </s:Envelope>
            """.trimIndent()
            sendSoapAction(controlUrl, "urn:schemas-upnp-org:service:AVTransport:1#Stop", soapStop)
        }.start()
    }

    fun sendSoapAction(controlUrl: String, soapAction: String, xmlPayload: String) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(controlUrl)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 2500
            conn.readTimeout = 3000
            conn.doOutput = true

            conn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            conn.setRequestProperty("SOAPACTION", "\"$soapAction\"")

            val outputBytes = xmlPayload.toByteArray(Charsets.UTF_8)
            conn.setRequestProperty("Content-Length", outputBytes.size.toString())

            conn.outputStream.use { os ->
                os.write(outputBytes)
                os.flush()
            }

            val code = conn.responseCode
            if (code >= 200 && code < 300) {
                val res = conn.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "UPnP SOAP success code $code Action $soapAction")
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.w(TAG, "UPnP SOAP failure $code: $err")
            }
        } catch (e: Exception) {
            Log.e(TAG, "SOAP HTTP POST request failed to $controlUrl: ${e.message}")
        } finally {
            conn?.disconnect()
        }
    }
}
