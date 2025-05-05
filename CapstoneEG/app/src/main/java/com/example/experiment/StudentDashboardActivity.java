package com.example.experiment;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StudentDashboardActivity extends Activity {
    private EditText searchBar;
    private ListView classListView;
    private SimpleCursorAdapter adapter;
    private DatabaseManager dbManager;
    private ExecutorService executorService;
    private Handler mainThreadHandler;
    private Button btnViewSavedCourses;
    private Button btnReturnToStudentDashboard;

    private static final String TAG = "StudentDashboard";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_dashboard);

        // Initialize thread management
        executorService = Executors.newSingleThreadExecutor();
        mainThreadHandler = new Handler(Looper.getMainLooper());

        // Initialize UI elements
        searchBar = findViewById(R.id.searchBar);
        classListView = findViewById(R.id.classListView);
        btnViewSavedCourses = findViewById(R.id.btnViewSavedCourses);
        btnReturnToStudentDashboard = findViewById(R.id.logoutButton); // Reusing the logout button

        // Set button text
        btnReturnToStudentDashboard.setText("Return to Student Dashboard");

        // Initialize database manager
        dbManager = DatabaseManager.getInstance(this);

        // Set up ListView adapter with empty cursor initially
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
        classListView.setAdapter(adapter);

        // Load courses from database
        loadCoursesFromDatabase();

        // Handle class selection - Launch CourseDetailsActivity instead of direct enrollment
        classListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Cursor cursor = (Cursor) adapter.getItem(position);
                @SuppressLint("Range") int courseId = cursor.getInt(cursor.getColumnIndex("_id"));

                // Start CourseDetailsActivity with course ID
                Intent intent = new Intent(StudentDashboardActivity.this, CourseDetailsActivity.class);
                intent.putExtra("course_id", courseId);
                startActivity(intent);
            }
        });

        // Setup saved courses button
        btnViewSavedCourses.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Start SavedCoursesActivity
                Intent intent = new Intent(StudentDashboardActivity.this, SavedCoursesActivity.class);
                startActivity(intent);
            }
        });

        // Setup return to dashboard button
        btnReturnToStudentDashboard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                returnToStudentDashboard();
            }
        });
    }

    private void loadCoursesFromDatabase() {
        Log.d(TAG, "Loading courses from database");

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                final Cursor cursor = dbManager.getAllCourses();

                mainThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (cursor != null && cursor.getCount() > 0) {
                            Log.d(TAG, "Retrieved " + cursor.getCount() + " courses");
                            adapter.changeCursor(cursor);
                        } else {
                            Log.d(TAG, "No courses found in database");
                            Toast.makeText(StudentDashboardActivity.this,
                                    "No courses found in database",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });
    }

    // Search Functionality
    public void searchClasses(View view) {
        final String query = searchBar.getText().toString().trim();
        if (query.isEmpty()) {
            Toast.makeText(this, "Enter a class name to search", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "Searching for: " + query);

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                final Cursor cursor = dbManager.searchCourses(query);

                mainThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (cursor != null && cursor.getCount() > 0) {
                            Log.d(TAG, "Found " + cursor.getCount() + " matching courses");
                            adapter.changeCursor(cursor);
                        } else {
                            Log.d(TAG, "No matching courses found");
                            Toast.makeText(StudentDashboardActivity.this,
                                    "No matching courses found",
                                    Toast.LENGTH_SHORT).show();
                            // Load all courses again if search returns no results
                            loadCoursesFromDatabase();
                        }
                    }
                });
            }
        });
    }

    // Return to student dashboard
    private void returnToStudentDashboard() {
        Intent intent = new Intent(this, StudentLandingPage.class);
        startActivity(intent);
        finish();
    }

    // Handle the renamed logoutUser method (keep for compatibility with onClick attribute in layout)
    public void logoutUser(View view) {
        returnToStudentDashboard();
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
        // Don't close dbManager here as it's a singleton
    }
}