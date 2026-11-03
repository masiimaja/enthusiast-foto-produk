package id.enthusiast.fotoproduk

import android.content.ContentValues
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFFE4202D))) { Layar() }
        }
    }
}

// ---------- Gaya latar & ukuran ----------
data class Gaya(val nama: String, val atas: Int, val bawah: Int, val cahaya: Int, val bayangan: Boolean, val pantulan: Boolean)

fun warna(v: Long): Int = v.toInt()

val GAYA = listOf(
    Gaya("Putih Studio", warna(0xFFFFFFFF), warna(0xFFE6E7EB), 0x66FFFFFF, true, false),
    Gaya("Hitam Elegan", warna(0xFF2B2B30), warna(0xFF0A0A0C), 0x33FFFFFF, false, true),
    Gaya("Merah Brand", warna(0xFFE4202D), warna(0xFF6E0E17), 0x40FFFFFF, true, false),
    Gaya("Biru Modern", warna(0xFF3B82F6), warna(0xFF0B2A6B), 0x40FFFFFF, true, false),
    Gaya("Krem Hangat", warna(0xFFFBF3E6), warna(0xFFE3CFAE), 0x66FFFFFF, true, false),
    Gaya("Abu Industri", warna(0xFFDCE0E6), warna(0xFF98A1AD), 0x55FFFFFF, true, false)
)

val UKURAN = listOf(
    Triple("Instagram 1:1", 1080, 1080),
    Triple("Feed 4:5", 1080, 1350),
    Triple("Story/Status 9:16", 1080, 1920),
    Triple("Facebook Iklan", 1200, 628),
    Triple("HD 2000", 2000, 2000)
)

// ---------- Pemrosesan gambar ----------
fun muat(ctx: Context, uri: Uri): Bitmap {
    val src = ImageDecoder.createSource(ctx.contentResolver, uri)
    return ImageDecoder.decodeBitmap(src) { d, info, _ ->
        d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val m = maxOf(info.size.width, info.size.height)
        if (m > 1600) {
            val s = 1600f / m
            d.setTargetSize((info.size.width * s).toInt(), (info.size.height * s).toInt())
        }
    }
}

fun pisah(bmp: Bitmap, selesai: (Bitmap?) -> Unit) {
    val opsi = SubjectSegmenterOptions.Builder().enableForegroundBitmap().build()
    SubjectSegmentation.getClient(opsi).process(InputImage.fromBitmap(bmp, 0))
        .addOnSuccessListener { selesai(it.foregroundBitmap) }
        .addOnFailureListener { selesai(null) }
}

fun potong(b: Bitmap): Bitmap {
    val w = b.width
    val h = b.height
    val px = IntArray(w * h)
    b.getPixels(px, 0, w, 0, 0, w, h)
    var x0 = w
    var y0 = h
    var x1 = -1
    var y1 = -1
    for (y in 0 until h) for (x in 0 until w) {
        if ((px[y * w + x] ushr 24) > 16) {
            if (x < x0) x0 = x
            if (x > x1) x1 = x
            if (y < y0) y0 = y
            if (y > y1) y1 = y
        }
    }
    if (x1 < 0) return b
    return Bitmap.createBitmap(b, x0, y0, x1 - x0 + 1, y1 - y0 + 1)
}

fun render(fg: Bitmap, g: Gaya, w: Int, h: Int): Bitmap {
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val cv = Canvas(out)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), g.atas, g.bawah, Shader.TileMode.CLAMP)
    cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
    p.shader = RadialGradient(w / 2f, h * 0.42f, maxOf(w, h) * 0.6f, g.cahaya, 0x00FFFFFF, Shader.TileMode.CLAMP)
    cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
    p.shader = null

    val s = minOf(w * 0.8f / fg.width, h * 0.58f / fg.height)
    val dw = fg.width * s
    val dh = fg.height * s
    val left = (w - dw) / 2f
    val top = h * 0.46f - dh / 2f
    val base = top + dh

    val cm = ColorMatrix()
    cm.setSaturation(1.12f)
    val k = 1.08f
    val t = (1f - k) * 128f
    cm.postConcat(ColorMatrix(floatArrayOf(k, 0f, 0f, 0f, t, 0f, k, 0f, 0f, t, 0f, 0f, k, 0f, t, 0f, 0f, 0f, 1f, 0f)))
    val fp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    fp.colorFilter = ColorMatrixColorFilter(cm)

    if (g.bayangan) {
        p.color = 0x59000000
        p.maskFilter = BlurMaskFilter(maxOf(w * 0.02f, 1f), BlurMaskFilter.Blur.NORMAL)
        cv.drawOval(RectF(w / 2f - dw * 0.42f, base - h * 0.012f, w / 2f + dw * 0.42f, base + h * 0.022f), p)
        p.maskFilter = null
    }
    if (g.pantulan) {
        val rh = (dh * 0.35f).toInt().coerceAtLeast(1)
        val rw = dw.toInt().coerceAtLeast(1)
        val ref = Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888)
        val rc = Canvas(ref)
        rc.save()
        rc.scale(1f, -1f)
        rc.drawBitmap(fg, null, RectF(0f, -dh, dw, 0f), fp)
        rc.restore()
        val mp = Paint()
        mp.shader = LinearGradient(0f, 0f, 0f, rh.toFloat(), 0x59FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        mp.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        rc.drawRect(0f, 0f, rw.toFloat(), rh.toFloat(), mp)
        cv.drawBitmap(ref, left, base, null)
    }
    cv.drawBitmap(fg, null, RectF(left, top, left + dw, top + dh), fp)
    return out
}

fun simpan(ctx: Context, bmp: Bitmap, nama: String): Boolean {
    val v = ContentValues()
    v.put(MediaStore.Images.Media.DISPLAY_NAME, "$nama.jpg")
    v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
    v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Enthusiast Foto Produk")
    val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v) ?: return false
    ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 93, it) }
    return true
}

// ---------- Tampilan ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Layar() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var fg by remember { mutableStateOf<Bitmap?>(null) }
    var status by remember { mutableStateOf("Pilih foto produk untuk memulai.") }
    var sibuk by remember { mutableStateOf(false) }
    var ukuran by remember { mutableStateOf(UKURAN[0]) }
    var hasil by remember { mutableStateOf<List<Bitmap>>(emptyList()) }

    val pilih = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            sibuk = true
            status = "Memisahkan produk dari latar…"
            hasil = emptyList()
            fg = null
            scope.launch {
                try {
                    val bmp = withContext(Dispatchers.IO) { muat(ctx, uri) }
                    pisah(bmp) { out ->
                        if (out == null) {
                            sibuk = false
                            status = "Gagal memproses. Pastikan internet aktif saat pertama kali (unduh model), lalu coba lagi."
                        } else {
                            scope.launch {
                                fg = withContext(Dispatchers.Default) { potong(out) }
                                sibuk = false
                                status = "Pilih gaya favoritmu, atur ukuran, lalu simpan."
                            }
                        }
                    }
                } catch (e: Exception) {
                    sibuk = false
                    status = "Foto tidak bisa dibuka."
                }
            }
        }
    }

    LaunchedEffect(fg, ukuran) {
        val f = fg
        if (f != null) {
            val sc = 640f / maxOf(ukuran.second, ukuran.third)
            val pw = (ukuran.second * sc).toInt().coerceAtLeast(1)
            val ph = (ukuran.third * sc).toInt().coerceAtLeast(1)
            hasil = withContext(Dispatchers.Default) { GAYA.map { render(f, it, pw, ph) } }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Enthusiast Foto Produk", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { pilih.launch("image/*") }, enabled = !sibuk, modifier = Modifier.fillMaxWidth()) {
            Text("Pilih foto produk")
        }
        Spacer(Modifier.height(8.dp))
        Text(status)
        if (sibuk) CircularProgressIndicator()
        if (fg != null) {
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(UKURAN) { u ->
                    FilterChip(selected = (u == ukuran), onClick = { ukuran = u }, label = { Text("${u.first} (${u.second}×${u.third})") })
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(hasil.size) { i ->
                    Card {
                        Column(Modifier.padding(8.dp)) {
                            Image(
                                bitmap = hasil[i].asImageBitmap(),
                                contentDescription = GAYA[i].nama,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth().aspectRatio(ukuran.second / ukuran.third.toFloat())
                            )
                            Text(GAYA[i].nama, fontWeight = FontWeight.Medium, modifier = Modifier.padding(vertical = 4.dp))
                            Button(onClick = {
                                scope.launch {
                                    val f = fg
                                    if (f != null) {
                                        val ok = withContext(Dispatchers.IO) {
                                            val penuh = render(f, GAYA[i], ukuran.second, ukuran.third)
                                            simpan(ctx, penuh, "produk_${GAYA[i].nama.replace(" ", "")}_${ukuran.second}x${ukuran.third}_${System.currentTimeMillis()}")
                                        }
                                        Toast.makeText(ctx, if (ok) "Tersimpan di Galeri (Pictures/Enthusiast Foto Produk)" else "Gagal menyimpan", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }, modifier = Modifier.fillMaxWidth()) { Text("Simpan") }
                        }
                    }
                }
            }
        }
    }
}
