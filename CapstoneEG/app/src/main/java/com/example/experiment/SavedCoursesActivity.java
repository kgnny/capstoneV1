package com.example.experiment;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SavedCoursesActivity extends Activity {
    private static final String TAG = "SavedCourses";

    private ListView savedCoursesListView;
    private SimpleCursorAdapter adapter;
    private Button btnReturnToCourseSearch;

    private DatabaseManager dbManager;
    private ExecutorService executorService;
    private Handler mainThreadHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_saved_courses);

        // Initialize thread management
        executorService = Executors.newSingleThreadExecutor();
        mainThreadHandler = new Handler(Looper.getMainLooper());

        // Initialize UI components
        savedCoursesListView = findViewById(R.id.savedCoursesListView);
        btnReturnToCourseSearch = findViewById(R.id.btnReturnToCourseSearch);

        // Initialize database manager
        dbManager = DatabaseManager.getInstance(this);

        // Set up adapter with empty cursor initially
        String[] from = new String[] {"course", "description"};
        int[] to = new int[] {android.R.id.text1};

        adapter = new SimpleCursorAdapter(
                this,
                android.R.layout.simple_list_item_1,
                null,
                from,
                to,
                0
        ) {
            @Override
            public void bindView(View view, android.content.Context context, Cursor cursor) {
                // Get values from cursor
                @SuppressLint("Range") String course = cursor.getString(cursor.getColumnIndex("course"));
                @SuppressLint("Range") String description = cursor.getString(cursor.getColumnIndex("description"));

                TextView text1 = view.findViewById(android.R.id.text1);
                text1.setText(course + " - " + description);
            }
        };
        savedCoursesListView.setAdapter(adapter);

        // Load saved courses
        loadSavedCourses();

        // Set click listeners
        savedCoursesListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Cursor cursor = (Cursor) adapter.getItem(position);
                @SuppressLint("Range") int courseId = cursor.getInt(cursor.getColumnIndex("_id"));

                // Open course details
                Intent intent = new Intent(SavedCoursesActivity.this, CourseDetailsActivity.class);
                intent.putExtra("course_id", courseId);
                startActivity(intent);
            }
        });

        btnReturnToCourseSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish(); // Return to previous screen
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload saved courses when coming back to this activity
        loadSavedCourses();
    }

    private void loadSavedCourses() {
        // Get username from SharedPreferences
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        final String username = prefs.getString("username", "");

        // Get saved courses from SharedPreferences
        final SharedPreferences savedCoursesPrefs = getSharedPreferences("SavedCourses_" + username, MODE_PRIVATE);
        final String savedCoursesString = savedCoursesPrefs.getString("courses", "");

        if (savedCoursesString.isEmpty()) {
            Toast.makeText(this, "No saved courses found", Toast.LENGTH_SHORT).show();
            // Create an empty cursor to clear the list
            MatrixCursor emptyCursor = new MatrixCursor(new String[] {"_id", "course", "description"});
            adapter.changeCursor(emptyCursor);
            return;
        }

        // Parse saved course IDs (comma-separated)
        final String[] courseIdsArray = savedCoursesString.split(",");
        if (courseIdsArray.length == 0) {
            Toast.makeText(this, "No saved courses found", Toast.LENGTH_SHORT).show();
            // Create an empty cursor to clear the list
            MatrixCursor emptyCursor = new MatrixCursor(new String[] {"_id", "course", "description"});
            adapter.changeCursor(emptyCursor);
            return;
        }

        // Convert to a Set for faster lookups
        final Set<String> savedCourseIds = new HashSet<>(Arrays.asList(courseIdsArray));

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                // Get all courses first
                final Cursor allCourses = dbManager.getAllCourses();

                // Create a new MatrixCursor to hold only the saved courses
                final MatrixCursor savedCoursesCursor = new MatrixCursor(
                        new String[] {"_id", "course", "description"});

                if (allCourses != null && allCourses.getCount() > 0) {
                    while (allCourses.moveToNext()) {
                        @SuppressLint("Range") int id = allCourses.getInt(allCourses.getColumnIndex("_id"));

                        // Only include courses that are in the saved list
                        if (savedCourseIds.contains(String.valueOf(id))) {
                            @SuppressLint("Range") String course = allCourses.getString(allCourses.getColumnIndex("course"));
                            @SuppressLint("Range") String description = allCourses.getString(allCourses.getColumnIndex("description"));

                            savedCoursesCursor.addRow(new Object[] {id, course, description});
                        }
                    }

                    allCourses.close();
                }

                // Update the UI on the main thread
                mainThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (savedCoursesCursor.getCount() > 0) {
                            adapter.changeCursor(savedCoursesCursor);
                            Log.d(TAG, "Loaded " + savedCoursesCursor.getCount() + " saved courses");
                        } else {
                            Toast.makeText(SavedCoursesActivity.this,
                                    "No saved courses found",
                                    Toast.LENGTH_SHORT).show();
                            // Clear the list with an empty cursor
                            MatrixCursor emptyCursor = new MatrixCursor(new String[] {"_id", "course", "description"});
                            adapter.changeCursor(emptyCursor);
                        }
                    }
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
        if (adapter != null && adapter.getCursor() != null) {
            adapter.getCursor().close();
        }
    }
}