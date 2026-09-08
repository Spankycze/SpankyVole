package cz.spanky.hclimate

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.ConsumerIrManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.net.*
import java.util.Collections
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class MainActivity : Activity() {
    data class Device(val ip: String, val port: Int = 6444, var name: String = "Klimatizace")
    enum class Tab { HOME, DEVICES, SCENES, SETTINGS }

    private val bg1 = Color.rgb(6, 12, 27)
    private val bg2 = Color.rgb(9, 16, 35)
    private val card = Color.rgb(16, 27, 52)
    private val card2 = Color.rgb(22, 36, 67)
    private val white = Color.rgb(245, 248, 255)
    private val muted = Color.rgb(145, 160, 190)
    private val blue = Color.rgb(62, 137, 255)
    private val purple = Color.rgb(129, 88, 255)
    private val green = Color.rgb(55, 211, 145)
    private val orange = Color.rgb(255, 178, 65)

    private lateinit var host: FrameLayout
    private lateinit var bottom: LinearLayout
    private var tab = Tab.HOME
    private var devices = mutableListOf<Device>()
    private var targetTemp = 22
    private var power = true
    private var mode = "Chlazení"
    private val worker = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg1
        window.navigationBarColor = bg2
        setContentView(shell())
        requestNearbyPermission()
        showHome()
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun shell(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = gradient(bg1, bg2, 0f)
        }
        host = FrameLayout(this)
        root.addView(host, LinearLayout.LayoutParams(-1, 0, 1f))
        bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(6))
            background = gradient(Color.rgb(11, 20, 39), Color.rgb(8, 14, 28), 0f)
        }
        root.addView(bottom, LinearLayout.LayoutParams(-1, dp(70)))
        refreshBottom()
        return root
    }

    private fun refreshBottom() {
        bottom.removeAllViews()
        listOf(
            Triple(Tab.HOME, "⌂", "Domů"),
            Triple(Tab.DEVICES, "▣", "Zařízení"),
            Triple(Tab.SCENES, "◇", "Scény"),
            Triple(Tab.SETTINGS, "⚙", "Nastavení")
        ).forEach { (t, icon, label) ->
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                setPadding(dp(4), dp(2), dp(4), dp(2))
                if (tab == t) background = rounded(Color.rgb(23, 48, 88), 18)
                addView(tv(icon, if (tab == t) blue else muted, 23f, true))
                addView(tv(label, if (tab == t) blue else muted, 11f, tab == t))
                setOnClickListener {
                    tab = t
                    refreshBottom()
                    when (t) {
                        Tab.HOME -> showHome()
                        Tab.DEVICES -> showDevices()
                        Tab.SCENES -> showScenes()
                        Tab.SETTINGS -> showSettings()
                    }
                }
            }
            bottom.addView(box, LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(dp(3),0,dp(3),0) })
        }
    }

    private fun page(): LinearLayout {
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(28))
        }
        scroll.addView(col, ViewGroup.LayoutParams(-1, -2))
        host.removeAllViews(); host.addView(scroll)
        return col
    }

    private fun showHome() {
        val col = page()
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(tv("HClimate Home", white, 30f, true))
        titles.addView(tv("Komfortní klima pro váš domov", muted, 13f, false))
        top.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(iconButton("●") { toast("Žádná nová upozornění") })
        col.addView(top)
        col.addView(space(18))

        val hello = panel().apply {
            val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(iconCircle("⌂", blue))
            val txt = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12),0,0,0) }
            txt.addView(tv("Příjemný den", white, 17f, true))
            txt.addView(tv("Váš domov je v dobrých rukou.", muted, 12f, false))
            row.addView(txt, LinearLayout.LayoutParams(0, -2, 1f))
            val weather = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
            weather.addView(tv("☀ 24°C", white, 20f, true))
            weather.addView(tv("Venku", muted, 11f, false))
            row.addView(weather)
            addView(row)
        }
        col.addView(hello)
        col.addView(space(22))
        sectionTitle(col, "Vaše klimatizace", if (devices.isEmpty()) "0 zařízení" else "${devices.size} zařízení")
        if (devices.isEmpty()) col.addView(emptyCard()) else devices.forEachIndexed { i, d -> col.addView(deviceCard(d, i)) }
        col.addView(space(16))
        col.addView(actionButton("⌕  Hledat klimatizaci", "Vyhledat nová zařízení v síti") { scanNetwork() })
    }

    private fun emptyCard(): View = panel().apply {
        gravity = Gravity.CENTER
        setPadding(dp(18), dp(24), dp(18), dp(24))
        addView(tv("❄", blue, 36f, false).apply { gravity = Gravity.CENTER })
        addView(tv("Zatím žádná klimatizace", white, 17f, true).apply { gravity = Gravity.CENTER })
        addView(tv("Spusť hledání nebo přidej IP ručně v Nastavení.", muted, 12f, false).apply { gravity = Gravity.CENTER })
    }

    private fun deviceCard(d: Device, idx: Int): View = panel().apply {
        val header = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(iconCircle("▰", blue))
        val names = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12),0,0,0) }
        names.addView(tv(if (idx == 0) "Obývák" else d.name, white, 18f, true))
        names.addView(tv("${d.ip}:${d.port}", muted, 12f, false))
        header.addView(names, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(tv("▥", green, 20f, true))
        addView(header)
        addView(space(16))
        val status = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val temp = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        temp.addView(tv("22°C", white, 34f, true))
        temp.addView(tv("❄  Chlazení", blue, 12f, true))
        status.addView(temp, LinearLayout.LayoutParams(0,-2,1f))
        status.addView(pill("●  Připojeno", green))
        addView(status)
        addView(space(12))
        addView(smallButton("Spravovat  ›") { showControl(d) })
    }

    private fun showDevices() {
        val col = page(); heading(col, "Zařízení", "Klimatizace dostupné v lokální síti")
        col.addView(actionButton("⌕  Hledat v síti", "UDP 6445 / 20086 + TCP 6444") { scanNetwork() })
        col.addView(space(16))
        if (devices.isEmpty()) col.addView(emptyCard()) else devices.forEachIndexed { i,d -> col.addView(deviceCard(d,i)) }
    }

    private fun showScenes() {
        val col = page(); heading(col, "Scény", "Jedním klepnutím k ideálnímu klimatu")
        sceneCard(col, "❄", "Rychlé chlazení", "Maximální výkon pro rychlé snížení teploty.", blue, "Chlazení  •  18°C  •  Turbo") { targetTemp=18; mode="Chlazení"; toast("Scéna připravena") }
        sceneCard(col, "☾", "Noční režim", "Tichý provoz pro klidný spánek.", purple, "Auto  •  24°C  •  Nízká rychlost") { targetTemp=24; mode="Auto"; toast("Scéna připravena") }
        sceneCard(col, "☀", "Topení 24°C", "Příjemné teplo v chladných dnech.", orange, "Topení  •  24°C  •  Auto") { targetTemp=24; mode="Topení"; toast("Scéna připravena") }
        sceneCard(col, "⌂", "Odchod z domu", "Úsporný režim, když nejste doma.", green, "Auto  •  26°C  •  Eco") { targetTemp=26; mode="Auto"; toast("Scéna připravena") }
        val custom = panel().apply {
            addView(tv("Vytvořte si vlastní scénu", white, 18f, true))
            addView(tv("Přizpůsobte klimatizaci vašemu životnímu stylu.", muted, 12f, false))
            addView(space(12)); addView(smallButton("＋  Nová scéna") { toast("Editor scén doplním po ověření jednotky") })
        }
        col.addView(custom)
    }

    private fun showSettings() {
        val col = page(); heading(col, "Nastavení", "Připojení, diagnostika a IR")
        settingsCard(col, "⌁", "Lokální Wi‑Fi / NetHome Plus", "Hledání Midea/NetHome Plus přes UDP 6445 a 20086, ověřování TCP 6444.", blue)
        settingsCard(col, "◉", "Infračervené ovládání", if (hasIr()) "Telefon má IR vysílač. IR profil lze přidat jako záložní režim." else "Telefon nemá systémem hlášený IR vysílač.", purple)
        val manual = panel().apply {
            addView(tv("Přidat klimatizaci ručně", white, 17f, true))
            addView(tv("Zadej lokální IP jednotky. Ověřím port 6444.", muted, 12f, false))
            addView(space(12)); addView(smallButton("＋  Zadat IP adresu") { askIp() })
        }
        col.addView(manual)
    }

    private fun showControl(d: Device) {
        host.removeAllViews()
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20),dp(12),dp(20),dp(30)) }
        scroll.addView(col); host.addView(scroll)
        val top = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        top.addView(iconButton("‹") { showHome() })
        val tt = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(10),0,0,0) }
        tt.addView(tv("Obývák", white, 23f, true)); tt.addView(tv("●  Vnitřní jednotka online", green, 11f, true))
        top.addView(tt, LinearLayout.LayoutParams(0,-2,1f)); top.addView(iconButton("⚙") { tab=Tab.SETTINGS; refreshBottom(); showSettings() })
        col.addView(top); col.addView(space(12))

        val dial = TemperatureDialView(this).apply { value = targetTemp; onChanged = { targetTemp = it } }
        col.addView(dial, LinearLayout.LayoutParams(-1, dp(300)))
        val pwr = panel().apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(iconCircle("⏻", blue)); addView(tv("Klimatizace zapnuta", white, 16f, true).apply { setPadding(dp(12),0,0,0) }, LinearLayout.LayoutParams(0,-2,1f))
            addView(Switch(this@MainActivity).apply { isChecked = power; setOnCheckedChangeListener { _,v -> power=v } })
        }
        col.addView(pwr); col.addView(space(18))
        col.addView(tv("Režim", white, 17f, true)); col.addView(space(8))
        val modes = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        listOf("❄\nChlazení","☀\nTopení","◒\nOdvlhč.","✣\nVentilátor","A\nAuto").forEach { label ->
            val b = TextView(this).apply {
                text=label; gravity=Gravity.CENTER; textSize=11f; setTextColor(if (label.contains(mode.take(4),true)) white else muted)
                background = rounded(if (label.contains(mode.take(4),true)) blue else card2, 16); setPadding(dp(3),dp(10),dp(3),dp(10))
                setOnClickListener { mode = when { label.contains("Chl") -> "Chlazení"; label.contains("Top") -> "Topení"; label.contains("Odv") -> "Odvlhčování"; label.contains("Vent") -> "Ventilátor"; else -> "Auto" }; showControl(d) }
            }
            modes.addView(b, LinearLayout.LayoutParams(0,dp(70),1f).apply { setMargins(dp(2),0,dp(2),0) })
        }
        col.addView(modes); col.addView(space(16))
        controlRow(col,"✣","Rychlost ventilátoru","Auto  ›")
        controlRow(col,"↕","Směr proudění","Auto  ›")
        col.addView(space(14)); col.addView(tv("Rychlé akce", white,17f,true)); col.addView(space(8))
        val quick = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        listOf("⚡\nTurbo","☾\nSleep","♧\nEco","◷\nTimer").forEach { q ->
            quick.addView(TextView(this).apply { text=q; gravity=Gravity.CENTER; setTextColor(white); textSize=12f; background=rounded(card2,16); setOnClickListener { toast(q.replace("\n"," ")) } }, LinearLayout.LayoutParams(0,dp(70),1f).apply { setMargins(dp(3),0,dp(3),0) })
        }
        col.addView(quick)
        col.addView(space(18))
        col.addView(tv("Připojeno k ${d.ip}:${d.port}. Ovládací UI je připravené; konkrétní LAN příkazy se aktivují po identifikaci protokolu jednotky.", muted, 11f, false))
    }

    private fun controlRow(col: LinearLayout, icon: String, title: String, value: String) {
        col.addView(panel().apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; addView(tv(icon,blue,22f,true)); addView(tv(title,white,14f,true).apply { setPadding(dp(12),0,0,0) }, LinearLayout.LayoutParams(0,-2,1f)); addView(tv(value,muted,13f,true)) })
    }

    private fun sceneCard(col: LinearLayout, icon:String, title:String, desc:String, color:Int, chips:String, action:()->Unit) {
        val p = panel(); val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        row.addView(iconCircle(icon,color)); val tx=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),0,0,0); addView(tv(title,white,17f,true)); addView(tv(desc,muted,12f,false)); addView(tv(chips,color,11f,true)) }
        row.addView(tx,LinearLayout.LayoutParams(0,-2,1f)); row.addView(iconButton("▶") { action() }); p.addView(row); col.addView(p)
    }

    private fun scanNetwork() {
        toast("Hledám klimatizaci v síti…")
        worker.execute {
            val found = linkedSetOf<String>()
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val lock = wifi?.createMulticastLock("hclimate")?.apply { setReferenceCounted(false); acquire() }
            try {
                val probe = hex("5A5A01114800920000000000000000000000000000000000000000000000000000000000000000007F75BD6B3E4F8B762E849C6E578D6590036E9D4342A50F1F569EB8EC918E92E5")
                DatagramSocket(null).use { s ->
                    s.reuseAddress=true; s.broadcast=true; s.soTimeout=250; s.bind(InetSocketAddress(0))
                    val targets = broadcastAddresses().toMutableSet().apply { add(InetAddress.getByName("255.255.255.255")) }
                    repeat(2) { intArrayOf(6445,20086).forEach { port -> targets.forEach { a -> runCatching { s.send(DatagramPacket(probe,probe.size,a,port)) } } } }
                    val end=System.currentTimeMillis()+2800; val buf=ByteArray(2048)
                    while(System.currentTimeMillis()<end) {
                        try { val p=DatagramPacket(buf,buf.size); s.receive(p); p.address?.hostAddress?.let { found.add(it) } } catch(_:SocketTimeoutException) {}
                    }
                }
            } catch(_:Throwable) {} finally { runCatching { if(lock?.isHeld==true) lock.release() } }
            if(found.isEmpty()) found.addAll(scan6444())
            runOnUiThread {
                devices = found.map { Device(it) }.toMutableList()
                if(devices.isEmpty()) toast("Nic nenalezeno. Zkus IP ručně v Nastavení.") else toast("Nalezeno: ${devices.size} zařízení")
                if(tab==Tab.DEVICES) showDevices() else showHome()
            }
        }
    }

    private fun scan6444(): Collection<String> {
        val local = localIp() ?: return emptyList(); val p=local.split('.'); if(p.size!=4) return emptyList(); val prefix=p.take(3).joinToString(".")
        val out=ConcurrentLinkedQueue<String>(); val pool=Executors.newFixedThreadPool(40)
        for(i in 1..254) { val ip="$prefix.$i"; if(ip==local) continue; pool.execute { runCatching { Socket().use { s -> s.connect(InetSocketAddress(ip,6444),160); if(s.isConnected) out.add(ip) } } } }
        pool.shutdown(); pool.awaitTermination(7,TimeUnit.SECONDS); return out
    }

    private fun askIp() {
        val input=EditText(this).apply { hint="192.168.1.123"; inputType=InputType.TYPE_CLASS_PHONE; setTextColor(white); setHintTextColor(muted); setPadding(dp(14),dp(12),dp(14),dp(12)); background=rounded(card2,14) }
        AlertDialog.Builder(this).setTitle("IP adresa klimatizace").setView(input).setNegativeButton("Zrušit",null).setPositiveButton("Ověřit") { _,_ ->
            val ip=input.text.toString().trim(); if(ip.isNotEmpty()) testIp(ip)
        }.show()
    }

    private fun testIp(ip:String) {
        worker.execute {
            val ok=runCatching { Socket().use { it.connect(InetSocketAddress(ip,6444),1500); it.isConnected } }.getOrDefault(false)
            runOnUiThread { if(ok) { if(devices.none { it.ip==ip }) devices.add(Device(ip)); toast("Jednotka odpovídá na TCP 6444"); showDevices() } else toast("Na $ip:6444 není odpověď") }
        }
    }

    private fun localIp(): String? {
        val list=runCatching { Collections.list(NetworkInterface.getNetworkInterfaces()) }.getOrDefault(emptyList())
        for(n in list) { if(!runCatching { n.isUp && !n.isLoopback }.getOrDefault(false)) continue; for(a in Collections.list(n.inetAddresses)) { val ip=a.hostAddress?:continue; if(!ip.contains(':')&&!ip.startsWith("127.")&&!ip.startsWith("169.254.")) return ip } }
        return null
    }

    private fun broadcastAddresses(): List<InetAddress> {
        val out=mutableListOf<InetAddress>(); val list=runCatching { Collections.list(NetworkInterface.getNetworkInterfaces()) }.getOrDefault(emptyList())
        for(n in list) if(runCatching { n.isUp && !n.isLoopback }.getOrDefault(false)) for(i in n.interfaceAddresses) i.broadcast?.let { out.add(it) }
        return out
    }

    private fun requestNearbyPermission() {
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES)!=PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES),42)
    }
    private fun hasIr() = (getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager)?.hasIrEmitter() == true
    private fun hex(s:String):ByteArray { val x=s.replace(" ",""); return ByteArray(x.length/2) { i -> ((Character.digit(x[i*2],16) shl 4)+Character.digit(x[i*2+1],16)).toByte() } }

    private fun heading(col:LinearLayout,title:String,sub:String) { col.addView(tv(title,white,30f,true)); col.addView(tv(sub,muted,13f,false)); col.addView(space(20)) }
    private fun sectionTitle(col:LinearLayout,title:String,right:String) { val r=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }; r.addView(tv(title,white,18f,true),LinearLayout.LayoutParams(0,-2,1f)); r.addView(tv(right,muted,12f,false)); col.addView(r); col.addView(space(10)) }
    private fun settingsCard(col:LinearLayout,icon:String,title:String,desc:String,color:Int) { col.addView(panel().apply { val r=LinearLayout(this@MainActivity).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.TOP; addView(iconCircle(icon,color)); val t=LinearLayout(this@MainActivity).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),0,0,0); addView(tv(title,white,16f,true)); addView(tv(desc,muted,12f,false)) }; addView(t,LinearLayout.LayoutParams(0,-2,1f)) }; addView(r) }) }
    private fun panel() = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(16),dp(16),dp(16)); background=rounded(card,20); layoutParams=LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(10) } }
    private fun actionButton(title:String,sub:String,onClick:()->Unit)=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; setPadding(dp(16),dp(14),dp(16),dp(14)); background=gradient(purple,blue,18f); isClickable=true; addView(tv(title,Color.WHITE,16f,true).apply { gravity=Gravity.CENTER }); addView(tv(sub,Color.rgb(221,230,255),11f,false).apply { gravity=Gravity.CENTER }); setOnClickListener { onClick() } }
    private fun smallButton(label:String,onClick:()->Unit)=TextView(this).apply { text=label; setTextColor(white); textSize=13f; typeface=Typeface.DEFAULT_BOLD; gravity=Gravity.CENTER; setPadding(dp(14),dp(11),dp(14),dp(11)); background=rounded(card2,14); setOnClickListener { onClick() } }
    private fun iconButton(icon:String,onClick:()->Unit)=TextView(this).apply { text=icon; setTextColor(white); textSize=22f; gravity=Gravity.CENTER; background=rounded(card2,18); layoutParams=LinearLayout.LayoutParams(dp(46),dp(46)); setOnClickListener { onClick() } }
    private fun iconCircle(icon:String,color:Int)=TextView(this).apply { text=icon; setTextColor(color); textSize=21f; typeface=Typeface.DEFAULT_BOLD; gravity=Gravity.CENTER; background=rounded(Color.argb(35,Color.red(color),Color.green(color),Color.blue(color)),18); layoutParams=LinearLayout.LayoutParams(dp(50),dp(50)) }
    private fun pill(label:String,color:Int)=TextView(this).apply { text=label; setTextColor(color); textSize=11f; typeface=Typeface.DEFAULT_BOLD; setPadding(dp(10),dp(7),dp(10),dp(7)); background=rounded(Color.argb(32,Color.red(color),Color.green(color),Color.blue(color)),20) }
    private fun tv(s:String,c:Int,size:Float,bold:Boolean)=TextView(this).apply { text=s; setTextColor(c); textSize=size; if(bold) typeface=Typeface.DEFAULT_BOLD; includeFontPadding=false }
    private fun space(h:Int)=Space(this).apply { layoutParams=LinearLayout.LayoutParams(1,dp(h)) }
    private fun rounded(color:Int,r:Int)=GradientDrawable().apply { shape=GradientDrawable.RECTANGLE; cornerRadius=dp(r).toFloat(); setColor(color) }
    private fun gradient(a:Int,b:Int,r:Float)=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(a,b)).apply { cornerRadius=dp(r.roundToInt()).toFloat() }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).roundToInt()
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()

    inner class TemperatureDialView(ctx:Context):View(ctx) {
        var value=22; var onChanged:((Int)->Unit)?=null
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG); private val rect=RectF()
        init { setLayerType(LAYER_TYPE_SOFTWARE,null); setOnClickListener { value=if(value>=30)16 else value+1; onChanged?.invoke(value); invalidate() } }
        override fun onDraw(c:Canvas) {
            super.onDraw(c); val cx=width/2f; val cy=height/2f; val radius=minOf(width,height)*0.34f; rect.set(cx-radius,cy-radius,cx+radius,cy+radius)
            paint.style=Paint.Style.STROKE; paint.strokeWidth=dp(14).toFloat(); paint.strokeCap=Paint.Cap.ROUND; paint.color=Color.rgb(31,50,84); c.drawArc(rect,135f,270f,false,paint)
            val pct=(value-16)/14f; paint.color=blue; paint.setShadowLayer(dp(18).toFloat(),0f,0f,blue); c.drawArc(rect,135f,270f*pct,false,paint); paint.clearShadowLayer()
            paint.style=Paint.Style.FILL; paint.textAlign=Paint.Align.CENTER; paint.typeface=Typeface.DEFAULT_BOLD; paint.color=white; paint.textSize=sp(56f); c.drawText("${value}°C",cx,cy+dp(12),paint)
            paint.typeface=Typeface.DEFAULT; paint.color=muted; paint.textSize=sp(13f); c.drawText("Nastavená teplota",cx,cy+dp(42),paint)
            paint.color=card2; c.drawCircle(cx-radius-dp(34),cy,dp(25).toFloat(),paint); c.drawCircle(cx+radius+dp(34),cy,dp(25).toFloat(),paint)
            paint.color=white; paint.textSize=sp(28f); paint.typeface=Typeface.DEFAULT_BOLD; c.drawText("−",cx-radius-dp(34),cy+dp(9),paint); c.drawText("+",cx+radius+dp(34),cy+dp(9),paint)
        }
        private fun sp(v:Float)=v*resources.displayMetrics.scaledDensity
    }
}
