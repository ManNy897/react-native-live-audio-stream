package com.imxiqi.rnliveaudiostream;

import android.media.AudioFormat;
import android.media.AudioRecord;
import static android.media.AudioRecord.READ_BLOCKING;
import android.media.MediaRecorder.AudioSource;
import android.util.Base64;
import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.lang.Math;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.Interpreter;
import java.nio.MappedByteBuffer;
import org.tensorflow.lite.support.common.FileUtil;
import java.io.IOException;
import org.tensorflow.op.core.LinSpace;
import org.tensorflow.EagerSession;
import org.tensorflow.op.Scope;
import org.tensorflow.op.OpScope;
import org.tensorflow.op.core.Constant;
import org.tensorflow.Graph;









public class RNLiveAudioStreamModule extends ReactContextBaseJavaModule {

    private final ReactApplicationContext reactContext;
    private DeviceEventManagerModule.RCTDeviceEventEmitter eventEmitter;

    private int sampleRateInHz;
    private int channelConfig;
    private int audioFormat;
    private int audioSource;

    private AudioRecord recorder;
    private int bufferSize;
    private boolean isRecording;
    private Interpreter model;

    public RNLiveAudioStreamModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;


    }

    @Override
    public String getName() {
        return "RNLiveAudioStream";
    }

    @ReactMethod
    public void init(ReadableMap options) {
        sampleRateInHz = 44100;
        if (options.hasKey("sampleRate")) {
            sampleRateInHz = options.getInt("sampleRate");
        }

        channelConfig = AudioFormat.CHANNEL_IN_MONO;
        if (options.hasKey("channels")) {
            if (options.getInt("channels") == 2) {
                channelConfig = AudioFormat.CHANNEL_IN_STEREO;
            }
        }

        audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        if (options.hasKey("bitsPerSample")) {
            if (options.getInt("bitsPerSample") == 8) {
                audioFormat = AudioFormat.ENCODING_PCM_8BIT;
            }
            if (options.getInt("bitsPerSample") == 32) {
                audioFormat = AudioFormat.ENCODING_PCM_FLOAT;
            }
        }

        audioSource = AudioSource.VOICE_RECOGNITION;
        if (options.hasKey("audioSource")) {
            audioSource = options.getInt("audioSource");
        }

        isRecording = false;
        eventEmitter = reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class);

        bufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);

        if (options.hasKey("bufferSize")) {
            bufferSize = Math.max(bufferSize, options.getInt("bufferSize"));
        }

        int recordingBufferSize = bufferSize * 3;
        recorder = new AudioRecord(audioSource, sampleRateInHz, channelConfig, audioFormat, recordingBufferSize);
    }

    @ReactMethod
    public void start() {
        isRecording = true;
        recorder.startRecording();

        Thread recordingThread = new Thread(new Runnable() {
            public void run() {
                try {
                    int bytesRead;
                    int count = 0;
                    String base64Data;
                    MappedByteBuffer mb=FileUtil.loadMappedFile(reactContext,"tflite_output.tflite");
                    // File modelFile = new File("android_asset/tflite_output.tflite");
                    Interpreter model = new Interpreter(mb);
//                    Graph graph =  new Graph();
//                    Scope scope = new OpScope(graph);
//                    LinSpace linspaceOperator = LinSpace.create(scope, Constant.scalarOf(scope, 0), Constant.scalarOf(scope, 7180), Constant.scalarOf(scope, 360));

//                    Tensor t = Tensor.of(...);

//                    this.centMapping = tf.add(tf.linspace(0, 7180, 360), tf.tensor(1997.3794084376191));

                    
                    if(audioFormat == AudioFormat.ENCODING_PCM_FLOAT) {
                        float[] floatBuffer = new float[bufferSize];

                        while (isRecording) {
                            bytesRead = recorder.read(floatBuffer, 0, floatBuffer.length,  READ_BLOCKING);
                            byte[] bytes = new byte[4 * floatBuffer.length];
                            ByteBuffer.wrap(bytes).asFloatBuffer().put(floatBuffer);
                            // skip first 2 buffers to eliminate "click sound"
                            if (bytesRead > 0 && ++count > 2) {
                                base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP);
                                eventEmitter.emit("data", base64Data);
                            }

                            float[] inputFloatArray = Arrays.copyOfRange(floatBuffer, 0, 1024);
                            FloatBuffer input = FloatBuffer.wrap(inputFloatArray);
                            FloatBuffer output = FloatBuffer.allocate(360);
                            model.run(input, output);
                            float[] result = output.array();
                            System.out.println(Arrays.toString(result));

                        }
                    } else {
                        byte[] byteBuffer = new byte[bufferSize];
                        while (isRecording) {
                            bytesRead = recorder.read(byteBuffer, 0, byteBuffer.length,  READ_BLOCKING);
                            
                            // skip first 2 buffers to eliminate "click sound"
                            if (bytesRead > 0 && ++count > 2) {
                                base64Data = Base64.encodeToString(byteBuffer, Base64.NO_WRAP);
                                eventEmitter.emit("data", base64Data);
                            }

                        }
                    }

                    recorder.stop();
                } catch (IOException e){
                    System.out.println(e);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        recordingThread.start();
    }

    @ReactMethod
    public void stop(Promise promise) {
        isRecording = false;
    }
}
