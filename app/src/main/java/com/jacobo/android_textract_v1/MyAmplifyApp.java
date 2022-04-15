package com.jacobo.android_textract_v1;

import android.app.Application;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import com.amazonaws.regions.Region;
import com.amazonaws.services.rekognition.AmazonRekognitionClient;
import com.amazonaws.services.rekognition.model.CreateCollectionRequest;
import com.amazonaws.services.textract.AmazonTextract;
import com.amazonaws.services.textract.model.*;
import com.amplifyframework.AmplifyException;
import com.amplifyframework.auth.cognito.AWSCognitoAuthPlugin;
import com.amplifyframework.core.Amplify;
import com.amplifyframework.predictions.aws.AWSPredictionsEscapeHatch;
import com.amplifyframework.predictions.aws.AWSPredictionsPlugin;
import com.amplifyframework.predictions.models.Table;
import com.amplifyframework.predictions.models.TextFormatType;
import com.amplifyframework.predictions.result.IdentifyDocumentTextResult;
import com.amplifyframework.predictions.result.IdentifyTextResult;
import com.amplifyframework.storage.s3.AWSS3StoragePlugin;

import java.io.*;
import java.util.List;

import static com.amazonaws.regions.Regions.US_WEST_1;

public class MyAmplifyApp extends Application {

    private final String bucket = "jacobo-textract-bucket";
    private final String document = "receipt.jpg";
    private IdentifyDocumentTextResult identifyResult;

    public void onCreate() {
        super.onCreate();

        try {
            Amplify.addPlugin(new AWSCognitoAuthPlugin());
            Amplify.addPlugin(new AWSS3StoragePlugin());
            Amplify.addPlugin(new AWSCognitoAuthPlugin());
            Amplify.addPlugin(new AWSPredictionsPlugin());
            Amplify.configure(getApplicationContext());
            Log.i("MyAmplifyApp", "Initialized Amplify");
        } catch (AmplifyException error) {
            Log.e("MyAmplifyApp", "Could not initialize Amplify", error);
        }

//        downloadFile();

//        File file = new File(getApplicationContext().getFilesDir() + "/" + document);
//        Bitmap image = fileToBitmap(file);
//        detectText(image);
        escapeHatch();
        System.out.println("---END---");
//        System.out.println("ENDING---TABLES SIZE" + identifyResult.getTables().size());
//        System.out.println("ENDING---KVs SIZE" + identifyResult.getKeyValues().size());
    }

//    static final int REQUEST_IMAGE_CAPTURE = 1;
//
//    private void dispatchTakePictureIntent() {
//        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
//        try {
//            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
//        } catch (ActivityNotFoundException e) {
//            // display error state to the user
//        }
//    }

    private void printTables(){
        System.out.println("ENDING---TABLES SIZE=" + identifyResult.getTables().size());
        System.out.println("ENDING---KVs SIZE=" + identifyResult.getKeyValues().size());
        System.out.println("ENDING---words SIZE=" + identifyResult.getWords().size());
        System.out.println("ENDING---lines SIZE=" + identifyResult.getLines().size());
        System.out.println("ENDING---selections SIZE=" + identifyResult.getSelections().size());

    }

    private void escapeHatch(){
        // Obtain reference to the plugin
        AWSPredictionsPlugin predictionsPlugin = (AWSPredictionsPlugin)
                Amplify.Predictions.getPlugin("awsPredictionsPlugin");
        AWSPredictionsEscapeHatch escapeHatch = predictionsPlugin.getEscapeHatch();

        // Send a new request to the Textract endpoint directly using the client
        AmazonTextract _client = escapeHatch.getTextractClient();
        _client.setRegion(Region.getRegion(US_WEST_1));
        DetectDocumentTextRequest _request = new DetectDocumentTextRequest()
                .withDocument(new Document().withS3Object(new S3Object().withName(document).withBucket(bucket)));

        Thread thread = new Thread(new Runnable() {

            @Override
            public void run() {
                try  {
                    //Your code goes here
                    DetectDocumentTextResult result = _client.detectDocumentText(_request);
                    List<Block> blocks = result.getBlocks();
                    for (Block block : blocks) {
                        DisplayBlockInfo(block);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        thread.start();


    }

    //Displays information from a block returned by text detection and text analysis
    private void DisplayBlockInfo(Block block) {
        System.out.println("Block Id : " + block.getId());
        if (block.getText()!=null)
            System.out.println("    Detected text: " + block.getText());
        System.out.println("    Type: " + block.getBlockType());

        if (block.getBlockType().equals("PAGE") !=true) {
            System.out.println("    Confidence: " + block.getConfidence().toString());
        }
        if(block.getBlockType().equals("CELL"))
        {
            System.out.println("    Cell information:");
            System.out.println("        Column: " + block.getColumnIndex());
            System.out.println("        Row: " + block.getRowIndex());
            System.out.println("        Column span: " + block.getColumnSpan());
            System.out.println("        Row span: " + block.getRowSpan());

        }

        System.out.println("    Relationships");
        List<Relationship> relationships=block.getRelationships();
        if(relationships!=null) {
            for (Relationship relationship : relationships) {
                System.out.println("        Type: " + relationship.getType());
                System.out.println("        IDs: " + relationship.getIds().toString());
            }
        } else {
            System.out.println("        No related Blocks");
        }

        System.out.println("    Geometry");
        System.out.println("        Bounding Box: " + block.getGeometry().getBoundingBox().toString());
        System.out.println("        Polygon: " + block.getGeometry().getPolygon().toString());

        List<String> entityTypes = block.getEntityTypes();

        System.out.println("    Entity Types");
        if(entityTypes!=null) {
            for (String entityType : entityTypes) {
                System.out.println("        Entity Type: " + entityType);
            }
        } else {
            System.out.println("        No entity type");
        }
        if(block.getPage()!=null)
            System.out.println("    Page: " + block.getPage());
        System.out.println();
    }

    private Bitmap fileToBitmap(File file) {
        String filePath = file.getPath();
        Bitmap bitmap = BitmapFactory.decodeFile(filePath);
        return bitmap;
    }

    private void detectText(Bitmap image) {
        Amplify.Predictions.identify(
                TextFormatType.TABLE,
                image,
                result -> {
                    identifyResult = (IdentifyDocumentTextResult) result;
                    Log.i("MyAmplifyApp", identifyResult.getFullText());
                    printTables();
//                    DisplayBlockInfo((Block) identifyResult);
                },
                error -> Log.e("MyAmplifyApp", "Identify text failed", error)

        );
    }

    private void generateUri(){
        Amplify.Storage.getUrl(
                document,
                result -> Log.i("MyAmplifyApp", "Successfully generated: " + result.getUrl()),
                error -> Log.e("MyAmplifyApp", "URL generation failure", error)
        );
    }

    private void downloadFile(){
        Amplify.Storage.downloadFile(
                document,
                new File(getApplicationContext().getFilesDir() + "/"+document),
                result -> Log.i("MyAmplifyApp", "Successfully downloaded: " + result.getFile().getName()),
                error -> Log.e("MyAmplifyApp",  "Download Failure", error)
        );
    }

    private void uploadFile() {
        File exampleFile = new File(getApplicationContext().getFilesDir(), "ExampleKey");

        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(exampleFile));
            writer.append("Example file contents");
            writer.close();
        } catch (Exception exception) {
            Log.e("MyAmplifyApp", "Upload failed", exception);
        }

        Amplify.Storage.uploadFile(
                "ExampleKey",
                exampleFile,
                result -> Log.i("MyAmplifyApp", "Successfully uploaded: " + result.getKey()),
                storageFailure -> Log.e("MyAmplifyApp", "Upload failed", storageFailure)
        );
    }

    private void uploadInputStream() {
//        Uri uri = ?
//        try {
//            InputStream exampleInputStream = getContentResolver().openInputStream(uri);
//
//            Amplify.Storage.uploadInputStream(
//                    "ExampleKey",
//                    exampleInputStream,
//                    result -> Log.i("MyAmplifyApp", "Successfully uploaded: " + result.getKey()),
//                    storageFailure -> Log.e("MyAmplifyApp", "Upload failed", storageFailure)
//            );
//        }  catch (FileNotFoundException error) {
//            Log.e("MyAmplifyApp", "Could not find file to open for input stream.", error);
//        }
    }
}
