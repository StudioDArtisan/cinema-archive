package com.cinema.archive

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var rootContainer: FrameLayout
    private lateinit var homeView: LinearLayout
    private lateinit var browserContainer: LinearLayout
    private lateinit var webView: WebView
    private lateinit var topBarTitle: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var logListLayout: LinearLayout
    private lateinit var autoTrackSwitch: Switch

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private val URL_COLLECTIO = "https://collectio.co.kr/main/index.jsp"
    private val URL_JIHASIL = "https://jihasil.com/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#0B0B0D")
        window.navigationBarColor = Color.parseColor("#0B0B0D")

        rootContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#0B0B0D"))
        }

        setupHomeView()
        setupBrowserView()

        rootContainer.addView(homeView)
        rootContainer.addView(browserContainer)
        browserContainer.visibility = View.GONE

        setContentView(rootContainer)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (customView != null) {
                    webView.webChromeClient?.onHideCustomView()
                } else if (browserContainer.visibility == View.VISIBLE) {
                    if (webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        closeBrowser()
                    }
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun setupHomeView() {
        homeView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(48), dp(24), dp(24))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // 상단 헤더 (미학적 시네마 타이틀)
        val subtitle = TextView(this).apply {
            text = "PRIVATE CINEMATHEQUE"
            setTextColor(Color.parseColor("#C5A880"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            letterSpacing = 0.25f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        val title = TextView(this).apply {
            text = "Le Cinéma"
            setTextColor(Color.parseColor("#F2F0EC"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            setPadding(0, dp(4), 0, dp(28))
        }

        homeView.addView(subtitle)
        homeView.addView(title)

        // 콜렉티오 & 지하실 입장 카드
        val cardCollectio = createPortalCard(
            title = "COLLECTIO",
            korTitle = "콜렉티오",
            desc = "Classic & Art-House Film Archive",
            accentColor = "#C5A880"
        ) {
            openPortal("COLLECTIO", URL_COLLECTIO)
        }

        val cardJihasil = createPortalCard(
            title = "JIHASIL",
            korTitle = "지하실",
            desc = "Underground Cinema Sanctuary",
            accentColor = "#9E7B66"
        ) {
            openPortal("JIHASIL", URL_JIHASIL)
        }

        homeView.addView(cardCollectio)
        homeView.addView(Space(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(16)) })
        homeView.addView(cardJihasil)

        // 관람 기록 헤더 & 자동 기록 스위치
        val logHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(32), 0, dp(12))
        }

        val logTitle = TextView(this).apply {
            text = "VIEWING LOG (관람 및 탐색 기록)"
            setTextColor(Color.parseColor("#8E8E93"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            letterSpacing = 0.1f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        autoTrackSwitch = Switch(this).apply {
            text = "자동 기록 "
            setTextColor(Color.parseColor("#8E8E93"))
            textSize = 11f
            isChecked = getSharedPreferences("cinema_prefs", Context.MODE_PRIVATE)
                .getBoolean("auto_track", true)
            setOnCheckedChangeListener { _, isChecked ->
                getSharedPreferences("cinema_prefs", Context.MODE_PRIVATE)
                    .edit().putBoolean("auto_track", isChecked).apply()
            }
        }

        val clearBtn = TextView(this).apply {
            text = "비우기"
            setTextColor(Color.parseColor("#636366"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(dp(12), dp(4), 0, dp(4))
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("기록 초기화")
                    .setMessage("저장된 모든 영화 기록을 삭제할까?")
                    .setPositiveButton("삭제") { _, _ ->
                        clearLogs()
                        refreshLogList()
                    }
                    .setNegativeButton("취소", null)
                    .show()
            }
        }

        logHeaderRow.addView(logTitle)
        logHeaderRow.addView(autoTrackSwitch)
        logHeaderRow.addView(clearBtn)
        homeView.addView(logHeaderRow)

        // 구분선
        homeView.addView(View(this).apply {
            setBackgroundColor(Color.parseColor("#222226"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
        })

        // 스크롤 가능한 기록 리스트
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        logListLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(24))
        }

        scrollView.addView(logListLayout)
        homeView.addView(scrollView)

        refreshLogList()
    }

    private fun createPortalCard(
        title: String,
        korTitle: String,
        desc: String,
        accentColor: String,
        onClick: () -> Unit
    ): LinearLayout {
        val bg = GradientDrawable().apply {
            setColor(Color.parseColor("#141417"))
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), Color.parseColor("#26262B"))
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bg
            setPadding(dp(22), dp(20), dp(22), dp(20))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }

            val topRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val engText = TextView(this@MainActivity).apply {
                text = title
                setTextColor(Color.parseColor("#F2F0EC"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                letterSpacing = 0.08f
            }

            val badge = TextView(this@MainActivity).apply {
                text = korTitle
                setTextColor(Color.parseColor(accentColor))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dp(10), 0, 0, 0)
            }

            val arrow = TextView(this@MainActivity).apply {
                text = "→"
                setTextColor(Color.parseColor(accentColor))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            topRow.addView(engText)
            topRow.addView(badge)
            topRow.addView(arrow)

            val descView = TextView(this@MainActivity).apply {
                text = desc
                setTextColor(Color.parseColor("#787880"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, dp(6), 0, 0)
            }

            addView(topRow)
            addView(descView)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupBrowserView() {
        browserContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // 상단 컨트롤 바
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#111114"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

        val homeBtn = TextView(this).apply {
            text = "✕ 로비"
            setTextColor(Color.parseColor("#C5A880"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(8), dp(6), dp(12), dp(6))
            setOnClickListener { closeBrowser() }
        }

        topBarTitle = TextView(this).apply {
            text = "ARCHIVE"
            setTextColor(Color.parseColor("#E5E5EA"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val manualLogBtn = TextView(this).apply {
            text = "+ 이 영화 기록"
            setTextColor(Color.parseColor("#0B0B0D"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#C5A880"))
                cornerRadius = dp(6).toFloat()
            }
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener {
                val currentTitle = webView.title ?: "제목 없음"
                val currentUrl = webView.url ?: ""
                if (currentUrl.isNotEmpty()) {
                    saveMovieLog(currentTitle, currentUrl, isManual = true)
                    Toast.makeText(this@MainActivity, "관람 기록에 저장됨: $currentTitle", Toast.LENGTH_SHORT).show()
                }
            }
        }

        topBar.addView(homeBtn)
        topBar.addView(topBarTitle)
        topBar.addView(manualLogBtn)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2))
            max = 100
        }

        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setBackgroundColor(Color.parseColor("#0B0B0D"))
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                userAgentString = settings.userAgentString.replace("; wv", "")
            }

            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    CookieManager.getInstance().flush()
                    val pageTitle = view?.title?.trim() ?: ""
                    topBarTitle.text = if (pageTitle.isNotEmpty()) pageTitle else "CINEMA"

                    if (url != null && autoTrackSwitch.isChecked) {
                        autoDetectAndSaveMovie(pageTitle, url)
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progressBar.progress = newProgress
                    progressBar.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
                }

                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (customView != null) {
                        callback?.onCustomViewHidden()
                        return
                    }
                    customView = view
                    customViewCallback = callback
                    browserContainer.visibility = View.GONE
                    rootContainer.addView(
                        customView,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                }

                override fun onHideCustomView() {
                    customView?.let { rootContainer.removeView(it) }
                    customView = null
                    customViewCallback?.onCustomViewHidden()
                    browserContainer.visibility = View.VISIBLE
                }
            }

            setDownloadListener { url, _, _, _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: Exception) {}
            }
        }

        browserContainer.addView(topBar)
        browserContainer.addView(progressBar)
        browserContainer.addView(webView)
    }

    private fun openPortal(name: String, url: String) {
        topBarTitle.text = name
        homeView.visibility = View.GONE
        browserContainer.visibility = View.VISIBLE
        webView.loadUrl(url)
    }

    private fun closeBrowser() {
        webView.loadUrl("about:blank")
        browserContainer.visibility = View.GONE
        homeView.visibility = View.VISIBLE
        refreshLogList()
    }

    // 메인 인덱스가 아닌 개별 작품/상영 페이지 진입 시 자동 기록
    private fun autoDetectAndSaveMovie(title: String, url: String) {
        if (url == "about:blank") return
        val normalizedUrl = url.lowercase()

        // 루트 메인 페이지나 로그인 페이지는 자동 기록에서 제외
        val isRootHome = normalizedUrl.endsWith("collectio.co.kr/main/index.jsp") ||
                normalizedUrl.endsWith("collectio.co.kr/") ||
                normalizedUrl.endsWith("jihasil.com/") ||
                normalizedUrl.endsWith("jihasil.com") ||
                normalizedUrl.contains("login") ||
                normalizedUrl.contains("member")

        if (!isRootHome && title.isNotBlank() && !title.startsWith("http")) {
            saveMovieLog(title, url, isManual = false)
        }
    }

    private fun saveMovieLog(title: String, url: String, isManual: Boolean) {
        val prefs = getSharedPreferences("cinema_prefs", Context.MODE_PRIVATE)
        val raw = prefs.getString("movie_logs", "[]") ?: "[]"
        val arr = JSONArray(raw)

        // 중복 URL 제거 (최신 기록을 맨 위로 올리기 위함)
        val updatedArr = JSONArray()
        val platform = when {
            url.contains("collectio", ignoreCase = true) -> "COLLECTIO"
            url.contains("jihasil", ignoreCase = true) -> "JIHASIL"
            else -> "WEB"
        }

        val sdf = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA)
        val newEntry = JSONObject().apply {
            put("title", title)
            put("url", url)
            put("platform", platform)
            put("time", sdf.format(Date()))
            put("manual", isManual)
        }
        updatedArr.put(newEntry)

        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            if (item.optString("url") != url && updatedArr.length() < 100) {
                updatedArr.put(item)
            }
        }

        prefs.edit().putString("movie_logs", updatedArr.toString()).apply()
    }

    private fun clearLogs() {
        getSharedPreferences("cinema_prefs", Context.MODE_PRIVATE)
            .edit().putString("movie_logs", "[]").apply()
    }

    private fun refreshLogList() {
        logListLayout.removeAllViews()
        val prefs = getSharedPreferences("cinema_prefs", Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString("movie_logs", "[]") ?: "[]")

        if (arr.length() == 0) {
            val emptyView = TextView(this).apply {
                text = "아직 기록된 작품이 없습니다.\n콜렉티오나 지하실에서 작품을 감상하면 이곳에 자동으로 아카이빙됩니다."
                setTextColor(Color.parseColor("#55555A"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, dp(28), 0, dp(28))
            }
            logListLayout.addView(emptyView)
            return
        }

        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            val title = item.optString("title")
            val url = item.optString("url")
            val platform = item.optString("platform")
            val time = item.optString("time")
            val isManual = item.optBoolean("manual", false)

            val itemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(4), dp(14), dp(4), dp(14))
                isClickable = true
                setOnClickListener { openPortal(platform, url) }
            }

            val metaRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val badge = TextView(this).apply {
                text = if (isManual) "★ $platform" else platform
                setTextColor(
                    if (platform == "COLLECTIO") Color.parseColor("#C5A880")
                    else Color.parseColor("#9E7B66")
                )
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                typeface = Typeface.DEFAULT_BOLD
            }

            val dateText = TextView(this).apply {
                text = "  ·  $time"
                setTextColor(Color.parseColor("#636366"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            }

            metaRow.addView(badge)
            metaRow.addView(dateText)

            val titleView = TextView(this).apply {
                text = title
                setTextColor(Color.parseColor("#E5E5EA"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(4), 0, 0)
            }

            val divider = View(this).apply {
                setBackgroundColor(Color.parseColor("#18181C"))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
            }

            itemLayout.addView(metaRow)
            itemLayout.addView(titleView)
            logListLayout.addView(itemLayout)
            logListLayout.addView(divider)
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
