package mwang.soundapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;

import android.media.MediaRecorder;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static String mFileName = null;

    private AudioTrack player = null;
    private AudioRecord recorder = null;

    private boolean mStartRecording = true;
    private boolean mStartPlaying = true;

    private static final int SAMPLERATE = 16000;
    private static final int RECORDER_CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int PLAYER_CHANNEL = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final int PLAYER_USAGE = AudioAttributes.USAGE_MEDIA;
    private static final int PLAYER_CONTENT_TYPE = AudioAttributes.CONTENT_TYPE_UNKNOWN;

    private int BufferElement2Rec = 1024;
    private int BytesPerElement = 2;

    private Thread audioThread = null;      // Thread for recording/playing audio

    private Button mRecordButton;
    private Button mPlayButton;

    private final Handler handler = new Handler();
    private final Runnable runner = new Runnable() {
        @Override
        public void run() {
            stopPlaying();
        }
    };

    //Requesting permission to record audio
    private boolean permissionToRecordAccepted = false;
    private String [] permissions = {Manifest.permission.RECORD_AUDIO};

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch(requestCode) {
            case REQUEST_RECORD_AUDIO_PERMISSION:
                permissionToRecordAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                break;
        }
        if (!permissionToRecordAccepted) finish();
    }

    // Decides whether to start recording or stop recording
    private void onRecord(boolean start) {
        if (start)
            startRecording();
        else
            stopRecording();
    }

    // Decides whether or not to start playing
    private void onPlay(boolean start) {
        if (start)
            startPlaying();
    }

    //Initializes AudioRecord object then starts audioThread
    private void startRecording() {
        mPlayButton.setEnabled(false);
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLERATE, RECORDER_CHANNEL, AUDIO_ENCODING, BufferElement2Rec * BytesPerElement);
        recorder.startRecording();
        audioThread = new Thread(new Runnable() {
            public void run() {
                writeAudioDataToFile();
            }
        }, "AudioRecorder Thread");
        audioThread.start();
    }

    // Initializes AudioTrack object then starts audioThread
    private void startPlaying() {
        mRecordButton.setEnabled(false);
        player = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(PLAYER_USAGE)
                        .setContentType(PLAYER_CONTENT_TYPE)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AUDIO_ENCODING)
                        .setSampleRate(SAMPLERATE)
                        .setChannelMask(PLAYER_CHANNEL)
                        .build())
                .setBufferSizeInBytes(BufferElement2Rec * BytesPerElement)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
        audioThread = new Thread(new Runnable() {
            public void run() {
                writeFileToAudioData();
            }
        }, "AudioPlayer Thread");
        audioThread.start();
    }

    // Records audio and writes to a file
    private void writeAudioDataToFile() {
        // Write the output audio in byte
        System.out.println("Recording");
        short sData[] = new short[BufferElement2Rec];

        DataOutputStream os = null;

        // Setup output stream
        try {
            os = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(mFileName)));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        // Record
        while(!mStartRecording) {
            recorder.read(sData, 0, BufferElement2Rec);
            try {
                for(int i = 0; i < BufferElement2Rec; i++) {
                    os.writeShort(sData[i]);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Flush and close
        try {
            System.out.println("closing file");
            os.flush();
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Reads file and plays the audio
    private void writeFileToAudioData() {
        DataInputStream is = null;

        // Setup input stream
        try {
            is = new DataInputStream(new BufferedInputStream(new FileInputStream(mFileName)));
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Start playing
        player.play();

        // Read audio data from file and write to audio stream.
        try {
            while (is.available() > 0 && !mStartPlaying) {
                short sData[] = new short[BufferElement2Rec];
                int write_size = BufferElement2Rec;
                for (int i = 0; i < BufferElement2Rec; i++) {
                    try {
                        sData[i] = is.readShort();
                    } catch (EOFException e) {
                        write_size = i;
                        break;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                player.write(sData, 0, write_size);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Close stream
        try {
            System.out.println("closing file");
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Calls stopPlaying to release AudioTrack
        handler.post(runner);
    }

    // Stops audio recording and releases AudioRecord
    private void stopRecording() {
        if (recorder != null) {
            recorder.stop();
            recorder.release();
            recorder = null;
            audioThread = null;
        }
        mPlayButton.setEnabled(true);
    }

    // Stops audio playback and releases AudioTrack
    private void stopPlaying() {
        if(player != null) {
            player.stop();
            player.release();
            player = null;
            audioThread = null;
        }
        mRecordButton.setEnabled(true);
        mPlayButton.setText("Play");
        mStartPlaying = true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        mRecordButton = (Button) findViewById(R.id.record_button);
        mRecordButton.setOnClickListener(this);

        mPlayButton = (Button) findViewById(R.id.play_button);
        mPlayButton.setOnClickListener(this);

        mFileName = getExternalCacheDir().getAbsolutePath();
        mFileName += "/audiorecordtest.pcm";
        System.out.println(mFileName);

        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (recorder != null) {
            recorder.stop();
            recorder.release();
            recorder = null;
        }
        if (player != null) {
            player.stop();
            player.release();
            player = null;
        }
    }

    // Button click handler
    public void onClick(View v) {
        switch(v.getId()) {
            case R.id.record_button: {
                onRecord(mStartRecording);
                if (mStartRecording)
                    mRecordButton.setText("Stop Recording");
                else
                    mRecordButton.setText("Record");
                mStartRecording = !mStartRecording;
                break;
            }
            case R.id.play_button: {
                onPlay(mStartPlaying);
                if (mStartPlaying)
                    mPlayButton.setText("Stop Playing");
                else
                    mPlayButton.setText("Play");
                mStartPlaying = !mStartPlaying;
                break;
            }
        }
    }
}
