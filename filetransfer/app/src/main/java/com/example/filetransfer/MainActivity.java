package com.example.filetransfer;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.Socket;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_PICK_FILES = 1;
    private static final int REQUEST_CODE_PERMISSIONS = 2;

    private Button btnPickFiles, btnSendFiles;
    private TextView txtStatus;
    private EditText editTextIp;

    private ArrayList<Uri> fileUris = new ArrayList<>();
    private int laptopPort = 9090;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnPickFiles = findViewById(R.id.btnPickFiles);
        btnSendFiles = findViewById(R.id.btnSendFiles);
        txtStatus = findViewById(R.id.txtStatus);
        editTextIp = findViewById(R.id.editTextIp);

        btnSendFiles.setEnabled(false);

        btnPickFiles.setOnClickListener(v -> pickFiles());
        btnSendFiles.setOnClickListener(v -> {
            if (!fileUris.isEmpty()) {
                String ip = editTextIp.getText().toString().trim();
                if (ip.isEmpty()) {
                    Toast.makeText(this, "Enter server IP address", Toast.LENGTH_SHORT).show();
                    return;
                }
                sendFiles(ip);
            }
        });

        checkPermissions();
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_CODE_PERMISSIONS);
        }
    }

    private void pickFiles() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQUEST_CODE_PICK_FILES);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_PICK_FILES && resultCode == RESULT_OK && data != null) {
            fileUris.clear();

            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                for (int i = 0; i < count; i++) {
                    Uri fileUri = data.getClipData().getItemAt(i).getUri();
                    fileUris.add(fileUri);
                }
            } else if (data.getData() != null) {
                fileUris.add(data.getData());
            }

            txtStatus.setText("Selected " + fileUris.size() + " file(s)");
            btnSendFiles.setEnabled(true);
        }
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) {
                        result = cursor.getString(index);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }

    private void sendFiles(String ip) {
        btnSendFiles.setEnabled(false);
        txtStatus.setText("Connecting to server...");

        new Thread(() -> {
            try (Socket socket = new Socket(ip, laptopPort);
                 DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

                dos.writeUTF("SEND_FILES");
                dos.flush();

                InputStream is = socket.getInputStream();
                byte[] respBuffer = new byte[10];
                int readBytes = is.read(respBuffer);
                String response = new String(respBuffer, 0, readBytes).trim();

                if (!"ACCEPT".equals(response)) {
                    runOnUiThread(() -> {
                        txtStatus.setText("Server rejected the files.");
                        btnSendFiles.setEnabled(true);
                    });
                    return;
                }

                dos.writeInt(fileUris.size());

                for (Uri uri : fileUris) {
                    String fileName = getFileName(uri);
                    if (fileName == null) fileName = "unknown";

                    dos.writeInt(fileName.getBytes().length);
                    dos.write(fileName.getBytes());

                    try (InputStream fis = getContentResolver().openInputStream(uri)) {
                        int fileSize = fis.available();
                        dos.writeLong(fileSize);

                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = fis.read(buffer)) != -1) {
                            dos.write(buffer, 0, bytesRead);
                        }
                    }
                }
                dos.flush();

                runOnUiThread(() -> {
                    txtStatus.setText("Files sent successfully!");
                    btnSendFiles.setEnabled(true);
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    txtStatus.setText("Error: " + e.getMessage());
                    btnSendFiles.setEnabled(true);
                });
            }
        }).start();
    }
}
