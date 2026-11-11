package ar.garibaldi.obd.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import kotlin.concurrent.thread

/** Canal de texto hacia el adaptador (Bluetooth real o simulador). */
interface ObdTransport {
    val nombre: String
    val conectado: Boolean

    /** Texto recibido, en pedazos tal como llega. Se cierra cuando se corta la conexión. */
    val entrada: ReceiveChannel<String>

    suspend fun conectar()
    suspend fun escribir(texto: String)
    fun cerrar()
}

/** Bluetooth clásico, perfil puerto serie (SPP), que es lo que usa el OBDLink MX+. */
@SuppressLint("MissingPermission")
class BluetoothSppTransport(private val dispositivo: BluetoothDevice) : ObdTransport {
    private val canal = Channel<String>(Channel.UNLIMITED)
    private var socket: BluetoothSocket? = null

    override val nombre: String get() = dispositivo.name ?: dispositivo.address
    override val conectado: Boolean get() = socket?.isConnected == true
    override val entrada: ReceiveChannel<String> get() = canal

    override suspend fun conectar() {
        withContext(Dispatchers.IO) {
            var ultimoError: IOException? = null
            // Primero el modo seguro; algunos teléfonos solo conectan con el "inseguro".
            for (seguro in listOf(true, false)) {
                try {
                    val s = if (seguro) dispositivo.createRfcommSocketToServiceRecord(SPP)
                    else dispositivo.createInsecureRfcommSocketToServiceRecord(SPP)
                    s.connect()
                    socket = s
                    ultimoError = null
                    break
                } catch (e: IOException) {
                    ultimoError = e
                }
            }
            ultimoError?.let {
                throw IOException("No se pudo conectar por Bluetooth con $nombre: ${it.message}", it)
            }
            val s = socket!!
            thread(name = "obd-rx", isDaemon = true) {
                val buf = ByteArray(1024)
                try {
                    val entradaBt = s.inputStream
                    while (true) {
                        val n = entradaBt.read(buf)
                        if (n < 0) break
                        if (n > 0) canal.trySend(String(buf, 0, n, Charsets.ISO_8859_1))
                    }
                } catch (_: IOException) {
                } finally {
                    canal.close()
                }
            }
        }
    }

    override suspend fun escribir(texto: String) {
        withContext(Dispatchers.IO) {
            val s = socket ?: throw IOException("Sin conexión Bluetooth")
            s.outputStream.write(texto.toByteArray(Charsets.ISO_8859_1))
            s.outputStream.flush()
        }
    }

    override fun cerrar() {
        try {
            socket?.close()
        } catch (_: IOException) {
        }
        socket = null
        canal.close()
    }

    companion object {
        val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
