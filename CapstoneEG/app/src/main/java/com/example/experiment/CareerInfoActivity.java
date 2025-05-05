package com.example.experiment;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class CareerInfoActivity extends Activity {
    private List<String[]> careerDataList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_career_info_simple);

        // Read careers from CSV
        readCareersFromCSV();

        // Create a list of just occupation names
        final List<String> occupationNames = new ArrayList<>();
        for (String[] careerData : careerDataList) {
            // Ensure we have at least one column and add only the first column (occupation name)
            if (careerData.length > 0) {
                occupationNames.add(careerData[0]);
            }
        }

        // Find ListView
        ListView listView = (ListView) findViewById(R.id.lvCareers);

        // Set up adapter to show only first column
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                occupationNames);
        listView.setAdapter(adapter);

        // Handle item clicks to show detailed information
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // Create intent to start CareerDetailActivity
                Intent intent = new Intent(CareerInfoActivity.this, CareerDetailActivity.class);

                // Use the full career title
                String careerTitle = careerDataList.get(position)[0];

                // Add logging
                Log.d("CareerInfoActivity", "Selected career title: " + careerTitle);

                intent.putExtra("CAREER_TITLE", careerTitle);

                startActivity(intent);
            }
        });
    }

    // Read careers from CSV
    private void readCareersFromCSV() {
        careerDataList.clear(); // Clear any existing data
        try {
            // Open the CSV file from the assets folder
            InputStream inputStream = getAssets().open("occupation_15_filtered.csv");
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            String line;
            // Skip header if exists
            reader.readLine();

            // Read careers from CSV
            while ((line = reader.readLine()) != null) {
                // Trim the line and split by tab
                String[] allColumns = line.trim().split("\t");

                // Ensure we have at least the occupation title
                if (allColumns.length > 0) {
                    // Create a new array to store only the meaningful columns
                    String[] columns = new String[1];

                    // Use the full first column as the occupation name
                    // Handle cases where the full title might span multiple columns
                    StringBuilder fullTitle = new StringBuilder(allColumns[0]);

                    // If there are more columns and the next column looks like an occupation code,
                    // don't include it in the title
                    for (int i = 1; i < allColumns.length; i++) {
                        if (allColumns[i].matches("\\d+-\\d+")) {
                            break;
                        }
                        fullTitle.append(" ").append(allColumns[i]);
                    }

                    columns[0] = fullTitle.toString().trim();

                    // Add parsed line to career data list
                    careerDataList.add(columns);
                }
            }

            reader.close();
        } catch (IOException e) {
            Log.e("CareerInfoActivity", "Error reading CSV", e);
        }
    }
}
