package com.example.myapplication;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.myapplication.ml.AMDDrebinModel;
import com.example.myapplication.ml.AutoModel1cnnPerApiDrebinBenign7;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.HashSet;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private final static int PICK_FROM_GALLERY = 833;
    private final static int GALLERY_REQUEST = 318;

    ImageView selectedImage;
    String FilePath;
    TextView info;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button btnUpload = findViewById(R.id.btnUpload);
        Button btnChoose = findViewById(R.id.btnChoose);
        info = findViewById(R.id.info);
        btnUpload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, UploadActivity.class));
            }
        });

        btnChoose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestImageGallery();
            }
        });
    }

    private void requestImageGallery() {
        if (Build.VERSION.SDK_INT >= 23) {
            String[] PERMISSIONS = {android.Manifest.permission.WRITE_EXTERNAL_STORAGE, android.Manifest.permission.READ_EXTERNAL_STORAGE};
            if (!hasPermissions(MainActivity.this, PERMISSIONS)) {
                ActivityCompat.requestPermissions((Activity) MainActivity.this, PERMISSIONS, GALLERY_REQUEST);
            } else {
                openGallery();
            }
        } else {
            openGallery();
        }
    }

    private static boolean hasPermissions(Context context, String... permissions) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }


    private void openGallery() {
        Intent myFileIntent = new Intent(Intent.ACTION_GET_CONTENT);
        myFileIntent.setType("*/*");
        Intent.createChooser(myFileIntent, "Choose a file");
        startActivityForResult(myFileIntent, 10);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
//        Toast.makeText(this, "Comes inside!", Toast.LENGTH_SHORT).show();


        switch (requestCode){
            case 10:
                if(resultCode== RESULT_OK)
                {
                    Uri fileUri = data.getData();

                    FilePath = FileUtils.getPath(this, fileUri);

                    File file =  new File(FilePath);
                    if(file.exists())
                    {
                        ApkFile apk = new ApkFile(FilePath);
                        try {
                            int[] dcm = Analyzer.decode2(apk);
                            ByteBuffer input = ByteBuffer.allocateDirect(1*44*44*1 * 4);//Input Length * Size of data (int)
                            IntBuffer x = input.asIntBuffer();
                            x.put(dcm);
                            try {
                                AMDDrebinModel model = AMDDrebinModel.newInstance(getApplicationContext());

                                // Creates inputs for reference.
                                TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 44, 44, 1}, DataType.FLOAT32);
                                inputFeature0.loadBuffer(input);
                                // Runs model inference and gets result.
                                AMDDrebinModel.Outputs outputs = model.process(inputFeature0);
                                TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();
                                int code = Analyzer.getCode(outputFeature0.getFloatArray());
                                System.out.println("=============");
                                System.out.println(code);
                                System.out.println("=============");
                                // Releases model resources if no longer used.
                                model.close();
                            } catch (IOException e) {
                                // TODO Handle the exception
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        info.setText("[+]File to location to upload: \n" +file.getPath());

                    }else{
                        Toast.makeText(this, "File cannot be added!", Toast.LENGTH_SHORT).show();
                        info.setText("[!]File cannot be added :(");

                    }

                }

                break;
        }


    }
}