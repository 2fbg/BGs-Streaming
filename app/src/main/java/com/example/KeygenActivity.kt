package com.example

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import java.security.MessageDigest

class KeygenActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Gather own device info
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "MK21DEVICEID"
        val cleanId = androidId.replace("[^A-Fa-f0-9]".toRegex(), "").padEnd(12, 'F').take(12).uppercase()
        val ownVirtualMac = cleanId.chunked(2).joinToString(":")

        setContent {
            MyApplicationTheme(useAmoledMode = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    KeygenScreen(
                        ownMac = ownVirtualMac,
                        onBackPressed = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeygenScreen(
    ownMac: String,
    onBackPressed: () -> Unit
) {
    var deviceMacInput by remember { mutableStateOf("") }
    var generatedKey by remember { mutableStateOf("") }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    
    val goldColor = Color(0xFFE2B93C)
    val cardBackground = Color(0xFF131111)
    val inputBackground = Color(0xFF1D1B1B)

    // Formula definition
    fun calculateKey(mac: String): String {
        if (mac.isEmpty()) return ""
        val cleanMac = mac.replace("[^A-Fa-f0-9]".toRegex(), "").take(12).uppercase()
        val paddedMac = cleanMac.padEnd(12, 'F')
        val formattedMac = paddedMac.chunked(2).joinToString(":")
        
        val salt = "MK21_GOLDEN_SALT_2026"
        val rawInput = formattedMac + salt
        return try {
            val md5 = MessageDigest.getInstance("MD5")
            val hashBytes = md5.digest(rawInput.toByteArray(Charsets.UTF_8))
            val sb = StringBuilder()
            for (b in hashBytes) {
                sb.append(String.format("%02X", b))
            }
            val fullHash = sb.toString()
            val p1 = fullHash.take(4)
            val p2 = fullHash.substring(4, 8)
            val p3 = fullHash.substring(8, 12)
            "MK-$p1-$p2-$p3"
        } catch (e: Exception) {
            "MK-ERR-HASH-FAIL"
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = "Key Icon",
                            tint = goldColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "GERADOR MK21",
                            color = goldColor,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            letterSpacing = 1.5.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.95f),
                    titleContentColor = goldColor
                ),
                modifier = Modifier.statusBarsPadding()
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Premium Admin Emblem Header Banner
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBackground),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, goldColor.copy(alpha = 0.25f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(goldColor.copy(alpha = 0.25f), Color.Transparent)
                                ),
                                shape = CircleShape
                            )
                            .border(1.5.dp, goldColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "Licensing",
                            tint = goldColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "GERADOR DE ATIVAÇÕES PREMIUM",
                        color = goldColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Gere chaves de desbloqueio vitalícias offline para qualquer dispositivo MK21 instantaneamente.",
                        color = Color.LightGray.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Device Virtual MAC input Section Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Laptop,
                    contentDescription = "MAC ID",
                    tint = goldColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "IDENTIFICAÇÃO DO DISPOSITIVO ALVO",
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Input Field
            OutlinedTextField(
                value = deviceMacInput,
                onValueChange = { input ->
                    // Auto-convert and filter to standard hex
                    val uppercaseInput = input.uppercase()
                    deviceMacInput = uppercaseInput
                },
                placeholder = {
                    Text(
                        "Ex: 4E:E8:43:80:D5:42",
                        color = Color.Gray.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )
                },
                label = { Text("Chave ou Virtual MAC", color = goldColor, fontSize = 11.sp) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = goldColor,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    focusedContainerColor = inputBackground,
                    unfocusedContainerColor = inputBackground,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = goldColor,
                    focusedLabelColor = goldColor,
                    unfocusedLabelColor = Color.Gray
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Quick device tools Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Generate for this device
                Button(
                    onClick = {
                        deviceMacInput = ownMac
                        Toast.makeText(context, "MAC deste aparelho colado!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF221F1F)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Text(
                        text = "USAR MEU MAC", 
                        color = Color.White, 
                        fontWeight = FontWeight.SemiBold, 
                        fontSize = 10.sp
                    )
                }

                // Format Input nicely
                Button(
                    onClick = {
                        val cleaned = deviceMacInput.replace("[^A-Fa-f0-9]".toRegex(), "").take(12).uppercase()
                        if (cleaned.length < 12) {
                            Toast.makeText(context, "Escreva pelo menos 12 caracteres hexadecimais.", Toast.LENGTH_SHORT).show()
                        } else {
                            deviceMacInput = cleaned.chunked(2).joinToString(":")
                            Toast.makeText(context, "Formato MAC corrigido!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF221F1F)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Text(
                        text = "AUTO-FORMATAR", 
                        color = Color.White, 
                        fontWeight = FontWeight.SemiBold, 
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Large Action Button to calculate key
            Button(
                onClick = {
                    val trimmedInput = deviceMacInput.trim()
                    if (trimmedInput.isEmpty()) {
                        Toast.makeText(context, "Digite ou cole a identificação antes de gerar", Toast.LENGTH_LONG).show()
                    } else {
                        generatedKey = calculateKey(trimmedInput)
                        Toast.makeText(context, "Código gerado com sucesso!", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = goldColor),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                Text(
                    text = "GERAR CÓDIGO",
                    color = Color.Black,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Display Results if generated
            if (generatedKey.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E190F)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, goldColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "LICENÇA PREMIUM COMBINADA",
                            color = goldColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 1.5.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Large Activation string
                        Text(
                            text = generatedKey,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 24.sp,
                            letterSpacing = 2.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Divider(color = goldColor.copy(alpha = 0.2f), modifier = Modifier.padding(bottom = 14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Copy Button
                            OutlinedButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(generatedKey))
                                    Toast.makeText(context, "Copiado para a área de transferência!", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, goldColor.copy(alpha = 0.6f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = goldColor),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy",
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("COPIAR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // Share via WhatsApp/System dialog
                            Button(
                                onClick = {
                                    val sendIntent: Intent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, "Aqui está sua chave de ativação Premium para o MK21:\n\n$generatedKey\n\nAbra o aplicativo, acesse Licença e digite esta chave.")
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, "Enviar chave via")
                                    context.startActivity(shareIntent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = goldColor),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share",
                                    tint = Color.Black,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("COMPARTILHAR", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}
