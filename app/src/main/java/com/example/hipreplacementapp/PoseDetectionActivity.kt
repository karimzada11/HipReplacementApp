package com.example.hipreplacementapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.atan2

@androidx.camera.core.ExperimentalGetImage
class PoseDetectionActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var repText: TextView
    private lateinit var angleText: TextView
    private lateinit var debugText: TextView
    private lateinit var resetButton: Button
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var poseDetector: PoseDetector

    private val MIN_CONFIDENCE = 0.7f
    private var selectedActivity: String = "Leg Lifts"

    // Rep counters
    private var leftRepCount = 0
    private var rightRepCount = 0
    private var armRepCount = 0

    // Movement states
    // Leg Lifts (hip extension cycle)
    private var movementDownLeft = false
    private var movementDownRight = false

    // Arm Raises
    private var armMovementDown = false

    // Heel Slides (knee flexion-extension cycle)
    private var heelFlexedLeft = false
    private var heelFlexedRight = false

    // Standing Hip Flexion (hip flex→extend cycle)
    private var hipFlexedLeft = false
    private var hipFlexedRight = false

    // Separate cooldowns (2.5 s)
    private var lastArmRepTs = 0L
    private var lastLeftRepTs = 0L
    private var lastRightRepTs = 0L
    private val cooldownMs = 2500

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pose_detection)

        previewView = findViewById(R.id.previewView)
        repText = findViewById(R.id.repText)
        angleText = findViewById(R.id.angleText)
        debugText = findViewById(R.id.debugText)
        resetButton = findViewById(R.id.resetButton)

        cameraExecutor = Executors.newSingleThreadExecutor()

        val options = PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
        poseDetector = PoseDetection.getClient(options)

        resetButton.setOnClickListener { resetCounters() }

        showActivityChooser()
    }

    private fun showActivityChooser() {
        val items = arrayOf("Arm Raises", "Leg Lifts", "Heel Slides", "Standing Hip Flexion")
        AlertDialog.Builder(this)
            .setTitle("Choose Your Exercise")
            .setItems(items) { _, which ->
                selectedActivity = items[which]
                resetCounters()
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.CAMERA),
                        100
                    )
                } else {
                    startCamera()
                }
            }
            .setCancelable(false)
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImage(imageProxy)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)

        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImage(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val inputImage =
                InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            poseDetector.process(inputImage)
                .addOnSuccessListener { pose ->
                    val landmarks = pose.allPoseLandmarks
                    if (landmarks.isEmpty()) {
                        runOnUiThread {
                            repText.text = "No person detected"
                            angleText.text = ""
                            debugText.text = ""
                        }
                        imageProxy.close()
                        return@addOnSuccessListener
                    }

                    when (selectedActivity) {
                        "Arm Raises" -> handleArmRaises(pose)
                        "Leg Lifts" -> handleLegLifts(pose)
                        "Heel Slides" -> handleHeelSlides(pose)
                        "Standing Hip Flexion" -> handleStandingHipFlexion(pose)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("POSE", "Pose detection failed", e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    /** WHOLE-ARM RAISES: HIP–SHOULDER–WRIST + wrist above shoulder. */
    private fun handleArmRaises(pose: Pose) {
        val rHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val rShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val rWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)

        if (listOf(rHip, rShoulder, rWrist).any { it == null || it.inFrameLikelihood < MIN_CONFIDENCE }) {
            runOnUiThread {
                repText.text = "Raise your whole arm"
                angleText.text = ""
                debugText.text = "Low confidence"
            }
            return
        }

        val angleShoulder = calculateAngle(rHip!!, rShoulder!!, rWrist!!)
        val wristY = rWrist.position.y
        val shoulderY = rShoulder.position.y
        val isArmRaised = wristY < shoulderY - 50 && angleShoulder > 160
        val now = System.currentTimeMillis()

        if (!isArmRaised) armMovementDown = true

        if (isArmRaised && armMovementDown && now - lastArmRepTs > cooldownMs) {
            armRepCount++
            armMovementDown = false
            lastArmRepTs = now
        }

        runOnUiThread {
            repText.text = "Arm Reps: $armRepCount"
            angleText.text = "Shoulder angle: ${angleShoulder.toInt()}°"
            debugText.text = if (isArmRaised) "Phase: UP" else "Phase: DOWN"
        }
    }

    /** LEG LIFTS: whole-leg via hip angle (SHOULDER–HIP–KNEE). */
    private fun handleLegLifts(pose: Pose) {
        val lShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val lHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val lKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val rShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val rHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val rKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)

        if (listOf(lShoulder, lHip, lKnee, rShoulder, rHip, rKnee).any { it == null || it.inFrameLikelihood < MIN_CONFIDENCE }) {
            runOnUiThread {
                repText.text = "Stand in front of camera"
                angleText.text = ""
                debugText.text = "Low confidence"
            }
            return
        }

        val leftHipAngle = calculateAngle(lShoulder!!, lHip!!, lKnee!!)
        val rightHipAngle = calculateAngle(rShoulder!!, rHip!!, rKnee!!)
        val now = System.currentTimeMillis()

        // Down when hip flexes (angle smaller), up when extended
        if (leftHipAngle < 120) movementDownLeft = true
        if (leftHipAngle > 165 && movementDownLeft && now - lastLeftRepTs > cooldownMs) {
            leftRepCount++
            movementDownLeft = false
            lastLeftRepTs = now
        }

        if (rightHipAngle < 120) movementDownRight = true
        if (rightHipAngle > 165 && movementDownRight && now - lastRightRepTs > cooldownMs) {
            rightRepCount++
            movementDownRight = false
            lastRightRepTs = now
        }

        runOnUiThread {
            repText.text = "Leg Lifts — L: $leftRepCount | R: $rightRepCount"
            angleText.text = "L hip: ${leftHipAngle.toInt()}°   R hip: ${rightHipAngle.toInt()}°"
            debugText.text = "L ${if (movementDownLeft) "DOWN→UP" else "READY"} | R ${if (movementDownRight) "DOWN→UP" else "READY"}"
        }
    }

    /** HEEL SLIDES (lying): knee flex <120°, then extend >170°. */
    private fun handleHeelSlides(pose: Pose) {
        val lHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val lKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val lAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val rHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val rKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
        val rAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)

        if (listOf(lHip, lKnee, lAnkle, rHip, rKnee, rAnkle).any { it == null || it.inFrameLikelihood < MIN_CONFIDENCE }) {
            runOnUiThread {
                repText.text = "Heel Slides — get fully in frame"
                angleText.text = ""
                debugText.text = "Low confidence"
            }
            return
        }

        val leftKneeAngle = calculateAngle(lHip!!, lKnee!!, lAnkle!!)
        val rightKneeAngle = calculateAngle(rHip!!, rKnee!!, rAnkle!!)
        val now = System.currentTimeMillis()

        if (leftKneeAngle < 120) heelFlexedLeft = true
        if (rightKneeAngle < 120) heelFlexedRight = true

        if (leftKneeAngle > 170 && heelFlexedLeft && now - lastLeftRepTs > cooldownMs) {
            leftRepCount++
            heelFlexedLeft = false
            lastLeftRepTs = now
        }
        if (rightKneeAngle > 170 && heelFlexedRight && now - lastRightRepTs > cooldownMs) {
            rightRepCount++
            heelFlexedRight = false
            lastRightRepTs = now
        }

        runOnUiThread {
            repText.text = "Heel Slides — L: $leftRepCount | R: $rightRepCount"
            angleText.text = "L knee: ${leftKneeAngle.toInt()}°   R knee: ${rightKneeAngle.toInt()}°"
            debugText.text = "L ${if (heelFlexedLeft) "FLEXED→EXTEND" else "READY"} | R ${if (heelFlexedRight) "FLEXED→EXTEND" else "READY"}"
        }
    }

    /** STANDING HIP FLEXION: lift knee (hip flex <120° & knee above hip), then extend (>165°). */
    private fun handleStandingHipFlexion(pose: Pose) {
        val lShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val lHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val lKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val rShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val rHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val rKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)

        if (listOf(lShoulder, lHip, lKnee, rShoulder, rHip, rKnee).any { it == null || it.inFrameLikelihood < MIN_CONFIDENCE }) {
            runOnUiThread {
                repText.text = "Standing Hip Flexion — stand facing camera"
                angleText.text = ""
                debugText.text = "Low confidence"
            }
            return
        }

        val leftHipAngle = calculateAngle(lShoulder!!, lHip!!, lKnee!!)
        val rightHipAngle = calculateAngle(rShoulder!!, rHip!!, rKnee!!)
        val now = System.currentTimeMillis()

        // Knee above hip (y smaller) + hip angle small enough = flex phase
        val leftKneeAboveHip = lKnee!!.position.y < lHip!!.position.y - 30
        val rightKneeAboveHip = rKnee!!.position.y < rHip!!.position.y - 30
        val leftFlex = (leftHipAngle < 120) && leftKneeAboveHip
        val rightFlex = (rightHipAngle < 120) && rightKneeAboveHip

        if (leftFlex) hipFlexedLeft = true
        if (rightFlex) hipFlexedRight = true

        // Count on return to extension
        if (leftHipAngle > 165 && hipFlexedLeft && now - lastLeftRepTs > cooldownMs) {
            leftRepCount++
            hipFlexedLeft = false
            lastLeftRepTs = now
        }
        if (rightHipAngle > 165 && hipFlexedRight && now - lastRightRepTs > cooldownMs) {
            rightRepCount++
            hipFlexedRight = false
            lastRightRepTs = now
        }

        runOnUiThread {
            repText.text = "Standing Hip Flexion — L: $leftRepCount | R: $rightRepCount"
            angleText.text = "L hip: ${leftHipAngle.toInt()}°   R hip: ${rightHipAngle.toInt()}°"
            debugText.text = "L ${if (hipFlexedLeft) "FLEX→EXTEND" else "READY"} | R ${if (hipFlexedRight) "FLEX→EXTEND" else "READY"}"
        }
    }

    /** Generic angle at middle joint 'b' formed by points a-b-c. */
    private fun calculateAngle(a: PoseLandmark, b: PoseLandmark, c: PoseLandmark): Double {
        val angle = Math.toDegrees(
            (atan2(c.position.y - b.position.y, c.position.x - b.position.x) -
                    atan2(a.position.y - b.position.y, a.position.x - b.position.x)).toDouble()
        )
        return if (angle < 0) angle + 360 else angle
    }

    private fun resetCounters() {
        // Counters
        armRepCount = 0
        leftRepCount = 0
        rightRepCount = 0

        // States
        armMovementDown = false
        movementDownLeft = false
        movementDownRight = false
        heelFlexedLeft = false
        heelFlexedRight = false
        hipFlexedLeft = false
        hipFlexedRight = false

        // Cooldowns
        lastArmRepTs = 0L
        lastLeftRepTs = 0L
        lastRightRepTs = 0L

        runOnUiThread {
            when (selectedActivity) {
                "Arm Raises" -> {
                    repText.text = "Arm Reps: 0"
                    angleText.text = ""
                    debugText.text = "Mode: Arm Raises"
                }
                "Leg Lifts" -> {
                    repText.text = "Leg Lifts — L: 0 | R: 0"
                    angleText.text = ""
                    debugText.text = "Mode: Leg Lifts"
                }
                "Heel Slides" -> {
                    repText.text = "Heel Slides — L: 0 | R: 0"
                    angleText.text = ""
                    debugText.text = "Mode: Heel Slides"
                }
                "Standing Hip Flexion" -> {
                    repText.text = "Standing Hip Flexion — L: 0 | R: 0"
                    angleText.text = ""
                    debugText.text = "Mode: Standing Hip Flexion"
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        poseDetector.close()
    }
}