package com.example.experiment;

import android.app.Activity;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class CareerDetailActivity extends Activity {
    private static final String TAG = "CareerDetailActivity";
    private DatabaseManager dbManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_career_detail);

        // Initialize DatabaseManager
        dbManager = new DatabaseManager(this);

        try {
            // Get the career title passed from the previous activity
            String careerTitle = getIntent().getStringExtra("CAREER_TITLE");

            // Initialize all TextViews
            TextView tvOccupationName = (TextView) findViewById(R.id.tvOccupationName);
            TextView tvMedianPay = (TextView) findViewById(R.id.tvMedianPay);
            TextView tvEducationRequired = (TextView) findViewById(R.id.tvEducationRequired);
            TextView tvEmployment2023 = (TextView) findViewById(R.id.tvEmployment2023);
            TextView tvJobOutlook = (TextView) findViewById(R.id.tvJobOutlook);
            ImageView ivCareerImage = (ImageView) findViewById(R.id.ivCareerImage);

            // Retrieve career details from database
            Cursor cursor = dbManager.getCareerDetailsByTitle(careerTitle);

            if (cursor != null && cursor.moveToFirst()) {
                // Extract details from cursor
                String occupationTitle = cursor.getString(cursor.getColumnIndex("occupation_title"));
                float medianAnnualWage = cursor.getFloat(cursor.getColumnIndex("median_annual_wage"));
                float employmentPercentChange = cursor.getFloat(cursor.getColumnIndex("employment_percent_change"));
                String educationWorkExperience = cursor.getString(cursor.getColumnIndex("education_work_experience"));
                float employment2023 = cursor.getFloat(cursor.getColumnIndex("employment_2023"));

                // Set text views
                tvOccupationName.setText(occupationTitle);
                tvMedianPay.setText(String.format("Median Annual Wage: $%,d", (int)medianAnnualWage));
                tvEducationRequired.setText("Education: " + educationWorkExperience);
                tvEmployment2023.setText(String.format("2023 Employment: %.1f thousand", employment2023));
                tvJobOutlook.setText(String.format("Expected Employment %% Change (2023-2033): %.1f%%", employmentPercentChange));

                // Handle image loading
                if (ivCareerImage != null) {
                    // Normalize image name
                    String imageName = occupationTitle.toLowerCase().replace(" ", "_");

                    // Special case for Web Developers
                    if (occupationTitle.equalsIgnoreCase("Web Developers")) {
                        imageName = "web_developers";
                    }

                    Log.d(TAG, "Attempting to load image: " + imageName);

                    // Get resource ID
                    int imageResourceId = getResources().getIdentifier(
                            imageName,
                            "drawable",
                            getPackageName()
                    );

                    Log.d(TAG, "Image Resource ID: " + imageResourceId);

                    if (imageResourceId != 0) {
                        try {
                            // Determine view dimensions or use default
                            int viewWidth = ivCareerImage.getWidth();
                            int viewHeight = ivCareerImage.getHeight();

                            // Use default size if not yet measured
                            if (viewWidth <= 0) viewWidth = 500;
                            if (viewHeight <= 0) viewHeight = 500;

                            // Decode sampled bitmap
                            Bitmap bitmap = decodeSampledBitmapFromResource(
                                    getResources(),
                                    imageResourceId,
                                    viewWidth,
                                    viewHeight
                            );

                            if (bitmap != null) {
                                ivCareerImage.setImageBitmap(bitmap);
                                Log.d(TAG, "Image set successfully");
                                Log.d(TAG, "Bitmap size: " + bitmap.getWidth() + "x" + bitmap.getHeight());
                            } else {
                                Log.e(TAG, "Failed to decode bitmap");
                                ivCareerImage.setVisibility(View.GONE);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error loading image", e);
                            ivCareerImage.setVisibility(View.GONE);
                        }
                    } else {
                        Log.e(TAG, "No drawable resource found for: " + imageName);
                        ivCareerImage.setVisibility(View.GONE);
                    }
                }

                cursor.close();
            } else {
                Toast.makeText(this, "No details found for this career", Toast.LENGTH_SHORT).show();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error loading career details", e);
            Toast.makeText(this, "Error loading career details", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    /**
     * Decodes a sampled bitmap from resources to reduce memory consumption
     */
    private Bitmap decodeSampledBitmapFromResource(Resources res, int resId, int reqWidth, int reqHeight) {
        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, resId, options);

        // Log original image dimensions
        Log.d(TAG, "Original Image Dimensions: " + options.outWidth + "x" + options.outHeight);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;

        // Use a more memory-efficient config
        options.inPreferredConfig = Bitmap.Config.RGB_565;

        return BitmapFactory.decodeResource(res, resId, options);
    }

    /**
     * Calculates the sample size for bitmap decoding
     */
    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        Log.d(TAG, "Calculated sample size: " + inSampleSize);
        return inSampleSize;
    }
}



