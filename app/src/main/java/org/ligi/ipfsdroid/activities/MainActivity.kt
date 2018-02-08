package org.ligi.ipfsdroid.activities

import android.app.ProgressDialog
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import io.ipfs.kotlin.IPFS
import io.ipfs.kotlin.IPFSConnection
import io.ipfs.kotlin.model.VersionInfo
import kotlinx.android.synthetic.main.activity_main.*
import org.ligi.ipfsdroid.*
import org.ligi.kaxt.startActivityFromClass
import org.ligi.tracedroid.sending.TraceDroidEmailSender
import javax.inject.Inject
import com.universalvideoview.UniversalMediaController
import com.universalvideoview.UniversalVideoView

class MainActivity : AppCompatActivity() , UniversalVideoView.VideoViewCallback{

    private val ipfsDaemon = IPFSDaemon(this)

    @Inject
    lateinit var ipfs: IPFS

    lateinit var mVideoView: UniversalVideoView
    lateinit var mMediaController: UniversalMediaController
    lateinit var mVideoLayout: View
    lateinit var mBottomLayout: View
    private var cachedHeight: Int = 0
    private var isFullscreen: Boolean = false
    private val TAG = "MainActivity"
    private val SEEK_POSITION_KEY = "SEEK_POSITION_KEY"
    private var mSeekPosition: Int = 0
    private var bootstrapAddress: String = "/ip4/172.17.140.83/tcp/4001/ipfs/QmeZCurirTSSaeLQTukuVVEjiijNz7jjReniLxFvKKGeeU"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        App.component().inject(this)
        setContentView(R.layout.activity_main)
        title = "IPFS Video Demo"

        mVideoLayout = findViewById(R.id.video_layout)
        mBottomLayout = findViewById(R.id.bottom_layout)
        mVideoView = findViewById(R.id.videoView) as UniversalVideoView
        mMediaController = findViewById(R.id.media_controller) as UniversalMediaController
        mVideoView.setMediaController(mMediaController)
        setVideoAreaSize()
        mVideoView.setVideoViewCallback(this)

        playVideo.setOnClickListener({
            val inputVideo: String = textVideoHashOrUrl.text.toString()
            var inputLen: Int = inputVideo.length;

            if (mSeekPosition > 0) {
                mVideoView.seekTo(mSeekPosition)
            }

            if (inputLen == 46) {
                mVideoView.setVideoPath("http://127.0.0.1:8080/ipfs/" + inputVideo)
                mVideoView.start()
            } else if (inputLen > 20) {
                mVideoView.setVideoPath(inputVideo)
                mVideoView.start()
            } else {
                mVideoView.setVideoPath("http://clips.vorwaerts-gmbh.de/big_buck_bunny.mp4")
            }
            mMediaController.setTitle("IPFS Video Demon")
        })

        downloadIPFSButton.setOnClickListener({
            ipfsDaemon.download(this) {
                refresh()
            }
        })

        daemonButton.setOnClickListener({
            startService(Intent(this, IPFSDaemonService::class.java))

            daemonButton.visibility = View.GONE
            State.isDaemonRunning = true

            val progressDialog = ProgressDialog(this)
            progressDialog.setMessage("starting daemon")
            progressDialog.show()


            Thread(Runnable {
                var version: VersionInfo? = null
                while (version == null) {
                    try {
                        version = ipfs.info.version()
                    } catch (ignored: Exception) {
                    }
                }

                ipfsDaemon.prepareBootstrap(bootstrapAddress)

                runOnUiThread {
                    progressDialog.dismiss()
                    //startActivityFromClass(DetailsActivity::class.java)
                }
            }).start()

            refresh()
        })

        daemonStopButton.setOnClickListener({
            stopService(Intent(this, IPFSDaemonService::class.java))
            State.isDaemonRunning = false

            refresh()
        })

        mVideoView.setOnCompletionListener(MediaPlayer.OnCompletionListener { Log.d(TAG, "onCompletion ") })

        TraceDroidEmailSender.sendStackTraces("gjz22cn@hotmail.com", this)

        refresh()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        if (!isFullscreen) {
            refresh()
        }
    }

    private fun refresh() {
        daemonButton.visibility = if (ipfsDaemon.isReady() && !State.isDaemonRunning) View.VISIBLE else View.GONE
        daemonStopButton.visibility = if (ipfsDaemon.isReady() && State.isDaemonRunning) View.VISIBLE else View.GONE
        downloadIPFSButton.visibility = if (ipfsDaemon.isReady()) View.GONE else View.VISIBLE
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause ")
        if (mVideoView.isPlaying()) {
            mSeekPosition = mVideoView.getCurrentPosition()
            Log.d(TAG, "onPause mSeekPosition=" + mSeekPosition)
            mVideoView.pause()
        }
    }

    /**
     * 置视频区域大小
     */
    private fun setVideoAreaSize() {
        mVideoLayout.post(Runnable {
            val width = mVideoLayout.getWidth()
            cachedHeight = width.times(405f).div(720f).toInt()
            //cachedHeight = (int) (width * 3f / 4f);
            //cachedHeight = (int) (width * 9f / 16f);
            val videoLayoutParams = mVideoLayout.getLayoutParams()
            videoLayoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            videoLayoutParams.height = cachedHeight
            mVideoLayout.setLayoutParams(videoLayoutParams)
            //mVideoView?.setVideoPath(VIDEO_URL)
            mVideoView.requestFocus()
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Log.d(TAG, "onSaveInstanceState Position=" + mVideoView.getCurrentPosition())
        outState.putInt(SEEK_POSITION_KEY, mSeekPosition)
    }

    override fun onRestoreInstanceState(outState: Bundle) {
        super.onRestoreInstanceState(outState)
        mSeekPosition = outState.getInt(SEEK_POSITION_KEY)
        Log.d(TAG, "onRestoreInstanceState Position=" + mSeekPosition)
    }

    override fun onScaleChange(isFullscreen: Boolean) {
        this.isFullscreen = isFullscreen
        if (isFullscreen) {
            val layoutParams = mVideoLayout.getLayoutParams()
            layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            mVideoLayout.setLayoutParams(layoutParams)
            //this.requestWindowFeature(Window.FEATURE_NO_TITLE);
            mBottomLayout.setVisibility(View.GONE)
        } else {
            val layoutParams = mVideoLayout.getLayoutParams()
            layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            layoutParams.height = this.cachedHeight
            mVideoLayout.setLayoutParams(layoutParams)
            mBottomLayout.setVisibility(View.VISIBLE)
        }

        switchTitleBar(!isFullscreen)
    }

    private fun switchTitleBar(show: Boolean) {
        val supportActionBar = supportActionBar
        Log.e(TAG, "switchTitleBar()")
        if (supportActionBar != null) {
            if (show) {
                Log.e(TAG, "supportActionBar.show()")
                supportActionBar.show()
            } else {
                Log.e(TAG, "supportActionBar.hide()")
                supportActionBar.hide()
            }
        }
    }

    override fun onPause(mediaPlayer: MediaPlayer) {
        Log.d(TAG, "onPause UniversalVideoView callback")
    }

    override fun onStart(mediaPlayer: MediaPlayer) {
        Log.d(TAG, "onStart UniversalVideoView callback")
    }

    override fun onBufferingStart(mediaPlayer: MediaPlayer) {
        Log.d(TAG, "onBufferingStart UniversalVideoView callback")
    }

    override fun onBufferingEnd(mediaPlayer: MediaPlayer) {
        Log.d(TAG, "onBufferingEnd UniversalVideoView callback")
    }

    override fun onBackPressed() {
        if (this.isFullscreen) {
            mVideoView.setFullscreen(false)
        } else {
            super.onBackPressed()
        }
    }
}
